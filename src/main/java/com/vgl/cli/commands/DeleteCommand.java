package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * DeleteCommand removes repos or branches (local and/or remote).
 * Requires confirmation for destructive operations.
 */
public class DeleteCommand implements Command {
    @Override
    public String name() {
        return "delete";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        boolean force = Args.hasFlag(args, "-f");
        
        // Check for -rr flag (TBD feature)
        if (Args.hasFlag(args, "-rr")) {
            System.out.println("Warning: Deleting remote repositories is not yet implemented.");
            System.out.println("For now, use your repository hosting tools (GitHub, GitLab, etc.).");
            return 1;
        }
        
        // Parse flags - may have values or be flags-only (use switch state)
        boolean hasLrFlag = Args.hasFlag(args, "-lr");
        boolean hasLbFlag = Args.hasFlag(args, "-lb");
        boolean hasRbFlag = Args.hasFlag(args, "-rb");
        boolean hasBbFlag = Args.hasFlag(args, "-bb");
        
        String localRepo = Args.getFlag(args, "-lr");
        String localBranch = Args.getFlag(args, "-lb");
        String remoteBranch = Args.getFlag(args, "-rb");
        String bothBranch = Args.getFlag(args, "-bb");
        
        if (!hasLrFlag && !hasLbFlag && !hasRbFlag && !hasBbFlag) {
            System.out.println("Usage: vgl delete [-lr [DIR]] [-lb [BRANCH]] [-rb [BRANCH]] | [-bb [BRANCH]]");
            System.out.println("Examples:");
            System.out.println("  vgl delete -lr              Delete local repository from switch state");
            System.out.println("  vgl delete -lr ../old       Delete specified local repository");
            System.out.println("  vgl delete -lb              Delete local branch from switch state");
            System.out.println("  vgl delete -lb oldbranch    Delete specified local branch");
            System.out.println("  vgl delete -bb              Delete both branches from switch state");
            return 1;
        }
        
        // Handle -bb flag: delete both local and remote branch
        if (hasBbFlag) {
            if (bothBranch == null) {
                // Use switch state branches
                localBranch = vgl.getLocalBranch();
                remoteBranch = vgl.getRemoteBranch();
            } else {
                localBranch = bothBranch;
                remoteBranch = bothBranch;
            }
        }
        
        // Default to switch state if flags present but no values
        if (hasLrFlag && localRepo == null) {
            localRepo = vgl.getLocalDir();
        }
        if (hasLbFlag && localBranch == null) {
            localBranch = vgl.getLocalBranch();
        }
        if (hasRbFlag && remoteBranch == null) {
            remoteBranch = vgl.getRemoteBranch();
        }
        
        // Delete local repository
        if (localRepo != null) {
            return deleteLocalRepository(localRepo);
        }
        
        // Delete local branch
        if (localBranch != null) {
            int result = deleteLocalBranch(vgl, localBranch, force);
            if (result != 0 && remoteBranch != null) {
                return result; // Don't try remote if local failed
            }
        }
        
        // Delete remote branch
        if (remoteBranch != null) {
            return deleteRemoteBranch(vgl, remoteBranch, force);
        }
        
        return 0;
    }
    
    private int deleteLocalRepository(String repoPath) throws IOException {
        Path dir = Paths.get(repoPath).toAbsolutePath().normalize();
        
        if (!Files.exists(dir)) {
            System.out.println("Error: Directory does not exist: " + dir);
            return 1;
        }
        
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Error: Not a Git repository: " + dir);
            return 1;
        }
        
        System.out.println("WARNING: This will permanently delete the entire repository:");
        System.out.println("  " + dir);
        System.out.println("This cannot be undone.");
        System.out.print("Type the directory name to confirm deletion: ");
        
        try (Scanner scanner = new Scanner(System.in)) {
            String confirmation = scanner.nextLine().trim();
            String dirName = dir.getFileName().toString();
            
            if (!confirmation.equals(dirName)) {
                System.out.println("Deletion cancelled.");
                return 0;
            }
        }
        
        // Delete recursively
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    System.err.println("Warning: Failed to delete " + p + ": " + e.getMessage());
                }
            });
        
        System.out.println("Deleted repository: " + dir);
        return 0;
    }
    
    private int deleteLocalBranch(VglCli vgl, String branch, boolean force) throws Exception {
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Error: No Git repository found at: " + dir);
            return 1;
        }
        
        try (Git git = Git.open(dir.toFile())) {
            String currentBranch = git.getRepository().getBranch();
            
            if (branch.equals(currentBranch)) {
                System.out.println("Error: Cannot delete the currently checked out branch '" + branch + "'.");
                System.out.println("Switch to another branch first using 'vgl switch -lb BRANCH'.");
                return 1;
            }
            
            // Check if branch exists
            List<Ref> branches = git.branchList().call();
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + branch));
            
            if (!branchExists) {
                System.out.println("Warning: Branch '" + branch + "' does not exist locally.");
                return 0;
            }
            
            // Check for unmerged commits
            // (This is a simplified check - could be enhanced)
            System.out.println("WARNING: Deleting branch '" + branch + "'.");
            System.out.println("Make sure it has been merged if you want to keep its changes.");
            
            if (!force) {
                System.out.print("Continue? (y/N): ");
                
                try (Scanner scanner = new Scanner(System.in)) {
                    String response = scanner.nextLine().trim().toLowerCase();
                    
                    if (!response.equals("y") && !response.equals("yes")) {
                        System.out.println("Deletion cancelled.");
                        return 0;
                    }
                }
            }
            
            // Delete the branch
            git.branchDelete()
                .setBranchNames(branch)
                .setForce(true)
                .call();
            
            System.out.println("Deleted local branch '" + branch + "'.");
        }
        
        return 0;
    }
    
    private int deleteRemoteBranch(VglCli vgl, String branch, boolean force) throws Exception {
        String remoteUrl = vgl.getRemoteUrl();
        
        if (remoteUrl == null || remoteUrl.isBlank()) {
            System.out.println("Error: No remote configured.");
            System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
            return 1;
        }
        
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Error: No Git repository found at: " + dir);
            return 1;
        }
        
        System.out.println("WARNING: Deleting remote branch '" + branch + "' from:");
        System.out.println("  " + remoteUrl);
        System.out.println("This cannot be undone.");
        
        if (!force) {
            System.out.print("Continue? (y/N): ");
            
            try (Scanner scanner = new Scanner(System.in)) {
                String response = scanner.nextLine().trim().toLowerCase();
                
                if (!response.equals("y") && !response.equals("yes")) {
                    System.out.println("Deletion cancelled.");
                    return 0;
                }
            }
        }
        
        try (Git git = Git.open(dir.toFile())) {
            // Delete remote branch using push with delete refspec
            git.push()
                .setRemote("origin")
                .add(":" + branch)  // Delete refspec
                .call();
            
            System.out.println("Deleted remote branch '" + branch + "'.");
        }
        
        return 0;
    }
}
