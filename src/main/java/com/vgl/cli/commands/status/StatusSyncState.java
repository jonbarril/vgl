package com.vgl.cli.commands.status;

import org.eclipse.jgit.api.Git;
// import org.eclipse.jgit.lib.BranchTrackingStatus;

public class StatusSyncState {
    public static void printSyncState(Git git, String remoteUrl, String remoteBranch, String localBranch, boolean verbose, boolean veryVerbose) {
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        // String effectiveRemoteBranch = remoteBranch;
        System.out.print("COMMITS  ");
        if (!hasRemote) {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                if (localHead == null) {
                    System.out.print("(no commits yet)");
                } else {
                    int count = 0;
                    try {
                        Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().call();
                        java.util.Iterator<org.eclipse.jgit.revwalk.RevCommit> it = logs.iterator();
                        while (it.hasNext()) { it.next(); count++; }
                    } catch (Exception ignore) {}
                    if (!verbose && !veryVerbose) {
                        System.out.println(count + " local commits");
                    } else if (verbose && !veryVerbose) {
                        // single-line summary: show latest local commit one-line if present
                        try {
                            Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().setMaxCount(1).call();
                            java.util.Iterator<org.eclipse.jgit.revwalk.RevCommit> it = logs.iterator();
                            if (it.hasNext()) {
                                org.eclipse.jgit.revwalk.RevCommit commit = it.next();
                                String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                String fullMsg = commit.getFullMessage();
                                String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                System.out.println(count + " local commits");
                                System.out.println("  " + shortId + " " + oneLine);
                            } else {
                                System.out.println(count + " local commits");
                            }
                        } catch (Exception ignore) { System.out.println(); }
                    } else if (veryVerbose) {
                        System.out.println(count + " local commits");
                        System.out.println("  -- Local Commits:");
                        try {
                            Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().call();
                            for (org.eclipse.jgit.revwalk.RevCommit commit : logs) {
                                String shortId = (commit.getName() != null && commit.getName().length() >= 7) ? commit.getName().substring(0, 7) : commit.getName();
                                String fullMsg = commit.getFullMessage();
                                String[] lines = fullMsg.split("\\r?\\n", -1);
                                if (lines.length > 0) {
                                    System.out.println("  " + shortId + " " + lines[0]);
                                    for (int i = 1; i < lines.length; i++) System.out.println("  " + lines[i]);
                                } else {
                                    System.out.println("  " + shortId + " " + fullMsg);
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception e) {
                // ...truncated for brevity...
            }
        }
    }
}
