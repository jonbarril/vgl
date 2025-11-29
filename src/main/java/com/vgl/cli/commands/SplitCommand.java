package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * SplitCommand creates a new branch and switches to it.
 * Like 'git checkout -b' but with VGL's simplified syntax.
 */
public class SplitCommand implements Command {
    @Override
    public String name() {
        return "split";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse flags
        String newBranch = Args.getFlag(args, "-lb");
        String sourceBranch = Args.getFlag(args, "-from");
        boolean createRemote = Args.hasFlag(args, "-bb");
        
        if (newBranch == null) {
            System.out.println("Usage: vgl split -lb BRANCH [-from BRANCH] [-bb]");
            System.out.println("Examples:");
            System.out.println("  vgl split -lb feature              Create and switch to 'feature' from current");
            System.out.println("  vgl split -lb hotfix -from main    Create 'hotfix' from 'main' branch");
            System.out.println("  vgl split -lb feature -bb          Create locally and push to remote");
            return 1;
        }
        
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Error: No Git repository found at: " + dir);
            System.out.println("Use 'vgl create -lr " + dir + "' to create a repository first.");
            return 1;
        }
        
        try (Git git = Git.open(dir.toFile())) {
            // Check if branch already exists
            List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + newBranch));
            
            if (branchExists) {
                System.out.println("Warning: Branch '" + newBranch + "' already exists.");
                System.out.println("Use 'vgl switch -lb " + newBranch + "' to switch to it.");
                return 0;
            }
            
            // If source branch specified, verify it exists
            if (sourceBranch != null) {
                boolean sourceExists = branches.stream()
                    .anyMatch(ref -> ref.getName().equals("refs/heads/" + sourceBranch));
                
                if (!sourceExists) {
                    System.out.println("Error: Source branch '" + sourceBranch + "' does not exist.");
                    System.out.println("Available branches:");
                    branches.forEach(ref -> System.out.println("  " + ref.getName().replace("refs/heads/", "")));
                    return 1;
                }
                
                // Checkout source branch first
                git.checkout().setName(sourceBranch).call();
            }
            
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty();
            
            if (hasChanges) {
                System.out.println("Warning: You have uncommitted changes.");
                System.out.println("These changes will be carried to the new branch.");
            }
            
            // Save current state as jump state
            String currentBranch = git.getRepository().getBranch();
            vgl.setJumpLocalDir(localDir);
            vgl.setJumpLocalBranch(currentBranch);
            vgl.setJumpRemoteUrl(vgl.getRemoteUrl());
            vgl.setJumpRemoteBranch(vgl.getRemoteBranch());
            
            // Create and checkout new branch
            git.checkout()
                .setCreateBranch(true)
                .setName(newBranch)
                .call();
            
            vgl.setLocalBranch(newBranch);
            vgl.save();
            
            System.out.println("Created and switched to branch '" + newBranch + "'" + 
                (sourceBranch != null ? " from '" + sourceBranch + "'" : "") + ".");
            
            // If -bb flag, push to remote
            if (createRemote) {
                String remoteUrl = vgl.getRemoteUrl();
                if (remoteUrl == null || remoteUrl.isBlank()) {
                    System.out.println("Warning: No remote configured. Cannot create remote branch.");
                    System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                } else {
                    // Push to remote
                    git.push()
                        .setRemote("origin")
                        .add(newBranch)
                        .call();
                    
                    // Set up tracking
                    git.branchCreate()
                        .setName(newBranch)
                        .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                        .setStartPoint("origin/" + newBranch)
                        .setForce(true)
                        .call();
                    
                    vgl.setRemoteBranch(newBranch);
                    vgl.save();
                    
                    System.out.println("Pushed branch '" + newBranch + "' to remote.");
                }
            }
        }
        
        return 0;
    }
}
