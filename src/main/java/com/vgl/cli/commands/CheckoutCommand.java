package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CheckoutCommand implements Command {
    @Override public String name(){ return "checkout"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse flags - use switch state as defaults
        boolean hasRrFlag = Args.hasFlag(args, "-rr");
        boolean hasRbFlag = Args.hasFlag(args, "-rb");
        boolean force = Args.hasFlag(args, "-f");
        
        String remoteUrl = Args.getFlag(args, "-rr");
        String remoteBranch = Args.getFlag(args, "-rb");
        
        if (!hasRrFlag && !hasRbFlag) {
            System.out.println("Usage: vgl checkout [-rr [URL]] [-rb [BRANCH]]");
            System.out.println("Examples:");
            System.out.println("  vgl checkout -rr URL -rb main   Clone remote URL/branch into switch state");
            System.out.println("  vgl checkout -rb feature        Clone switch state remote/branch");
            return 1;
        }
        
        // Default to switch state if flags present but no values
        if (hasRrFlag && remoteUrl == null) {
            remoteUrl = vgl.getRemoteUrl();
        }
        if (hasRbFlag && remoteBranch == null) {
            remoteBranch = vgl.getRemoteBranch();
        }
        
        // Validate remote URL
        if (remoteUrl == null || remoteUrl.isBlank()) {
            System.out.println("Error: No remote URL specified and none in switch state.");
            System.out.println("Use 'vgl checkout -rr URL' or configure remote with 'vgl switch -rr URL'.");
            return 1;
        }
        
        String branch = remoteBranch != null ? remoteBranch : "main";
        
        // Clone into switch state local directory
        String targetDir = vgl.getLocalDir();
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

        // Check for nested repository and get confirmation
        if (Utils.isNestedRepo(dir)) {
            if (!force) {
                if (!Utils.warnNestedRepo(dir, Utils.getGitRepoRoot(dir.getParent()))) {
                    System.out.println("Checkout cancelled.");
                    return 0;
                }
            }
        }

        Git git = Git.cloneRepository().setURI(remoteUrl).setDirectory(dir.toFile()).setBranch("refs/heads/" + branch).call();
        git.close();
        
        // Save current state as jump state before updating switch state
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
        
        System.out.println("Cloned remote repository.");
        Utils.printSwitchState(vgl);
        return 0;
    }
}
