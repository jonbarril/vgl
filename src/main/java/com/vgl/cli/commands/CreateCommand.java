package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.VglCli;
import com.vgl.cli.services.RepoResolution;

import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import com.vgl.cli.utils.NestedRepoDetector;
import java.util.List;

public class CreateCommand implements Command {
    @Override public String name() { return "create"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        // Parse flags
        String newLocalRepo = Args.getFlag(args, "-lr");
        String newLocalBranch = Args.getFlag(args, "-lb");
        String newRemoteUrl = Args.getFlag(args, "-rr");
        String newRemoteBranch = Args.getFlag(args, "-rb");
        boolean createBothBranches = Args.hasFlag(args, "-bb");
        boolean force = Args.hasFlag(args, "-f");

        if (newRemoteUrl != null || newRemoteBranch != null) {
            System.out.println("Warning: create [-rr URL][-rb BRANCH] is not yet implemented.");
            System.out.println("Use 'vgl checkout -rr URL -rb BRANCH' to clone from remote instead.");
            return 1;
        }

        if (createBothBranches) {
            String branchName = Args.getFlag(args, "-bb");
            if (branchName != null) newLocalBranch = branchName;
        }

        String path = newLocalRepo;
        if (path == null || path.isBlank()) {
            // Only use vgl.getLocalDir() if config exists, else use cwd
            String configPath = System.getProperty("user.dir");
            try {
                String candidate = vgl.getLocalDir();
                if (candidate != null && !candidate.isBlank()) {
                    path = candidate;
                } else {
                    path = configPath;
                }
            } catch (Exception e) {
                path = configPath;
            }
        }
        String branch = newLocalBranch != null ? newLocalBranch : vgl.getLocalBranch();
        if (branch == null || branch.isBlank()) branch = "main";
        final String finalBranch = branch;
        final boolean pushToRemote = createBothBranches;
        boolean branchSpecified = newLocalBranch != null || createBothBranches;

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);

        // --- NEW LOGIC: Use target-relative repo resolution and ancestor detection ---
        // 1. If an ancestor repo (git or vgl) exists, warn and prompt user to continue (but only if the ancestor is not the target itself)
        Path ancestorRepo = NestedRepoDetector.findAncestorRepo(dir);
        boolean nestedOk = true;

        // 2. If a valid VGL repo exists in the target, allow branch creation if requested, else quit (no-op)
        RepoResolution res = com.vgl.cli.utils.RepoResolver.resolveForCommand(dir);
        boolean vglRepoExists = res.getKind() == com.vgl.cli.services.RepoResolution.ResolutionKind.FOUND_BOTH &&
            res.getRepoRoot() != null && res.getRepoRoot().equals(dir);
        if (ancestorRepo != null) {
            nestedOk = Utils.warnNestedRepo(dir, ancestorRepo, force);
            if (!nestedOk) {
                System.out.println("Create cancelled. No repository created.");
                return 0;
            }
        }
        if (vglRepoExists && !branchSpecified) {
            System.out.println("VGL repository already exists at: " + dir);
            Utils.printSwitchState(vgl);
            return 0;
        }

        // 3. If no .git exists, create new repo (use RepoManager)
        if (!Files.exists(dir.resolve(".git"))) {
            java.util.Properties vglProps = new java.util.Properties();
            vglProps.setProperty("local.dir", dir.toString());
            vglProps.setProperty("local.branch", finalBranch);
            try (@SuppressWarnings("unused") Git git = com.vgl.cli.services.RepoManager.createVglRepo(dir, finalBranch, vglProps)) {
                System.out.println("Created new local repository at: " + dir);
                System.out.println("Created and switched to branch: " + finalBranch);
            } catch (Exception e) {
                System.err.println("Error: Failed to create repository at " + dir + ": " + e.getMessage());
                return 1;
            }
        } else {
            // .git exists (repo exists): handle branch creation or error
            if (branchSpecified) {
                try (Git git = Git.open(dir.toFile())) {
                    // Ensure at least one commit exists (HEAD is not unborn)
                    org.eclipse.jgit.lib.ObjectId headId = null;
                    try { headId = git.getRepository().resolve("HEAD"); } catch (Exception ignored) {}
                    if (headId == null) {
                        // Create an empty commit so branch creation works
                        git.commit().setMessage("initial (autocreated)").setAllowEmpty(true).call();
                    }
                    boolean branchExists = git.branchList().call().stream()
                            .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalBranch));
                    if (!branchExists) {
                        git.branchCreate().setName(finalBranch).call();
                        System.out.println("Created new local branch: " + finalBranch);
                    } else {
                        System.out.println("Using existing local branch: " + finalBranch);
                    }
                    git.checkout().setName(finalBranch).call();
                    System.out.println("Switched to branch: " + finalBranch);
                    if (pushToRemote) {
                        String remoteUrlForPush = vgl.getRemoteUrl();
                        if (remoteUrlForPush == null || remoteUrlForPush.isBlank()) {
                            System.out.println("Warning: No remote configured. Cannot create remote branch.");
                            System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                        } else {
                            git.push().setRemote("origin").add(finalBranch).call();
                            System.out.println("Pushed branch '" + finalBranch + "' to remote.");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error: Failed to create/switch branch '" + finalBranch + "' in repo at " + dir + ": " + e.getMessage());
                    return 1;
                }
            } else {
                System.err.println("Error: Repository already exists at " + dir + ". Use -lb to create/switch branch.");
                return 1;
            }
        }

        // Save current state as jump state before creating/switching
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

        Utils.printSwitchState(vgl);
        return 0;
    }
}
