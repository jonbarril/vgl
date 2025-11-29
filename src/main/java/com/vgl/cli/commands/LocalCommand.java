package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalCommand implements Command {
    @Override
    public String name() {
        return "local";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        String path = vgl.getLocalDir(); // Default to .vgl state
        String branch = vgl.getLocalBranch(); // Default to .vgl state

        String newLocalDir = Args.getFlag(args, "-lr");
        String newLocalBranch = Args.getFlag(args, "-lb");
        
        if (newLocalDir != null) path = newLocalDir;
        if (newLocalBranch != null) branch = newLocalBranch;

        // Fallback to current working directory and "main" branch if not set
        if (path == null || path.isBlank()) path = ".";
        if (branch == null || branch.isBlank()) branch = "main";
        
        final String finalBranch = branch;

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Warning: No local repository found in: " + dir);
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            // Verify the branch exists (if any branches exist at all)
            List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
        if (!branches.isEmpty()) {
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalBranch));
            
            if (!branchExists) {
                System.out.println("Warning: Branch '" + finalBranch + "' does not exist in local repository: " + dir);
                System.out.println("Create the branch with: vgl create -lb " + finalBranch);
                return 1;
            }
        }
        // If no branches exist (fresh repo), allow any branch name
        
        // Check for unpushed commits
        String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
        if (remoteUrl != null && !branches.isEmpty()) {
            try {
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + git.getRepository().getBranch());
                
                if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                    org.eclipse.jgit.lib.BranchTrackingStatus bts = org.eclipse.jgit.lib.BranchTrackingStatus.of(git.getRepository(), git.getRepository().getBranch());
                    if (bts != null && bts.getAheadCount() > 0) {
                        System.out.println("Warning: Current branch has " + bts.getAheadCount() + " unpushed commit(s).");
                        System.out.println("These commits will not be lost, but you should push before switching.");
                    }
                }
            } catch (Exception e) {
                // Ignore - remote tracking may not be set up
            }
        }
        
        // Check for uncommitted changes before switching
        org.eclipse.jgit.api.Status status = git.status().call();
        boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                            !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() || 
                            !status.getMissing().isEmpty();
        
        if (hasChanges && !branches.isEmpty()) {
            System.out.println("Warning: You have uncommitted changes.");
            System.out.println("Switching branches will discard these changes:");
            status.getModified().forEach(f -> System.out.println("  M " + f));
            status.getChanged().forEach(f -> System.out.println("  M " + f));
            status.getAdded().forEach(f -> System.out.println("  A " + f));
            status.getRemoved().forEach(f -> System.out.println("  D " + f));
            status.getMissing().forEach(f -> System.out.println("  D " + f));
            System.out.println();
            System.out.print("Continue? (y/N): ");
            
            String response;
            try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                response = scanner.nextLine().trim().toLowerCase();
            }
            
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("Branch switch cancelled.");
                return 0;
            }
        }
        
        // Actually checkout the branch in Git
        if (!branches.isEmpty()) {
            git.checkout().setName(finalBranch).call();
        }
        
        // Save current state as jump state before switching
        String currentDir = vgl.getLocalDir();
        String currentBranch = vgl.getLocalBranch();
        String currentRemoteUrl = vgl.getRemoteUrl();
        String currentRemoteBranch = vgl.getRemoteBranch();
        
        vgl.setJumpLocalDir(currentDir);
        vgl.setJumpLocalBranch(currentBranch);
        vgl.setJumpRemoteUrl(currentRemoteUrl);
        vgl.setJumpRemoteBranch(currentRemoteBranch);
        
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(finalBranch);
        vgl.save();
        System.out.println("Switched to local repository: " + dir + " on branch '" + branch + "'.");
        return 0;
        }
    }
}
