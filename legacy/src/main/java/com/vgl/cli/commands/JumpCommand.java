package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * JumpCommand toggles between current and previous workspace context.
 * Swaps all four state values: localDir, localBranch, remoteUrl, remoteBranch
 */
public class JumpCommand implements Command {
    @Override
    public String name() {
        return "jump";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();

        // Check if jump state exists
        if (!vgl.hasJumpState()) {
            System.out.println("Warning: No previous workspace context to jump to.");
            return 0;
        }

        // Get current state
        String currentLocalDir = vgl.getLocalDir();
        String currentLocalBranch = vgl.getLocalBranch();
        String currentRemoteUrl = vgl.getRemoteUrl();
        String currentRemoteBranch = vgl.getRemoteBranch();

        // Get jump state
        String jumpLocalDir = vgl.getJumpLocalDir();
        String jumpLocalBranch = vgl.getJumpLocalBranch();
        String jumpRemoteUrl = vgl.getJumpRemoteUrl();
        String jumpRemoteBranch = vgl.getJumpRemoteBranch();

        // Check if they're the same (no change needed)
        boolean sameLocal = currentLocalDir.equals(jumpLocalDir) && currentLocalBranch.equals(jumpLocalBranch);
        boolean sameRemote = 
            (currentRemoteUrl == null && jumpRemoteUrl == null) ||
            (currentRemoteUrl != null && currentRemoteUrl.equals(jumpRemoteUrl) && currentRemoteBranch.equals(jumpRemoteBranch));
        
        if (sameLocal && sameRemote) {
            System.out.println("Warning: Current and jump contexts are the same. No change made.");
            return 0;
        }

        // Verify jump directory exists
        Path jumpDir = Paths.get(jumpLocalDir).toAbsolutePath().normalize();
        if (!Files.exists(jumpDir.resolve(".git"))) {
            System.out.println("Warning: Jump directory does not contain a Git repository: " + jumpDir);
            System.out.println("Jump state may be stale. No change made.");
            return 1;
        }

        // Check for uncommitted changes in current repo before switching
        Path currentDir = Paths.get(currentLocalDir).toAbsolutePath().normalize();
        if (Files.exists(currentDir.resolve(".git"))) {
            try (Git git = Git.open(currentDir.toFile())) {
                org.eclipse.jgit.api.Status status = git.status().call();
                boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                    !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                    !status.getUntracked().isEmpty();
                
                if (hasChanges) {
                    System.out.println("Warning: You have uncommitted changes in current workspace.");
                    System.out.println("Commit or restore them before jumping to avoid confusion.");
                    System.out.println("Use 'vgl status' to see changes.");
                }
            }
        }

        // Open jump repo and switch to jump branch
        try (Git git = Git.open(jumpDir.toFile())) {
            // Verify jump branch exists
            List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + jumpLocalBranch));
            
            if (!branchExists) {
                System.out.println("Warning: Branch '" + jumpLocalBranch + "' does not exist in jump repository.");
                System.out.println("Jump state may be stale. No change made.");
                return 1;
            }

            // Switch to the branch
            git.checkout().setName(jumpLocalBranch).call();
        }

        // Swap: current becomes jump, jump becomes current
        vgl.setJumpLocalDir(currentLocalDir);
        vgl.setJumpLocalBranch(currentLocalBranch);
        vgl.setJumpRemoteUrl(currentRemoteUrl);
        vgl.setJumpRemoteBranch(currentRemoteBranch);

        vgl.setLocalDir(jumpLocalDir);
        vgl.setLocalBranch(jumpLocalBranch);
        vgl.setRemoteUrl(jumpRemoteUrl);
        vgl.setRemoteBranch(jumpRemoteBranch);

        vgl.save();

        System.out.println("Jumped to previous state.");
        Utils.printSwitchState(vgl);
        return 0;
    }
}
