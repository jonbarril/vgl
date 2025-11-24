package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.util.List;

public class RemoteCommand implements Command {
    @Override
    public String name() {
        return "remote";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl remote [<url>] [-b <branch>]");
            return 1;
        }

        String url = args.get(0);
        String branch = "main";
        if (args.contains("-b")) {
            int index = args.indexOf("-b");
            if (index + 1 < args.size()) {
                branch = args.get(index + 1);
            }
        }

        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("No focused repository. Use `vgl local` to set a local repository.");
                return 1;
            }

            StoredConfig cfg = git.getRepository().getConfig();
            cfg.setString("remote", "origin", "url", url);
            cfg.setString("branch", branch, "remote", "origin");
            cfg.setString("branch", branch, "merge", "refs/heads/" + branch);
            cfg.save();

            System.out.println("Set remote repository: " + url + " on branch " + branch);
        }
        return 0;
    }
}
