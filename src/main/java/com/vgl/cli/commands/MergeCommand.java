package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * MergeCommand merges another branch into the current branch.
 * Simplifies Git's merge with clearer novice-friendly messaging.
 */
public class MergeCommand implements Command {
    @Override
    public String name() {
        return "merge";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse flags
        String sourceRepo = Args.getFlag(args, "-lr");
        String sourceBranch = Args.getFlag(args, "-lb");
        String remoteBranch = Args.getFlag(args, "-rb");
        boolean deleteBranch = Args.hasFlag(args, "-del");
        
        // Check for TBD features
        if (sourceRepo != null) {
            System.out.println("Warning: merge -lr REPO is not yet implemented.");
            System.out.println("Currently merge only works with branches, not repositories.");
            return 1;
        }
        
        if (deleteBranch) {
            System.out.println("Warning: merge -del is not yet implemented.");
            System.out.println("Use 'vgl delete -lb BRANCH' after merge to delete the branch.");
            return 1;
        }
        
        if (sourceBranch == null && remoteBranch == null) {
            System.out.println("Usage: vgl merge -lb BRANCH | -rb BRANCH");
            System.out.println("Examples:");
            System.out.println("  vgl merge -lb feature      Merge local 'feature' branch into current");
            System.out.println("  vgl merge -rb main         Merge remote 'main' branch into current");
            return 1;
        }
        
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Error: No Git repository found at: " + dir);
            return 1;
        }
        
        try (Git git = Git.open(dir.toFile())) {
            String currentBranch = git.getRepository().getBranch();
            
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty();
            
            if (hasChanges) {
                System.out.println("Error: You have uncommitted changes.");
                System.out.println("Commit them before merging:");
                status.getModified().forEach(f -> System.out.println("  M " + f));
                status.getChanged().forEach(f -> System.out.println("  M " + f));
                status.getAdded().forEach(f -> System.out.println("  A " + f));
                status.getRemoved().forEach(f -> System.out.println("  D " + f));
                System.out.println("\nUse 'vgl commit \"message\"' to save your changes first.");
                return 1;
            }
            
            String branchToMerge;
            boolean isRemote = false;
            
            if (sourceBranch != null) {
                // Verify local branch exists
                List<Ref> branches = git.branchList().call();
                boolean branchExists = branches.stream()
                    .anyMatch(ref -> ref.getName().equals("refs/heads/" + sourceBranch));
                
                if (!branchExists) {
                    System.out.println("Error: Branch '" + sourceBranch + "' does not exist.");
                    System.out.println("Available branches:");
                    branches.forEach(ref -> System.out.println("  " + ref.getName().replace("refs/heads/", "")));
                    return 1;
                }
                
                if (sourceBranch.equals(currentBranch)) {
                    System.out.println("Error: Cannot merge branch '" + sourceBranch + "' into itself.");
                    return 1;
                }
                
                branchToMerge = sourceBranch;
            } else {
                // Merge from remote branch
                String remoteUrl = vgl.getRemoteUrl();
                if (remoteUrl == null || remoteUrl.isBlank()) {
                    System.out.println("Error: No remote configured.");
                    System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                    return 1;
                }
                
                // Fetch first
                System.out.println("Fetching from remote...");
                git.fetch().setRemote("origin").call();
                
                branchToMerge = "origin/" + remoteBranch;
                isRemote = true;
            }
            
            // Perform merge
            System.out.println("Merging " + (isRemote ? "remote" : "local") + " branch '" + 
                (isRemote ? remoteBranch : sourceBranch) + "' into '" + currentBranch + "'...");
            
            MergeResult result = git.merge()
                .include(git.getRepository().resolve(branchToMerge))
                .call();
            
            // Handle result
            switch (result.getMergeStatus()) {
                case FAST_FORWARD:
                    System.out.println("Fast-forward merge successful.");
                    System.out.println("Your branch was updated to include all commits from '" + 
                        (isRemote ? remoteBranch : sourceBranch) + "'.");
                    break;
                    
                case MERGED:
                    System.out.println("Merge successful.");
                    System.out.println("Created new merge commit combining both branches.");
                    break;
                    
                case ALREADY_UP_TO_DATE:
                    System.out.println("Already up to date.");
                    System.out.println("Your branch already contains all commits from '" + 
                        (isRemote ? remoteBranch : sourceBranch) + "'.");
                    break;
                    
                case CONFLICTING:
                    System.out.println("Merge conflicts detected!");
                    System.out.println("The following files have conflicts:");
                    result.getConflicts().keySet().forEach(f -> System.out.println("  " + f));
                    System.out.println("\nResolve conflicts manually, then:");
                    System.out.println("  1. Edit the files to resolve conflicts");
                    System.out.println("  2. Use 'vgl track' to mark files as resolved");
                    System.out.println("  3. Use 'vgl commit \"Merge message\"' to complete the merge");
                    System.out.println("  OR use 'vgl abort' to cancel the merge");
                    return 1;
                    
                case FAILED:
                    System.out.println("Merge failed: " + result.getMergeStatus());
                    return 1;
                    
                default:
                    System.out.println("Merge status: " + result.getMergeStatus());
            }
        }
        
        return 0;
    }
}
