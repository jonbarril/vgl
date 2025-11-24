package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalCommand implements Command {
    private static Path lastLocalDir = null;

    @Override
    public String name() {
        return "local";
    }

    @Override
    public int run(List<String> args) throws Exception {
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
        } else if (lastLocalDir != null) {
            path = lastLocalDir.toString();
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
            if (!Files.exists(dir.resolve(".git"))) {
                System.out.println("Warning: No local repository found in the specified directory.");
            }

            if (!branchExists(git, branch)) {
                System.out.println("Warning: Branch '" + branch + "' does not exist in the local repository.");
            }

            cfg.setString("branch", branch, "remote", "origin");
            cfg.setString("branch", branch, "merge", "refs/heads/" + branch);
            cfg.save();

            lastLocalDir = dir; // Update the last local directory
            System.out.println("Switched to local repository: " + dir + " on branch '" + branch + "'.");
        }
        return 0;
    }

    private boolean branchExists(Git git, String branch) throws Exception {
        return git.branchList().call().stream().anyMatch(ref -> ref.getName().endsWith(branch));
    }
}
