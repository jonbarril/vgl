package com.vgl.cli.commands;

import com.vgl.cli.Vgl;
import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import java.util.List;

public class LocalCommand implements Command {
    @Override
    public String name() {
        return "local";
    }

    @Override
    public int run(List<String> args) throws Exception {
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
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Warning: No Git repository found in: " + dir);
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            vgl.setLocalDir(dir.toString());
            vgl.setLocalBranch(branch);
            System.out.println("Switched to local repository: " + dir + " on branch '" + branch + "'.");
        }
        return 0;
    }
}
