package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;

import java.nio.file.*;
import java.util.List;

public class CreateCommand implements Command {
    private static Path lastCreatedDir = null;

    @Override public String name(){ return "create"; }

    @Override public int run(List<String> args) throws Exception {
        String path = ".";
        String branch = "main";

        if (!args.isEmpty()) {
            path = args.get(0);
            if (args.contains("-b")) {
                int index = args.indexOf("-b");
                if (index + 1 < args.size()) {
                    branch = args.get(index + 1);
                }
            }
        } else if (lastCreatedDir != null) {
            path = lastCreatedDir.toString();
        }

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            boolean branchCreated = false;
            if (branchExists(git, branch)) {
                System.out.println("Switched to existing local repository: " + dir + " on branch '" + branch + "'.");
            } else {
                git.branchCreate().setName(branch).call();
                branchCreated = true;
                System.out.println("Created new branch '" + branch + "' in local repository: " + dir + ".");
            }
            git.checkout().setName(branch).call();
            lastCreatedDir = dir; // Update the last created directory
            if (!branchCreated) {
                System.out.println("Switched to local repository: " + dir + " on branch '" + branch + "'.");
            }
        }
        return 0;
    }

    private boolean branchExists(Git git, String branch) throws Exception {
        return git.branchList().call().stream().anyMatch(ref -> ref.getName().endsWith(branch));
    }

    @Override
    public String toString() {
        return name();
    }
}
