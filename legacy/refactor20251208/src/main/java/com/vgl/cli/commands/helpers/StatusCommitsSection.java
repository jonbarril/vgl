
package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.Map;

public class StatusCommitsSection {
    /**
     * Prints the COMMITS section for status output.
     * Handles commit, push, and pull counts and verbose subsections.
     *
     * @param git The JGit instance (may be null)
     * @param commitsLabelPad The padded label (e.g. "COMMITS:   ")
     * @param remoteUrl The remote URL (may be null)
     * @param remoteBranch The remote branch name (may be null)
     * @param localBranch The local branch name (may be null)
     * @param verbose true if -v
     * @param veryVerbose true if -vv
     * @param filesToCommit Map to populate with files to commit (for summary)
     * @param filesToPush Map to populate with files to push (for summary)
     * @param filesToPull Map to populate with files to pull (for summary)
     * @return int[] {commitCount, pushCount, pullCount}
     */
    public static int[] printCommitsSection(Git git, String commitsLabelPad, String remoteUrl, String remoteBranch, String localBranch, boolean verbose, boolean veryVerbose,
                                            Map<String, String> filesToCommit, Map<String, String> filesToPush, Map<String, String> filesToPull) throws Exception {
        int commitCount = 0, pushCount = 0, pullCount = 0;
        // Always print summary line, even if there are no commits yet (counts will be zero)
        if (git == null) {
            System.out.println(commitsLabelPad + "0 to Commit, 0 to Push, 0 to Pull");
            if (verbose || veryVerbose) {
                System.out.println("-- Files to Commit:");
                System.out.println("  (none)");
                System.out.println("-- Files to Push:");
                System.out.println("  (none)");
                System.out.println("-- Files to Pull:");
                System.out.println("  (none)");
            }
            return new int[]{0, 0, 0};
        }
        boolean unbornRepo = com.vgl.cli.utils.RepoUtils.hasCommits(git.getRepository()) == false;
        if (unbornRepo) {
            System.out.println(commitsLabelPad + "0 to Commit, 0 to Push, 0 to Pull");
            if (verbose || veryVerbose) {
                System.out.println("-- Files to Commit:");
                System.out.println("  (none)");
                System.out.println("-- Files to Push:");
                System.out.println("  (none)");
                System.out.println("-- Files to Pull:");
                System.out.println("  (none)");
            }
            return new int[]{0, 0, 0};
        }
        // Always use HEAD vs working tree for filesToCommit (Gitless-style)
        org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
        if (head != null) {
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
                    String statusLetter = switch (d.getChangeType()) {
                        case ADD -> "A";
                        case MODIFY -> "M";
                        case DELETE -> "D";
                        case RENAME, COPY -> "R";
                        default -> "M";
                    };
                    String filePath = d.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? d.getOldPath() : d.getNewPath();
                    filesToCommit.put(filePath, statusLetter);
                }
            }
            reader.close();
            rw.close();
        }
        ObjectId localHead = git.getRepository().resolve("HEAD");
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        String effectiveRemoteBranch = remoteBranch;
        if (!hasRemote) {
            try {
                org.eclipse.jgit.lib.StoredConfig jgitCfg = git.getRepository().getConfig();
                String gitRemoteUrl = jgitCfg.getString("remote", "origin", "url");
                if (gitRemoteUrl != null && !gitRemoteUrl.isEmpty()) {
                    hasRemote = true;
                    String currentBranch = git.getRepository().getBranch();
                    String trackedBranch = jgitCfg.getString("branch", currentBranch, "merge");
                    if (trackedBranch != null) effectiveRemoteBranch = trackedBranch.replaceFirst("refs/heads/", "");
                    else effectiveRemoteBranch = currentBranch;
                }
            } catch (Exception ignore) {}
        }
        ObjectId remoteHead = null;
        if (hasRemote) {
            remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
        }
        if (localHead != null) {
            if (remoteHead == null) {
                Iterable<RevCommit> commits = git.log().add(localHead).call();
                for (RevCommit commit : commits) {
                    org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                    org.eclipse.jgit.treewalk.AbstractTreeIterator oldTree;
                    org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                    if (commit.getParentCount() > 0) {
                        oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                        ((org.eclipse.jgit.treewalk.CanonicalTreeParser)oldTree).reset(reader, commit.getParent(0).getTree());
                    } else {
                        oldTree = new org.eclipse.jgit.treewalk.EmptyTreeIterator();
                    }
                    newTree.reset(reader, commit.getTree());
                    try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                        df.setRepository(git.getRepository());
                        df.setDetectRenames(true);
                        java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                            String newPath = diff.getNewPath();
                            String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                            String statusLetter = switch (diff.getChangeType()) {
                                case ADD -> "A";
                                case MODIFY -> "M";
                                case DELETE -> "D";
                                case RENAME, COPY -> "R";
                                default -> "M";
                            };
                            filesToPush.put(filePath, statusLetter);
                        }
                    }
                    reader.close();
                }
                for (String pushed : filesToPush.keySet()) {
                    filesToCommit.remove(pushed);
                }
            } else if (!localHead.equals(remoteHead)) {
                Iterable<RevCommit> commitsToPush = git.log().add(localHead).not(remoteHead).call();
                for (RevCommit commit : commitsToPush) {
                    if (commit.getParentCount() > 0) {
                        org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                        org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                        org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                        oldTree.reset(reader, commit.getParent(0).getTree());
                        newTree.reset(reader, commit.getTree());
                        try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                            df.setRepository(git.getRepository());
                            df.setDetectRenames(true);
                            java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                            for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                String newPath = diff.getNewPath();
                                String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                String statusLetter = switch (diff.getChangeType()) {
                                    case ADD -> "A";
                                    case MODIFY -> "M";
                                    case DELETE -> "D";
                                    case RENAME, COPY -> "R";
                                    default -> "M";
                                };
                                if (!filesToPush.containsKey(filePath)) {
                                    filesToPush.put(filePath, statusLetter);
                                }
                            }
                        }
                        reader.close();
                    }
                }
                for (String pushed : filesToPush.keySet()) {
                    filesToCommit.remove(pushed);
                }
            }
        }
        if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
            Iterable<RevCommit> commitsToPull = git.log().add(remoteHead).not(localHead).call();
            for (RevCommit commit : commitsToPull) {
                if (commit.getParentCount() > 0) {
                    org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                    org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                    org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                    oldTree.reset(reader, commit.getParent(0).getTree());
                    newTree.reset(reader, commit.getTree());
                    try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                        df.setRepository(git.getRepository());
                        df.setDetectRenames(true);
                        java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                            String newPath = diff.getNewPath();
                            String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                            String statusLetter = switch (diff.getChangeType()) {
                                case ADD -> "A";
                                case MODIFY -> "M";
                                case DELETE -> "D";
                                case RENAME, COPY -> "R";
                                default -> "M";
                            };
                            filesToPull.put(filePath, statusLetter);
                        }
                    }
                    reader.close();
                }
            }
        }
        commitCount = filesToCommit.size();
        pushCount = filesToPush.size();
        pullCount = filesToPull.size();
        // Print summary line with 'Pull' for test compatibility
        System.out.println(commitsLabelPad + commitCount + " to Commit, " + pushCount + " to Push, " + pullCount + " to Pull");

        // Print verbose subsections for both -v and -vv (always print all three, even if empty)
        if (verbose || veryVerbose) {
            System.out.println("-- Files to Commit:");
            if (filesToCommit.isEmpty()) System.out.println("  (none)");
            else filesToCommit.forEach((f, s) -> System.out.println("  " + s + " " + f));
            System.out.println("-- Files to Push:");
            if (filesToPush.isEmpty()) System.out.println("  (none)");
            else filesToPush.forEach((f, s) -> System.out.println("  " + s + " " + f));
            System.out.println("-- Files to Pull:");
            if (filesToPull.isEmpty()) System.out.println("  (none)");
            else filesToPull.forEach((f, s) -> System.out.println("  " + s + " " + f));
        }
        return new int[]{commitCount, pushCount, pullCount};
    }
}
