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
        boolean force = Args.hasFlag(args, "-f");

        // Parse new flags
        String newLocalDir = Args.getFlag(args, "-lr");
        String newLocalBranch = Args.getFlag(args, "-lb");
        String newRemoteUrl = Args.getFlag(args, "-rr");
        String newRemoteBranch = Args.getFlag(args, "-rb");
        boolean bothBranches = Args.hasFlag(args, "-bb");

        // Handle -bb flag (switch both local and remote to same branch)
        if (bothBranches) {
            String branchName = Args.getFlag(args, "-bb");
            if (branchName != null) {
                newLocalBranch = branchName;
                newRemoteBranch = branchName;
            }
        }
        
        // Get current state
        String currentLocalDir = vgl.getLocalDir();
        String currentLocalBranch = vgl.getLocalBranch();
        String currentRemoteUrl = vgl.getRemoteUrl();
        String currentRemoteBranch = vgl.getRemoteBranch();
        
        // Determine what we're switching
        boolean switchingLocal = newLocalDir != null || newLocalBranch != null;
        boolean switchingRemote = newRemoteUrl != null || newRemoteBranch != null;
        
        if (!switchingLocal && !switchingRemote) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_USAGE);
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_EXAMPLES_HEADER);
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_EXAMPLE_1);
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_EXAMPLE_2);
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_EXAMPLE_3);
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_EXAMPLE_4);
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
            // Check for nested repo if switching to a new local directory
            if (!dir.toString().equals(Paths.get(currentLocalDir).toAbsolutePath().normalize().toString())) {
                // Use the same nested repo warning logic as CreateCommand/CheckoutCommand
                if (!com.vgl.cli.utils.RepoUtils.checkAndWarnIfNestedRepo(dir, force)) {
                    // Only print the cancel message from RepoUtils, do not print anything else
                    return 1;
                }
            }
            // Check if directory exists
            if (!Files.exists(dir.resolve(".git"))) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_PREFIX + dir);
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_HELP);
                return 1;
            }
            try (Git git = Git.open(dir.toFile())) {
                // Verify branch exists FIRST, before checking for uncommitted changes
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                boolean branchExists = branches.stream()
                    .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalNewLocalBranch));
                if (!branchExists) {
                    System.err.println(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST + finalNewLocalBranch);
                    return 1;
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
                                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_UNCOMMITTED_WARNING);
                                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_MAY_LOSE_CHANGES);
                                if (!force) {
                                    System.out.print("Continue? (y/N): ");
                                    String response;
                                    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                                        response = scanner.nextLine().trim().toLowerCase();
                                    }
                                    if (!response.equals("y") && !response.equals("yes")) {
                                        System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_CANCELLED);
                                        return 0;
                                    }
                                }
                            }
                        }
                    }
                    // Switch branch in target repo
                    git.checkout().setName(finalNewLocalBranch).call();
                }
            }
            // Save current state as jump state (removed)
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
        // Print only the expected single-line success message for test compatibility
        System.out.println(com.vgl.cli.utils.MessageConstants.MSG_SWITCH_SUCCESS_PREFIX + vgl.getLocalBranch() + "'");
        return 0;
    }
}
