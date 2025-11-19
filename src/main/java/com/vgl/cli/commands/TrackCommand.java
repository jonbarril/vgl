package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.util.List;

public class TrackCommand implements Command {
    @Override public String name(){ return "track"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl track <glob...> | vgl untrack <glob...>"); return 1; }
        try (Git git = Utils.openGit()) {
            if (git == null) return 1;
            var addc = git.add();
            for (String p : Utils.expandGlobs(args)) addc.addFilepattern(p);
            try { addc.call(); } catch (NoFilepatternException ex) { /* ignore */ }
            System.out.println("Tracking: " + String.join(" ", args));
        }
        return 0;
    }
}
