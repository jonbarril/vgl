package com.vgl.cli.commands.helpers;

import java.util.Set;

public class StatusCommandHelpers {
    // Helper to resolve file management categories (tracked, untracked, undecided, ignored)

    public static void resolveFileCategories(org.eclipse.jgit.api.Status status, org.eclipse.jgit.lib.Repository repo, Set<String> tracked, Set<String> untracked, Set<String> undecided, Set<String> ignored) {
        // 1. Scan for nested repos
        java.io.File workTree = repo != null ? repo.getWorkTree() : null;
        java.util.Set<String> repoDirs = new java.util.HashSet<>();
        if (workTree != null) findAllNestedRepos(workTree, "", repoDirs);
        for (String repoDir : repoDirs) {
            String dirPath = repoDir.endsWith("/") ? repoDir : repoDir + "/";
            String undecorated = dirPath;
            String undecoratedNoSlash = dirPath.endsWith("/") ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
            untracked.remove(undecorated);
            untracked.remove(undecoratedNoSlash);
            undecided.remove(undecorated);
            undecided.remove(undecoratedNoSlash);
            ignored.remove(undecorated);
            ignored.remove(undecoratedNoSlash);
        }
        for (String repoDir : repoDirs) {
            String decorated = repoDir + " (repo)";
            ignored.add(decorated);
        }
        if (ignored.contains(".git")) {
            ignored.remove(".git");
            ignored.add(".git (repo)");
        }
        // Ignored: JGit's ignored plus .git and nested repos
        try { ignored.addAll(status.getIgnoredNotInIndex()); } catch (Exception ignore) {}
        ignored.add(".git");
        java.nio.file.Path repoRoot = repo != null ? repo.getWorkTree().toPath() : null;
        java.util.Set<String> nestedRepos = new java.util.LinkedHashSet<>();
        if (repoRoot != null) {
            nestedRepos = com.vgl.cli.utils.GitUtils.listNestedRepos(repoRoot);
            ignored.addAll(nestedRepos);
        }
        // Untracked: JGit's untracked files
        java.util.Set<String> allUntracked = new java.util.LinkedHashSet<>();
        try { allUntracked.addAll(status.getUntracked()); } catch (Exception ignore) {}
        // Compute the set of all files that have ever been decided (tracked, untracked, or ignored)
        java.util.Set<String> everDecided = new java.util.LinkedHashSet<>();
        everDecided.addAll(tracked);
        everDecided.addAll(untracked);
        everDecided.addAll(ignored);
        // Only files that have never been decided can be undecided
        for (String f : allUntracked) {
            if (!everDecided.contains(f) && !nestedRepos.contains(f)) {
                undecided.add(f);
            }
        }
        // Remove all undecided files from untracked so new files only appear in Undecided
        untracked.addAll(allUntracked);
        untracked.removeAll(undecided);
    }

    // Recursively find all directories containing a .git subdirectory
    private static void findAllNestedRepos(java.io.File dir, String relPath, java.util.Set<String> repoDirs) {
        if (!dir.isDirectory()) return;
        java.io.File gitDir = new java.io.File(dir, ".git");
        if (gitDir.exists()) {
            if (!relPath.isEmpty()) {
                repoDirs.add(relPath);
                return; // Do not recurse further into a nested repo
            }
            // If root, do not add, but still recurse into children
        }
        java.io.File[] children = dir.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                if (child.isDirectory()) {
                    String childRel = relPath.isEmpty() ? child.getName() : relPath + "/" + child.getName();
                    findAllNestedRepos(child, childRel, repoDirs);
                }
            }
        }
    }

    // Helper to resolve file change categories (added, modified, removed, renamed)
    public static void resolveChangeCategories(org.eclipse.jgit.api.Status status, Set<String> added, Set<String> modified, Set<String> removed, Set<String> renamed) {
        try { added.addAll(status.getAdded()); } catch (Exception ignore) {}
        try { modified.addAll(status.getModified()); } catch (Exception ignore) {}
        try { removed.addAll(status.getRemoved()); } catch (Exception ignore) {}
        // Renamed: JGit does not provide renamed directly; placeholder for future logic
    }
}
