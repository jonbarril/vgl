package com.vgl.cli.commands.status;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.util.List;

public final class StatusSyncFiles {
    private StatusSyncFiles() {}

    public static void printSyncFiles(Git git, Status status, String remoteUrl, String remoteBranch,
                                      List<String> filters, boolean verbose, boolean veryVerbose, VglCli vgl) {
        // Build filesToCommit from working tree status
        java.util.Map<String, String> filesToCommit = new java.util.LinkedHashMap<>();
        if (status != null) {
            for (String p : status.getModified()) filesToCommit.put(p, "M");
            for (String p : status.getChanged()) filesToCommit.put(p, "M");
            for (String p : status.getAdded()) filesToCommit.put(p, "A");
            for (String p : status.getRemoved()) filesToCommit.put(p, "D");
            for (String p : status.getMissing()) filesToCommit.put(p, "D");
        }

        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        String effectiveRemoteBranch = remoteBranch;
        if (!hasRemote) {
            try {
                org.eclipse.jgit.lib.StoredConfig cfg = git.getRepository().getConfig();
                String gitRemoteUrl = cfg.getString("remote", "origin", "url");
                if (gitRemoteUrl != null && !gitRemoteUrl.isEmpty()) {
                    hasRemote = true;
                    String currentBranch = git.getRepository().getBranch();
                    String trackedBranch = cfg.getString("branch", currentBranch, "merge");
                    if (trackedBranch != null) effectiveRemoteBranch = trackedBranch.replaceFirst("refs/heads/", "");
                    else effectiveRemoteBranch = currentBranch;
                }
            } catch (Exception ignore) {}
        }

        if (hasRemote) {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                    // commits to push
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPush = git.log().add(localHead).not(remoteHead).call();
                    for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPush) {
                        if (commit.getParentCount() > 0) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            oldTree.reset(reader, commit.getParent(0).getTree());
                            newTree.reset(reader, commit.getTree());
                            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                                df.setRepository(git.getRepository());
                                df.setDetectRenames(true);
                                List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                    String newPath = diff.getNewPath();
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                    String statusLetter = switch (diff.getChangeType()) {
                                        case ADD -> "A";
                                        case MODIFY -> "M";
                                        case DELETE -> "D";
                                        case RENAME -> "R";
                                        case COPY -> "R"; // treat copy as moved/renamed for user-facing summary
                                        default -> "M";
                                    };
                                    // If this file came from a commit that needs to be pushed, mark it with an up-arrow
                                    filesToCommit.put(filePath, "↑ " + statusLetter);
                                }
                            }
                            reader.close();
                        }
                    }

                    

                    // commits to pull
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPull = git.log().add(remoteHead).not(localHead).call();
                    java.util.Map<String, String> filesToMerge = new java.util.LinkedHashMap<>();
                    for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPull) {
                        if (commit.getParentCount() > 0) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            oldTree.reset(reader, commit.getParent(0).getTree());
                            newTree.reset(reader, commit.getTree());
                            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                                df.setRepository(git.getRepository());
                                df.setDetectRenames(true);
                                List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                    String newPath = diff.getNewPath();
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                    String statusLetter = switch (diff.getChangeType()) {
                                        case ADD -> "A";
                                        case MODIFY -> "M";
                                        case DELETE -> "D";
                                        case RENAME -> "R";
                                        case COPY -> "R"; // show copies as renames/moves
                                        default -> "M";
                                    };
                                    // If this file came from a commit that needs to be pulled, mark it with a down-arrow
                                    filesToMerge.put(filePath, "↓ " + statusLetter);
                                }
                            }
                            reader.close();
                        }
                    }

                    // Print Files to Commit
                    System.out.println("  -- Files to Commit:");
                    if (filesToCommit.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        for (java.util.Map.Entry<String, String> e : filesToCommit.entrySet()) {
                            if (!filters.isEmpty() && !matchesAnyFilter(e.getKey(), filters)) continue;
                            System.out.println("  " + e.getValue() + " " + e.getKey());
                        }
                    }

                    // Print Files to Merge
                    System.out.println("  -- Files to Merge:");
                    if (filesToMerge.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        for (java.util.Map.Entry<String, String> e : filesToMerge.entrySet()) {
                            if (!filters.isEmpty() && !matchesAnyFilter(e.getKey(), filters)) continue;
                            System.out.println("  " + e.getValue() + " " + e.getKey());
                        }
                    }
                    return;
                }
                // else fall through to print working-tree only
            } catch (Exception e) {
                // fall through and print working-tree only
            }
        }

        // No remote or remote in sync: print working-tree changes only
        System.out.println("  -- Files to Commit:");
        if (filesToCommit.isEmpty()) {
            System.out.println("  (none)");
        } else {
            boolean any = false;
            for (java.util.Map.Entry<String, String> e : filesToCommit.entrySet()) {
                if (filters != null && !filters.isEmpty() && !matchesAnyFilter(e.getKey(), filters)) continue;
                System.out.println("  " + e.getValue() + " " + e.getKey());
                any = true;
            }
            if (!any) System.out.println("  (none)");
        }
        System.out.println("  -- Files to Merge:");
        System.out.println("  (none)");

        // print undecided/verbose lists handled by StatusCommand or StatusVerboseOutput
    }

    /**
     * Detect renames between HEAD tree and working tree (uncommitted/staged renames).
     * Returns a map of oldPath -> newPath for detected renames/copies.
     */
    public static java.util.Map<String, String> computeWorkingRenames(Git git) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        if (git == null) return out;
        try {
            org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) return out;
            org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
            org.eclipse.jgit.revwalk.RevCommit headCommit = rw.parseCommit(head);
            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
            oldTree.reset(reader, headCommit.getTree());
            org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository());
            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                df.setRepository(git.getRepository());
                df.setDetectRenames(true);
                java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, workingTreeIt);
                for (org.eclipse.jgit.diff.DiffEntry d : diffs) {
                    if (d.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME || d.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY) {
                        out.put(d.getOldPath(), d.getNewPath());
                    }
                }
            }
            reader.close();
            rw.close();
        } catch (Exception ignore) {}
        return out;
    }

    private static boolean matchesAnyFilter(String path, List<String> filters) {
        for (String filter : filters) {
            if (filter.contains("*") || filter.contains("?")) {
                String regex = filter.replace(".", "\\.")
                                    .replace("*", ".*")
                                    .replace("?", ".");
                if (path.matches(regex)) return true;
            } else {
                if (path.equals(filter) || path.startsWith(filter + "/") || path.contains("/" + filter)) return true;
            }
        }
        return false;
    }

    public static int computeCommitRenamedCount(Git git, Status status, String remoteUrl, String remoteBranch) {
        java.util.Set<String> renamed = new java.util.LinkedHashSet<>();
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        String effectiveRemoteBranch = remoteBranch;
        if (!hasRemote) {
            try {
                org.eclipse.jgit.lib.StoredConfig cfg = git.getRepository().getConfig();
                String gitRemoteUrl = cfg.getString("remote", "origin", "url");
                if (gitRemoteUrl != null && !gitRemoteUrl.isEmpty()) {
                    hasRemote = true;
                    String currentBranch = git.getRepository().getBranch();
                    String trackedBranch = cfg.getString("branch", currentBranch, "merge");
                    if (trackedBranch != null) effectiveRemoteBranch = trackedBranch.replaceFirst("refs/heads/", "");
                    else effectiveRemoteBranch = currentBranch;
                }
            } catch (Exception ignore) {}
        }

        if (hasRemote) {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                    // commits to push
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPush = git.log().add(localHead).not(remoteHead).call();
                    for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPush) {
                        if (commit.getParentCount() > 0) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            oldTree.reset(reader, commit.getParent(0).getTree());
                            newTree.reset(reader, commit.getTree());
                            List<org.eclipse.jgit.diff.DiffEntry> diffs = git.diff().setOldTree(oldTree).setNewTree(newTree).call();
                            for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                if (diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME || diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY) {
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                                    renamed.add(filePath);
                                }
                            }
                            reader.close();
                        }
                    }

                    // commits to pull
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPull = git.log().add(remoteHead).not(localHead).call();
                    for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPull) {
                        if (commit.getParentCount() > 0) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            oldTree.reset(reader, commit.getParent(0).getTree());
                            newTree.reset(reader, commit.getTree());
                            List<org.eclipse.jgit.diff.DiffEntry> diffs = git.diff().setOldTree(oldTree).setNewTree(newTree).call();
                            for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                if (diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME || diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY) {
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                                    renamed.add(filePath);
                                }
                            }
                            reader.close();
                        }
                    }
                }
            } catch (Exception ignore) {}
        }

        // Fallback: if we couldn't determine remote commits-to-push/pull, inspect the most
        // recent local commit vs its parent for renames (covers the common case of a single
        // local commit that hasn't been pushed yet).
        if (renamed.isEmpty()) {
            try {
                org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
                if (head != null) {
                    org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
                    org.eclipse.jgit.revwalk.RevCommit headCommit = rw.parseCommit(head);
                    if (headCommit.getParentCount() > 0) {
                        org.eclipse.jgit.revwalk.RevCommit parent = rw.parseCommit(headCommit.getParent(0));
                        org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                        org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                        org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                        oldTree.reset(reader, parent.getTree());
                        newTree.reset(reader, headCommit.getTree());
                        List<org.eclipse.jgit.diff.DiffEntry> diffs = git.diff().setOldTree(oldTree).setNewTree(newTree).call();
                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                            if (diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME || diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY) {
                                String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                                renamed.add(filePath);
                            }
                        }
                        reader.close();
                    }
                    rw.close();
                }
            } catch (Exception ignore) {}
        }

        return renamed.size();
    }
}
