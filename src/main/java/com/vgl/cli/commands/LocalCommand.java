package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalCommand implements Command {
    @Override
    public String name() {
        return "local";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl local [<dir>] [-b <branch>]");
            return 1;
        }

        String path = args.get(0);
        String branch = "main";
        if (args.contains("-b")) {
            int index = args.indexOf("-b");
            if (index + 1 < args.size()) {
                branch = args.get(index + 1);
            }
        }

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.println("Directory does not exist: " + dir);
            return 1;
        }

        try (Git git = Utils.openGit(dir.toFile())) {
            if (git == null) {
                System.out.println("No Git repository found in: " + dir);
                return 1;
            }

            StoredConfig cfg = git.getRepository().getConfig();
            cfg.setString("branch", branch, "remote", "origin");
            cfg.setString("branch", branch, "merge", "refs/heads/" + branch);
            cfg.save();

            System.out.println("Switched to local repository: " + dir + " on branch " + branch);
        }
        return 0;
    }
}
