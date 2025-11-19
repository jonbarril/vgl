package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.nio.file.*;

import java.util.List;

public class CreateCommand implements Command {
    @Override public String name(){ return "create"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl create <path>[@branch] | vgl create -b <branch>"); return 1; }

        String first = args.get(0);
        if (first.equals("-b") || first.startsWith("@")) {
            String branch = first.equals("-b") ? (args.size() >= 2 ? args.get(1) : "main") : first.substring(1);
            try (Git git = Git.open(Paths.get(".").toFile())) {
                try {
                    git.branchCreate().setName(branch).call();
                    git.checkout().setName(branch).call();
                    System.out.println("Created branch " + branch + ".");
                } catch (RefAlreadyExistsException ex) {
                    git.checkout().setName(branch).call();
                    System.out.println("Branch already exists; switched to " + branch + ".");
                }
            }
            return 0;
        }

        String path = first;
        String branch = "main";
        int at = first.indexOf('@');
        if (at >= 0) { path = first.substring(0, at); branch = first.substring(at+1); }
        Path dir = Paths.get(path).toAbsolutePath().normalize();
        // warn if nested
        Path cur = Paths.get(".").toAbsolutePath().normalize();
        if (cur.startsWith(dir) || dir.startsWith(cur)) {
            // warn
        }
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
