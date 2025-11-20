package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;

import java.nio.file.*;
import java.util.List;

public class CreateCommand implements Command {
    @Override public String name(){ return "create"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl create <path> -b <branch>"); return 1; }

        String path = args.get(0);
        String branch = "main";
        if (args.contains("-b")) {
            int index = args.indexOf("-b");
            if (index + 1 < args.size()) {
                branch = args.get(index + 1);
            }
        }

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir)) Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            try {
                git.branchCreate().setName(branch).call();
                git.checkout().setName(branch).call();
                System.out.println("Created branch " + branch + ".");
            } catch (RefAlreadyExistsException ex) {
                git.checkout().setName(branch).call();
                System.out.println("Branch already exists; switched to " + branch + ".");
            }
            System.out.println("Created repo: " + dir + " on branch " + branch);
        }
        return 0;
    }
}
