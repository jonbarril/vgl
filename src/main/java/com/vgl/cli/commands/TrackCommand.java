package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TrackCommand implements Command {
    @Override public String name(){ return "track"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl track <glob...>");
            return 1;
        }

        VglCli vgl = new VglCli();
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();

        if (!vgl.isConfigurable()) {
            System.out.println("Warning: No local repository found in: " + dir);
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            var addc = git.add();
            List<String> expanded = Utils.expandGlobs(args);
            for (String p : expanded) {
                addc.addFilepattern(p);
            }
            try {
                addc.call();
                System.out.println("Tracking: " + String.join(" ", args));
            } catch (NoFilepatternException ex) {
                System.out.println("No matching files to track.");
            }
        }
        return 0;
    }
}
