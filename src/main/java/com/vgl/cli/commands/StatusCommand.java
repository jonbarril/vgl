package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import com.vgl.cli.VglRepo;
import com.vgl.cli.Utils;
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
        // Treat the configured local repository as active only when the current
        // working directory is inside that configured repository. This prevents
        // `vgl status` from reporting a remote/local repo when run in an
        // unrelated empty directory that happens to have a persisted .vgl
        // pointing elsewhere.
        java.nio.file.Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        boolean hasLocalRepo = false;
        try {
            String cfgLocal = vgl.getLocalDir();
            if (cfgLocal != null && !cfgLocal.isEmpty()) {
                java.nio.file.Path cfgPath = Paths.get(cfgLocal).toAbsolutePath().normalize();
                if (java.nio.file.Files.exists(cfgPath.resolve(".git")) && cwd.startsWith(cfgPath)) {
                    hasLocalRepo = true;
                }
            }
        } catch (Exception ignored) {}
        String localBranch = hasLocalRepo ? vgl.getLocalBranch() : "main";
        String remoteUrl = hasLocalRepo ? (vgl.getRemoteUrl() != null ? vgl.getRemoteUrl() : "") : "";
        String remoteBranch = hasLocalRepo ? vgl.getRemoteBranch() : "main";

        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        List<String> filters = new ArrayList<>();
        for (String arg : args) {
            if (!arg.equals("-v") && !arg.equals("-vv")) filters.add(arg);
        }

        if (!hasLocalRepo) {
            System.out.println("LOCAL  (none) :: (none)");
            System.out.println("       (none) :: (none)");
            System.out.println("REMOTE (none) :: (none)");
            System.out.println("       (none) :: (none)");
            return 0;
        } else {
            Utils.printSwitchState(vgl, verbose, veryVerbose);
        }

        try (Git git = Git.open(Paths.get(vgl.getLocalDir()).toFile())) {
            Status status = null;
            boolean unbornRepo = false;
            try {
                status = git.status().call();
                unbornRepo = !Utils.hasCommits(git.getRepository());
            } catch (Exception e) {
                unbornRepo = !Utils.hasCommits(git.getRepository());
            }

            if (unbornRepo) {
                System.out.println("COMMITS  (no commits yet)");
            } else {
                StatusSyncState.printSyncState(git, remoteUrl, remoteBranch, localBranch, verbose, veryVerbose);
            }

            Set<String> tracked = new LinkedHashSet<>();
            Set<String> untracked = new LinkedHashSet<>();
            Set<String> undecided = new LinkedHashSet<>();
            Set<String> nested = new LinkedHashSet<>();

            if (status != null && !unbornRepo) {
                // Untracked files come directly from JGit status
                untracked.addAll(status.getUntracked());

                // Build the tracked set from the working tree: list all non-ignored files
                // and subtract untracked/undecided/nested later. This ensures committed
                // (clean) tracked files are included in the Tracked Files listing.
                try {
                    java.nio.file.Path repoRoot = Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize();
                    java.util.Set<String> nonIgnored = Utils.listNonIgnoredFiles(repoRoot, git.getRepository());
                    if (nonIgnored != null) tracked.addAll(nonIgnored);
                } catch (Exception ignore) {}

                // Also add status-reported changed/modified entries to be safe
                try {
                    tracked.addAll(status.getChanged());
                    tracked.addAll(status.getModified());
                } catch (Exception ignore) {}
            }

            // Detect nested repositories (directories containing their own .git)
            try {
                java.util.Set<String> found = Utils.listNestedRepos(Paths.get(vgl.getLocalDir()));
                if (found != null) nested.addAll(found);
            } catch (Exception ignore) {}

            try {
                VglRepo repo = Utils.findVglRepo(Paths.get(vgl.getLocalDir()));
                if (repo != null) {
                    // Only update undecided files if a .vgl config file already exists.
                    // Avoid creating a new .vgl during a passive `status` run on an empty
                    // directory (the user shouldn't get a config file unless they ran
                    // `vgl create` or similar).
                        try {
                            java.nio.file.Path vglFile = repo.getRepoRoot().resolve(".vgl");
                            if (java.nio.file.Files.exists(vglFile)) {
                                try {
                                    // Use compute-only API to avoid creating or updating .vgl during passive status
                                    DefaultVglRepoCore core = new DefaultVglRepoCore();
                                    java.util.Set<String> computed = core.computeUndecidedFiles(git, status);
                                    if (computed != null) undecided.addAll(computed);
                                } catch (Exception ignore) {}
                            }
                        } catch (Exception ignore) {}
                }
            } catch (Exception ignored) {
            }

            // Remove undecided files from the untracked set so they appear only in the Undecided section
            untracked.removeAll(undecided);

            // Remove nested repositories from untracked/undecided so they only appear in Ignored
                if (!nested.isEmpty()) {
                untracked.removeAll(nested);
                undecided.removeAll(nested);
            }

                // Build ignored set: include JGit's ignored files, ensure .vgl is treated as ignored,
                // and include nested repos as ignored entries.
                Set<String> ignored = new LinkedHashSet<>();
                if (status != null) {
                    try {
                        ignored.addAll(status.getIgnoredNotInIndex());
                    } catch (Exception ignore) {}
                }
                try {
                    java.nio.file.Path repoRoot = Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize();
                    if (Utils.isGitIgnored(repoRoot.resolve(".vgl"), git.getRepository())) {
                        ignored.add(".vgl");
                    }
                } catch (Exception ignore) {}
                if (!nested.isEmpty()) ignored.addAll(nested);
                    if (!nested.isEmpty()) ignored.addAll(nested);

                    // Ensure untracked/undecided do not include ignored entries
                    try {
                        if (!ignored.isEmpty()) {
                            untracked.removeAll(ignored);
                            undecided.removeAll(ignored);
                        }
                    } catch (Exception ignore) {}

                    // Ensure tracked files do not include ignored or nested-repo entries
                    try {
                        if (!ignored.isEmpty()) tracked.removeAll(ignored);
                    } catch (Exception ignore) {}
                    // Also remove untracked and undecided so sections are mutually exclusive
                    try {
                        if (!untracked.isEmpty()) tracked.removeAll(untracked);
                        if (!undecided.isEmpty()) tracked.removeAll(undecided);
                    } catch (Exception ignore) {}
                    // Extra guard: always ensure the VGL config file itself is never listed as tracked
                    try { tracked.remove(".vgl"); } catch (Exception ignore) {}

                // Use DefaultStatusService to compute a structured status model (includes rename detection)
                try {
                    DefaultStatusService svc = new DefaultStatusService();
                    StatusModel model = svc.computeStatus(git, filters, verbose, veryVerbose);
                    int mergeCount = 0;
                    StatusFileSummary.printFileSummary(model.modified, model.added, model.removed, model.renamed,
                            mergeCount, undecided, tracked, untracked, ignored);
                } catch (Exception ex) {
                    // Fallback to legacy behavior if the new service fails
                    com.vgl.cli.commands.status.StatusFileCounts counts = com.vgl.cli.commands.status.StatusFileCounts.fromStatus(status);
                    int mergeCount = 0;
                    StatusFileSummary.printFileSummary(counts.modified, counts.added, counts.removed, 0,
                            mergeCount, undecided, tracked, untracked, ignored);
                }
            // Commit message is printed under STATE (handled earlier). Do not duplicate here.

            // Print sync-related subsections for -v and -vv (Files to Commit / Files to Merge)
            if (verbose || veryVerbose) {
                com.vgl.cli.commands.status.StatusSyncFiles.printSyncFiles(git, status, remoteUrl, remoteBranch, filters, verbose, veryVerbose, vgl);
            }

            // Print detailed file lists only for very-verbose (-vv)
            if (veryVerbose) {
                StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, vgl.getLocalDir(), filters);
            }
        } catch (Exception e) {
            System.err.println("StatusCommand: unable to open repo: " + e.getMessage());
        }

        return 0;
    }
}

