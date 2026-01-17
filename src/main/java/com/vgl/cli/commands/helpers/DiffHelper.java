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

    public enum Verbosity {
        SUMMARY, HUMAN, RAW
    }

    public static Verbosity computeVerbosity(List<String> args) {
        if (args != null && args.contains("-vv")) {
            return Verbosity.RAW;
        }
        if (args != null && args.contains("-v")) {
            return Verbosity.HUMAN;
        }
        // Historically diffs printed full output by default; keep human-readable diffs
        // as the default behavior unless a summary flag is explicitly requested.
        return Verbosity.HUMAN;
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
            if (av != null && bv != null && java.util.Arrays.equals(av, bv)) continue;

            int added = 0;
            int removed = 0;
            if (av != null && bv != null) {
                RawText at = new RawText(av != null ? av : new byte[0]);
                RawText bt = new RawText(bv != null ? bv : new byte[0]);
                HistogramDiff alg = new HistogramDiff();
                EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
                for (org.eclipse.jgit.diff.Edit e : edits) {
                    removed += Math.max(0, e.getEndA() - e.getBeginA());
                    added += Math.max(0, e.getEndB() - e.getBeginB());
                }
            } else if (av == null) {
                added = countLines(bv);
            } else {
                removed = countLines(av);
            }
            s.perFileCounts.put(rel, new int[] {added, removed});
            s.totalAdded += added;
            s.totalRemoved += removed;
        }
        return s;
    }

    private static int countLines(byte[] b) {
        if (b == null || b.length == 0) return 0;
        int lines = 0;
        for (byte c : b) if (c == '\n') lines++;
        return lines;
    }

    public static void printSummary(PrintStream out, DiffSummary s) {
        out.println(s.perFileCounts.size() + " file(s) changed — +" + s.totalAdded + "/-" + s.totalRemoved);
        for (Map.Entry<String,int[]> e : s.perFileCounts.entrySet()) {
            int[] ar = e.getValue();
            out.println("  M " + e.getKey() + " (+" + ar[0] + "/-" + ar[1] + ")");
        }
        out.println();
        out.println("Use `vgl diff -v` to see full diffs.");
    }

    public static boolean diffWorkingTrees(Path leftRoot, Path rightRoot, List<String> globs, Verbosity v) throws IOException {
        Map<String, byte[]> left = snapshotFiles(leftRoot, globs);
        Map<String, byte[]> right = snapshotFiles(rightRoot, globs);
        if (v == Verbosity.SUMMARY) {
            DiffSummary s = computeDiffSummary(left, right);
            if (s.perFileCounts.isEmpty()) return false;
            printSummary(System.out, s);
            return true;
        }
        List<String> all = new ArrayList<>();
        all.addAll(left.keySet());
        for (String k : right.keySet()) if (!all.contains(k)) all.add(k);
        all.sort(String::compareTo);
        boolean any = false;
        for (String rel : all) {
            byte[] a = left.get(rel);
            byte[] b = right.get(rel);
            if (a != null && b != null && java.util.Arrays.equals(a,b)) continue;
            any = true;
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
            if (a != null && RawText.isBinary(a) || b != null && RawText.isBinary(b)) {
                System.out.println("Binary files differ");
                continue;
            }
            RawText at = new RawText(a != null ? a : new byte[0]);
            RawText bt = new RawText(b != null ? b : new byte[0]);
            HistogramDiff alg = new HistogramDiff();
            EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.format(edits, at, bt);
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
                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                if (v == Verbosity.SUMMARY) {
                    DiffSummary s = new DiffSummary();
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) continue;
                        any = true;
                        var fh = df.toFileHeader(d);
                        EditList edits = fh.toEditList();
                        int added = 0; int removed = 0;
                        for (org.eclipse.jgit.diff.Edit e : edits) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) path = d.getOldPath();
                        s.perFileCounts.put(path, new int[] {added, removed});
                        s.totalAdded += added; s.totalRemoved += removed;
                    }
                    if (!any) return false;
                    printSummary(System.out, s);
                    return true;
                }
                try (DiffFormatter dfOut = new DiffFormatter(System.out)) {
                    dfOut.setRepository(repo);
                    dfOut.setDetectRenames(true);
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) continue;
                        any = true;
                        dfOut.format(d);
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

                List<DiffEntry> diffs = df.scan(oldTree, workingTree);
                if (v == Verbosity.SUMMARY) {
                    DiffSummary s = new DiffSummary();
                    for (DiffEntry d : diffs) {
                        if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                            continue;
                        }
                        any = true;
                        var fh = df.toFileHeader(d);
                        EditList edits = fh.toEditList();
                        int added = 0;
                        int removed = 0;
                        for (org.eclipse.jgit.diff.Edit e : edits) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) {
                            path = d.getOldPath();
                        }
                        s.perFileCounts.put(path, new int[] {added, removed});
                        s.totalAdded += added;
                        s.totalRemoved += removed;
                    }
                    if (!any) {
                        return false;
                    }
                    printSummary(System.out, s);
                    return true;
                }

                // IMPORTANT: tree -> working diffs can involve synthetic object IDs for the
                // working-tree side. Calling DiffFormatter.format(DiffEntry) can then throw
                // "Missing blob ...". Instead, load both sides ourselves and emit a simple
                // unified diff.
                for (DiffEntry d : diffs) {
                    if (!GlobUtils.matchesAny(d.getOldPath(), globs) && !GlobUtils.matchesAny(d.getNewPath(), globs)) {
                        continue;
                    }

                    String oldPath = (d.getOldPath() == null) ? "/dev/null" : d.getOldPath();
                    String newPath = (d.getNewPath() == null) ? "/dev/null" : d.getNewPath();

                    byte[] oldBytes = "/dev/null".equals(oldPath) ? null : readBlobOrNull(repo, d.getOldId().toObjectId());
                    byte[] newBytes = "/dev/null".equals(newPath) ? null : readWorkingFileOrNull(repo, newPath);

                    if (oldBytes != null && newBytes != null && java.util.Arrays.equals(oldBytes, newBytes)) {
                        continue;
                    }

                    any = true;
                    printUnifiedDiff(oldPath, newPath, oldBytes, newBytes);
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

    private static void printUnifiedDiff(String oldPath, String newPath, byte[] oldBytes, byte[] newBytes) throws IOException {
        String aGit = "/dev/null".equals(oldPath) ? "a/" + newPath : "a/" + oldPath;
        String bGit = "/dev/null".equals(newPath) ? "b/" + oldPath : "b/" + newPath;

        String aPath = "/dev/null".equals(oldPath) ? "/dev/null" : ("a/" + oldPath);
        String bPath = "/dev/null".equals(newPath) ? "/dev/null" : ("b/" + newPath);

        System.out.println("diff --git " + aGit + " " + bGit);
        System.out.println("--- " + aPath);
        System.out.println("+++ " + bPath);

        if ((oldBytes != null && RawText.isBinary(oldBytes)) || (newBytes != null && RawText.isBinary(newBytes))) {
            System.out.println("Binary files differ");
            return;
        }

        RawText at = new RawText(oldBytes != null ? oldBytes : new byte[0]);
        RawText bt = new RawText(newBytes != null ? newBytes : new byte[0]);
        HistogramDiff alg = new HistogramDiff();
        EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
        try (DiffFormatter df = new DiffFormatter(System.out)) {
            df.format(edits, at, bt);
        }
    }
}
