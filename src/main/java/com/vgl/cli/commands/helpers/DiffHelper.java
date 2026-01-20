package com.vgl.cli.commands.helpers;
import com.vgl.cli.utils.GlobUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;

public final class DiffHelper {
    private DiffHelper() {}

    private enum FileChangeKind {
        ADDED,
        DELETED,
        MODIFIED
    }

    public enum Verbosity {
        SUMMARY, HUMAN, RAW
    }

    private static byte[] normalizeNewlines(byte[] in) {
        if (in == null || in.length == 0) return in;
        // Fast path: if no CR present, return original
        boolean hasCR = false;
        for (byte b : in) if (b == '\r') { hasCR = true; break; }
        if (!hasCR) return in;

        // Convert \r\n and lone \r to \n
        byte[] out = new byte[in.length];
        int wi = 0;
        for (int i = 0; i < in.length; i++) {
            byte b = in[i];
            if (b == '\r') {
                // If followed by \n, emit single \n and skip
                if (i + 1 < in.length && in[i + 1] == '\n') {
                    out[wi++] = (byte) '\n';
                    i++; // skip \n
                } else {
                    out[wi++] = (byte) '\n';
                }
            } else {
                out[wi++] = b;
            }
        }
        if (wi == out.length) return out; // no shrink
        byte[] shrunk = new byte[wi];
        System.arraycopy(out, 0, shrunk, 0, wi);
        return shrunk;
    }

    public static Verbosity computeVerbosity(List<String> args) {
        if (args != null && args.contains("-vv")) {
            return Verbosity.RAW;
        }
        if (args != null && args.contains("-v")) {
            return Verbosity.HUMAN;
        }
        // Default to a concise summary view. Use `-v` for full human-readable diffs
        // and `-vv` for raw/unmodified diff output.
        return Verbosity.SUMMARY;
    }

    public static Map<String, byte[]> snapshotFiles(Path root, List<String> globs) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        if (root == null) return out;
        if (!Files.exists(root)) return out;
        Path absRoot = root.toAbsolutePath().normalize();

        Files.walk(absRoot)
            .filter(Files::isRegularFile)
            .forEach(p -> {
                Path rel = absRoot.relativize(p);
                String relStr = rel.toString().replace('\\', '/');
                if (relStr.startsWith(".git/") || relStr.equals(".vgl")) return;
                if (!GlobUtils.matchesAny(relStr, globs)) return;
                try {
                    out.put(relStr, Files.readAllBytes(p));
                } catch (IOException ignored) {}
            });

