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

        for (String g : globs) {
            if ("*".equals(g) || ".".equals(g)) {
                try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(base)) {
                    s.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
                        try {
                            if (GitUtils.listNestedRepos(base).contains(base.relativize(p).toString().replace('\\','/'))) return;
                            if (isGitIgnored(p, repo)) return;
                            Path rel = base.relativize(p);
                            String rels = rel.toString().replace('\\','/');
                            if (rels.startsWith(".git/") || ".git".equals(rels)) return;
                            out.add(rel.toString().replace('\\','/'));
                        } catch (Exception ignored) {}
                    });
                }
                continue;
            }

            final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
            try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(base)) {
                s.forEach(p -> {
                    try {
                        Path rel = base.relativize(p);
                        if (!m.matches(rel)) return;
                        if (java.nio.file.Files.isDirectory(p)) {
                            try (java.util.stream.Stream<Path> sub = java.nio.file.Files.walk(p)) {
                                sub.filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                                    try {
                                        if (GitUtils.listNestedRepos(base).contains(base.relativize(f).toString().replace('\\','/'))) return;
                                        if (isGitIgnored(f, repo)) return;
                                        Path r2 = base.relativize(f);
                                        out.add(r2.toString().replace('\\','/'));
                                    } catch (Exception ignored) {}
                                });
                            }
                        } else if (java.nio.file.Files.isRegularFile(p)) {
                            if (GitUtils.listNestedRepos(base).contains(rel.toString().replace('\\','/'))) return;
                            if (isGitIgnored(p, repo)) return;
                            String rels = rel.toString().replace('\\','/');
                            if (rels.startsWith(".git/") || ".git".equals(rels)) return;
                            out.add(rels);
                        }
                    } catch (Exception ignored) {}
                });
            }
        }

        return new ArrayList<>(out);
    }
}
