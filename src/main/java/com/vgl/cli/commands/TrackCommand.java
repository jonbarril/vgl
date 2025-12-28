package com.vgl.cli.commands;

import com.vgl.cli.utils.RepoUtils;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.List;

public class TrackCommand implements Command {
    @Override public String name(){ return "track"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_TRACK_USAGE);
            return 1;
        }

        Path startDir = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        boolean interactive = true;
        com.vgl.cli.services.RepoResolution res = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(startDir, interactive);
        if (res.getVglRepo() == null) {
            String warn = com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_RESOLVED;
            System.err.println(warn);
            return 1;
        }
        com.vgl.cli.services.VglRepo vglRepo = res.getVglRepo();
        Path dir = vglRepo.getRepoRoot();

        boolean useAll = args.size() == 1 && args.get(0).equals("-all");
        List<String> filesToTrack;
        if (useAll) {
            filesToTrack = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
            try {
                java.util.Set<String> nested = RepoUtils.listNestedRepos(dir);
                if (nested != null && !nested.isEmpty()) {
                    java.util.List<String> filtered = new java.util.ArrayList<>();
                    for (String f : filesToTrack) {
                        boolean insideNested = false;
                        for (String n : nested) {
                            if (f.equals(n) || f.startsWith(n + "/")) { insideNested = true; break; }
                        }
                        if (!insideNested) filtered.add(f);
                    }
                    filesToTrack = filtered;
                }
            } catch (Exception ignored) {}
            if (filesToTrack.isEmpty()) {
                System.out.println("No undecided files to track.");
                return 0;
            }
        } else {
            try (Git git = Git.open(dir.toFile())) {
                filesToTrack = RepoUtils.expandGlobsToFiles(args, dir, git.getRepository());
            }
        }

        // Remove .vgl and nested repo paths from filesToTrack
        java.util.List<String> filteredFiles = new java.util.ArrayList<>();
        java.util.List<String> nestedRequests = new java.util.ArrayList<>();
        for (String p : filesToTrack) {
            if (".vgl".equals(p)) continue;
            Path fp = dir.resolve(p).toAbsolutePath().normalize();
            if (RepoUtils.isInsideNestedRepo(dir, fp)) {
                nestedRequests.add(p);
                continue;
            }
            filteredFiles.add(p);
        }
        if (!nestedRequests.isEmpty()) {
            System.out.println("Warning: Ignoring nested repository paths: " + String.join(" ", nestedRequests));
            if (filteredFiles.isEmpty()) {
                System.out.println("No matching files to track after ignoring nested repositories.");
                return 1;
            }
        }
        if (filteredFiles.isEmpty()) {
            System.out.println("No matching files");
            return 1;
        }

        // Only .vgl config determines tracked files; do not infer from Git index or HEAD
        List<String> actuallyTracked = new java.util.ArrayList<>();
        List<String> failed = new java.util.ArrayList<>();
        List<String> tracked = new java.util.ArrayList<>(vglRepo.getTrackedFiles());
        List<String> undecided = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
        List<String> toStage = new java.util.ArrayList<>();
        for (String p : filteredFiles) {
            if (tracked.contains(p)) {
                failed.add(p); // Already tracked
            } else {
                tracked.add(p);
                actuallyTracked.add(p);
                undecided.remove(p);
                toStage.add(p);
            }
        }
        // Stage newly tracked files in the Git index
        if (!toStage.isEmpty()) {
            try (Git git = Git.open(dir.toFile())) {
                for (String f : toStage) {
                    git.add().addFilepattern(f).call();
                }
            } catch (Exception e) {
                System.err.println("Error staging files: " + e.getMessage());
            }
        }
        vglRepo.setTrackedFiles(tracked);
        vglRepo.setUndecidedFiles(undecided);
        vglRepo.saveConfig();

        if (!actuallyTracked.isEmpty()) {
            if (args.size() == 1 && ".".equals(args.get(0))) {
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_TRACK_SUCCESS_PREFIX + ".");
            } else {
                System.out.println(com.vgl.cli.utils.MessageConstants.MSG_TRACK_SUCCESS_PREFIX + String.join(" ", actuallyTracked));
            }
        }
        if (!failed.isEmpty()) {
            for (String file : failed) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_FILE_NOT_TRACKED + file);
            }
        }
        return 0;
    }
}

// ...rest of the TrackCommand class and run() method as previously read...
