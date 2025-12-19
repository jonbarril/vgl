package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * SplitCommand creates branches in either direction.
 * Supports -into (create new from switch state) and -from (create new from specified source).
 */
public class SplitCommand implements Command {
    @Override
    public String name() {
        return "split";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse direction flags
        boolean splitInto = Args.hasFlag(args, "-into");
        boolean splitFrom = Args.hasFlag(args, "-from");
        
        // Parse target/source flags
        String localDir = Args.getFlag(args, "-lr");
        String localBranch = Args.getFlag(args, "-lb");
        String remoteUrl = Args.getFlag(args, "-rr");
        String remoteBranch = Args.getFlag(args, "-rb");
        boolean hasBbFlag = Args.hasFlag(args, "-bb");
        
        // Validate direction
        if (!splitInto && !splitFrom) {
            System.out.println("Usage: vgl split -into|-from [-lr [DIR]] [-lb [BRANCH]] [-rr [URL]] [-rb [BRANCH]] [-bb [BRANCH]]");
            System.out.println("Examples:");
            System.out.println("  vgl split -into -lb feature    Create 'feature' branch from switch state");
            System.out.println("  vgl split -from -lb develop    Create switch state branch from 'develop'");
            System.out.println("  vgl split -into -bb feature    Create feature locally and push to remote");
            return 1;
        }
        
        if (splitInto && splitFrom) {
            System.out.println("Error: Cannot specify both -into and -from.");
            return 1;
        }
        
        // Apply switch state defaults
        String switchLocalDir = vgl.getLocalDir();
        String switchLocalBranch = vgl.getLocalBranch();
        String switchRemoteUrl = vgl.getRemoteUrl();
        String switchRemoteBranch = vgl.getRemoteBranch();
        
        // Handle -bb flag (both branches with same name)
        if (hasBbFlag) {
            if (localBranch == null) {
                System.out.println("Error: Must specify branch name with -lb when using -bb.");
                return 1;
            }
            remoteBranch = localBranch;
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
            System.out.println("Use 'vgl create -lr " + dir + "' to create a repository first.");
            return 1;
        }
        
        try (Git git = Git.open(dir.toFile())) {
            String currentBranch = git.getRepository().getBranch();
            
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty();
            
            if (hasChanges) {
                System.out.println("Warning: You have uncommitted changes.");
                System.out.println("These changes will be carried to the new branch.");
            }
            
            // Save current state as jump state
                // Removed jump state references
            vgl.save();
            
            String sourceBranchName;
            String newBranchName;
            boolean useRemote = false;
            
            if (splitInto) {
                // Split INTO new branch FROM switch state (current branch)
                sourceBranchName = currentBranch;
                
                if (localBranch == null) {
                    System.out.println("Error: Must specify -lb with -into.");
                    return 1;
                }
                
                newBranchName = localBranch;
                
                // Check if branch already exists
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                boolean branchExists = branches.stream()
                    .anyMatch(ref -> ref.getName().equals("refs/heads/" + newBranchName));
                
                if (branchExists) {
                    System.out.println("Warning: Branch '" + newBranchName + "' already exists.");
                    System.out.println("Use 'vgl switch -lb " + newBranchName + "' to switch to it.");
                    return 0;
                }
            } else {
                // Split FROM specified source INTO new branch at switch state
                if (localBranch == null) {
                    System.out.println("Error: Must specify -lb with -from (source branch).");
                    return 1;
                }
                
                sourceBranchName = localBranch;
                
                // Use switch state branch name or a derived name
                newBranchName = switchLocalBranch;
                
                if (remoteBranch != null) {
                    // Clone from remote branch
                    String effectiveRemoteUrl = (remoteUrl != null) ? remoteUrl : switchRemoteUrl;
                    if (effectiveRemoteUrl == null || effectiveRemoteUrl.isBlank()) {
                        System.out.println("Error: No remote configured.");
                        System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                        return 1;
                    }
                    
                    System.out.println("Fetching from remote...");
                    git.fetch().setRemote("origin").call();
                    
                    sourceBranchName = "origin/" + remoteBranch;
                    useRemote = true;
                } else {
                    // Verify local source branch exists
                    final String sourceToCheck = sourceBranchName;
                    List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                    boolean branchExists = branches.stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + sourceToCheck));
                    
                    if (!branchExists) {
                        System.out.println("Error: Source branch '" + sourceToCheck + "' does not exist.");
                        System.out.println("Available branches:");
                        branches.forEach(ref -> System.out.println("  " + ref.getName().replace("refs/heads/", "")));
                        return 1;
                    }
                }
                
                // Checkout source branch first
                if (!useRemote) {
                    git.checkout().setName(sourceBranchName).call();
                }
            }
            
            // Create and checkout new branch
            // Create and checkout new branch
            System.out.println("Creating branch '" + newBranchName + "' from '" + sourceBranchName + "'...");
            
            git.checkout()
                .setCreateBranch(true)
                .setName(newBranchName)
                .setStartPoint(sourceBranchName)
                .call();
            
            vgl.setLocalBranch(newBranchName);
            vgl.save();
            
            System.out.println("Split branch created.");
            Utils.printSwitchState(vgl);
            
            // If -bb flag or remote specified, push to remote
            if (hasBbFlag || remoteBranch != null) {
                String effectiveRemoteUrl = (remoteUrl != null) ? remoteUrl : switchRemoteUrl;
                if (effectiveRemoteUrl == null || effectiveRemoteUrl.isBlank()) {
                    System.out.println("Warning: No remote configured. Cannot create remote branch.");
                    System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                } else {
                    // Push to remote
                    git.push()
                        .setRemote("origin")
                        .add(newBranchName)
                        .call();
                    
                    // Set up tracking
                    git.branchCreate()
                        .setName(newBranchName)
                        .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                        .setStartPoint("origin/" + newBranchName)
                        .setForce(true)
                        .call();
                    
                    vgl.setRemoteBranch(newBranchName);
                    vgl.save();
                    
                    System.out.println("Pushed branch '" + newBranchName + "' to remote.");
                }
            }
        }
        
        return 0;
    }
}
