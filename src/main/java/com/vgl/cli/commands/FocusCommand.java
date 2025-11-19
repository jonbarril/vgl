package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;

import java.nio.file.*;
import java.util.List;

public class FocusCommand implements Command {
    @Override public String name(){ return "focus"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl focus <repo>[@branch] | -b <branch>"); return 1; }
        String repo = args.get(0);
        String branch = null;
        int at = repo.indexOf('@');
        if (at >= 0) { branch = repo.substring(at+1); repo = repo.substring(0, at); }
        if ("-b".equals(repo)) { branch = args.get(1); repo = "."; }

        Path dir = Paths.get(repo).toAbsolutePath().normalize();
        if (!Files.exists(dir)) { System.out.println("Repo path does not exist: " + dir); return 1; }
        try (Git git = Git.open(dir.toFile())) {
            if (branch != null) {
                try { git.checkout().setName(branch).call(); }
                catch (RefNotFoundException e){ System.out.println("Branch does not exist locally: " + branch + ". Create it with: vgl create -b " + branch); return 1; }
            } else {
                branch = git.getRepository().getBranch();
            }
            System.out.println("Focused: " + dir + "@" + branch);
        }
        return 0;
    }
}
