package com.vgl.cli.commands;

import com.vgl.cli.Args;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import java.util.List;

public class PullCommand implements Command {
    @Override public String name(){ return "pull"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_USAGE);
            return 0;
        }
        boolean force = Args.hasFlag(args, "-f");
        boolean dr = args.contains("-noop");
        if (dr) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_DRY_RUN);
            return 0;
        }
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = true;
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_RESOLVED);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() ||
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                !status.getMissing().isEmpty();
            if (hasChanges) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_UNCOMMITTED_WARNING);
                System.err.println("Pulling may cause conflicts or data loss:");
                status.getModified().forEach(f -> System.err.println("  M " + f));
                status.getChanged().forEach(f -> System.err.println("  M " + f));
                status.getAdded().forEach(f -> System.err.println("  A " + f));
                status.getRemoved().forEach(f -> System.err.println("  D " + f));
                status.getMissing().forEach(f -> System.err.println("  D " + f));
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_UNCOMMITTED_WARNING);
                if (!force) {
                    System.err.print("Continue? (y/N): ");
                    String response;
                    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                        response = scanner.nextLine().trim().toLowerCase();
                    }
                    if (!response.equals("y") && !response.equals("yes")) {
                        System.err.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_CANCELLED);
                        return 0;
                    }
                }
            }
            PullResult r = git.pull().call();
            if (r.isSuccessful()) System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_SUCCESS);
            else System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PULL_CONFLICT);
        }
        return 0;
    }
}
