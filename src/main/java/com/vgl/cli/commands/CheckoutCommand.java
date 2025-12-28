    // ...existing code...
package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.VglCli;
import com.vgl.cli.services.RepoResolution;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;


// Explicit import for JGit Ref type
import org.eclipse.jgit.lib.Ref;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CheckoutCommand implements Command {
    @Override public String name(){ return "checkout"; }

    @Override public int run(List<String> args) throws Exception {
        // All debug output removed for clean user-facing output
        // Parse flags - use switch state as defaults, but do not instantiate VglCli yet
        boolean hasRrFlag = Args.hasFlag(args, "-rr");
        boolean hasRbFlag = Args.hasFlag(args, "-rb");
        boolean force = Args.hasFlag(args, "-f");
        String remoteUrl = Args.getFlag(args, "-rr");
        String remoteBranch = Args.getFlag(args, "-rb");
        // Accept optional positional argument for target directory
        String targetDir = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            String val = args.get(i);
            if (!val.startsWith("-")) {
                targetDir = val;
                break;
            }
        }
        Path dir = null;
        if (targetDir != null) {
            dir = Paths.get(targetDir).toAbsolutePath().normalize();
        }

        // Only instantiate VglCli if needed for config (after clone or if no targetDir)
        VglCli vgl = null;
        if (targetDir == null) {
            vgl = new VglCli();
            targetDir = vgl.getLocalDir();
            dir = Paths.get(targetDir).toAbsolutePath().normalize();
        }

        // If flags present but no values, use config (if available)
        if (hasRrFlag && remoteUrl == null) {
            if (vgl == null) vgl = new VglCli();
            remoteUrl = vgl.getRemoteUrl();
        }
        if (hasRbFlag && remoteBranch == null) {
            if (vgl == null) vgl = new VglCli();
            remoteBranch = vgl.getRemoteBranch();
        }

        // Validate remote URL
        if (remoteUrl == null || remoteUrl.isBlank()) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_NO_REMOTE_URL);
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_REMOTE_URL_HELP);
            return 1;
        }
        // If directory exists and is not empty, check for repo and warn/abort as before
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            // Only check for repo if directory is not empty
            RepoResolution repoRes = RepoResolver.resolveForCommand(dir);
            if (repoRes.getKind() == RepoResolution.ResolutionKind.FOUND_BOTH || repoRes.getKind() == RepoResolution.ResolutionKind.FOUND_GIT_ONLY) {
                try (Git existingGit = repoRes.getGit()) {
                    org.eclipse.jgit.api.Status status = existingGit.status().call();
                    boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() ||
                                        !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                        !status.getMissing().isEmpty();
                    if (hasChanges) {
                        System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_UNCOMMITTED_CHANGES + dir);
                        System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_OVERWRITE_CHANGES);
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
                            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_CHECKOUT_CANCELLED);
                            return 0;
                        }
                    } else {
                        System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_DIR_EXISTS + dir);
                        System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_USE_LOCAL);
                        return 1;
                    }
                }
            } else {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_DIR_NOT_EMPTY + dir);
                return 1;
            }
        }
        // If directory does not exist or is empty, skip all repo resolution and proceed to clone

        // Always check for parent repo (nested repo) before proceeding (common logic)
        if (!RepoUtils.checkAndWarnIfNestedRepo(dir, force)) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_CHECKOUT_CANCELLED);
            return 0;
        }

        // If directory does not exist or is empty, allow clone/checkout
        String branch = remoteBranch != null ? remoteBranch : "main";
        try {
            // Check if the remote branch exists before cloning
            boolean branchExists = false;
            try {
                java.util.Collection<Ref> refs = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(remoteUrl)
                    .call();
                for (Ref ref : refs) {
                    if (ref.getName().equals("refs/heads/" + branch)) {
                        branchExists = true;
                        break;
                    }
                }
            } catch (Exception e) {
                // ignore, will fail below
            }
            if (!branchExists) {
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST + branch);
                return 1;
            }
            try {
                Git git = Git.cloneRepository().setURI(remoteUrl).setDirectory(dir.toFile()).setBranch("refs/heads/" + branch).call();
                // Use RepoManager to ensure .vgl and .gitignore are set up consistently
                java.util.Properties vglProps = new java.util.Properties();
                vglProps.setProperty("local.dir", dir.toString());
                vglProps.setProperty("local.branch", branch);
                vglProps.setProperty("remote.url", remoteUrl);
                vglProps.setProperty("remote.branch", branch);
                com.vgl.cli.services.RepoManager.createVglRepo(dir, branch, vglProps);
                git.close();

                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_CHECKOUT_SUCCESS_PREFIX + branch + "'");
                // Print switch state using a new VglCli instance rooted in the new repo
                VglCli vglNew = new VglCli();
                vglNew.setLocalDir(dir.toString());
                vglNew.setLocalBranch(branch);
                vglNew.setRemoteUrl(remoteUrl);
                vglNew.setRemoteBranch(branch);
                RepoUtils.printSwitchState(vglNew);
                return 0;
            } catch (RefNotFoundException e) {
                // Standard JGit error for missing branch
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST + branch);
                return 1;
            } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
                // Other expected JGit errors (e.g., network, auth, repo not found)
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST + branch);
                return 1;
            }
        } catch (Exception e) {
            // Allow truly exceptional errors to propagate for diagnostics
            throw e;
        }
    }
}
