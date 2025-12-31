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
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_DELETE_REMOTE_NOT_IMPLEMENTED);
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
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_DELETE_USAGE);
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

        boolean localDeleted = false;
        boolean remoteDeleted = false;
        boolean localBranchMissing = false;

        if (localBranch != null) {
            int result = deleteLocalBranch(vgl, localBranch, force);
            if (result == 0) {
                localDeleted = true;
            } else {
                localBranchMissing = true;
            }
        }

        if (remoteBranch != null) {
            int result = deleteRemoteBranch(vgl, remoteBranch, force);
            if (result == 0) {
                remoteDeleted = true;
            }
        }

        // Print only one message: success if either branch deleted, error only if both failed
        if (remoteDeleted) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_DELETE_BRANCH_SUCCESS_PREFIX + remoteBranch + "'");
            return 0;
        }
        if (localDeleted) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_DELETE_BRANCH_SUCCESS_PREFIX + localBranch + "'");
            return 0;
        }
        if (localBranchMissing) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST + localBranch);
        }
        return 1;
    }

    private int deleteLocalRepository(String repoPath) throws IOException {
        Path dir = Paths.get(repoPath).toAbsolutePath().normalize();

        if (!Files.exists(dir)) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_DIR_NOT_FOUND + dir);
            return 1;
        }
        if (!Files.exists(dir.resolve(".git"))) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_NOT_A_GIT_REPO + dir);
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
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_PREFIX + dir);
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            String currentBranch = git.getRepository().getBranch();

            if (branch.equals(currentBranch)) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_DELETE_CURRENT_BRANCH + branch);
                return 1;
            }

            // Check if branch exists
            List<Ref> branches = git.branchList().call();
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + branch));

            if (!branchExists) {
                return 1;
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

            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_DELETE_BRANCH_SUCCESS_PREFIX + branch + "'");
        }

        return 0;
    }

    private int deleteRemoteBranch(VglCli vgl, String branch, boolean force) throws Exception {
        String remoteUrl = vgl.getRemoteUrl();

        if (remoteUrl == null || remoteUrl.isBlank()) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_NO_REMOTE_URL);
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_REMOTE_URL_HELP);
            return 1;
        }

        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();

        if (!Files.exists(dir.resolve(".git"))) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_PREFIX + dir);
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
            String refspec = ":refs/heads/" + branch;
            Iterable<org.eclipse.jgit.transport.PushResult> pushResults = git.push()
                .setRemote("origin")
                .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(refspec))
                .call();

            boolean deleted = false;
            for (org.eclipse.jgit.transport.PushResult result : pushResults) {
                for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK ||
                        update.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        deleted = true;
                    }
                }
            }
            if (deleted) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}
