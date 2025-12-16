package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import java.util.List;

public class CreateCommand implements Command {
    @Override public String name() { return "create"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        
        // Parse new flags
        String newLocalRepo = Args.getFlag(args, "-lr");
        String newLocalBranch = Args.getFlag(args, "-lb");
        String newRemoteUrl = Args.getFlag(args, "-rr");
        String newRemoteBranch = Args.getFlag(args, "-rb");
        boolean createBothBranches = Args.hasFlag(args, "-bb");
        boolean force = Args.hasFlag(args, "-f");
        
        // Check for TBD feature: create with remote
        if (newRemoteUrl != null || newRemoteBranch != null) {
            System.out.println("Warning: create [-rr URL][-rb BRANCH] is not yet implemented.");
            System.out.println("Use 'vgl checkout -rr URL -rb BRANCH' to clone from remote instead.");
            return 1;
        }
        
        // Handle -bb flag
        if (createBothBranches) {
            String branchName = Args.getFlag(args, "-bb");
            if (branchName != null) newLocalBranch = branchName;
        }
        
        // Use defaults from VGL config
        String path = newLocalRepo != null ? newLocalRepo : vgl.getLocalDir();
        String branch = newLocalBranch != null ? newLocalBranch : vgl.getLocalBranch();
        
        // Fallback to current working directory and "main" branch if not set
        if (path == null || path.isBlank()) path = ".";
        if (branch == null || branch.isBlank()) branch = "main";
        
        final String finalBranch = branch;
        final boolean pushToRemote = createBothBranches;
        
        // Determine if branch was explicitly specified
        boolean branchSpecified = newLocalBranch != null || createBothBranches;

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);

        // Check for nested repository and get confirmation. If the target
        // directory is inside an existing git repository (but is not itself
        // the repo root), prompt to confirm creating a nested repository.
        Path parentRepoRoot = Utils.getGitRepoRoot(dir);
        boolean nested = (parentRepoRoot != null && !parentRepoRoot.equals(dir));
        if (nested) {
            if (!force) {
                if (!Utils.warnNestedRepo(dir, parentRepoRoot)) {
                    System.out.println("Create cancelled.");
                    return 0;
                }
            }
        }

        // Case 1: No .git exists - create new repository (use RepoManager)
        if (!Files.exists(dir.resolve(".git"))) {
            java.util.Properties vglProps = new java.util.Properties();
            vglProps.setProperty("local.dir", dir.toString());
            vglProps.setProperty("local.branch", finalBranch);
            try (@SuppressWarnings("unused") Git git = com.vgl.cli.RepoManager.createVglRepo(dir, finalBranch, vglProps)) {
                System.out.println("Created new local repository: " + dir);
                System.out.println("Created new local branch: " + finalBranch);
            }
        }
        // Case 2: .git exists and -b specified - create new branch
        else if (branchSpecified) {
              try (Git git = Git.open(dir.toFile())) {
                boolean branchExists = git.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalBranch));
                
                if (!branchExists) {
                    git.branchCreate().setName(finalBranch).call();
                    System.out.println("Created new local branch: " + finalBranch);
                } else {
                    System.out.println("Warning: Local branch '" + finalBranch + "' already exists.");
                    return 0;
                }
                
                // Check for unpushed commits
                String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
                if (remoteUrl != null) {
                    try {
                        org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                        org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + git.getRepository().getBranch());
                        
                        if (localHead != null && remoteHead != null && !localHead.equals(localHead)) {
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
                
                if (hasChanges) {
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
                        System.out.println("Branch creation cancelled.");
                        return 0;
                    }
                }
                
                // Checkout the new branch
                git.checkout().setName(finalBranch).call();
                
                // If -bb flag, push to remote
                if (pushToRemote) {
                    String remoteUrlForPush = vgl.getRemoteUrl();
                    if (remoteUrlForPush == null || remoteUrlForPush.isBlank()) {
                        System.out.println("Warning: No remote configured. Cannot create remote branch.");
                        System.out.println("Use 'vgl switch -rr URL' to configure a remote first.");
                    } else {
                        git.push()
                            .setRemote("origin")
                            .add(finalBranch)
                            .call();
                        System.out.println("Pushed branch '" + finalBranch + "' to remote branch '");
                    }
                }
            }
        }
        // Case 3: .git exists but no -lb or -bb specified
        else {
            // Creating a new VGL configuration inside an existing git repository without explicit branch is an anti-pattern.
            // Error and do not create/overwrite .vgl
            System.err.println("Error: Repository already exists");
            return 1;
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

        // Set the new repo/branch as current
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(finalBranch);
        vgl.save();

        // Print switch state feedback
        System.out.println("Created.");
        Utils.printSwitchState(vgl);

        return 0;
    }
}
