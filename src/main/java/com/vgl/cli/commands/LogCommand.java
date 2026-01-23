package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

public class LogCommand implements Command {
    @Override
    public String name() {
        return "log";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.logUsage());
            return 0;
        }

        boolean veryVerbose = args.contains("-vv");
        boolean verbose = args.contains("-v") || veryVerbose;
        boolean showAll = args.contains("-all");
        boolean graph = args.contains("-graph");

        // Collect positional arguments (non-flags) - supports multiple commits or date specs
        java.util.List<String> positional = new java.util.ArrayList<>();
        if (args != null) {
            for (String a : args) {
                if (a == null) continue;
                if (a.startsWith("-")) continue;
                positional.add(a);
            }
        }

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            if (veryVerbose) {
                String vvArg = positional.isEmpty() ? null : positional.get(0);
                return runVeryVerbose(git, fmt, vvArg, showAll);
            }

            // Default behavior: single-line per commit, truncated messages.
            // If no flags and no positional args, show 10 most recent commits (with ellipsis if more).
            var logCmd = git.log();

            // Support multiple positional arguments and ranges.
            // Date range filter (epoch seconds) - optional
            Long dateRangeStart = null;
            Long dateRangeEnd = null;

            boolean limitedToRecent = !showAll;
            if (limitedToRecent) {
                // Default: limit to 11 so we can show 10 + ellipsis indicator.
                logCmd.setMaxCount(11);
            }

            int explicitCommitAdds = 0;
            if (!positional.isEmpty()) {
                for (String p : positional) {
                    if (p.contains("..")) {
                        String[] parts = p.split("\\.\\.");
                        if (parts.length == 2) {
                            // Attempt commit range first
                            ObjectId a = resolveCommitOrNull(git.getRepository(), parts[0]);
                            ObjectId b = resolveCommitOrNull(git.getRepository(), parts[1]);
                            if (a != null && b != null) {
                                try {
                                    // Ensure the range is ordered older..newer. If the user
                                    // supplied the reverse, swap so we still produce results.
                                    try (org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                                        org.eclipse.jgit.revwalk.RevCommit ca = rw.parseCommit(a);
                                        org.eclipse.jgit.revwalk.RevCommit cb = rw.parseCommit(b);
                                        if (rw.isMergedInto(ca, cb)) {
                                            logCmd.addRange(a, b);
                                        } else if (rw.isMergedInto(cb, ca)) {
                                            logCmd.addRange(b, a);
                                        } else {
                                            // Not an ancestor relationship; fall back to adding the end commit
                                            logCmd.add(b);
                                            explicitCommitAdds++;
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // Fall back to adding the end commit and we'll filter later
                                    logCmd.add(b);
                                    explicitCommitAdds++;
                                }
                                continue;
                            }

                            // Attempt date range
                            long[] dr = parseDateRange(parts[0], parts[1]);
                            if (dr != null) {
                                dateRangeStart = dr[0];
                                dateRangeEnd = dr[1];
                                continue;
                            }
                        }
                        // If we get here, treat as a literal and attempt to resolve as commit-ish
                        ObjectId fallback = resolveCommitOrNull(git.getRepository(), p);
                        if (fallback != null) { logCmd.add(fallback); explicitCommitAdds++; }
                    } else {
                        // single token: could be a date or a commit-ish
                        long[] singleDate = parseDateRangeSingle(p);
                        if (singleDate != null) {
                            dateRangeStart = singleDate[0];
                            dateRangeEnd = singleDate[1];
                            continue;
                        }
                        ObjectId oid = resolveCommitOrNull(git.getRepository(), p);
                        if (oid != null) {
                            logCmd.add(oid);
                            explicitCommitAdds++;
                        } else {
                            System.err.println("Warning: cannot resolve '" + p + "' as commit or date; ignoring");
                        }
                    }
                }
            } else if (verbose) {
                // -v lists all commits (no max count)
                // leave logCmd without setMaxCount
            }

            // If the user explicitly provided exactly one commit-ish token (not a date)
            // and asked for verbose output, treat that as a request to show only that
            // single commit (not its ancestors). Override any previous max count.
            if (explicitCommitAdds == 1 && positional.size() == 1 && verbose) {
                logCmd.setMaxCount(1);
            }

            Iterable<RevCommit> logs = logCmd.call();
            int i = 0;
            int limit = limitedToRecent ? 10 : Integer.MAX_VALUE;
            for (RevCommit c : logs) {
                if (i >= limit) {
                    // If we fetched an extra item, indicate more commits exist.
                    System.out.println("  ...");
                    System.out.println("Hint: Use 'vgl log -all' to show all commits.");
                    break;
                }
                String id = c.getId().abbreviate(7).name();
                String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                String author = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
                // Use short message for single-line output
                String rawMsg = oneLine(c.getShortMessage());
                // Full commit message (may contain newlines) used for verbose output
                String fullMsg = (c.getFullMessage() == null) ? "" : c.getFullMessage();

                String prefix = graph ? "* " : "";

                // If a date range filter is set, skip commits outside the window
                if (dateRangeStart != null || dateRangeEnd != null) {
                    long t = c.getCommitTime();
                    if (dateRangeStart != null && t < dateRangeStart) { i++; continue; }
                    if (dateRangeEnd != null && t > dateRangeEnd) { i++; continue; }
                }

                if (verbose) {
                    // Verbose (-v): column-align the start of the message. Make the
                    // author column a bit wider and truncate the author if needed.
                    int maxWidth = 80;
                    int valueColumn = 42; // desired column where message should start
                    String base = prefix + id + "  " + date + "  ";
                    int targetFirstLen = Math.max(0, valueColumn - 2); // first.length + 2 == valueColumn
                    String firstCombined;
                    if (!author.isBlank()) {
                        String a = author;
                        // If combined exceeds target, truncate author with ellipsis
                        if (base.length() + a.length() > targetFirstLen) {
                            int avail = Math.max(0, targetFirstLen - base.length());
                            if (avail <= 3) {
                                a = a.substring(0, Math.max(0, avail));
                            } else {
                                a = a.substring(0, Math.max(0, avail - 3)) + "...";
                            }
                        }
                        firstCombined = base + a;
                    } else {
                        firstCombined = base.trim();
                    }
                    // Pad to exact width so message column aligns
                    if (firstCombined.length() < targetFirstLen) {
                        firstCombined = firstCombined + " ".repeat(targetFirstLen - firstCombined.length());
                    }
                    printWrappedColumns(firstCombined, fullMsg, maxWidth);
                } else {
                    // single-line default: show abbreviated id and truncated one-line message
                    int maxWidth = 80;
                    // Column where message should start (including prefix)
                    int valueColumn = 10;
                    String msg = rawMsg;
                    int used = prefix.length() + id.length();
                    int pad = Math.max(2, valueColumn - used);
                    int remaining = Math.max(10, maxWidth - valueColumn);
                    if (msg.length() > remaining) {
                        msg = msg.substring(0, Math.max(0, remaining - 3)) + "...";
                    }
                    System.out.println(prefix + id + " ".repeat(pad) + msg);
                }
                i++;
            }
            return 0;
        }
    }

    private static int runVeryVerbose(Git git, DateTimeFormatter fmt, String commitArg, boolean showAll) throws Exception {
        Repository repo = git.getRepository();
        if (commitArg != null) {
            ObjectId start = resolveCommitOrNull(repo, commitArg);
            if (start == null) {
                System.err.println("Error: Cannot resolve commit: " + commitArg);
                return 1;
            }
            RevCommit target;
            try (org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
                target = rw.parseCommit(start);
            }
            printCommitHeader(fmt, target);
            printCommitChangesSummary(repo, target);
            System.out.println();
            System.out.println("Hint: Run 'vgl log <commit> -vv' to show the full patch for a specific commit.");
            System.out.println("Hint: Run 'vgl log -v' to list more commits.");
            return 0;
        }

        // No specific commit: print commits in full. Respect -all (showAll) to decide whether to limit.
        Iterable<RevCommit> logs = git.log().call();
        int i = 0;
        int limit = showAll ? Integer.MAX_VALUE : 10;
            for (RevCommit c : logs) {
            if (i >= limit) {
                System.out.println("  ...");
                System.out.println("Hint: Use 'vgl log -all' to show all commits.");
                break;
            }
            printCommitHeader(fmt, c);
            printCommitChangesSummary(repo, c);
            System.out.println();
            i++;
        }
        System.out.println("Hint: Run 'vgl log <commit> -vv' to show the full patch for a specific commit.");
        System.out.println("Hint: Run 'vgl log -v' to list more commits in single-line summary form.");
        return 0;
    }

    private static void printCommitHeader(DateTimeFormatter fmt, RevCommit c) {
        String fullId = c.getId().name();
        String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
        String authorName = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
        String authorEmail = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getEmailAddress() : "";
        String subject = oneLine(c.getShortMessage());
        // Align header values in a column. Compute label width from known labels.
        String labelCommit = "Commit:";
        String labelAuthor = "Author:";
        String labelDate = "Date:";
        String labelMessage = "Message:";
        int maxLabel = Math.max(Math.max(labelCommit.length(), labelAuthor.length()), Math.max(labelDate.length(), labelMessage.length()));
        int valueStart = maxLabel + 2; // at least one space after longest label

        // Helper prints label and wrapped value aligned at valueStart column.
        java.io.PrintStream out = System.out;
        String shortId = fullId.substring(0, Math.min(7, fullId.length()));
        printAligned(out, labelCommit, shortId + " (" + fullId + ")", valueStart);
        if (!authorName.isBlank()) {
            String auth = (authorEmail != null && !authorEmail.isBlank()) ? (authorName + " <" + authorEmail + ">") : authorName;
            printAligned(out, labelAuthor, auth, valueStart);
        }
        printAligned(out, labelDate, date, valueStart);
        printAligned(out, labelMessage, subject, valueStart);
    }

    private static void printAligned(java.io.PrintStream out, String label, String value, int valueStart) {
        if (out == null) return;
        if (label == null) label = "";
        if (value == null) value = "";

        int maxWidth = 80;
        String sanitized = value.replace("\r", "");

        // Determine a consistent column where all values start.
        // Use the longest label length + two spaces as the starting column for values.
        int maxLabelLen = Math.max(Math.max("Commit:".length(), "Author:".length()), Math.max("Date:".length(), "Message:".length()));
        int valueColumn = maxLabelLen + 2; // number of characters before value (labels + at least two spaces)

        // Build firstIndent: label followed by enough spaces so value begins at valueColumn
        int labelPad = Math.max(1, valueColumn - label.length());
        String firstIndent = label + " ".repeat(labelPad);

        // Subsequent wrapped lines should be indented to the valueColumn (i.e., valueColumn spaces)
        String subsequentIndent = " ".repeat(valueColumn);

        int wrapWidth = Math.max(10, maxWidth - valueColumn);

        // Simple word wrap
        String[] words = sanitized.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (line.length() == 0) {
                line.append(w);
            } else if (line.length() + 1 + w.length() <= wrapWidth) {
                line.append(' ').append(w);
            } else {
                // flush current line
                out.println(firstIndent + line.toString());
                // subsequent lines use the fixed subsequentIndent
                firstIndent = subsequentIndent;
                line.setLength(0);
                line.append(w);
            }
        }
        // Print remaining text (handle case where value is empty)
        out.println(firstIndent + line.toString());
    }

    private static void printCommitPatch(Repository repo, RevCommit commit) {
        if (repo == null || commit == null) {
            return;
        }

        ObjectId newTree;
        ObjectId oldTree;
        try {
            newTree = repo.resolve(commit.getName() + "^{tree}");
            oldTree = (commit.getParentCount() > 0)
                ? repo.resolve(commit.getParent(0).getName() + "^{tree}")
                : null;
        } catch (Exception e) {
            return;
        }

        if (newTree == null) {
            return;
        }

        try (ObjectReader reader = repo.newObjectReader()) {
            AbstractTreeIterator oldIter;
            if (commit.getParentCount() > 0 && oldTree != null) {
                CanonicalTreeParser oldParser = new CanonicalTreeParser();
                oldParser.reset(reader, oldTree);
                oldIter = oldParser;
            } else {
                oldIter = new EmptyTreeIterator();
            }

            CanonicalTreeParser newParser = new CanonicalTreeParser();
            newParser.reset(reader, newTree);

            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                java.util.List<DiffEntry> diffs = df.scan(oldIter, newParser);
                if (diffs == null || diffs.isEmpty()) {
                    return;
                }

                // For -vv print a concise changes summary (counts) and a grouped file list.
                int added = 0, modified = 0, renamed = 0, deleted = 0;
                java.util.List<java.util.Map.Entry<String,String>> entries = new java.util.ArrayList<>();
                for (DiffEntry d : diffs) {
                    switch (d.getChangeType()) {
                        case ADD: added++; break;
                        case MODIFY: modified++; break;
                        case DELETE: deleted++; break;
                        case RENAME: renamed++; break;
                        case COPY: added++; break;
                        default: break;
                    }
                    String p = d.getNewPath();
                    if (p == null || p.equals("/dev/null")) p = d.getOldPath();
                    if (p != null) {
                        String marker;
                        switch (d.getChangeType()) {
                            case ADD: marker = "A"; break;
                            case DELETE: marker = "D"; break;
                            case RENAME: marker = "R"; break;
                            case COPY: marker = "A"; break;
                            case MODIFY: marker = "M"; break;
                            default: marker = "M"; break;
                        }
                        entries.add(new java.util.AbstractMap.SimpleEntry<>(p, marker));
                    }
                }
                System.out.println("Changes: " + com.vgl.cli.commands.helpers.StatusFileSummary.getSummaryCountsLine(added, modified, renamed, deleted));

                if (!entries.isEmpty()) {
                    // Group by directory, include change marker for each filename
                    java.util.Map<String, java.util.List<String>> grouped = new java.util.TreeMap<>();
                    for (java.util.Map.Entry<String,String> ent : entries) {
                        String p = ent.getKey();
                        String marker = ent.getValue();
                        int idx = p.lastIndexOf('/');
                        String dir = (idx >= 0) ? p.substring(0, idx + 1) : "./";
                        String name = (idx >= 0) ? p.substring(idx + 1) : p;
                        grouped.computeIfAbsent(dir, k -> new java.util.ArrayList<>()).add(marker + " " + name);
                    }
                    for (java.util.Map.Entry<String, java.util.List<String>> e : grouped.entrySet()) {
                        String dir = e.getKey();
                        java.util.List<String> files = e.getValue();
                        System.out.println("  " + dir);
                        String joined = String.join("  ", files);
                        printWrappedIndented("    ", joined, 80);
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static void printTruncatedRecentCommits(Git git, DateTimeFormatter fmt, int maxLines) throws Exception {
        if (git == null) {
            return;
        }

        // We already printed the most recent commit in detail.
        int fetch = 1 + maxLines + 1; // first + up to maxLines + sentinel
        Iterable<RevCommit> logs = git.log().setMaxCount(fetch).call();

        int i = 0;
        boolean hasMore = false;

        System.out.println("Recent commits:");
        for (RevCommit c : logs) {
            if (i == 0) {
                i++;
                continue;
            }

            int lineIndex = i - 1;
            if (lineIndex >= maxLines) {
                hasMore = true;
                break;
            }

            String id = c.getId().abbreviate(7).name();
            String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
            String msg = oneLine(c.getShortMessage());
            // Align recent commits message column consistently (reuse same valueColumn)
            int maxWidth = 80;
            int valueColumn = 42;
            String base = "  " + id + "  " + date + "  ";
            int targetFirstLen = Math.max(0, valueColumn - 2);
            String firstCombined = base;
            if (firstCombined.length() < targetFirstLen) {
                firstCombined = firstCombined + " ".repeat(targetFirstLen - firstCombined.length());
            }
            printWrappedColumns(firstCombined, msg, maxWidth);
            i++;
        }

        if (hasMore) {
            System.out.println("  ...");
            System.out.println("Hint: Use 'vgl log -all' to show all commits.");
        }
    }

    private static void printWrappedColumns(String first, String rest, int maxWidth) {
        if (first == null) first = "";
        if (rest == null) rest = "";

        // First line prefix (first column + two spaces). Subsequent wrapped lines
        // use a small fixed indent and the full available width.
        int firstColIndent = first.length() + 2; // two spaces between columns
        int firstWrap = Math.max(10, maxWidth - firstColIndent);
        int subsequentIndentLen = 2; // small fixed indent for wrapped lines
        String firstPrefix = first + "  ";
        String subsequentIndent = " ".repeat(subsequentIndentLen);
        int subsequentWrap = Math.max(10, maxWidth - subsequentIndentLen);

        // Treat newlines as paragraph boundaries and wrap each paragraph separately.
        String[] paragraphs = rest.replace("\r", "").split("\n");
        boolean firstPara = true;
        boolean lastWasBlank = false;
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                // Preserve a single blank line between paragraphs, collapsing multiples
                if (!lastWasBlank) {
                    System.out.println("");
                    lastWasBlank = true;
                }
                firstPara = false;
                continue;
            }
            lastWasBlank = false;

            String[] words = trimmed.split("\\s+");
            StringBuilder line = new StringBuilder();
            String indentToUse = firstPara ? firstPrefix : subsequentIndent;
            int curWrap = firstPara ? firstWrap : subsequentWrap;

            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (line.length() == 0) {
                    line.append(w);
                } else if (line.length() + 1 + w.length() <= curWrap) {
                    line.append(' ').append(w);
                } else {
                    System.out.println(indentToUse + line.toString());
                    indentToUse = subsequentIndent;
                    curWrap = subsequentWrap;
                    line.setLength(0);
                    line.append(w);
                }
            }
            // Print remaining
            System.out.println(indentToUse + line.toString());
            firstPara = false;
        }
    }

    /** Wrap a long string into lines starting with `indent`. Uses word boundaries. */
    private static void printWrappedIndented(String indent, String text, int maxWidth) {
        if (indent == null) indent = "";
        if (text == null) text = "";
        String[] paragraphs = text.replace("\r", "").split("\n");
        int wrapWidth = Math.max(10, maxWidth - indent.length());
        boolean lastWasBlank = false;
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                if (!lastWasBlank) {
                    System.out.println("");
                    lastWasBlank = true;
                }
                continue;
            }
            lastWasBlank = false;
            String[] words = trimmed.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (line.length() == 0) {
                    line.append(w);
                } else if (line.length() + 1 + w.length() <= wrapWidth) {
                    line.append(' ').append(w);
                } else {
                    System.out.println(indent + line.toString());
                    line.setLength(0);
                    line.append(w);
                }
            }
            System.out.println(indent + line.toString());
        }
    }

    private static void printCommitChangesSummary(Repository repo, RevCommit commit) {
        // Currently the commit patch printer has been adapted to print a concise changes
        // summary (counts). Reuse that implementation via a simple wrapper to keep
        // call sites readable.
        printCommitPatch(repo, commit);
    }

    private static ObjectId resolveCommitOrNull(Repository repo, String commitish) {
        if (repo == null || commitish == null || commitish.isBlank()) {
            return null;
        }
        try {
            return repo.resolve(commitish);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Parse two date tokens into epoch-second inclusive range. Supports yyyy-MM-dd and yyyy-MM.
     * Returns null if parsing fails for either token.
     */
    private static long[] parseDateRange(String a, String b) {
        long[] s = parseDateRangeSingle(a);
        long[] e = parseDateRangeSingle(b);
        if (s == null || e == null) return null;
        return new long[] { s[0], e[1] };
    }

    /**
     * Parse a single date token into [startEpochSec, endEpochSec] for that day or month.
     * Supports yyyy-MM-dd and yyyy-MM formats. Returns null if not parseable.
     */
    private static long[] parseDateRangeSingle(String token) {
        if (token == null) return null;
        try {
            if (token.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate d = LocalDate.parse(token);
                ZonedDateTime start = d.atStartOfDay(ZoneId.systemDefault());
                ZonedDateTime end = d.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault());
                return new long[] { start.toEpochSecond(), end.toEpochSecond() };
            }
            if (token.matches("\\d{4}-\\d{2}")) {
                YearMonth ym = YearMonth.parse(token);
                LocalDate startD = ym.atDay(1);
                LocalDate endD = ym.atEndOfMonth();
                ZonedDateTime start = startD.atStartOfDay(ZoneId.systemDefault());
                ZonedDateTime end = endD.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault());
                return new long[] { start.toEpochSecond(), end.toEpochSecond() };
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String firstPositionalOrNull(List<String> args) {
        if (args == null) {
            return null;
        }
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if (a.startsWith("-")) {
                continue;
            }
            return a;
        }
        return null;
    }

    private static String oneLine(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
