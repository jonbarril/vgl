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
 * MergeCommand merges branches in either direction.
 * Supports -from (merge source into switch state) and -into (merge switch state into target).
 */
public class MergeCommand implements Command {
    @Override
    public String name() {
        return "merge";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse direction flags
        boolean mergeFrom = Args.hasFlag(args, "-from");
        boolean mergeInto = Args.hasFlag(args, "-into");
        
        // Parse target/source flags
        String localDir = Args.getFlag(args, "-lr");
        String localBranch = Args.getFlag(args, "-lb");
        String remoteUrl = Args.getFlag(args, "-rr");
        String remoteBranch = Args.getFlag(args, "-rb");
        String bothBranch = Args.getFlag(args, "-bb");
        
        // Validate direction
        if (!mergeFrom && !mergeInto) {
            System.out.println("Usage: vgl merge -from|-into [-lr [DIR]] [-lb [BRANCH]] [-rr [URL]] [-rb [BRANCH]] [-bb [BRANCH]]");
            System.out.println("Examples:");
            System.out.println("  vgl merge -from -lb feature    Merge 'feature' branch into switch state");
            System.out.println("  vgl merge -into -lb main       Merge switch state branch into 'main'");
            System.out.println("  vgl merge -from -bb feature    Merge feature from both local and remote");
            return 1;
        }
        
        if (mergeFrom && mergeInto) {
            System.out.println("Error: Cannot specify both -from and -into.");
            return 1;
        }
        
        // Apply switch state defaults
        String switchLocalDir = vgl.getLocalDir();
        String switchLocalBranch = vgl.getLocalBranch();
        String switchRemoteUrl = vgl.getRemoteUrl();
        String switchRemoteBranch = vgl.getRemoteBranch();
        
        // Handle -bb flag (both branches with same name)
        if (bothBranch != null) {
            if (localBranch != null || remoteBranch != null) {
                System.out.println("Error: Cannot specify -bb with -lb or -rb.");
                return 1;
            }
            localBranch = bothBranch;
            remoteBranch = bothBranch;
        }
        
        // Apply defaults for unspecified values
        if (localDir != null && localDir.isEmpty()) {
            localDir = switchLocalDir;
        }
        if (localBranch != null && localBranch.isEmpty()) {
            localBranch = switchLocalBranch;
        }
        if (remoteUrl != null && remoteUrl.isEmpty()) {
            remoteUrl = switchRemoteUrl;
        }
        if (remoteBranch != null && remoteBranch.isEmpty()) {
            remoteBranch = switchRemoteBranch;
        }
        
        // Determine working directory
        String workingDir = (localDir != null) ? localDir : switchLocalDir;
        Path dir = Paths.get(workingDir).toAbsolutePath().normalize();
        
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
            
            // Save current state as jump state
            vgl.setJumpLocalDir(workingDir);
            vgl.setJumpLocalBranch(currentBranch);
            vgl.setJumpRemoteUrl(vgl.getRemoteUrl());
            vgl.setJumpRemoteBranch(vgl.getRemoteBranch());
            vgl.save();
            
            String sourceBranchName;
            String targetBranchName;
            
            if (mergeFrom) {
                // Merge FROM specified source INTO switch state (current branch)
                targetBranchName = currentBranch;
                
                if (remoteBranch != null) {
                    // Merge from remote
                    String effectiveRemoteUrl = (remoteUrl != null) ? remoteUrl : switchRemoteUrl;
                    if (effectiveRemoteUrl == null || effectiveRemoteUrl.isBlank()) {
                        System.out.println("Error: No remote configured.");
                        System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                        return 1;
                    }
                    
                    System.out.println("Fetching from remote...");
                    git.fetch().setRemote("origin").call();
                    
                    sourceBranchName = "origin/" + remoteBranch;
                } else if (localBranch != null) {
                    // Merge from local branch
                    final String sourceBranch = localBranch;
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
                    
                    sourceBranchName = sourceBranch;
                } else {
                    System.out.println("Error: Must specify -lb or -rb with -from.");
                    return 1;
                }
            } else {
                // Merge FROM switch state (current branch) INTO specified target
                sourceBranchName = currentBranch;
                
                if (localBranch != null) {
                    targetBranchName = localBranch;
                    
                    // Verify target branch exists
                    List<Ref> branches = git.branchList().call();
                    boolean branchExists = branches.stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + targetBranchName));
                    
                    if (!branchExists) {
                        System.out.println("Error: Target branch '" + targetBranchName + "' does not exist.");
                        System.out.println("Available branches:");
                        branches.forEach(ref -> System.out.println("  " + ref.getName().replace("refs/heads/", "")));
                        return 1;
                    }
                    
                    if (targetBranchName.equals(currentBranch)) {
                        System.out.println("Error: Cannot merge branch '" + currentBranch + "' into itself.");
                        return 1;
                    }
                    
                    // Switch to target branch first
                    git.checkout().setName(targetBranchName).call();
                } else {
                    System.out.println("Error: Must specify -lb with -into.");
                    return 1;
                }
            }
            
            // Perform merge
            // Perform merge
            System.out.println("Merging '" + sourceBranchName + "' into '" + targetBranchName + "'...");
            
            MergeResult result = git.merge()
                .include(git.getRepository().resolve(sourceBranchName))
                .call();
            
            // Handle result
            switch (result.getMergeStatus()) {
                case FAST_FORWARD:
                    System.out.println("Fast-forward merge successful.");
                    System.out.println("Branch '" + targetBranchName + "' updated to include all commits from '" + sourceBranchName + "'.");
                    break;
                    
                case MERGED:
                    System.out.println("Merge successful.");
                    System.out.println("Created new merge commit combining both branches.");
                    break;
                    
                case ALREADY_UP_TO_DATE:
                    System.out.println("Already up to date.");
                    System.out.println("Branch '" + targetBranchName + "' already contains all commits from '" + sourceBranchName + "'.");
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
            
            // Print switch state feedback
            String finalBranch = git.getRepository().getBranch();
            System.out.println("Switched to: " + workingDir + ":" + finalBranch);
        }
        
        return 0;
    }
}
