package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BranchTrackingStatus;

public class StatusSyncState {
    public static void printSyncState(Git git, String remoteUrl, String remoteBranch, String localBranch) {
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
        String effectiveRemoteBranch = remoteBranch;
        System.out.print("STATE  ");
        if (!hasRemote) {
            System.out.print("(local only)");
        } else {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                if (localHead == null) {
                    System.out.print("(no commits yet)");
                } else if (remoteHead == null) {
                    System.out.print("(remote branch not found)");
                } else if (localHead.equals(remoteHead)) {
                    System.out.print("in sync");
                } else {
                    BranchTrackingStatus bts = BranchTrackingStatus.of(git.getRepository(), localBranch);
                    if (bts != null) {
                        int ahead = bts.getAheadCount();
                        int behind = bts.getBehindCount();
                        if (ahead > 0 && behind == 0) {
                            System.out.print("ahead " + ahead);
                        } else if (ahead == 0 && behind > 0) {
                            System.out.print("behind " + behind);
                        } else if (ahead > 0 && behind > 0) {
                            System.out.print("diverged (ahead " + ahead + ", behind " + behind + ")");
                        } else {
                            System.out.print("out of sync");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.print("(error reading remote state)");
            }
        }
        System.out.println();
    }
}
