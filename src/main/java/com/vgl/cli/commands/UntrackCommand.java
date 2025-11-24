package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.util.List;

public class UntrackCommand implements Command {
    @Override public String name(){ return "untrack"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl untrack <glob...>");
            return 1;
        }
        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("No Git repository found.");
                return 1;
            }
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
