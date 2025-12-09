package com.vgl.refactor;

import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class IgnoreUtils {
    private IgnoreUtils() {}

    public static boolean isGitIgnored(Path file, Repository repo) {
        if (repo == null || file == null) return false;
        try {
            if (file.getFileName() != null && ".vgl".equals(file.getFileName().toString())) return true;
        } catch (Exception e) {}
        try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repo)) {
            Path repoRoot = repo.getWorkTree().toPath();
            String relPath = repoRoot.relativize(file.toAbsolutePath()).toString().replace('\\','/');
            return git.status().call().getIgnoredNotInIndex().contains(relPath);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> expandGlobsToFiles(List<String> globs, Path repoRoot, Repository repo) throws IOException {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (globs == null || globs.isEmpty()) return new ArrayList<>();
        Path base = repoRoot.toAbsolutePath().normalize();
        // Use JGit-based list of non-ignored repository files to avoid uncontrolled filesystem walks.
        java.util.Set<String> repoFiles = GitUtils.listNonIgnoredFiles(base, repo);
        java.util.Set<String> nested = GitUtils.listNestedRepos(base);

        for (String g : globs) {
            if ("*".equals(g) || ".".equals(g)) {
                for (String f : repoFiles) {
                    try {
                        if (isUnderNested(f, nested)) continue;
                        if (f.startsWith(".git/") || ".git".equals(f)) continue;
                        out.add(f);
                    } catch (Exception ignored) {}
                }
                continue;
            }

            // If the glob is a literal directory path inside the repo, expand to files under that dir.
            try {
                Path possibleDir = base.resolve(g).normalize();
                if (java.nio.file.Files.exists(possibleDir) && java.nio.file.Files.isDirectory(possibleDir)) {
                    String prefix = base.relativize(possibleDir).toString().replace('\\','/');
                    if (!prefix.isEmpty() && !prefix.endsWith("/")) prefix = prefix + "/";
                    for (String f : repoFiles) {
                        try {
                            if (!f.startsWith(prefix)) continue;
                            if (isUnderNested(f, nested)) continue;
                            if (f.startsWith(".git/") || ".git".equals(f)) continue;
                            out.add(f);
                        } catch (Exception ignored) {}
                    }
                    continue;
                }
            } catch (Exception ignored) {}

            final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
            for (String f : repoFiles) {
                try {
                    Path rel = FileSystems.getDefault().getPath(f);
                    if (!m.matches(rel)) continue;
                    if (isUnderNested(f, nested)) continue;
                    out.add(f);
                } catch (Exception ignored) {}
            }
        }

        return new ArrayList<>(out);
    }

    private static boolean isUnderNested(String f, java.util.Set<String> nested) {
        if (nested == null || nested.isEmpty()) return false;
        for (String n : nested) {
            if (n == null || n.isEmpty()) continue;
            if (f.equals(n)) return true;
            if (f.startsWith(n + "/")) return true;
        }
        return false;
    }
}
