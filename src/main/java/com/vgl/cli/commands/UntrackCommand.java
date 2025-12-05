package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class UntrackCommand implements Command {
    @Override public String name(){ return "untrack"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl untrack <glob...> | -all");
            return 1;
        }

        VglCli vgl = new VglCli();
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();

        if (!vgl.isConfigurable()) {
            System.out.println("Warning: No local repository found in: " + dir);
            return 1;
        }

        // Load undecided files from .vgl if -all is specified
        boolean useAll = args.size() == 1 && args.get(0).equals("-all");
        List<String> filesToUntrack;
        com.vgl.cli.VglRepo vglRepo = com.vgl.cli.Utils.findVglRepo(dir);
        if (vglRepo != null) {
            try (Git git = Git.open(dir.toFile())) {
                // Always update undecided files before untrack
                org.eclipse.jgit.api.Status status = git.status().call();
                vglRepo.updateUndecidedFilesFromWorkingTree(git, status);
            }
        }
        if (useAll) {
            if (vglRepo == null) {
                System.out.println("No .vgl repo found for undecided files.");
                return 1;
            }
            filesToUntrack = vglRepo.getUndecidedFiles();
            if (filesToUntrack.isEmpty()) {
                System.out.println("No undecided files to untrack.");
                return 0;
            }
        } else {
            filesToUntrack = Utils.expandGlobs(args);
        }

        if (filesToUntrack.isEmpty()) {
            System.out.println("No matching files to untrack.");
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            org.eclipse.jgit.lib.Repository repo = git.getRepository();
            List<String> filteredFiles = new java.util.ArrayList<>();
            for (String p : filesToUntrack) {
                Path filePath = dir.resolve(p);
                if (!Utils.isGitIgnored(filePath, repo)) {
                    filteredFiles.add(p);
                }
            }
            if (filteredFiles.isEmpty()) {
                System.out.println("All matching files are ignored by git.");
                return 1;
            }
            var rmc = git.rm().setCached(true);
            for (String p : filteredFiles) {
                rmc.addFilepattern(p);
            }
            try {
                rmc.call();
                System.out.println("Untracked: " + String.join(" ", filteredFiles));
                // Remove untracked files from undecided in .vgl
                if (vglRepo != null) {
                    List<String> undecided = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
                    undecided.removeAll(filteredFiles);
                    vglRepo.setUndecidedFiles(undecided);
                    vglRepo.saveConfig();
                }
            } catch (NoFilepatternException ex) {
                System.out.println("No matching files to untrack.");
            }
        }
        return 0;
    }
}
