package com.vgl.cli.commands.helpers;

import java.util.Set;

public class StatusCommandHelpers {
    // Helper to resolve file management categories (tracked, untracked, undecided, ignored)

    public static void resolveFileCategories(org.eclipse.jgit.api.Status status, org.eclipse.jgit.lib.Repository repo, Set<String> tracked, Set<String> untracked, Set<String> undecided, Set<String> ignored) {
        // 1. Forcefully scan all directories in the working tree for .git subdirectories
        java.io.File workTree = repo != null ? repo.getWorkTree() : null;
        java.util.Set<String> repoDirs = new java.util.HashSet<>();
        if (workTree != null) findAllNestedRepos(workTree, "", repoDirs);
        // Remove all forms of nested repo dirs from untracked, undecided, and ignored, then add only decorated form to ignored
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
        // Now add only the decorated form for each nested repo
        for (String repoDir : repoDirs) {
            String dirPath = repoDir.endsWith("/") ? repoDir : repoDir + "/";
            String decorated = dirPath + " (repo)";
            ignored.add(decorated);
        }
        // Ensure .git is always decorated as (repo)
        if (ignored.contains(".git")) {
            ignored.remove(".git");
            ignored.add(".git (repo)");
        }
        // 2. All remaining untracked files are considered undecided (not untracked)
        undecided.addAll(untracked);
        // Do not add to untracked set

        // Tracked: all files that are tracked by git (added, changed, modified, removed, missing, conflicting, etc.)
        try { tracked.addAll(status.getAdded()); } catch (Exception ignore) {}
        try { tracked.addAll(status.getChanged()); } catch (Exception ignore) {}
        try { tracked.addAll(status.getModified()); } catch (Exception ignore) {}
        try { tracked.addAll(status.getRemoved()); } catch (Exception ignore) {}
        try { tracked.addAll(status.getMissing()); } catch (Exception ignore) {}
        try { tracked.addAll(status.getConflicting()); } catch (Exception ignore) {}

        // Ignored: JGit's ignored plus .git and nested repos
        try { ignored.addAll(status.getIgnoredNotInIndex()); } catch (Exception ignore) {}
        // Always add .git directory
        ignored.add(".git");
        // Add nested repos (directories with .git)
        java.nio.file.Path repoRoot = repo != null ? repo.getWorkTree().toPath() : null;
        java.util.Set<String> nestedRepos = new java.util.LinkedHashSet<>();
        if (repoRoot != null) {
            nestedRepos = com.vgl.cli.utils.GitUtils.listNestedRepos(repoRoot);
            ignored.addAll(nestedRepos);
        }

        // Also add all HEAD-tracked files (committed files) to tracked set
        try {
            if (repo != null && repo.resolve("HEAD") != null) {
                org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repo);
                org.eclipse.jgit.revwalk.RevCommit headCommit = walk.parseCommit(repo.resolve("HEAD"));
                org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repo);
                treeWalk.addTree(headCommit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    tracked.add(treeWalk.getPathString());
                }
                walk.close();
                treeWalk.close();
            }
        } catch (Exception ignore) {}

        // Untracked: JGit's untracked files
        java.util.Set<String> allUntracked = new java.util.LinkedHashSet<>();
        try { allUntracked.addAll(status.getUntracked()); } catch (Exception ignore) {}

        // Undecided: all files that are not tracked, not ignored, not nested repo (i.e., all new files)
        for (String f : allUntracked) {
            if (!tracked.contains(f) && !ignored.contains(f) && !nestedRepos.contains(f)) {
                undecided.add(f);
            }
        }

        // Remove all undecided files from untracked so new files only appear in Undecided
        untracked.addAll(allUntracked);
        untracked.removeAll(undecided);

        // Force: if a file is not tracked, not ignored, not nested repo, and not in undecided, add it to undecided
        for (String f : allUntracked) {
            if (!tracked.contains(f) && !ignored.contains(f) && !nestedRepos.contains(f) && !undecided.contains(f)) {
                undecided.add(f);
                untracked.remove(f);
            }
        }
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
