package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * SwitchCommand changes workspace context between repos/branches.
 * Supports new flag-based syntax: -lr, -lb, -rr, -rb
 */
public class SwitchCommand implements Command {
    @Override
    public String name() {
        return "switch";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse new flags
        String newLocalDir = Args.getFlag(args, "-lr");
        String newLocalBranch = Args.getFlag(args, "-lb");
        String newRemoteUrl = Args.getFlag(args, "-rr");
        String newRemoteBranch = Args.getFlag(args, "-rb");
        
        // Get current state
        String currentLocalDir = vgl.getLocalDir();
        String currentLocalBranch = vgl.getLocalBranch();
        String currentRemoteUrl = vgl.getRemoteUrl();
        String currentRemoteBranch = vgl.getRemoteBranch();
        
        // Determine what we're switching
        boolean switchingLocal = newLocalDir != null || newLocalBranch != null;
        boolean switchingRemote = newRemoteUrl != null || newRemoteBranch != null;
        
        if (!switchingLocal && !switchingRemote) {
            System.out.println("Usage: vgl switch [-lr DIR] [-lb BRANCH] [-rr URL] [-rb BRANCH]");
            System.out.println("Examples:");
            System.out.println("  vgl switch -lr ../other -lb develop    Switch to different repo and branch");
            System.out.println("  vgl switch -lb feature                  Switch branch in current repo");
            System.out.println("  vgl switch -rr https://... -rb main     Configure remote");
            return 1;
        }
        
        // Apply defaults for unspecified values
        if (newLocalDir == null) newLocalDir = currentLocalDir;
        if (newLocalBranch == null) newLocalBranch = currentLocalBranch;
        if (newRemoteUrl == null) newRemoteUrl = currentRemoteUrl;
        if (newRemoteBranch == null) newRemoteBranch = currentRemoteBranch;
        
        // Make final for lambda usage
        final String finalNewLocalBranch = newLocalBranch;
        
        // Handle local repository/branch switching
        if (switchingLocal) {
            Path dir = Paths.get(newLocalDir).toAbsolutePath().normalize();
            
            // Check if directory exists
            if (!Files.exists(dir.resolve(".git"))) {
                System.out.println("Error: No Git repository found at: " + dir);
                System.out.println("Use 'vgl create -lr " + dir + "' to create a new repository.");
                return 1;
            }
            
            try (Git git = Git.open(dir.toFile())) {
                // Verify branch exists
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                if (!branches.isEmpty()) {
                    boolean branchExists = branches.stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalNewLocalBranch));
                    
                    if (!branchExists) {
                        System.out.println("Error: Branch '" + finalNewLocalBranch + "' does not exist in: " + dir);
                        System.out.println("Available branches:");
                        branches.forEach(ref -> System.out.println("  " + ref.getName().replace("refs/heads/", "")));
                        System.out.println("Use 'vgl create -lb " + finalNewLocalBranch + "' to create this branch.");
                        return 1;
                    }
                }
                
                // Check for uncommitted changes if switching repos or branches
                boolean changingContext = !dir.toString().equals(Paths.get(currentLocalDir).toAbsolutePath().normalize().toString()) 
                                       || !finalNewLocalBranch.equals(currentLocalBranch);
                
                if (changingContext) {
                    // Check current repo for uncommitted changes
                    Path currentDir = Paths.get(currentLocalDir).toAbsolutePath().normalize();
                    if (Files.exists(currentDir.resolve(".git"))) {
                        try (Git currentGit = Git.open(currentDir.toFile())) {
                            org.eclipse.jgit.api.Status status = currentGit.status().call();
                            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                                !status.getUntracked().isEmpty();
                            
                            if (hasChanges) {
                                System.out.println("Warning: You have uncommitted changes in current workspace.");
                                System.out.println("Commit or restore them before switching to avoid confusion.");
                            }
                        }
                    }
                    
                    // Switch branch in target repo
                    if (!branches.isEmpty()) {
                        git.checkout().setName(finalNewLocalBranch).call();
                    }
                }
            }
            
            // Save current state as jump state
            vgl.setJumpLocalDir(currentLocalDir);
            vgl.setJumpLocalBranch(currentLocalBranch);
            vgl.setJumpRemoteUrl(currentRemoteUrl);
            vgl.setJumpRemoteBranch(currentRemoteBranch);
            
            vgl.setLocalDir(dir.toString());
            vgl.setLocalBranch(finalNewLocalBranch);
        }
        
        // Handle remote configuration
        if (switchingRemote) {
            if (newRemoteUrl != null && !newRemoteUrl.isBlank()) {
                // Configure remote in Git
                Path dir = Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize();
                if (Files.exists(dir.resolve(".git"))) {
                    try (Git git = Git.open(dir.toFile())) {
                        git.remoteSetUrl()
                            .setRemoteName("origin")
                            .setRemoteUri(new org.eclipse.jgit.transport.URIish(newRemoteUrl))
                            .call();
                    }
                }
            }
            
            vgl.setRemoteUrl(newRemoteUrl);
            vgl.setRemoteBranch(newRemoteBranch);
        }
        
        vgl.save();
        
        // Print confirmation
        if (switchingLocal && switchingRemote) {
            System.out.println("Switched to: " + newLocalDir + " on branch '" + finalNewLocalBranch + "'");
            System.out.println("Remote: " + newRemoteUrl + " on branch '" + newRemoteBranch + "'");
        } else if (switchingLocal) {
            System.out.println("Switched to: " + newLocalDir + " on branch '" + finalNewLocalBranch + "'");
        } else {
            System.out.println("Configured remote: " + newRemoteUrl + " on branch '" + newRemoteBranch + "'");
        }
        
        return 0;
    }
}
