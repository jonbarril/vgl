package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RemoteCommand implements Command {
    @Override
    public String name() {
        return "remote";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        String url = vgl.getRemoteUrl(); // Default to .vgl state
        String branch = vgl.getRemoteBranch(); // Default to .vgl state

        // Parse arguments
        int bIndex = args.indexOf("-b");
        if (bIndex != -1 && bIndex + 1 < args.size()) {
            branch = args.get(bIndex + 1);
        }
        
        // Get URL from first non-flag argument
        for (String arg : args) {
            if (!arg.equals("-b") && !arg.equals(branch)) {
                url = arg;
                break;
            }
        }

        // Fallback to "main" branch if not set
        if (branch == null || branch.isBlank()) branch = "main";

        Path dir = Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize();
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Warning: No Git repository found in: " + dir);
            return 1;
        }

        @SuppressWarnings("resource")
        Git git = Git.open(dir.toFile());
        git.close();
        System.out.println("Set remote repository: " + url + " on branch '" + branch + "'.");
        return 0;
    }
}
