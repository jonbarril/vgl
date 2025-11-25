package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;

import java.util.List;

public class PullCommand implements Command {
    @Override public String name(){ return "pull"; }

    @Override public int run(List<String> args) throws Exception {
        boolean dr = args.contains("-dr");
        if (dr) { System.out.println("(dry run) would pull from remote"); return 0; }
        try (Git git = Utils.openGit()) {
            if (git == null) return 1;
            PullResult r = git.pull().call();
            if (r.isSuccessful()) System.out.println("Pulled and merged.");
            else System.out.println("Pull had conflicts or failed.");
        }
        return 0;
    }
}
