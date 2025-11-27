package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import java.util.List;

public class CreateCommand implements Command {
    @Override public String name() { return "create"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        String path = vgl.getLocalDir(); // Default to .vgl state
        String branch = vgl.getLocalBranch(); // Default to .vgl state

        // Parse arguments
        int bIndex = args.indexOf("-b");
        if (bIndex != -1 && bIndex + 1 < args.size()) {
            branch = args.get(bIndex + 1);
        }
        
        // Get path from first non-flag argument
        for (String arg : args) {
            if (!arg.equals("-b") && !arg.equals(branch)) {
                path = arg;
                break;
            }
        }

        // Fallback to current working directory and "main" branch if not set
        if (path == null || path.isBlank()) path = ".";
        if (branch == null || branch.isBlank()) branch = "main";
        
        final String finalBranch = branch;

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);

        if (!Files.exists(dir.resolve(".git"))) {
            try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
                System.out.println("Created new local repository: " + dir);
                // Correctly link HEAD to the new branch
                git.getRepository().updateRef("HEAD").link("refs/heads/" + finalBranch);
                System.out.println("Created new branch: " + finalBranch);
            }

            // Create a default .gitignore following common conventions
            Path gi = dir.resolve(".gitignore");
            if (!Files.exists(gi)) {
                String content = String.join("\n",
                    "# VGL configuration",
                    ".vgl",
                    "# Compiled class files",
                    "*.class",
                    "# Log files",
                    "*.log",
                    "# Build directories",
                    "/build/",
                    "/out/",
                    "# IDE files",
                    ".idea/",
                    ".vscode/",
                    "# Gradle",
                    ".gradle/",
                    "# Mac files",
                    ".DS_Store"
                );
                Files.writeString(gi, content);
            }
        } else {
            // If -b was specified, create the branch if it doesn't exist
            if (bIndex != -1) {
                try (Git git = Git.open(dir.toFile())) {
                    boolean branchExists = git.branchList().call().stream()
                        .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalBranch));
                    
                    if (!branchExists) {
                        git.branchCreate().setName(finalBranch).call();
                        System.out.println("Created new local branch: " + finalBranch);
                    } else {
                        System.out.println("Local branch '" + finalBranch + "' already exists");
                    }
                    
                    // Check for unpushed commits
                    String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
                    if (remoteUrl != null) {
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
                            System.out.println("Branch switch cancelled.");
                            return 0;
                        }
                    }
                    
                    // Checkout the branch
                    git.checkout().setName(finalBranch).call();
                }
            }
        }

        // Perform the local command to set the new repo/branch
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(finalBranch);
        vgl.save();

        return 0;
    }
}
