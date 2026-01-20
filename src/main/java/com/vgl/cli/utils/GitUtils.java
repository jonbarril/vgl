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
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.dircache.DirCache;

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
     * Returns repo-root-relative file paths (with '/' separators) for the working tree,
     * excluding files ignored by Git ignore rules (.gitignore, info/exclude, etc.).
     */
    public static Set<String> listNonIgnoredFiles(Path repoRoot, Repository repo) {
        Set<String> out = new LinkedHashSet<>();
        if (repo == null || repoRoot == null) {
            return out;
        }

        try {
            FileTreeIterator workingTreeIt = new FileTreeIterator(repo);
            try (TreeWalk walk = new TreeWalk(repo)) {
                walk.addTree(workingTreeIt);
                walk.setRecursive(true);

                while (walk.next()) {
                    WorkingTreeIterator wti = walk.getTree(0, WorkingTreeIterator.class);
                    if (wti == null) {
                        continue;
                    }

                    boolean ignored;
                    try {
                        ignored = wti.isEntryIgnored();
                    } catch (Exception e) {
                        ignored = false;
                    }
                    if (ignored) {
                        continue;
                    }

                    String path = walk.getPathString();
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    out.add(path.replace('\\', '/'));
                }
            }
        } catch (Exception ignored) {
            // best-effort
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

    /**
     * Returns true if the given repo's index (DirCache) contains an entry for the provided
     * repo-relative path. Path should use '/' separators.
     */
    public static boolean indexContains(Repository repo, String repoRelativePath) {
        if (repo == null || repoRelativePath == null || repoRelativePath.isBlank()) return false;
        try {
            DirCache dc = DirCache.read(repo);
            return dc.getEntry(repoRelativePath) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves the remote URL from the Git repository.
     */
    public static String getRemoteUrl(Repository repo) {
        if (repo == null) return null;
        return repo.getConfig().getString("remote", "origin", "url");
    }

    /**
     * Retrieves the remote branch from the Git repository.
     */
    public static String getRemoteBranch(Repository repo) {
        if (repo == null) return null;
        try {
            String branch = repo.getConfig().getString("branch", repo.getBranch(), "merge");
            return (branch != null && branch.startsWith("refs/heads/")) ? branch.substring(11) : branch;
        } catch (IOException e) {
            return null; // Return null if an exception occurs
        }
    }
}
