package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.util.List;

public class RemoteCommand implements Command {
    private static String lastRemoteUrl = null;

    @Override
    public String name() {
        return "remote";
    }

    @Override
    public int run(List<String> args) throws Exception {
        String url = null;
        String branch = "main";

        if (!args.isEmpty()) {
            url = args.get(0);
            if (args.contains("-b")) {
                int index = args.indexOf("-b");
                if (index + 1 < args.size()) {
                    branch = args.get(index + 1);
                }
            }
        } else if (lastRemoteUrl != null) {
            url = lastRemoteUrl;
        } else {
            System.out.println("Warning: No remote URL specified. Use `vgl remote <url>` to set a remote repository.");
            return 1;
        }

        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("No local repository. Use `vgl local` to set a local repository.");
                return 1;
            }

            StoredConfig cfg = git.getRepository().getConfig();
            if (!branchExists(git, branch)) {
                System.out.println("Warning: Branch '" + branch + "' does not exist in the remote repository.");
            }

            cfg.setString("remote", "origin", "url", url);
            cfg.setString("branch", branch, "remote", "origin");
            cfg.setString("branch", branch, "merge", "refs/heads/" + branch);
            cfg.save();

            lastRemoteUrl = url; // Update the last remote URL
            System.out.println("Set remote repository: " + url + " on branch '" + branch + "'.");
        }
        return 0;
    }

    private boolean branchExists(Git git, String branch) throws Exception {
        return git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                .call().stream().anyMatch(ref -> ref.getName().endsWith(branch));
    }
}
