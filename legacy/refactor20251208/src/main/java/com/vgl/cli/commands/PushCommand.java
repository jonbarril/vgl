package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import java.util.List;

public class PushCommand implements Command {
    @Override public String name(){ return "push"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PUSH_USAGE);
            return 0;
        }
        boolean noop = args.contains("-noop");
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = true;
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_PUSH_NO_REPO);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            String branch = git.getRepository().getBranch();
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
            if (remoteUrl == null) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_PUSH_NO_REMOTE);
                return 1;
            }
            if (noop) {
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PUSH_DRY_RUN);
                return 0;
            }
            git.push().setRemote("origin").setRefSpecs(new RefSpec(branch+":"+branch)).setForce(false).call();
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_PUSH_SUCCESS);
        }
        return 0;
    }
}
