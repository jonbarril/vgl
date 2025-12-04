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

        // Get jump state values
        
        // Print switch state (LOCAL and REMOTE, current and jump) using consolidated method
        com.vgl.cli.Utils.printSwitchState(vgl, verbose || veryVerbose);
        
        // Show all LOCAL branches in -vv mode
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
        
        // Show all REMOTE branches in -vv mode
        if (veryVerbose && hasLocalRepo && remoteUrl != null && !remoteUrl.isEmpty()) {
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
                Status status;
                try {
                    status = git.status().call();
                } catch (Exception e) {
                    // Handle unborn branch or invalid repository state
                    System.err.println("Warning: Cannot read repository status.");
                    System.err.println("Repository may be in an invalid state or branch may be unborn.");
                    return 1;
                }
                
                // Check sync state with remote
                System.out.print("STATE  ");
                
                // Check if remote exists in VGL config OR in Git config
                boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();
                String effectiveRemoteBranch = remoteBranch;
                
                // If VGL doesn't have remote configured, check Git's config
                if (!hasRemote) {
                    try {
                        org.eclipse.jgit.lib.StoredConfig config = git.getRepository().getConfig();
                        String gitRemoteUrl = config.getString("remote", "origin", "url");
                        if (gitRemoteUrl != null && !gitRemoteUrl.isEmpty()) {
                            hasRemote = true;
                            // Try to get the tracked branch name
                            String currentBranch = git.getRepository().getBranch();
                            String trackedBranch = config.getString("branch", currentBranch, "merge");
                            if (trackedBranch != null) {
                                effectiveRemoteBranch = trackedBranch.replaceFirst("refs/heads/", "");
                            } else {
                                effectiveRemoteBranch = currentBranch;  // Assume same name
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors checking Git config
                    }
                }
                
                if (!hasRemote) {
                    System.out.print("(local only)");
                } else {
                    try {
                        // Try to get remote tracking info
                        org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                        org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                        
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
                
                // Apply filters if specified
                if (!filters.isEmpty()) {
                    numModified = (int) status.getChanged().stream().filter(p -> matchesAnyFilter(p, filters)).count() +
                                  (int) status.getModified().stream().filter(p -> matchesAnyFilter(p, filters)).count();
                    numAdded = (int) status.getAdded().stream().filter(p -> matchesAnyFilter(p, filters)).count();
                    numDeleted = (int) status.getRemoved().stream().filter(p -> matchesAnyFilter(p, filters)).count() +
                                 (int) status.getMissing().stream().filter(p -> matchesAnyFilter(p, filters)).count();
                    numUntracked = (int) status.getUntracked().stream().filter(p -> matchesAnyFilter(p, filters)).count();
                }
                
                // Build summary string with full category names
                List<String> fileSummary = new java.util.ArrayList<>();
                fileSummary.add(numModified + " Modified");
                fileSummary.add(numAdded + " Added");
                fileSummary.add(numDeleted + " Deleted");
                fileSummary.add(numUntracked + " Untracked");
                
                System.out.println("FILES  " + String.join(", ", fileSummary));

                // For -v, show which files need commit and merge
                if (verbose || veryVerbose) {
                    // Files to Commit section - ALWAYS show (working dir changes + unpushed commits)
                    System.out.println("-- Files to Commit:");
                    
                    // Start with working directory changes (modified, added, deleted)
                    Map<String, String> filesToCommit = new LinkedHashMap<>();
                    
                    // Add modified files
                    for (String path : status.getModified()) {
                        filesToCommit.put(path, "M");
                    }
                    for (String path : status.getChanged()) {
                        filesToCommit.put(path, "M");
                    }
                    
                    // Add new files
                    for (String path : status.getAdded()) {
                        filesToCommit.put(path, "A");
                    }
                    
                    // Add deleted files
                    for (String path : status.getRemoved()) {
                        filesToCommit.put(path, "D");
                    }
                    for (String path : status.getMissing()) {
                        filesToCommit.put(path, "D");
                    }
                    
                    // Add files from commits that need push (if remote exists)
                    if (hasRemote) {
                        try {
                            org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                            org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                            
                            if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                                // Get commits to push
                                Iterable<RevCommit> commitsToPush = git.log()
                                    .add(localHead)
                                    .not(remoteHead)
                                    .call();
                                
                                for (RevCommit commit : commitsToPush) {
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
                                            if (newPath != null && !newPath.equals("/dev/null")) {
                                                String statusLetter = switch (diff.getChangeType()) {
                                                    case ADD -> "A";
                                                    case MODIFY -> "M";
                                                    case DELETE -> "D";
                                                    case RENAME -> "R";
                                                    case COPY -> "C";
                                                    default -> "M";
                                                };
                                                String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE 
                                                    ? diff.getOldPath() : newPath;
                                                // Only add if not already in working directory changes
                                                if (!filesToCommit.containsKey(filePath)) {
                                                    filesToCommit.put(filePath, statusLetter);
                                                }
                                            }
                                        }
                                        reader.close();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore errors in detailed sync reporting
                        }
                    }
                    
                    // Display files to commit with proper prefixes
                    if (filesToCommit.isEmpty()) {
                        if (!hasRemote) {
                            System.out.println("  (no remote configured)");
                        } else {
                            System.out.println("  (none)");
                        }
                    } else {
                        // Track if any files match the filter
                        boolean anyPrinted = false;
                        
                        // First show uncommitted working directory changes (no arrow)
                        for (Map.Entry<String, String> entry : filesToCommit.entrySet()) {
                            String path = entry.getKey();
                            String statusLetter = entry.getValue();
                            
                            // Apply filters if specified
                            if (!filters.isEmpty() && !matchesAnyFilter(path, filters)) {
                                continue;
                            }
                            
                            anyPrinted = true;
                            
                            // Check if this file is in working directory changes
                            boolean isUncommitted = status.getModified().contains(path) || 
                                                   status.getChanged().contains(path) ||
                                                   status.getAdded().contains(path) ||
                                                   status.getRemoved().contains(path) ||
                                                   status.getMissing().contains(path);
                            
                            if (isUncommitted) {
                                System.out.println("  " + statusLetter + " " + path);
                            } else {
                                // Committed but not pushed - use up arrow
                                System.out.println("↑ " + statusLetter + " " + path);
                            }
                        }
                        
                        // If filters were applied but nothing matched, show (none)
                        if (!anyPrinted) {
                            System.out.println("  (none)");
                        }
                    }
                    
                    // Files to Merge section - ALWAYS show
                    System.out.println("-- Files to Merge:");
                    
                    if (!hasRemote) {
                        System.out.println("  (no remote configured)");
                    } else {
                        try {
                            org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                            org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                            
                            if (localHead == null || remoteHead == null) {
                                System.out.println("  (remote branch not found)");
                            } else if (localHead.equals(remoteHead)) {
                                System.out.println("  (none)");
                            } else {
                                // Get commits to pull
                                Iterable<RevCommit> commitsToPull = git.log()
                                    .add(remoteHead)
                                    .not(localHead)
                                    .call();
                                
                                Map<String, String> filesToPull = new LinkedHashMap<>();
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
                                            if (newPath != null && !newPath.equals("/dev/null")) {
                                                String statusLetter = switch (diff.getChangeType()) {
                                                    case ADD -> "A";
                                                    case MODIFY -> "M";
                                                    case DELETE -> "D";
                                                    case RENAME -> "R";
                                                    case COPY -> "C";
                                                    default -> "M";
                                                };
                                                String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE 
                                                    ? diff.getOldPath() : newPath;
                                                filesToPull.put(filePath, statusLetter);
                                            }
                                        }
                                        reader.close();
                                    }
                                }
                                
                                if (filesToPull.isEmpty()) {
                                    System.out.println("  (none)");
                                } else {
                                    // Use down arrow for files to merge (pull from remote)
                                    filesToPull.forEach((path, statusLetter) -> 
                                        System.out.println("↓ " + statusLetter + " " + path));
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("  (error checking remote)");
                        }
                    }

                    // Show current working tree files - only in very verbose mode
                    if (veryVerbose) {
                        System.out.println("-- Tracked Files:");
                        
                        try {
                            org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository());
                            org.eclipse.jgit.lib.ObjectId headId = git.getRepository().resolve("HEAD^{tree}");
                            if (headId != null) {
                                treeWalk.addTree(headId);
                                treeWalk.setRecursive(true);
                                boolean hasTracked = false;
                                while (treeWalk.next()) {
                                    String path = treeWalk.getPathString();
                                    // Apply filters if specified
                                    if (!filters.isEmpty() && !matchesAnyFilter(path, filters)) {
                                        continue;
                                    }
                                    // Just show filename, no status prefix
                                    System.out.println("  " + path);
                                    hasTracked = true;
                                }
                                treeWalk.close();
                                if (!hasTracked) {
                                    System.out.println("  (none)");
                                }
                            } else {
                                System.out.println("  (none)");
                            }
                        } catch (Exception e) {
                            System.out.println("  (error reading tracked files)");
                        }
                    }
                    
                    // Show untracked files - only in very verbose mode
                    if (veryVerbose) {
                        System.out.println("-- Untracked Files:");
                        Set<String> untrackedFiles = status.getUntracked();
                        
                        // Apply filters if specified
                        if (!filters.isEmpty()) {
                            untrackedFiles = untrackedFiles.stream()
                                .filter(p -> matchesAnyFilter(p, filters))
                                .collect(java.util.stream.Collectors.toSet());
                        }
                        
                        if (untrackedFiles.isEmpty()) {
                            System.out.println("  (none)");
                        } else {
                            untrackedFiles.forEach(p -> System.out.println("  " + p));
                        }
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
                            ignoredFiles.forEach(p -> System.out.println("  " + p));
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
