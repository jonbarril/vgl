package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CheckoutCommand implements Command {
    @Override public String name(){ return "checkout"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl checkout -rr URL [-rb BRANCH] [-lr DIR]");
            return 1;
        }

        String remoteUrl = Args.getFlag(args, "-rr");
        String remoteBranch = Args.getFlag(args, "-rb");
        String localDir = Args.getFlag(args, "-lr");
        
        if (remoteUrl == null) {
            System.out.println("Usage: vgl checkout -rr URL [-rb BRANCH] [-lr DIR]");
            return 1;
        }

        String branch = remoteBranch != null ? remoteBranch : "main";
        String repoName = remoteUrl.replaceAll(".*/", "").replaceAll("\\.git$", "");
        String targetDir = localDir != null ? localDir : repoName;
        Path dir = Paths.get(targetDir).toAbsolutePath().normalize();
        
        // Check if directory already exists
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            // Check if it's a git repository with uncommitted changes
            if (Files.exists(dir.resolve(".git"))) {
                try (Git existingGit = Git.open(dir.toFile())) {
                    org.eclipse.jgit.api.Status status = existingGit.status().call();
                    boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                        !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() || 
                                        !status.getMissing().isEmpty();
                    
                    if (hasChanges) {
                        System.out.println("Warning: Directory already exists with uncommitted changes: " + dir);
                        System.out.println("Checking out will overwrite these changes:");
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
                            System.out.println("Checkout cancelled.");
                            return 0;
                        }
                    } else {
                        System.out.println("Directory already exists: " + dir);
                        System.out.println("Use 'vgl local' to switch to this repository.");
                        return 1;
                    }
                }
            } else {
                System.out.println("Directory already exists and is not empty: " + dir);
                return 1;
            }
        }

        Git git = Git.cloneRepository().setURI(remoteUrl).setDirectory(dir.toFile()).setBranch("refs/heads/" + branch).call();
        git.close();
        
        // Create .vgl config for the cloned repository
        VglCli vgl = new VglCli();
        
        // Save current state as jump state before cloning
        String currentDir = vgl.getLocalDir();
        String currentBranch = vgl.getLocalBranch();
        String currentRemoteUrl = vgl.getRemoteUrl();
        String currentRemoteBranch = vgl.getRemoteBranch();
        
        vgl.setJumpLocalDir(currentDir);
        vgl.setJumpLocalBranch(currentBranch);
        vgl.setJumpRemoteUrl(currentRemoteUrl);
        vgl.setJumpRemoteBranch(currentRemoteBranch);
        
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(branch);
        vgl.setRemoteUrl(remoteUrl);
        vgl.setRemoteBranch(branch);
        vgl.save();
        
        System.out.println("Cloned remote repository: " + remoteUrl + " to local directory: " + dir + " on branch '" + branch + "'.");
        return 0;
    }
}
