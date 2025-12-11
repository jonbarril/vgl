package com.vgl.cli.commands;

import com.vgl.cli.RepoResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;

import java.nio.file.Paths;
import java.util.List;

public class PushCommand implements Command {
    @Override public String name(){ return "push"; }

    @Override public int run(List<String> args) throws Exception {
        boolean noop = args.contains("-noop");
        com.vgl.cli.RepoResolution repoRes = RepoResolver.resolveForCommand();
        if (repoRes.getGit() == null) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            String branch = git.getRepository().getBranch();
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
            if (remoteUrl == null) {
                System.err.println("No remote connected.");
                return 1;
            }
            if (noop) {
                System.out.println("(dry run) would push local branch '" + branch + "' to remote: " + remoteUrl);
                return 0;
            }
            git.push().setRemote("origin").setRefSpecs(new RefSpec(branch+":"+branch)).setForce(false).call();
            System.out.println("Pushed local branch '" + branch + "' to remote: " + remoteUrl);
        }
        return 0;
    }
}
