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
        if (veryVerbose && hasLocalRepo) {
            try (Git git = Git.open(Paths.get(localDir).toFile())) {
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                if (!branches.isEmpty()) {
                    for (org.eclipse.jgit.lib.Ref ref : branches) {
                        String branchName = ref.getName().replaceFirst("refs/heads/", "");
                        if (branchName.equals(localBranch)) {
                            System.out.println("  * " + branchName);
                        } else {
                            System.out.println("    " + branchName);
                        }
                    }
                }
            }
        }

        // Report REMOTE
        System.out.println("REMOTE  " + (!remoteUrl.equals("none") ? remoteUrl + ":" + remoteBranch : "(none)"));
        if (veryVerbose && hasLocalRepo && !remoteUrl.equals("none")) {
            try (Git git = Git.open(Paths.get(localDir).toFile())) {
                List<org.eclipse.jgit.lib.Ref> remoteBranches = git.branchList()
                    .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                if (!remoteBranches.isEmpty()) {
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
                
                // Show commits subsection for -v under STATE
                if (verbose || veryVerbose) {
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

                if (verbose || veryVerbose) {
                    System.out.println("-- Tracked Files:");
                    // Use a map to track each file once with its most relevant status
                    Map<String, String> trackedFiles = new LinkedHashMap<>();
                    
                    if (veryVerbose) {
                        // For -vv, show ALL tracked files (including clean ones)
                        try {
                            org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository());
                            org.eclipse.jgit.lib.ObjectId headId = git.getRepository().resolve("HEAD^{tree}");
                            if (headId != null) {
                                treeWalk.addTree(headId);
                                treeWalk.setRecursive(true);
                                while (treeWalk.next()) {
                                    trackedFiles.put(treeWalk.getPathString(), " "); // Clean files have no status marker
                                }
                                treeWalk.close();
                            }
                        } catch (Exception e) {
                            // If there's an error reading the tree, fall through to modified-only
                        }
                    }
                    
                    // Priority order: Added > Removed/Missing > Modified/Changed
                    // These will overwrite clean file entries with status codes
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
                    // Exclude files that are tracked or ignored
                    Set<String> untrackedOnly = new LinkedHashSet<>(status.getUntracked());
                    if (untrackedOnly.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        untrackedOnly.forEach(p -> System.out.println("  ? " + p));
                    }
                }
                
                if (veryVerbose) {
                    System.out.println("-- Ignored Files:");
                    // Manually scan for ignored files using TreeWalk and ignore rules
                    // Exclude files that appear in tracked or untracked sections
                    Set<String> trackedOrUntracked = new LinkedHashSet<>();
                    if (verbose || veryVerbose) {
                        // Collect all tracked files
                        try {
                            org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository());
                            org.eclipse.jgit.lib.ObjectId headId = git.getRepository().resolve("HEAD^{tree}");
                            if (headId != null) {
                                treeWalk.addTree(headId);
                                treeWalk.setRecursive(true);
                                while (treeWalk.next()) {
                                    trackedOrUntracked.add(treeWalk.getPathString());
                                }
                                treeWalk.close();
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                        status.getModified().forEach(trackedOrUntracked::add);
                        status.getChanged().forEach(trackedOrUntracked::add);
                        status.getAdded().forEach(trackedOrUntracked::add);
                        // Don't add removed/missing - those should show as ignored if they match patterns
                    }
                    trackedOrUntracked.addAll(status.getUntracked());
                    
                    try {
                        Set<String> ignoredFiles = new LinkedHashSet<>();
                        org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = 
                            new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository());
                        org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository());
                        treeWalk.addTree(workingTreeIt);
                        treeWalk.setRecursive(true);
                        
                        while (treeWalk.next()) {
                            org.eclipse.jgit.treewalk.WorkingTreeIterator workingTreeIterator = 
                                (org.eclipse.jgit.treewalk.WorkingTreeIterator) treeWalk.getTree(0, org.eclipse.jgit.treewalk.WorkingTreeIterator.class);
                            String path = treeWalk.getPathString();
                            if (workingTreeIterator != null && workingTreeIterator.isEntryIgnored()) {
                                // Only show if not already in tracked or untracked sections
                                if (!trackedOrUntracked.contains(path)) {
                                    ignoredFiles.add(path);
                                }
                            }
                        }
                        treeWalk.close();
                        
                        if (ignoredFiles.isEmpty()) {
                            System.out.println("  (none)");
                        } else {
                            ignoredFiles.forEach(p -> System.out.println("  ! " + p));
                        }
                    } catch (Exception e) {
                        System.out.println("  (error detecting ignored files)");
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
