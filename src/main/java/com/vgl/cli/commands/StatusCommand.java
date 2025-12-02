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
        
        // Extract filter arguments (file patterns or commit IDs)
        List<String> filters = new java.util.ArrayList<>();
        for (String arg : args) {
            if (!arg.equals("-v") && !arg.equals("-vv")) {
                filters.add(arg);
            }
        }

        // Helper to truncate path with ellipsis if too long
        java.util.function.BiFunction<String, Integer, String> formatPath = (path, maxLen) -> {
            if (path.length() <= maxLen) return path;
            return "..." + path.substring(path.length() - maxLen + 3);
        };
        
        // Report LOCAL with current and jump state
        String jumpLocalDir = vgl.getJumpLocalDir();
        String jumpLocalBranch = vgl.getJumpLocalBranch();
        boolean hasJump = jumpLocalDir != null && !jumpLocalDir.isEmpty() && 
                          jumpLocalBranch != null && !jumpLocalBranch.isEmpty();
        
        if (hasLocalRepo) {
            // Format paths for display (truncate if too long)
            String currentDisplay = formatPath.apply(localDir, 50) + ":" + localBranch;
            System.out.println("LOCAL   " + currentDisplay);
            
            if (hasJump) {
                String jumpDisplay = formatPath.apply(jumpLocalDir, 50) + ":" + jumpLocalBranch;
                System.out.println("        " + jumpDisplay + " (jump)");
            }
            
            // Show all branches in -vv mode
            if (veryVerbose) {
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
        } else {
            System.out.println("LOCAL   (none)");
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
                
                // Show commits subsection for -v
                if (verbose || veryVerbose) {
                    System.out.println("-- Commits:");
                    try {
                        Iterable<RevCommit> commits = git.log().setMaxCount(5).call();
                        for (RevCommit commit : commits) {
                            String hash = commit.getId().abbreviate(7).name();
                            String message = commit.getShortMessage();
                            
                            // Check if filtering by commit ID
                            if (!filters.isEmpty()) {
                                boolean matchesFilter = false;
                                for (String filter : filters) {
                                    if (hash.startsWith(filter) || commit.getId().name().startsWith(filter)) {
                                        matchesFilter = true;
                                        break;
                                    }
                                }
                                if (!matchesFilter) continue;
                            }
                            
                            System.out.print("  " + hash);
                            if (verbose && !veryVerbose) {
                                // For -v, truncate message to fit ~70 chars total (hash is 7 + 2 spaces = 9)
                                int maxLength = 61;
                                if (message.length() > maxLength) {
                                    System.out.print(" " + message.substring(0, maxLength) + "...");
                                } else {
                                    System.out.print(" " + message);
                                }
                            } else if (veryVerbose) {
                                // For -vv, show full message
                                System.out.print(" " + commit.getFullMessage().trim());
                            }
                            System.out.println();
                        }
                    } catch (NoHeadException ex) {
                        System.out.println("  (none)");
                    }
                }
                
                // Count files by status
                int numModified = status.getChanged().size() + status.getModified().size();
                int numAdded = status.getAdded().size();
                int numDeleted = status.getRemoved().size() + status.getMissing().size();
                int numUntracked = status.getUntracked().size();
                
                // Build summary string
                List<String> fileSummary = new java.util.ArrayList<>();
                if (numModified > 0) fileSummary.add(numModified + "M");
                if (numAdded > 0) fileSummary.add(numAdded + "A");
                if (numDeleted > 0) fileSummary.add(numDeleted + "D");
                if (numUntracked > 0) fileSummary.add(numUntracked + "?");
                
                if (fileSummary.isEmpty()) {
                    System.out.println("FILES   (clean)");
                } else {
                    System.out.println("FILES   " + String.join(", ", fileSummary));
                }

                // For -v, show which files need push/pull under FILES section
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
                                            String newPath = diff.getNewPath();
                                            // Filter out /dev/null entries
                                            if (newPath != null && !newPath.equals("/dev/null")) {
                                                filesToPush.add(newPath);
                                            }
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
                                            String newPath = diff.getNewPath();
                                            // Filter out /dev/null entries
                                            if (newPath != null && !newPath.equals("/dev/null")) {
                                                filesToPull.add(newPath);
                                            }
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

                    // Show current working tree files
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
                                    String path = treeWalk.getPathString();
                                    // Apply filters if specified
                                    if (!filters.isEmpty() && !matchesAnyFilter(path, filters)) {
                                        continue;
                                    }
                                    trackedFiles.put(path, " "); // Clean files have no status marker
                                }
                                treeWalk.close();
                            }
                        } catch (Exception e) {
                            // If there's an error reading the tree, fall through to modified-only
                        }
                    }
                    
                    // Priority order: Added > Removed/Missing > Modified/Changed
                    // These will overwrite clean file entries with status codes
                    status.getModified().forEach(p -> {
                        if (filters.isEmpty() || matchesAnyFilter(p, filters)) {
                            trackedFiles.put(p, "M");
                        }
                    });
                    status.getChanged().forEach(p -> {
                        if (filters.isEmpty() || matchesAnyFilter(p, filters)) {
                            trackedFiles.put(p, "M");
                        }
                    });
                    status.getRemoved().forEach(p -> {
                        if (filters.isEmpty() || matchesAnyFilter(p, filters)) {
                            trackedFiles.put(p, "D");
                        }
                    });
                    status.getMissing().forEach(p -> {
                        if (filters.isEmpty() || matchesAnyFilter(p, filters)) {
                            trackedFiles.put(p, "D");
                        }
                    });
                    status.getAdded().forEach(p -> {
                        if (filters.isEmpty() || matchesAnyFilter(p, filters)) {
                            trackedFiles.put(p, "A");
                        }
                    });
                    
                    if (trackedFiles.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        trackedFiles.forEach((file, statusCode) -> 
                            System.out.println("  " + statusCode + " " + file));
                    }
                    
                    System.out.println("-- Untracked Files:");
                    // Exclude files that are tracked (including removed ones)
                    Set<String> untrackedOnly = new LinkedHashSet<>(status.getUntracked());
                    // Remove files that are marked as removed/missing from tracked - these shouldn't appear as untracked
                    status.getRemoved().forEach(untrackedOnly::remove);
                    status.getMissing().forEach(untrackedOnly::remove);
                    
                    // Apply filters if specified
                    if (!filters.isEmpty()) {
                        untrackedOnly.removeIf(p -> !matchesAnyFilter(p, filters));
                    }
                    
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
    
    private boolean matchesAnyFilter(String path, List<String> filters) {
        for (String filter : filters) {
            // Support glob patterns
            if (filter.contains("*") || filter.contains("?")) {
                String regex = filter.replace(".", "\\.")
                                    .replace("*", ".*")
                                    .replace("?", ".");
                if (path.matches(regex)) {
                    return true;
                }
            } else {
                // Exact match or prefix match
                if (path.equals(filter) || path.startsWith(filter + "/") || path.contains("/" + filter)) {
                    return true;
                }
            }
        }
        return false;
    }
}
