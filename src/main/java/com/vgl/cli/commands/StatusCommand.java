package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import com.vgl.cli.VglRepo;
import com.vgl.cli.Utils;
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
        boolean hasLocalRepo = vgl.isConfigurable();

        String localDir = hasLocalRepo ? Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize().toString() : "(none)";
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
                System.out.println("STATE  (no commits yet)");
            } else {
                StatusSyncState.printSyncState(git, remoteUrl, remoteBranch, localBranch);
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
                    // Ensure the undecided file list is up-to-date by scanning the working tree
                    try {
                        if (status != null) repo.updateUndecidedFilesFromWorkingTree(git, status);
                        else repo.updateUndecidedFilesFromWorkingTree(git);
                    } catch (Exception ignore) {}
                    undecided.addAll(repo.getUndecidedFiles());
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

                com.vgl.cli.commands.status.StatusFileCounts counts = com.vgl.cli.commands.status.StatusFileCounts.fromStatus(status);
                // Compute rename count as union of commit-derived renames and working-tree (uncommitted) renames
                java.util.Set<String> commitRenames = com.vgl.cli.commands.status.StatusSyncFiles.computeCommitRenamedSet(git, status, remoteUrl, remoteBranch);
                java.util.Map<String, String> workingRenames = com.vgl.cli.commands.status.StatusSyncFiles.computeWorkingRenames(git);
                java.util.Set<String> unionRenames = new java.util.LinkedHashSet<>();
                if (commitRenames != null) unionRenames.addAll(commitRenames);
                if (workingRenames != null) unionRenames.addAll(workingRenames.values());
                int renamedCountUnion = unionRenames.size();
                int mergeCount = 0;
                StatusFileSummary.printFileSummary(counts.modified, counts.added, counts.removed, renamedCountUnion,
                    mergeCount, undecided, tracked, untracked, ignored);
            // Print latest commit message for verbose and very-verbose modes
            if (verbose || veryVerbose) {
                try {
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().setMaxCount(1).call();
                    java.util.Iterator<org.eclipse.jgit.revwalk.RevCommit> it = logs.iterator();
                    if (it.hasNext()) {
                        org.eclipse.jgit.revwalk.RevCommit head = it.next();
                        String shortId = (head.getName() != null && head.getName().length() >= 7) ? head.getName().substring(0, 7) : head.getName();
                        String msg = head.getShortMessage();
                        System.out.println(msg + " (" + shortId + ")");
                    }
                } catch (Exception ignore) {
                }

            }

            if (verbose || veryVerbose) {
                com.vgl.cli.commands.status.StatusSyncFiles.printSyncFiles(git, status, remoteUrl, remoteBranch, filters, verbose, veryVerbose, vgl);

                if (verbose && !veryVerbose) {
                    System.out.println("  -- Undecided Files:");
                    if (undecided.isEmpty()) System.out.println("  (none)"); else undecided.forEach(p -> System.out.println("  " + p));
                }
                if (veryVerbose) {
                    StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, vgl.getLocalDir(), filters);
                }
            }
        } catch (Exception e) {
            System.err.println("[vgl.debug] StatusCommand: unable to open repo: " + e.getMessage());
        }

        return 0;
    }
}

