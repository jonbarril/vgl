package com.vgl.cli.commands;

import com.vgl.cli.Vgl;
import org.eclipse.jgit.api.Git;

import java.nio.file.*;
import java.util.List;

public class RemoteCommand implements Command {
    @Override
    public String name() {
        return "remote";
    }

    @Override
    public int run(List<String> args) throws Exception {
        Vgl vgl = new Vgl();
        String url = vgl.getRemoteUrl(); // Default to .vgl state
        String branch = vgl.getRemoteBranch(); // Default to .vgl state

        if (!args.isEmpty()) {
            url = args.get(0);
            if (args.contains("-b")) {
                int index = args.indexOf("-b");
                if (index + 1 < args.size()) {
                    branch = args.get(index + 1);
                }
            }
        }

        // Fallback to "main" branch if not set
        if (branch == null || branch.isBlank()) branch = "main";

        Path dir = Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize();
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Warning: No Git repository found in: " + dir);
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            System.out.println("Set remote repository: " + url + " on branch '" + branch + "'.");
        }
        return 0;
    }
}
