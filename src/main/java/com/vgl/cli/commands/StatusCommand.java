package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.api.errors.NoHeadException;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatusCommand implements Command {
    @Override public String name() { return "status"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        boolean hasLocalRepo = vgl.isConfigurable();
        String localDir = hasLocalRepo ? Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize().toString() : "(none)";
        String localBranch = hasLocalRepo ? vgl.getLocalBranch() : "main";
        String remoteUrl = hasLocalRepo ? (vgl.getRemoteUrl() != null ? vgl.getRemoteUrl() : "none") : "none";
        String remoteBranch = hasLocalRepo ? vgl.getRemoteBranch() : "main";

        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        // Report LOCAL
        System.out.println("LOCAL   " + (hasLocalRepo ? localDir + ":" + localBranch : "(none)"));

        // Report REMOTE
        System.out.println("REMOTE  " + (!remoteUrl.equals("none") ? remoteUrl + ":" + remoteBranch : "(none)"));

        // Report STATE and FILES
        if (hasLocalRepo) {
            try (Git git = Git.open(Paths.get(localDir).toFile())) {
                // Get working tree status
                Status status = git.status().call();
                int modified = status.getChanged().size() + status.getModified().size() + status.getAdded().size() +
                               status.getRemoved().size() + status.getMissing().size();
                int untracked = status.getUntracked().size();
                
                // Check sync state with remote
                System.out.print("STATE   ");
                boolean hasRemote = !remoteUrl.equals("none");
                
                if (!hasRemote) {
                    System.out.print("(local only)");
                } else {
                    try {
                        // Try to get remote tracking info
                        org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                        org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + remoteBranch);
                        
                        if (localHead == null) {
                            System.out.print("(no commits yet)");
                        } else if (remoteHead == null) {
                            System.out.print("(remote branch not found)");
                        } else if (localHead.equals(remoteHead)) {
                            System.out.print("in sync");
                        } else {
                            // Calculate differences
                            BranchTrackingStatus bts = BranchTrackingStatus.of(git.getRepository(), localBranch);
                            if (bts != null) {
                                int ahead = bts.getAheadCount();
                                int behind = bts.getBehindCount();
                                
                                if (ahead > 0 && behind == 0) {
                                    System.out.print("needs push");
                                } else if (ahead == 0 && behind > 0) {
                                    System.out.print("needs pull");
                                } else if (ahead > 0 && behind > 0) {
                                    System.out.print("diverged");
                                }
                                
                                // Show commit details for -v
                                if (verbose || veryVerbose) {
                                    System.out.print(" (ahead " + ahead + ", behind " + behind + ")");
                                }
                            } else {
                                System.out.print("out of sync");
                            }
                        }
                    } catch (Exception e) {
                        System.out.print("(unknown)");
                    }
                }
                System.out.println();
                System.out.printf("FILES   %d modified(tracked), %d untracked%n", modified, untracked);

                // For -v, show which files need push/pull
                if (verbose || veryVerbose) {
                    if (hasRemote) {
                        try {
                            org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                            org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + remoteBranch);
                            
                            if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                                // Get commits to push
                                Iterable<RevCommit> commitsToPush = git.log()
                                    .add(localHead)
                                    .not(remoteHead)
                                    .call();
                                
                                Set<String> filesToPush = new LinkedHashSet<>();
                                for (RevCommit commit : commitsToPush) {
                                    // Get files changed in this commit
                                    if (commit.getParentCount() > 0) {
                                        org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                                        org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                        org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                        oldTree.reset(reader, commit.getParent(0).getTree());
                                        newTree.reset(reader, commit.getTree());
                                        
                                        List<org.eclipse.jgit.diff.DiffEntry> diffs = git.diff()
                                            .setOldTree(oldTree)
                                            .setNewTree(newTree)
                                            .call();
                                        
                                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                            filesToPush.add(diff.getNewPath());
                                        }
                                        reader.close();
                                    }
                                }
                                
                                if (!filesToPush.isEmpty()) {
                                    System.out.println("-- Files to Push:");
                                    filesToPush.forEach(f -> System.out.println("  " + f));
                                }
                                
                                // Get commits to pull
                                Iterable<RevCommit> commitsToPull = git.log()
                                    .add(remoteHead)
                                    .not(localHead)
                                    .call();
                                
                                Set<String> filesToPull = new LinkedHashSet<>();
                                for (RevCommit commit : commitsToPull) {
                                    if (commit.getParentCount() > 0) {
                                        org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                                        org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                        org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                        oldTree.reset(reader, commit.getParent(0).getTree());
                                        newTree.reset(reader, commit.getTree());
                                        
                                        List<org.eclipse.jgit.diff.DiffEntry> diffs = git.diff()
                                            .setOldTree(oldTree)
                                            .setNewTree(newTree)
                                            .call();
                                        
                                        for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                            filesToPull.add(diff.getNewPath());
                                        }
                                        reader.close();
                                    }
                                }
                                
                                if (!filesToPull.isEmpty()) {
                                    System.out.println("-- Files to Pull:");
                                    filesToPull.forEach(f -> System.out.println("  " + f));
                                }
                            }
                        } catch (Exception e) {
                            // Ignore errors in detailed sync reporting
                        }
                    }
                }

                // Show commits subsection for -v
                if (verbose || veryVerbose) {
                    System.out.println("-- Commits:");
                    try {
                        Iterable<RevCommit> commits = git.log().setMaxCount(5).call();
                        for (RevCommit commit : commits) {
                            System.out.print("  " + commit.getId().abbreviate(7).name());
                            if (veryVerbose) {
                                System.out.print(" " + commit.getShortMessage());
                            }
                            System.out.println();
                        }
                    } catch (NoHeadException ex) {
                        System.out.println("  (none)");
                    }
                }

                if (veryVerbose) {
                    System.out.println("-- Local Branches:");
                    List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                    if (branches.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        for (org.eclipse.jgit.lib.Ref ref : branches) {
                            String branchName = ref.getName().replaceFirst("refs/heads/", "");
                            if (branchName.equals(localBranch)) {
                                System.out.println("  * " + branchName);
                            } else {
                                System.out.println("    " + branchName);
                            }
                        }
                    }
                    
                    if (!remoteUrl.equals("none")) {
                        System.out.println("-- Remote Branches:");
                        List<org.eclipse.jgit.lib.Ref> remoteBranches = git.branchList()
                            .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                        if (remoteBranches.isEmpty()) {
                            System.out.println("  (none)");
                        } else {
                            for (org.eclipse.jgit.lib.Ref ref : remoteBranches) {
                                String branchName = ref.getName().replaceFirst("refs/remotes/origin/", "");
                                if (branchName.equals(remoteBranch)) {
                                    System.out.println("  * " + branchName);
                                } else {
                                    System.out.println("    " + branchName);
                                }
                            }
                        }
                    }
                }

                if (verbose || veryVerbose) {
                    System.out.println("-- Tracked Files:");
                    // Use a map to track each file once with its most relevant status
                    Map<String, String> trackedFiles = new LinkedHashMap<>();
                    // Priority order: Added > Removed/Missing > Modified/Changed
                    status.getModified().forEach(p -> trackedFiles.put(p, "M"));
                    status.getChanged().forEach(p -> trackedFiles.put(p, "M"));
                    status.getRemoved().forEach(p -> trackedFiles.put(p, "D"));
                    status.getMissing().forEach(p -> trackedFiles.put(p, "D"));
                    status.getAdded().forEach(p -> trackedFiles.put(p, "A"));
                    if (trackedFiles.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        trackedFiles.forEach((file, statusCode) -> 
                            System.out.println("  " + statusCode + " " + file));
                    }
                    
                    System.out.println("-- Untracked Files:");
                    if (status.getUntracked().isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        status.getUntracked().forEach(p -> System.out.println("  ? " + p));
                    }
                }
                
                if (veryVerbose) {
                    System.out.println("-- Ignored Files:");
                    if (status.getIgnoredNotInIndex().isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        status.getIgnoredNotInIndex().forEach(p -> System.out.println("  ! " + p));
                    }
                }
            }
        } else {
            System.out.println("STATE   (no local repository)");
            System.out.println("FILES   (no local repository)");
        }

        return 0;
    }
}
