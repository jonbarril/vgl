package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.VglCli;
import com.vgl.cli.services.RepoResolution;

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
            System.err.println("Error: No remote URL specified and none in switch state.");
            System.err.println("Use 'vgl checkout -rr URL' or configure remote with 'vgl switch -rr URL'.");
            return 1;
        }

        String branch = remoteBranch != null ? remoteBranch : "main";

        // Use RepoResolver to check for existing repo state and directory
        String targetDir = vgl.getLocalDir();
        Path dir = Paths.get(targetDir).toAbsolutePath().normalize();
        RepoResolution repoRes = RepoResolver.resolveForCommand(dir);
        if (repoRes.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH && repoRes.getKind() != RepoResolution.ResolutionKind.FOUND_GIT_ONLY) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        // Only proceed if repo was found
        try (Git existingGit = repoRes.getGit()) {
            org.eclipse.jgit.api.Status status = existingGit.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() ||
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                !status.getMissing().isEmpty();
            if (hasChanges) {
                System.err.println("Warning: Directory already exists with uncommitted changes: " + dir);
                System.err.println("Checking out will overwrite these changes:");
                status.getModified().forEach(f -> System.err.println("  M " + f));
                status.getChanged().forEach(f -> System.err.println("  M " + f));
                status.getAdded().forEach(f -> System.err.println("  A " + f));
                status.getRemoved().forEach(f -> System.err.println("  D " + f));
                status.getMissing().forEach(f -> System.err.println("  D " + f));
                System.err.println();
                System.err.print("Continue? (y/N): ");
                String response;
                try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                    response = scanner.nextLine().trim().toLowerCase();
                }
                if (!response.equals("y") && !response.equals("yes")) {
                    System.err.println("Checkout cancelled.");
                    return 0;
                }
            } else {
                System.err.println("Directory already exists: " + dir);
                System.err.println("Use 'vgl local' to switch to this repository.");
                return 1;
            }
        }
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            System.err.println("Directory already exists and is not empty: " + dir);
            return 1;
        }

        // Check for nested repository and get confirmation
        if (Utils.isNestedRepo(dir)) {
            if (!force) {
                Path parentRepo = Utils.getGitRepoRoot(dir.getParent());
                if (!Utils.warnNestedRepo(dir, parentRepo, false)) {
                    System.err.println("Checkout cancelled.");
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