        return out;
    }

    public static class DiffSummary {
        public final Map<String,int[]> perFileCounts = new HashMap<>();
        public final Map<String,Integer> perFileBlocks = new HashMap<>();
        public final Map<String,FileChangeKind> perFileKind = new HashMap<>();
        public int totalAdded = 0;
        public int totalRemoved = 0;
    }

    public static DiffSummary computeDiffSummary(Map<String, byte[]> a, Map<String, byte[]> b) {
        DiffSummary s = new DiffSummary();
        List<String> all = new ArrayList<>();
        if (a != null) all.addAll(a.keySet());
        if (b != null) for (String k : b.keySet()) if (!all.contains(k)) all.add(k);
        all.sort(String::compareTo);

        for (String rel : all) {
            byte[] av = a == null ? null : a.get(rel);
            byte[] bv = b == null ? null : b.get(rel);
            if (av == null && bv == null) continue;
            // If both present, compare after normalizing newlines so CRLF/LF-only
            // differences don't produce spurious change entries.
            if (av != null && bv != null) {
                byte[] an = normalizeNewlines(av);
                byte[] bn = normalizeNewlines(bv);
                if (java.util.Arrays.equals(an, bn)) continue;
            } else if (av != null && bv != null && java.util.Arrays.equals(av, bv)) continue;

            FileChangeKind kind = (av == null) ? FileChangeKind.ADDED : (bv == null ? FileChangeKind.DELETED : FileChangeKind.MODIFIED);

            int added = 0;
            int removed = 0;
            int blocks = 0;
            if (av != null && bv != null) {
                RawText at = new RawText(normalizeNewlines(av != null ? av : new byte[0]));
                RawText bt = new RawText(normalizeNewlines(bv != null ? bv : new byte[0]));
                HistogramDiff alg = new HistogramDiff();
                EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
                blocks = edits.size();
                for (org.eclipse.jgit.diff.Edit e : edits) {
                    removed += Math.max(0, e.getEndA() - e.getBeginA());
                    added += Math.max(0, e.getEndB() - e.getBeginB());
                }
            } else if (av == null) {
                added = countLines(bv);
                blocks = (added > 0) ? 1 : 0;
            } else {
                removed = countLines(av);
                blocks = (removed > 0) ? 1 : 0;
            }
            s.perFileCounts.put(rel, new int[] {added, removed});
            s.perFileBlocks.put(rel, blocks);
            s.perFileKind.put(rel, kind);
            s.totalAdded += added;
            s.totalRemoved += removed;
        }
        return s;
    }

    private static int countLines(byte[] b) {
        if (b == null || b.length == 0) return 0;
        int lines = 0;
        for (byte c : b) {
            if (c == '\n') {
                lines++;
            }
        }
        // If the last line doesn't end with a newline, count it.
        if (b[b.length - 1] != (byte) '\n') {
            lines++;
        }
        return lines;
    }

    public static void printSummary(PrintStream out, DiffSummary s, int matchedFiles) {
        // Keep output ASCII-only for broad terminal compatibility.
        int changedFiles = s.perFileCounts.size();
        if (matchedFiles < 0) matchedFiles = changedFiles;
        out.println("Matched files: " + matchedFiles + "; Changed files: " + changedFiles);
        out.println();
        for (Map.Entry<String,int[]> e : s.perFileCounts.entrySet()) {
            int[] ar = e.getValue();
            int blocks = s.perFileBlocks.getOrDefault(e.getKey(), 0);
            FileChangeKind kind = s.perFileKind.getOrDefault(e.getKey(), FileChangeKind.MODIFIED);
            printFileSummary(out, kind, e.getKey(), ar[0], ar[1], blocks);
        }
        out.println();
        out.println("Use `vgl diff -v` for full diffs, `-vv` for raw output.");
    }

    private static void printFileSummary(PrintStream out, FileChangeKind kind, String path, int added, int removed, int blocks) {
        String marker = switch (kind) {
            case ADDED -> "A";
            case DELETED -> "D";
            case MODIFIED -> "M";
        };
        String countsWrapped = "(+" + added + "/-" + removed + " lines, " + blocks + " blocks)";

        int maxWidth = 80;
        int countsCol = 30; // desired column where filename should start when space permits

        String left = "  " + marker + " " + countsWrapped;
        int leftLen = left.length();
        int pad = Math.max(2, countsCol - leftLen);

        // If everything fits on one line, print compactly: marker + counts then path
        if (leftLen + pad + path.length() <= maxWidth) {
            out.println(left + " ".repeat(pad) + path);
            return;
        }

        // Otherwise print left and then wrap the path aligned under the filename column.
        int availFirst = Math.max(10, maxWidth - (leftLen + pad));
        if (path.length() <= availFirst) {
            out.println(left + " ".repeat(pad) + path);
            return;
        }

        // Print first line with left and the first chunk of path
        String firstChunk = path.substring(0, Math.min(availFirst, path.length()));
        out.println(left + " ".repeat(pad) + firstChunk);

        // Print continuation lines starting at the filename column
        int idx = firstChunk.length();
        String continuationPad = "".repeat(leftLen + pad);
        int contWidth = maxWidth - (leftLen + pad);
        while (idx < path.length()) {
            int take = Math.min(contWidth, path.length() - idx);
            out.println(continuationPad + path.substring(idx, idx + take));
            idx += take;
        }
    }

    private static String markerForCounts(int added, int removed) {
        return (added > 0 && removed > 0) ? "M" : (added > 0 ? "A" : "D");
    }

    private static FileChangeKind toKind(DiffEntry.ChangeType t) {
        if (t == null) {
            return FileChangeKind.MODIFIED;
        }
        return switch (t) {
            case ADD -> FileChangeKind.ADDED;
            case DELETE -> FileChangeKind.DELETED;
            // Treat rename/copy/modify as modified for display purposes.
            case MODIFY, RENAME, COPY -> FileChangeKind.MODIFIED;
        };
    }

    private static void printTotalLine(PrintStream out, DiffSummary s) {
        if (s.perFileCounts.size() == 1) {
            out.println("1 file(s) changed");
            return;
        }
        out.println(s.perFileCounts.size() + " file(s) changed - +" + s.totalAdded + "/-" + s.totalRemoved);
    }

    public static boolean diffWorkingTrees(Path leftRoot, Path rightRoot, List<String> globs, Verbosity v) throws IOException {
        // If globs were provided, expand them to the set of repo-relative files
        // so we can report what they matched (and fail early if none matched).
        int matchedFiles = -1;
        if (globs != null && !globs.isEmpty() && leftRoot != null) {
            List<String> resolved = GlobUtils.resolveGlobs(globs, leftRoot, System.out);
            if (resolved.isEmpty()) {
                return false;
            }
            // Use the explicit file list as the filtering set.
            globs = resolved;
            matchedFiles = resolved.size();
        }

        Map<String, byte[]> left = snapshotFiles(leftRoot, globs);
        Map<String, byte[]> right = snapshotFiles(rightRoot, globs);
        if (v == Verbosity.SUMMARY) {
            DiffSummary s = computeDiffSummary(left, right);
            if (s.perFileCounts.isEmpty()) return false;
            printSummary(System.out, s, matchedFiles);
            return true;
        }

        DiffSummary summary = computeDiffSummary(left, right);
        if (summary.perFileCounts.isEmpty()) {
            return false;
        }

        List<String> all = new ArrayList<>();
        all.addAll(left.keySet());
        for (String k : right.keySet()) if (!all.contains(k)) all.add(k);
        all.sort(String::compareTo);
        if (matchedFiles < 0) {
            matchedFiles = all.size();
        }

        if (v == Verbosity.HUMAN) {
            int changedFiles = 0;
            for (String rel : all) {
                byte[] a = left.get(rel);
                byte[] b = right.get(rel);
                if (a != null && b != null && java.util.Arrays.equals(a, b)) {
                    continue;
                }
                if (a == null && b == null) {
                    continue;
                }
                changedFiles++;
            }
            System.out.println("Matched files: " + matchedFiles + "; Changed files: " + changedFiles);
            System.out.println();
        }

        // Top summary already shows matched/changed files; avoid redundant totals here.

        boolean any = false;
        for (String rel : all) {
            byte[] a = left.get(rel);
            byte[] b = right.get(rel);
            if (a != null && b != null && java.util.Arrays.equals(a,b)) continue;
            any = true;

            int[] counts = summary.perFileCounts.get(rel);
            int added = (counts == null) ? 0 : counts[0];
            int removed = (counts == null) ? 0 : counts[1];
            int blocks = summary.perFileBlocks.getOrDefault(rel, 0);
            FileChangeKind kind = (a == null) ? FileChangeKind.ADDED : (b == null ? FileChangeKind.DELETED : FileChangeKind.MODIFIED);
            printFileSummary(System.out, kind, rel, added, removed, blocks);

            if (v == Verbosity.RAW) {
                String aName = "a/" + rel;
                String bName = "b/" + rel;
                System.out.println("diff --git " + aName + " " + bName);
                if (a == null) {
                    System.out.println("new file mode 100644");
                    System.out.println("--- /dev/null");
                    System.out.println("+++ " + bName);
                } else if (b == null) {
                    System.out.println("deleted file mode 100644");
                    System.out.println("--- " + aName);
                    System.out.println("+++ /dev/null");
                } else {
                    System.out.println("--- " + aName);
                    System.out.println("+++ " + bName);
                }
            }

            if (a != null && RawText.isBinary(a) || b != null && RawText.isBinary(b)) {
                if (v == Verbosity.RAW) {
                    System.out.println("Binary files differ");
                } else {
                    System.out.println("  (binary files differ)");
                }
                System.out.println();
                continue;
            }
            RawText at = new RawText(normalizeNewlines(a != null ? a : new byte[0]));
            RawText bt = new RawText(normalizeNewlines(b != null ? b : new byte[0]));
            HistogramDiff alg = new HistogramDiff();
            EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
            if (v == Verbosity.RAW) {
                try (DiffFormatter df = new DiffFormatter(System.out)) {
                    df.format(edits, at, bt);
                }
            } else {
                printHumanReadableDiff(at, bt, edits);
                System.out.println();
            }
        }
        return any;
    }

    public static boolean diffTrees(Repository repo, ObjectId oldTreeId, ObjectId newTreeId, List<String> globs, Verbosity v) throws Exception {
        boolean any = false;
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                // If globs provided, expand them against the repo work tree for clarity.
                int matchedFiles = -1;
                if (globs != null && !globs.isEmpty()) {
                    try {
                        Path repoRoot = repo.getWorkTree() == null ? null : repo.getWorkTree().toPath();
                        if (repoRoot != null) {
                            List<String> resolved = GlobUtils.resolveGlobs(globs, repoRoot, System.out);
                            if (resolved.isEmpty()) {
                                return false;
                            }
                            globs = resolved;
                            matchedFiles = resolved.size();
                        }
                    } catch (IOException ignored) {
                        // Fall back to pattern matching if expansion fails
                    }
                }

                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                if (v == Verbosity.SUMMARY) {
                    DiffSummary s = new DiffSummary();
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) continue;
                        any = true;
                        // Compute counts for summary using the same algorithm as human diff
                        byte[] oldBytes = (d.getOldId() == null || d.getOldId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                        byte[] newBytes = (d.getNewId() == null || d.getNewId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());
                        int added = 0;
                        int removed = 0;
                        int blocks = 0;
                        if (oldBytes != null && newBytes != null) {
                            RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                            RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                            HistogramDiff alg2 = new HistogramDiff();
                            EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                            blocks = edits2.size();
                            for (org.eclipse.jgit.diff.Edit e : edits2) {
                                removed += Math.max(0, e.getEndA() - e.getBeginA());
                                added += Math.max(0, e.getEndB() - e.getBeginB());
                            }
                        } else if (oldBytes == null) {
                            added = countLines(newBytes);
                            blocks = (added > 0) ? 1 : 0;
                        } else {
                            removed = countLines(oldBytes);
                            blocks = (removed > 0) ? 1 : 0;
                        }
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) path = d.getOldPath();
                        s.perFileCounts.put(path, new int[] {added, removed});
                        s.perFileBlocks.put(path, blocks);
                        s.perFileKind.put(path, toKind(d.getChangeType()));
                        s.totalAdded += added; s.totalRemoved += removed;
                    }
                    if (!any) return false;
                    printSummary(System.out, s, matchedFiles);
                    return true;
                }

                DiffSummary s = new DiffSummary();
                List<DiffEntry> matched = new ArrayList<>();
                for (DiffEntry d : diffs) {
                    if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                        continue;
                    }
                    matched.add(d);

                    // Compute edits using the same HistogramDiff algorithm used for printing
                    // so the summary counts match the printed +/- lines.
                    byte[] oldBytes = (d.getOldId() == null || d.getOldId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                    byte[] newBytes = (d.getNewId() == null || d.getNewId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());
                    int added = 0;
                    int removed = 0;
                    int blocks = 0;
                    if (oldBytes != null && newBytes != null) {
                        RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                        RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                        HistogramDiff alg2 = new HistogramDiff();
                        EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                        blocks = edits2.size();
                        for (org.eclipse.jgit.diff.Edit e : edits2) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                    } else if (oldBytes == null) {
                        added = countLines(newBytes);
                        blocks = (added > 0) ? 1 : 0;
                    } else {
                        removed = countLines(oldBytes);
                        blocks = (removed > 0) ? 1 : 0;
                    }
                    String path = d.getNewPath();
                    if (path == null || path.equals("/dev/null")) path = d.getOldPath();
                    s.perFileCounts.put(path, new int[] {added, removed});
                    s.perFileBlocks.put(path, blocks);
                    s.perFileKind.put(path, toKind(d.getChangeType()));
                    s.totalAdded += added; s.totalRemoved += removed;
                }

                if (matched.isEmpty()) {
                    return false;
                }

                if (v == Verbosity.HUMAN) {
                    if (matchedFiles < 0) {
                        matchedFiles = matched.size();
                    }
                    System.out.println("Matched files: " + matchedFiles + "; Changed files: " + matched.size());
                    System.out.println();
                }

                // Top summary already shows matched/changed files; avoid redundant totals here.

                try (DiffFormatter dfOut = new DiffFormatter(System.out)) {
                    dfOut.setRepository(repo);
                    dfOut.setDetectRenames(true);
                    for (DiffEntry d : matched) {
                        any = true;
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) {
                            path = d.getOldPath();
                        }
                        byte[] oldBytes = (d.getOldId() == null || d.getOldId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                        byte[] newBytes = (d.getNewId() == null || d.getNewId().toObjectId() == null) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());
                        if (oldBytes != null && newBytes != null && java.util.Arrays.equals(oldBytes, newBytes)) {
                            continue;
                        }

                        int[] counts = s.perFileCounts.get(path);
                        int added = (counts == null) ? 0 : counts[0];
                        int removed = (counts == null) ? 0 : counts[1];
                        int blocks = s.perFileBlocks.getOrDefault(path, 0);
                        FileChangeKind kind = toKind(d.getChangeType());
                        printFileSummary(System.out, kind, path, added, removed, blocks);

                        RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                        RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                        HistogramDiff alg2 = new HistogramDiff();
                        EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                        if (v == Verbosity.RAW) {
                            dfOut.format(d);
                        } else {
                            printHumanReadableDiff(at, bt, edits2);
                            System.out.println();
                        }
                    }
                }
            }
        }
        return any;
    }

    public static boolean diffTreeToWorking(Repository repo, ObjectId oldTreeId, FileTreeIterator workingTree, List<String> globs, Verbosity v) throws Exception {
        boolean any = false;
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);
            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                // If globs provided, expand them against the repo work tree for clarity.
                int matchedFiles = -1;
                if (globs != null && !globs.isEmpty()) {
                    try {
                        Path repoRoot = repo.getWorkTree() == null ? null : repo.getWorkTree().toPath();
                        if (repoRoot != null) {
                            List<String> resolved = GlobUtils.resolveGlobs(globs, repoRoot, System.out);
                            if (resolved.isEmpty()) {
                                return false;
                            }
                            globs = resolved;
                            matchedFiles = resolved.size();
                        }
                    } catch (IOException ignored) {
                        // Fall back to pattern matching if expansion fails
                    }
                }

                List<DiffEntry> diffs = df.scan(oldTree, workingTree);
                if (v == Verbosity.SUMMARY) {
                    DiffSummary s = new DiffSummary();
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                            continue;
                        }
                        any = true;
                        // Compute counts using HistogramDiff on the actual blob bytes so counts
                        // match the human-readable rendering.
                        String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                        String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();
                        byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                        byte[] newBytes = "/dev/null".equals(newPath) ? null : readWorkingFileOrNull(repo, newPath);
                        int added = 0;
                        int removed = 0;
                        int blocks = 0;
                        if (oldBytes != null && newBytes != null) {
                            RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                            RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                            HistogramDiff alg2 = new HistogramDiff();
                            EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                            blocks = edits2.size();
                            for (org.eclipse.jgit.diff.Edit e : edits2) {
                                removed += Math.max(0, e.getEndA() - e.getBeginA());
                                added += Math.max(0, e.getEndB() - e.getBeginB());
                            }
                        } else if (oldBytes == null) {
                            added = countLines(newBytes);
                            blocks = (added > 0) ? 1 : 0;
                        } else {
                            removed = countLines(oldBytes);
                            blocks = (removed > 0) ? 1 : 0;
                        }
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) {
                            path = d.getOldPath();
                        }
                        s.perFileCounts.put(path, new int[] {added, removed});
                        s.perFileBlocks.put(path, blocks);
                        s.perFileKind.put(path, toKind(d.getChangeType()));
                        s.totalAdded += added;
                        s.totalRemoved += removed;
                    }
                    if (!any) {
                        return false;
                    }
                    printSummary(System.out, s, matchedFiles);
                    return true;
                }

                DiffSummary s = new DiffSummary();
                List<DiffEntry> matched = new ArrayList<>();
                for (DiffEntry d : diffs) {
                    if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                        continue;
                    }
                    matched.add(d);

                    // Compute edits using the same HistogramDiff algorithm used for printing
                    // so the summary counts match the printed +/- lines.
                    String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                    String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();
                    byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                    byte[] newBytes = "/dev/null".equals(newPath) ? null : readWorkingFileOrNull(repo, newPath);
                    int added = 0;
                    int removed = 0;
                    int blocks = 0;
                    if (oldBytes != null && newBytes != null) {
                        RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                        RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                        HistogramDiff alg2 = new HistogramDiff();
                        EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                        blocks = edits2.size();
                        for (org.eclipse.jgit.diff.Edit e : edits2) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                    } else if (oldBytes == null) {
                        added = countLines(newBytes);
                        blocks = (added > 0) ? 1 : 0;
                    } else {
                        removed = countLines(oldBytes);
                        blocks = (removed > 0) ? 1 : 0;
                    }
                    String path = d.getNewPath();
                    if (path == null || path.equals("/dev/null")) path = d.getOldPath();
                    s.perFileCounts.put(path, new int[] {added, removed});
                    s.perFileBlocks.put(path, blocks);
                    s.perFileKind.put(path, toKind(d.getChangeType()));
                    s.totalAdded += added; s.totalRemoved += removed;
                }

                if (matched.isEmpty()) {
                    return false;
                }

                // IMPORTANT: tree -> working diffs can involve synthetic object IDs for the
                // working-tree side. Calling DiffFormatter.format(DiffEntry) can then throw
                // "Missing blob ...". Instead, load both sides ourselves and emit a simple
                // unified diff.
                if (v == Verbosity.HUMAN) {
                    int changedFiles = matched.size();
                    if (matchedFiles < 0) {
                        matchedFiles = changedFiles;
                    }
                    System.out.println("Matched files: " + matchedFiles + "; Changed files: " + changedFiles);
                    System.out.println();
                }

                // Top summary already shows matched/changed files; avoid redundant totals here.

                for (DiffEntry d : matched) {

                    String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                    String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();

                    byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                    byte[] newBytes = "/dev/null".equals(newPath) ? null : readWorkingFileOrNull(repo, newPath);

                    if (oldBytes != null && newBytes != null && java.util.Arrays.equals(oldBytes, newBytes)) {
                        continue;
                    }

                    any = true;

                    String rel = newPath.equals("/dev/null") ? oldPath : newPath;
                    int[] counts = s.perFileCounts.get(rel);
                    int added = (counts == null) ? 0 : counts[0];
                    int removed = (counts == null) ? 0 : counts[1];
                    int blocks = s.perFileBlocks.getOrDefault(rel, 0);
                    FileChangeKind kind = s.perFileKind.getOrDefault(rel, toKind(d.getChangeType()));
                    printFileSummary(System.out, kind, rel, added, removed, blocks);

                    printUnifiedDiff(oldPath, newPath, oldBytes, newBytes, v);
                }
            }
        }
        return any;
    }

    public static boolean diffWorkingToTree(Repository repo, FileTreeIterator workingTree, ObjectId newTreeId, List<String> globs, Verbosity v) throws Exception {
        boolean any = false;
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);
            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                // If globs provided, expand them against the repo work tree for clarity.
                int matchedFiles = -1;
                if (globs != null && !globs.isEmpty()) {
                    try {
                        Path repoRoot = repo.getWorkTree() == null ? null : repo.getWorkTree().toPath();
                        if (repoRoot != null) {
                            List<String> resolved = GlobUtils.resolveGlobs(globs, repoRoot, System.out);
                            if (resolved.isEmpty()) {
                                return false;
                            }
                            globs = resolved;
                            matchedFiles = resolved.size();
                        }
                    } catch (IOException ignored) {
                        // Fall back to pattern matching if expansion fails
                    }
                }

                List<DiffEntry> diffs = df.scan(workingTree, newTree);
                if (v == Verbosity.SUMMARY) {
                    DiffSummary s = new DiffSummary();
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                            continue;
                        }
                        any = true;

                        String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                        String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();

                        byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readWorkingFileOrNull(repo, oldPath);
                        byte[] newBytes = "/dev/null".equals(newPath) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());

                        int added = 0;
                        int removed = 0;
                        int blocks = 0;
                        if (oldBytes != null && newBytes != null) {
                            RawText at = new RawText(normalizeNewlines(oldBytes));
                            RawText bt = new RawText(normalizeNewlines(newBytes));
                            HistogramDiff alg2 = new HistogramDiff();
                            EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                            blocks = edits2.size();
                            for (Edit e : edits2) {
                                removed += Math.max(0, e.getEndA() - e.getBeginA());
                                added += Math.max(0, e.getEndB() - e.getBeginB());
                            }
                        } else if (oldBytes == null) {
                            added = countLines(newBytes);
                            blocks = (added > 0) ? 1 : 0;
                        } else {
                            removed = countLines(oldBytes);
                            blocks = (removed > 0) ? 1 : 0;
                        }

                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) {
                            path = d.getOldPath();
                        }
                        s.perFileCounts.put(path, new int[] {added, removed});
                        s.perFileBlocks.put(path, blocks);
                        s.perFileKind.put(path, toKind(d.getChangeType()));
                        s.totalAdded += added;
                        s.totalRemoved += removed;
                    }
                    if (!any) {
                        return false;
                    }
                    printSummary(System.out, s, matchedFiles);
                    return true;
                }

                DiffSummary s = new DiffSummary();
                List<DiffEntry> matched = new ArrayList<>();
                for (DiffEntry d : diffs) {
                    if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                        continue;
                    }
                    matched.add(d);

                    String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                    String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();

                    byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readWorkingFileOrNull(repo, oldPath);
                    byte[] newBytes = "/dev/null".equals(newPath) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());

                    int added = 0;
                    int removed = 0;
                    int blocks = 0;
                    if (oldBytes != null && newBytes != null) {
                        RawText at = new RawText(normalizeNewlines(oldBytes));
                        RawText bt = new RawText(normalizeNewlines(newBytes));
                        HistogramDiff alg2 = new HistogramDiff();
                        EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                        blocks = edits2.size();
                        for (Edit e : edits2) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                    } else if (oldBytes == null) {
                        added = countLines(newBytes);
                        blocks = (added > 0) ? 1 : 0;
                    } else {
                        removed = countLines(oldBytes);
                        blocks = (removed > 0) ? 1 : 0;
                    }

                    String path = d.getNewPath();
                    if (path == null || path.equals("/dev/null")) path = d.getOldPath();
                    s.perFileCounts.put(path, new int[] {added, removed});
                    s.perFileBlocks.put(path, blocks);
                    s.perFileKind.put(path, toKind(d.getChangeType()));
                    s.totalAdded += added;
                    s.totalRemoved += removed;
                }

                if (matched.isEmpty()) {
                    return false;
                }

                for (DiffEntry d : matched) {
                    any = true;
                    String path = d.getNewPath();
                    if (path == null || path.equals("/dev/null")) {
                        path = d.getOldPath();
                    }

                    int[] counts = s.perFileCounts.get(path);
                    int added = (counts == null) ? 0 : counts[0];
                    int removed = (counts == null) ? 0 : counts[1];
                    int blocks = s.perFileBlocks.getOrDefault(path, 0);
                    FileChangeKind kind = toKind(d.getChangeType());
                    printFileSummary(System.out, kind, path, added, removed, blocks);

                    if (v == Verbosity.RAW) {
                        try (DiffFormatter dfOut = new DiffFormatter(System.out)) {
                            dfOut.setRepository(repo);
                            dfOut.setDetectRenames(true);
                            dfOut.format(d);
                        }
                    } else {
                        String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                        String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();
                        byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readWorkingFileOrNull(repo, oldPath);
                        byte[] newBytes = "/dev/null".equals(newPath) ? null : readBlobOrNull(repo, d.getNewId().toObjectId());

                        RawText at = new RawText(normalizeNewlines(oldBytes != null ? oldBytes : new byte[0]));
                        RawText bt = new RawText(normalizeNewlines(newBytes != null ? newBytes : new byte[0]));
                        HistogramDiff alg2 = new HistogramDiff();
                        EditList edits2 = alg2.diff(RawTextComparator.DEFAULT, at, bt);
                        printHumanReadableDiff(at, bt, edits2);
                        System.out.println();
                    }
                }
            }
        }
        return any;
    }

    private static byte[] readBlobOrNull(Repository repo, ObjectId id) {
        if (repo == null || id == null || ObjectId.zeroId().equals(id)) {
            return null;
        }
        try {
            return repo.open(id).getBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readWorkingFileOrNull(Repository repo, String repoRelativePath) {
        if (repo == null || repoRelativePath == null || repoRelativePath.isBlank()) {
            return null;
        }
        try {
            java.io.File wt = repo.getWorkTree();
            if (wt == null) {
                return null;
            }
            Path p = wt.toPath().resolve(repoRelativePath).normalize();
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return null;
            }
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static void printUnifiedDiff(String oldPath, String newPath, byte[] oldBytes, byte[] newBytes, Verbosity v) throws IOException {
        if (v == Verbosity.RAW) {
            String aGit = "/dev/null".equals(oldPath) ? "a/" + newPath : "a/" + oldPath;
            String bGit = "/dev/null".equals(newPath) ? "b/" + oldPath : "b/" + newPath;

            String aPath = "/dev/null".equals(oldPath) ? "/dev/null" : ("a/" + oldPath);
            String bPath = "/dev/null".equals(newPath) ? "/dev/null" : ("b/" + newPath);

            System.out.println("diff --git " + aGit + " " + bGit);
            System.out.println("--- " + aPath);
            System.out.println("+++ " + bPath);
        }

        if ((oldBytes != null && RawText.isBinary(oldBytes)) || (newBytes != null && RawText.isBinary(newBytes))) {
            if (v == Verbosity.RAW) {
                System.out.println("Binary files differ");
            } else {
                System.out.println("  (binary files differ)");
            }
            System.out.println();
            return;
        }

        RawText at = new RawText(oldBytes != null ? oldBytes : new byte[0]);
        RawText bt = new RawText(newBytes != null ? newBytes : new byte[0]);
        HistogramDiff alg = new HistogramDiff();
        EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
        if (v == Verbosity.RAW) {
            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.format(edits, at, bt);
            }
        } else {
            printHumanReadableDiff(at, bt, edits);
            System.out.println();
        }
    }

    private static void printHumanReadableDiff(RawText at, RawText bt, EditList edits) {
        if (edits == null || edits.isEmpty()) {
            return;
        }
        // No debug output in normal runs.

        int blockIndex = 1;
        int totalBlocks = edits.size();
        for (Edit e : edits) {
            System.out.println("    Block " + blockIndex + " of " + totalBlocks + ":");
            int aStart = e.getBeginA();
            int aEnd = e.getEndA();
            int bStart = e.getBeginB();
            int bEnd = e.getEndB();

            int aLen = Math.max(0, aEnd - aStart);
            int bLen = Math.max(0, bEnd - bStart);

            // If this is a large REPLACE (many lines) we try a finer-grained LCS
            // within the replaced ranges so we print only the small changed
            // regions (like a merge tool would) instead of dumping the whole
            // file. Threshold chosen conservatively to avoid DP blowup.
            // Prefer refining when the DP work required is modest. This allows
            // us to refine REPLACE edits even if they span many lines, as long
            // as n*m stays below a safe budget. This handles cases where most
            // lines are unchanged but HistogramDiff collapsed them into a big
            // REPLACE.
            final long MAX_DP_CELLS = 200_000L; // tunable budget
            long dpCells = (long) aLen * (long) bLen;
            if (e.getType() == Edit.Type.REPLACE && dpCells <= MAX_DP_CELLS) {
                printRefinedReplace(at, bt, aStart, aEnd, bStart, bEnd);
            } else {
                if (aStart < aEnd) {
                    for (int i = aStart; i < aEnd; i++) {
                        String s = (i < at.size()) ? at.getString(i) : "";
                        System.out.println("- " + s);
                    }
                }
                if (bStart < bEnd) {
                    for (int i = bStart; i < bEnd; i++) {
                        String s = (i < bt.size()) ? bt.getString(i) : "";
                        System.out.println("+ " + s);
                    }
                }
            }
            System.out.println();
            blockIndex++;
        }
    }

    private static void printRefinedReplace(RawText at, RawText bt, int aStart, int aEnd, int bStart, int bEnd) {
        final int REFINEMENT_CONTEXT = 3;
        final int COLLAPSE_THRESHOLD = 6;
        int aLen = Math.max(0, aEnd - aStart);
        int bLen = Math.max(0, bEnd - bStart);
        if (aLen <= 0 && bLen <= 0) return;

        String[] aLines = new String[aLen];
        for (int i = 0; i < aLen; i++) aLines[i] = (aStart + i < at.size()) ? at.getString(aStart + i) : "";
        String[] bLines = new String[bLen];
        for (int i = 0; i < bLen; i++) bLines[i] = (bStart + i < bt.size()) ? bt.getString(bStart + i) : "";

        int n = aLines.length;
        int m = bLines.length;

        // Build LCS table (DP). For reasonable block sizes this is fine.
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (aLines[i].equals(bLines[j])) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Reconstruct match positions (indices of equal lines in the LCS).
        java.util.List<int[]> matches = new java.util.ArrayList<>();
        int ia = 0, ib = 0;
        while (ia < n && ib < m) {
            if (aLines[ia].equals(bLines[ib])) {
                matches.add(new int[] {ia, ib});
                ia++; ib++;
            } else if (dp[ia + 1][ib] >= dp[ia][ib + 1]) {
                ia++;
            } else {
                ib++;
            }
        }

        int lastMatchA = -1;
        int lastMatchB = -1;
        int printedMatchAUntil = -1;

        for (int k = 0; k <= matches.size(); k++) {
            int nextA = (k < matches.size()) ? matches.get(k)[0] : n;
            int nextB = (k < matches.size()) ? matches.get(k)[1] : m;

            // differing region between lastMatch+1 .. nextA-1  (a side)
            int remA0 = lastMatchA + 1;
            int remA1 = nextA - 1;
            int remB0 = lastMatchB + 1;
            int remB1 = nextB - 1;

            boolean hasRemA = remA0 <= remA1;
            boolean hasRemB = remB0 <= remB1;

            if (!hasRemA && !hasRemB) {
                // nothing changed in this gap; advance
                lastMatchA = nextA;
                lastMatchB = nextB;
                continue;
            }

            // Pre-context: print up to REFINEMENT_CONTEXT matched lines before this change
            if (lastMatchA >= 0 && printedMatchAUntil < lastMatchA) {
                int ctxStart = Math.max(0, lastMatchA - REFINEMENT_CONTEXT + 1);
                int gapStart = printedMatchAUntil + 1;
                int gapEnd = ctxStart - 1;
                int gapLen = gapEnd - gapStart + 1;
                if (gapLen > COLLAPSE_THRESHOLD) {
                    System.out.println("  ... " + gapLen + " unchanged lines collapsed ...");
                } else {
                    for (int i = ctxStart; i <= lastMatchA; i++) {
                        System.out.println(" " + aLines[i]);
                    }
                }
                printedMatchAUntil = lastMatchA;
            }

            // Print removed lines
            if (hasRemA) {
                for (int i = remA0; i <= remA1; i++) System.out.println("- " + aLines[i]);
            }
            // Print added lines
            if (hasRemB) {
                for (int i = remB0; i <= remB1; i++) System.out.println("+ " + bLines[i]);
            }

            // Post-context: print up to REFINEMENT_CONTEXT matched lines after this change
            if (k < matches.size()) {
                int ma = matches.get(k)[0];
                int mb = matches.get(k)[1];
                for (int c = 0; c < REFINEMENT_CONTEXT; c++) {
                    int idxA = ma + c;
                    int idxB = mb + c;
                    if (idxA < n && idxB < m && aLines[idxA].equals(bLines[idxB])) {
                        System.out.println(" " + aLines[idxA]);
                        printedMatchAUntil = idxA;
                    } else {
                        break;
                    }
                }
            }

            lastMatchA = nextA;
            lastMatchB = nextB;
        }
    }
}
