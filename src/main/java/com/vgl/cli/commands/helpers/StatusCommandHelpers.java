package com.vgl.cli.commands.helpers;

import java.util.Set;

public class StatusCommandHelpers {
    // Helper to resolve file management categories (tracked, untracked, undecided, ignored)
    public static void resolveFileCategories(org.eclipse.jgit.api.Status status, org.eclipse.jgit.lib.Repository repo, Set<String> tracked, Set<String> untracked, Set<String> undecided, Set<String> ignored) {
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

    // Helper to resolve file change categories (added, modified, removed, renamed)
    public static void resolveChangeCategories(org.eclipse.jgit.api.Status status, Set<String> added, Set<String> modified, Set<String> removed, Set<String> renamed) {
        try { added.addAll(status.getAdded()); } catch (Exception ignore) {}
        try { modified.addAll(status.getModified()); } catch (Exception ignore) {}
        try { removed.addAll(status.getRemoved()); } catch (Exception ignore) {}
        // Renamed: JGit does not provide renamed directly; placeholder for future logic
    }
}
