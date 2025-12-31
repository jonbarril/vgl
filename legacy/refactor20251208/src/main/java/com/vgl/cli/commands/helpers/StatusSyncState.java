package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Git;
// import org.eclipse.jgit.lib.BranchTrackingStatus;

public class StatusSyncState {
    public static void printSyncState(Git git, String remoteUrl, String remoteBranch, String localBranch, boolean verbose, boolean veryVerbose) {
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        String effectiveRemoteBranch = remoteBranch;
        System.out.print("COMMITS  ");
        if (!hasRemote) {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                if (localHead == null) {
                    System.out.println("(no commits yet)");
                } else {
                    // If user requested verbose output, show local commits
                    if (verbose || veryVerbose) {
                        try {
                            System.out.println("  -- Local Commits:");
                            Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().call();
                            for (org.eclipse.jgit.revwalk.RevCommit commit : logs) {
                                String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                String fullMsg = commit.getFullMessage();
                                if (!veryVerbose) {
                                    String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                    System.out.println("  " + shortId + " " + oneLine);
                                } else {
                                    String[] lines = fullMsg.split("\\r?\\n", -1);
                                    if (lines.length > 0) {
                                        System.out.println("  " + shortId + " " + lines[0]);
                                        for (int i = 1; i < lines.length; i++) System.out.println("  " + lines[i]);
                                    } else {
                                        System.out.println("  " + shortId + " " + fullMsg);
                                    }
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception e) {
                System.out.print("(error reading local state)");
            }
        } else {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                if (localHead == null) {
                    System.out.println("(no commits yet)");
                } else if (remoteHead == null) {
                    System.out.println("(remote branch not found)");
                    if (verbose || veryVerbose) {
                        try {
                            System.out.println("  -- Local Commits:");
                            Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().call();
                            for (org.eclipse.jgit.revwalk.RevCommit commit : logs) {
                                String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                String fullMsg = commit.getFullMessage();
                                if (!veryVerbose) {
                                    String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                    System.out.println("  " + shortId + " " + oneLine);
                                } else {
                                    String[] lines = fullMsg.split("\\r?\\n", -1);
                                    if (lines.length > 0) {
                                        System.out.println("  " + shortId + " " + lines[0]);
                                        for (int i = 1; i < lines.length; i++) System.out.println("  " + lines[i]);
                                    } else {
                                        System.out.println("  " + shortId + " " + fullMsg);
                                    }
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                } else if (localHead.equals(remoteHead)) {
                    System.out.println("in sync");
                } else {
                    org.eclipse.jgit.lib.BranchTrackingStatus bts = org.eclipse.jgit.lib.BranchTrackingStatus.of(git.getRepository(), localBranch);
                    if (bts != null) {
                        int ahead = bts.getAheadCount();
                        int behind = bts.getBehindCount();
                        if (ahead == 0 && behind == 0) {
                            System.out.println("in sync");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            if (ahead > 0) sb.append(ahead).append(" commits to push");
                            if (ahead > 0 && behind > 0) sb.append(", ");
                            if (behind > 0) sb.append(behind).append(" commits to pull");
                            System.out.println(sb.toString());
                        }
                        if (verbose || veryVerbose) {
                            try {
                                Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPush = git.log().add(localHead).not(remoteHead).call();
                                System.out.println("  -- To Push:");
                                for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPush) {
                                    String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                    String fullMsg = commit.getFullMessage();
                                    if (!veryVerbose) {
                                        String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                        System.out.println("  " + shortId + " " + oneLine);
                                    } else {
                                        String[] lines = fullMsg.split("\\r?\\n", -1);
                                        if (lines.length > 0) {
                                            System.out.println("  " + shortId + " " + lines[0]);
                                            for (int i = 1; i < lines.length; i++) System.out.println("  " + lines[i]);
                                        } else {
                                            System.out.println("  " + shortId + " " + fullMsg);
                                        }
                                    }
                                }
                            } catch (Exception ignore) {}
                            try {
                                Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPull = git.log().add(remoteHead).not(localHead).call();
                                System.out.println("  -- To Pull:");
                                for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPull) {
                                    String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                    String fullMsg = commit.getFullMessage();
                                    if (!veryVerbose) {
                                        String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                        System.out.println("  " + shortId + " " + oneLine);
                                    } else {
                                        String[] lines = fullMsg.split("\\r?\\n", -1);
                                        if (lines.length > 0) {
                                            System.out.println("  " + shortId + " " + lines[0]);
                                            for (int i = 1; i < lines.length; i++) System.out.println("  " + lines[i]);
                                        } else {
                                            System.out.println("  " + shortId + " " + fullMsg);
                                        }
                                    }
                                }
                            } catch (Exception ignore) {}
                        }
                    } else {
                        System.out.print("out of sync");
                    }
                }
            } catch (Exception e) {
                System.out.print("(error reading remote state)");
            }
        }
    }
}
