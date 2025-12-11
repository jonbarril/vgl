package com.vgl.cli.commands;

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
        // Untracked
        try { untracked.addAll(status.getUntracked()); } catch (Exception ignore) {}
        // Ignored
        try { ignored.addAll(status.getIgnoredNotInIndex()); } catch (Exception ignore) {}
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
        // Undecided: currently not used, but placeholder for future logic
    }

    // Helper to resolve file change categories (added, modified, removed, renamed)
    public static void resolveChangeCategories(org.eclipse.jgit.api.Status status, Set<String> added, Set<String> modified, Set<String> removed, Set<String> renamed) {
        try { added.addAll(status.getAdded()); } catch (Exception ignore) {}
        try { modified.addAll(status.getModified()); } catch (Exception ignore) {}
        try { removed.addAll(status.getRemoved()); } catch (Exception ignore) {}
        // Renamed: JGit does not provide renamed directly; placeholder for future logic
    }
}