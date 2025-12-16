package com.vgl.cli.commands;

// VglRepo is referenced via RepoResolution; avoid direct import here.
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.RepoResolution;
import org.eclipse.jgit.api.Git;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
public class StatusCommand implements Command {
    @Override
    public String name() {
        return "status";
    }

    @Override
    public int run(List<String> args) throws Exception {
        RepoResolution resolution;
        try {
            resolution = RepoResolver.resolveForCommand(java.nio.file.Paths.get("").toAbsolutePath());
        } catch (Exception e) {
            resolution = null;
        }

        // Fail fast for all repo resolution failures; print message from RepoResolution
        if (resolution == null || resolution.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH) {
            String warn = (resolution != null && resolution.getMessage() != null) ? resolution.getMessage() : "WARNING: No VGL repository found in this directory or any parent.\nHint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            return 1;
        }

        // Only print status sections if both .git and .vgl are present and valid in repo root
        // (This block is only reached if all checks above passed)
        // Print LOCAL section
        System.out.println("LOCAL    " + resolution.getVglRepo().getRepoRoot());

        // Print REMOTE section
        String remoteUrl = "";
        String remoteBranch = "";
        java.util.Properties cfg = resolution.getVglRepo().getConfig();
        remoteUrl = cfg.getProperty("remote.url", "");
        remoteBranch = cfg.getProperty("remote.branch", "main");
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            System.out.println("REMOTE   " + remoteUrl + " [" + remoteBranch + "]");
        } else {
            System.out.println("REMOTE   (no remote configured)");
        }

        // Parse verbosity flags
        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        // Print COMMITS section
        Git git = resolution.getGit();
        if (git == null) {
            System.out.println("COMMITS  (no commits yet)");
        } else {
            boolean unbornRepo = !Utils.hasCommits(git.getRepository());
            if (unbornRepo) {
                System.out.println("COMMITS  (no commits yet)");
            } else {
                StatusSyncState.printSyncState(git, remoteUrl, remoteBranch, "main", verbose, veryVerbose);
            }
        }

        // Print FILES section
        Set<String> tracked = new LinkedHashSet<>();
        Set<String> untracked = new LinkedHashSet<>();
        Set<String> undecided = new LinkedHashSet<>();
        Set<String> ignored = new LinkedHashSet<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        Set<String> renamed = new LinkedHashSet<>();
        if (git != null) {
            try {
                org.eclipse.jgit.api.Status status = git.status().call();
                if (status != null) {
                    StatusCommandHelpers.resolveFileCategories(status, git.getRepository(), tracked, untracked, undecided, ignored);
                    StatusCommandHelpers.resolveChangeCategories(status, added, modified, removed, renamed);
                }
            } catch (Exception ignore) {}
        }
        StatusFileSummary.printFileSummary(undecided, tracked, untracked, ignored);

        // Print verbose/very-verbose file and commit subsections if requested
        if (verbose || veryVerbose) {
            // Print sync files (files to commit/merge)
            com.vgl.cli.commands.status.StatusSyncFiles.printSyncFiles(
                git, 
                (git != null) ? safeStatus(git) : null, 
                remoteUrl, 
                remoteBranch, 
                new java.util.ArrayList<>(), 
                verbose, 
                veryVerbose, 
                null
            );
        }
        if (veryVerbose) {
            // Print detailed file lists
            StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, (resolution.getVglRepo() != null ? resolution.getVglRepo().getRepoRoot().toString() : "."), new java.util.ArrayList<>());
        }
        return 0;
    }

    // Helper to safely get status from git
    private static org.eclipse.jgit.api.Status safeStatus(Git git) {
        try {
            return git.status().call();
        } catch (Exception e) {
            return null;
        }
    }
}

