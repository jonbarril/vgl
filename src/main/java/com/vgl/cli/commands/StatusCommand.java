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
                // Print latest commit message under STATE section when verbose flags are used.
                if (verbose || veryVerbose) {
                    try {
                        Iterable<org.eclipse.jgit.revwalk.RevCommit> logs = git.log().setMaxCount(1).call();
                        java.util.Iterator<org.eclipse.jgit.revwalk.RevCommit> it = logs.iterator();
                        if (it.hasNext()) {
                            org.eclipse.jgit.revwalk.RevCommit head = it.next();
                            String shortId = (head.getName() != null && head.getName().length() >= 7) ? head.getName().substring(0, 7) : head.getName();
                            String fullMsg = head.getFullMessage();
                            if (!veryVerbose) {
                                // -v: show single-line message (truncate newlines)
                                String oneLine = fullMsg.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                                System.out.println("  " + shortId + " " + oneLine);
                            } else {
                                // -vv: show full commit message (may be multiline)
                                // Indent the first line; preserve subsequent newlines as-is.
                                String[] lines = fullMsg.split("\\r?\\n", -1);
                                if (lines.length > 0) {
                                    System.out.println("  " + shortId + " " + lines[0]);
                                    for (int i = 1; i < lines.length; i++) {
                                        System.out.println("  " + lines[i]);
                                    }
                                } else {
                                    System.out.println("  " + shortId + " " + fullMsg);
                                }
                            }
                        }
                    } catch (Exception ignore) {}
                }
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
                // Compute the set of rename targets that will actually be displayed as R
                java.util.Set<String> displayedRenameTargets = new java.util.LinkedHashSet<>();
                if (commitRenames != null) {
                    for (String c : commitRenames) {
                        // If the commit rename target is subsequently renamed in the working tree,
                        // it will be shown under the working-tree target instead, so skip it here.
                        if (workingRenames != null && workingRenames.containsKey(c)) continue;
                        displayedRenameTargets.add(c);
                    }
                }
                if (workingRenames != null) displayedRenameTargets.addAll(workingRenames.values());
                int renamedCountUnion = displayedRenameTargets.size();
                // Adjust added/removed counts to exclude files that are actually rename targets
                int adjustedAdded = counts.added;
                int adjustedRemoved = counts.removed;
                try {
                    // compute sources (old paths) for commit renames and working renames
                    java.util.Set<String> commitRenameSources = com.vgl.cli.commands.status.StatusSyncFiles.computeCommitRenamedSourceSet(git, status, remoteUrl, remoteBranch);
                    java.util.Set<String> workingRenameSources = new java.util.LinkedHashSet<>();
                    if (workingRenames != null) workingRenameSources.addAll(workingRenames.keySet());
                    // Decrement added for any new-path rename targets (already in unionRenames)
                    if (displayedRenameTargets != null && !displayedRenameTargets.isEmpty() && status != null) {
                        for (String r : displayedRenameTargets) {
                            if (status.getAdded().contains(r)) adjustedAdded = Math.max(0, adjustedAdded - 1);
                        }
                    }
                    // Decrement removed for any old-path rename sources that appear in removed/missing
                    java.util.Set<String> allRenameSources = new java.util.LinkedHashSet<>();
                    if (commitRenameSources != null) allRenameSources.addAll(commitRenameSources);
                    if (workingRenameSources != null) allRenameSources.addAll(workingRenameSources);
                    if (!allRenameSources.isEmpty() && status != null) {
                        for (String sPath : allRenameSources) {
                            if (status.getRemoved().contains(sPath) || status.getMissing().contains(sPath)) adjustedRemoved = Math.max(0, adjustedRemoved - 1);
                        }
                    }
                } catch (Exception ignore) {}
                int mergeCount = 0;
                StatusFileSummary.printFileSummary(counts.modified, adjustedAdded, adjustedRemoved, renamedCountUnion,
                    mergeCount, undecided, tracked, untracked, ignored);
            // Commit message is printed under STATE (handled earlier). Do not duplicate here.

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
            System.err.println("StatusCommand: unable to open repo: " + e.getMessage());
        }

        return 0;
    }
}

