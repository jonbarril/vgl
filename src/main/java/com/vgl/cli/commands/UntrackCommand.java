package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.nio.file.Paths;
import java.util.List;

public class UntrackCommand implements Command {
    @Override public String name(){ return "untrack"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl untrack <glob...>");
            return 1;
        }

        VglCli vgl = new VglCli();
        String localDir = vgl.getLocalDir();

        try (Git git = Git.open(Paths.get(localDir).toFile())) {
            var rmc = git.rm().setCached(true);
            for (String p : Utils.expandGlobs(args)) {
                rmc.addFilepattern(p);
            }
            try {
                rmc.call();
                System.out.println("Untracked: " + String.join(" ", args));
            } catch (NoFilepatternException ex) {
                System.out.println("No matching files to untrack.");
            }
        }
        return 0;
    }
}
