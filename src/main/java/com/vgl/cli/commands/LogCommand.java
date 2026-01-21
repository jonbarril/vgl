package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        boolean graph = args.contains("-graph");

        String commitArg = firstPositionalOrNull(args);

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            if (veryVerbose) {
                return runVeryVerbose(git, fmt, commitArg);
            }

            int maxCount = verbose ? 50 : 10;

            // If a commit is specified, behave like a commit lookup (single result) rather than
            // showing the entire history reachable from that commit.
            if (commitArg != null) {
                maxCount = 1;
            }

            var logCmd = git.log().setMaxCount(maxCount);
            if (commitArg != null) {
                ObjectId start = resolveCommitOrNull(git.getRepository(), commitArg);
                if (start == null) {
                    System.err.println("Error: Cannot resolve commit: " + commitArg);
                    return 1;
                }
                logCmd.add(start);
            }

            Iterable<RevCommit> logs = logCmd.call();
            for (RevCommit c : logs) {
                String id = c.getId().abbreviate(7).name();
                String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                String author = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
                String msg = oneLine(c.getFullMessage());

                String prefix = graph ? "* " : "";
                if (verbose) {
                    if (!author.isBlank()) {
                        System.out.println(prefix + id + "  " + date + "  " + author + "  " + msg);
                    } else {
                        System.out.println(prefix + id + "  " + date + "  " + msg);
                    }
                } else {
                    System.out.println(prefix + id + "  " + date + "  " + msg);
                }
            }
            return 0;
        }
    }

    private static int runVeryVerbose(Git git, DateTimeFormatter fmt, String commitArg) throws Exception {
        Repository repo = git.getRepository();

        ObjectId start = (commitArg != null)
            ? resolveCommitOrNull(repo, commitArg)
            : resolveCommitOrNull(repo, "HEAD");

        if (start == null) {
            System.err.println(commitArg != null
                ? ("Error: Cannot resolve commit: " + commitArg)
                : "Error: Cannot resolve HEAD");
            return 1;
        }

        RevCommit target;
        try (org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
            target = rw.parseCommit(start);
        }

        printCommitHeader(fmt, target);
        printCommitPatch(repo, target);

        // In -vv mode, also show a short, truncated list of other recent commits (when not filtered).
        if (commitArg == null) {
            printTruncatedRecentCommits(git, fmt, 25);
        }

        System.out.println();
        System.out.println("Tip: Run 'vgl log <commit> -vv' to show the full patch for a specific commit.");
        System.out.println("Tip: Run 'vgl log -v' to list more commits.");
        return 0;
    }

    private static void printCommitHeader(DateTimeFormatter fmt, RevCommit c) {
        String fullId = c.getId().name();
        String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
        String authorName = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
        String authorEmail = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getEmailAddress() : "";
        String subject = oneLine(c.getShortMessage());

        System.out.println("commit " + fullId);
        if (!authorName.isBlank()) {
            if (authorEmail != null && !authorEmail.isBlank()) {
                System.out.println("Author: " + authorName + " <" + authorEmail + ">");
            } else {
                System.out.println("Author: " + authorName);
            }
        }
        System.out.println("Date:   " + date);
        System.out.println("Message:    " + subject);
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
                for (DiffEntry d : diffs) {
                    df.format(d);
                }
                df.flush();
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
            System.out.println("  " + id + "  " + date + "  " + msg);
            i++;
        }

        if (hasMore) {
            System.out.println("  ...");
        }
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
