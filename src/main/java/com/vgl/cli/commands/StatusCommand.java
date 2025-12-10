package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
// VglRepo is referenced via RepoResolution; avoid direct import here.
import com.vgl.cli.Utils;
import com.vgl.cli.RepoResolver;
import com.vgl.refactor.DefaultVglRepoCore;
import com.vgl.refactor.DefaultStatusService;
import com.vgl.refactor.StatusModel;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.nio.file.Paths;
import java.util.ArrayList;
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
        VglCli vgl = new VglCli();
        // If there is neither a Git repository nor a .vgl configuration in any
        // parent of the current working directory, treat this as the simple
        // "no repo here" case and print only a single concise guidance line.
        java.nio.file.Path cwdCheck = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        // Use the central resolver which returns a rich RepoResolution describing
        // whether a git and/or .vgl were found (and whether one was created).
        com.vgl.cli.RepoResolution resolution = null;
        try {
            resolution = RepoResolver.resolveForCommand(cwdCheck);
        } catch (Exception e) {
            System.err.println("No VGL repository found in this directory or any parent. Run 'vgl create <path>' to make one.");
            return 1;
        }

        // If the resolver did not find both a VglRepo and a Git repository, then
        // we must not print partial status. Follow the documented flows:
        // - FOUND_BOTH or CREATED_VGL: proceed and show full status
        // - FOUND_VGL_ONLY: non-interactive -> hint and exit; interactive flows
        //   may create a git repo, but status should remain passive
        // - FOUND_GIT_ONLY: non-interactive -> hint and exit
        if (resolution.getKind() != com.vgl.cli.RepoResolution.ResolutionKind.FOUND_BOTH
                && resolution.getKind() != com.vgl.cli.RepoResolution.ResolutionKind.CREATED_VGL) {
            // If the resolver provided an explanatory message (e.g. corrupted .vgl state), show it.
            try { if (resolution.getMessage() != null && !resolution.getMessage().isBlank()) System.err.println(resolution.getMessage()); } catch (Exception ignore) {}
            System.err.println("No VGL repository found in this directory or any parent. Run 'vgl create <path>' to make one.");
            // Close any resources on the resolution and exit non-zero
            try { if (resolution.getVglRepo() != null) resolution.getVglRepo().close(); } catch (Exception ignore) {}
            try { if (resolution.getGit() != null) resolution.getGit().close(); } catch (Exception ignore) {}
            return 1;
        }

        // We have both .vgl and a git repo (or a .vgl created during interactive flow)
        // Load configuration from the resolved VglRepo into the VglCli instance so
        // downstream behavior is consistent and deterministic.
        try {
            com.vgl.cli.VglRepo vglRepo = resolution.getVglRepo();
            if (vglRepo != null) {
                java.util.Properties cfg = vglRepo.getConfig();
                String ld = cfg.getProperty("local.dir", null);
                if (ld != null && !ld.isBlank()) vgl.setLocalDir(ld);
                String lb = cfg.getProperty("local.branch", null);
                if (lb != null && !lb.isBlank()) vgl.setLocalBranch(lb);
                String ru = cfg.getProperty("remote.url", null);
                if (ru != null && !ru.isBlank()) vgl.setRemoteUrl(ru);
                String rb = cfg.getProperty("remote.branch", null);
                if (rb != null && !rb.isBlank()) vgl.setRemoteBranch(rb);
            }
        } catch (Exception ignored) {}
        // Keep resolver resources open for the status computation below; we'll
        // close the Git instance when we're done. Do not close them early because
        // we must use the resolved Git to reliably compute status (avoids races
        // where vgl.getLocalDir points at a non-existent repo).
        // If resolution indicated a valid repository (FOUND_BOTH / CREATED_VGL)
        // we should treat that repository as active for status reporting. This
        // ensures status prints the full set of sections (LOCAL/REMOTE/COMMITS/FILES)
        // whenever a Vgl repo was found by the resolver.
        // Prefer configured values from VglCli; fall back to sensible defaults.
        String localBranch = vgl.getLocalBranch() != null ? vgl.getLocalBranch() : "main";
        String remoteUrl = vgl.getRemoteUrl() != null ? vgl.getRemoteUrl() : "";
        String remoteBranch = vgl.getRemoteBranch() != null ? vgl.getRemoteBranch() : "main";

        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        List<String> filters = new ArrayList<>();
        for (String arg : args) {
            if (!arg.equals("-v") && !arg.equals("-vv")) filters.add(arg);
        }

        // Use the resolved Git instance and VglRepo from the resolver to compute status.
        Git git = resolution.getGit();
        com.vgl.cli.VglRepo repo = resolution.getVglRepo();
        if (git == null) {
            System.err.println("No VGL repository found in this directory or any parent. Run 'vgl create <path>' to make one.");
            return 1;
        }

        try (Git g = git) {
            Status status = g.status().call();
            boolean unbornRepo = !Utils.hasCommits(g.getRepository());

            // Print commits section
            if (unbornRepo) {
                System.out.println("COMMITS  (no commits yet)");
            } else {
                StatusSyncState.printSyncState(g, remoteUrl, remoteBranch, localBranch, verbose, veryVerbose);
            }

            // Compute file lists
            Set<String> tracked = new LinkedHashSet<>();
            Set<String> untracked = new LinkedHashSet<>();
            Set<String> undecided = new LinkedHashSet<>();
            Set<String> nested = new LinkedHashSet<>();

            if (status != null) {
                try { untracked.addAll(status.getUntracked()); } catch (Exception ignore) {}
            }

            try {
                java.nio.file.Path repoRoot = g.getRepository().getWorkTree().toPath();
                java.util.Set<String> nonIgnored = Utils.listNonIgnoredFiles(repoRoot, g.getRepository());
                if (nonIgnored != null) tracked.addAll(nonIgnored);
            } catch (Exception ignore) {}

            if (status != null) {
                try { tracked.addAll(status.getChanged()); tracked.addAll(status.getModified()); } catch (Exception ignore) {}
            }

            try { java.util.Set<String> found = Utils.listNestedRepos(Paths.get(vgl.getLocalDir())); if (found != null) nested.addAll(found); } catch (Exception ignore) {}

            // Compute undecided using the resolved repo (only if .vgl exists)
            try {
                if (repo != null) {
                    java.nio.file.Path vglFile = repo.getRepoRoot().resolve(".vgl");
                    if (java.nio.file.Files.exists(vglFile)) {
                        try {
                            DefaultVglRepoCore core = new DefaultVglRepoCore();
                            java.util.Set<String> computed = core.computeUndecidedFiles(g, status);
                            if (computed != null) undecided.addAll(computed);
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception ignore) {}

            untracked.removeAll(undecided);
            if (!nested.isEmpty()) { untracked.removeAll(nested); undecided.removeAll(nested); }

            Set<String> ignored = new LinkedHashSet<>();
            if (status != null) {
                try { ignored.addAll(status.getIgnoredNotInIndex()); } catch (Exception ignore) {}
            }
            try { java.nio.file.Path repoRoot = g.getRepository().getWorkTree().toPath(); if (Utils.isGitIgnored(repoRoot.resolve(".vgl"), g.getRepository())) ignored.add(".vgl"); } catch (Exception ignore) {}
            if (!nested.isEmpty()) ignored.addAll(nested);

            try { if (!ignored.isEmpty()) { untracked.removeAll(ignored); undecided.removeAll(ignored); tracked.removeAll(ignored); } } catch (Exception ignore) {}
            try { if (!untracked.isEmpty()) tracked.removeAll(untracked); if (!undecided.isEmpty()) tracked.removeAll(undecided); } catch (Exception ignore) {}
            try { tracked.remove(".vgl"); } catch (Exception ignore) {}

            // Use DefaultStatusService to compute structured model; fall back if it fails
            try {
                DefaultStatusService svc = new DefaultStatusService();
                StatusModel model = svc.computeStatus(g, filters, verbose, veryVerbose);
                StatusFileSummary.printFileSummary(model.modified, model.added, model.removed, model.renamed, 0, undecided, tracked, untracked, ignored);
            } catch (Exception ex) {
                com.vgl.cli.commands.status.StatusFileCounts counts = com.vgl.cli.commands.status.StatusFileCounts.fromStatus(status);
                StatusFileSummary.printFileSummary(counts.modified, counts.added, counts.removed, 0, 0, undecided, tracked, untracked, ignored);
            }

            if (verbose || veryVerbose) {
                com.vgl.cli.commands.status.StatusSyncFiles.printSyncFiles(g, status, remoteUrl, remoteBranch, filters, verbose, veryVerbose, vgl);
            }
            if (veryVerbose) {
                StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, vgl.getLocalDir(), filters);
            }

        } catch (Exception e) {
            System.err.println("No VGL repository found in this directory or any parent. Run 'vgl create <path>' to make one.");
            return 1;
        }

        return 0;
    }
}

