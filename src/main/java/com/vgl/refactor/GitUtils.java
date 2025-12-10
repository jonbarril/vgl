package com.vgl.refactor;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class GitUtils {
    private GitUtils() {}

    public static Git findGitRepo(Path startPath) throws IOException {
        return findGitRepo(startPath, null);
    }

    public static Git findGitRepo(Path startPath, Path ceilingDir) throws IOException {
        if (startPath == null) return null;
        File ceilingFile = (ceilingDir != null) ? ceilingDir.toFile() : null;
        Repository r = openNearestGitRepo(startPath.toFile(), ceilingFile);
        if (r == null || r.isBare()) return null;
        return new Git(r);
    }

    public static Repository openNearestGitRepo(File start, File ceiling) throws IOException {
        FileRepositoryBuilder fb = new FileRepositoryBuilder();
        if (ceiling != null) fb.addCeilingDirectory(ceiling);
        fb.findGitDir(start);
        return (fb.getGitDir() != null) ? fb.build() : null;
    }

    public static Path getGitRepoRoot(Path startPath) throws IOException {
        try (Git git = findGitRepo(startPath, null)) {
            if (git == null) return null;
            return git.getRepository().getWorkTree().toPath();
        }
    }

    public static boolean hasCommits(Repository repo) {
        if (repo == null) return false;
        try {
            org.eclipse.jgit.lib.ObjectId head = repo.resolve("HEAD");
            return head != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static java.util.Set<String> listNestedRepos(Path repoRoot) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (repoRoot == null) return out;
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(repoRoot)) {
            s.filter(java.nio.file.Files::isDirectory).forEach(d -> {
                try {
                    if (java.nio.file.Files.exists(d.resolve(".git"))) {
                        Path rel = repoRoot.toAbsolutePath().normalize().relativize(d.toAbsolutePath().normalize());
                        String r = rel.toString().replace('\\','/');
                        if (!r.isEmpty()) out.add(r);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {}
        return out;
    }

    public static java.util.Set<String> listNonIgnoredFiles(Path repoRoot, Repository repo) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (repo == null || repoRoot == null) return out;
        try {
            org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(repo);
            org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repo);
            treeWalk.addTree(workingTreeIt);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                org.eclipse.jgit.treewalk.WorkingTreeIterator wti = (org.eclipse.jgit.treewalk.WorkingTreeIterator) treeWalk.getTree(0, org.eclipse.jgit.treewalk.WorkingTreeIterator.class);
                String path = treeWalk.getPathString();
                if (wti == null) continue;
                try {
                    if (!wti.isEntryIgnored()) {
                        out.add(path.replace('\\','/'));
                    }
                } catch (Exception e) {}
            }
            treeWalk.close();
        } catch (Exception e) {}
        return out;
    }

}
