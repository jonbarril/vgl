package com.vgl.cli.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

public final class GitUtils {
    private GitUtils() {}

    /**
     * Opens a Git repository rooted at {@code repoRoot}. Supports worktree-style repositories where
     * {@code .git} is a file rather than a directory.
     */
    public static Git openGit(Path repoRoot) throws IOException {
        if (repoRoot == null) {
            throw new IOException("repoRoot is null");
        }
        File root = repoRoot.toFile();
        try {
            return Git.open(root);
        } catch (org.eclipse.jgit.errors.RepositoryNotFoundException e) {
            Path dotGit = repoRoot.resolve(".git");
            if (Files.exists(dotGit)) {
                return Git.open(dotGit.toFile());
            }
            throw e;
        }
    }

    public static boolean hasCommits(Repository repo) {
        try {
            return repo != null && repo.resolve("HEAD") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns repo-root-relative paths (with '/' separators) of files in HEAD tree. */
    public static Set<String> listHeadFiles(Repository repo) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        if (repo == null) {
            return out;
        }
        ObjectId treeId;
        try {
            treeId = repo.resolve("HEAD^{tree}");
        } catch (Exception e) {
            treeId = null;
        }
        if (treeId == null) {
            return out;
        }

        try (TreeWalk walk = new TreeWalk(repo)) {
            walk.addTree(treeId);
            walk.setRecursive(true);
            while (walk.next()) {
                out.add(walk.getPathString());
            }
        }
        return out;
    }

    /**
     * Returns repo-root-relative directory paths of nested git repos (directories containing a .git folder),
     * excluding the repo root itself. Returned paths use '/' separators and have no trailing '/'.
     */
    public static Set<String> listNestedRepos(Path repoRoot) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        if (repoRoot == null) {
            return out;
        }

        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(normalizedRoot)) {
                    return FileVisitResult.CONTINUE;
                }

                Path gitDir = dir.resolve(".git");
                if (Files.isDirectory(gitDir)) {
                    Path rel = normalizedRoot.relativize(dir);
                    String relStr = rel.toString().replace('\\', '/');
                    out.add(relStr);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return out;
    }
}
