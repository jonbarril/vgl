package com.vgl.cli.commands;

import com.vgl.cli.Vgl;
import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import java.util.List;

public class CreateCommand implements Command {
    @Override public String name() { return "create"; }

    @Override public int run(List<String> args) throws Exception {
        Vgl vgl = new Vgl();
        String path = vgl.getLocalDir(); // Default to .vgl state
        String branch = vgl.getLocalBranch(); // Default to .vgl state

        if (!args.isEmpty()) {
            path = args.get(0);
            if (args.contains("-b")) {
                int index = args.indexOf("-b");
                if (index + 1 < args.size()) {
                    branch = args.get(index + 1);
                }
            }
        }

        // Fallback to current working directory and "main" branch if not set
        if (path == null || path.isBlank()) path = ".";
        if (branch == null || branch.isBlank()) branch = "main";

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);

        if (!Files.exists(dir.resolve(".git"))) {
            try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
                System.out.println("Created new local repository: " + dir);
                // Correctly link HEAD to the new branch
                git.getRepository().updateRef("HEAD").link("refs/heads/" + branch);
                System.out.println("Created new branch: " + branch);
            }
        } else {
            System.out.println("Git repository already exists in: " + dir);
        }

        // Perform the local command to set the new repo/branch
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(branch);

        return 0;
    }
}
