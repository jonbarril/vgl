package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;

import java.nio.file.Paths;
import java.util.List;

public class PushCommand implements Command {
    @Override public String name(){ return "push"; }

    @Override public int run(List<String> args) throws Exception {
        boolean dr = args.contains("-dr");
        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("Warning: No local repository found in: " + 
                    Paths.get(".").toAbsolutePath().normalize());
                return 1;
            }
            String branch = git.getRepository().getBranch();
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
            if (remoteUrl == null) { System.out.println("No remote connected."); return 1; }
            if (dr) { System.out.println("(dry run) would push local branch '" + branch + "' to remote: " + remoteUrl); return 0; }
            git.push().setRemote("origin").setRefSpecs(new RefSpec(branch+":"+branch)).setForce(false).call();
            System.out.println("Pushed local branch '" + branch + "' to remote: " + remoteUrl);
        }
        return 0;
    }
}
