package com.vgl.cli.commands;

// VglRepo is referenced via RepoResolution; avoid direct import here.
import com.vgl.cli.utils.Utils;
import com.vgl.cli.commands.helpers.StatusFileSummary;
import com.vgl.cli.commands.helpers.StatusCommandHelpers;
import com.vgl.cli.commands.helpers.StatusVerboseOutput;
import com.vgl.cli.services.RepoResolution;

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
    // ...existing code...

        // Use helper to ensure .vgl config exists if only Git is present
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = !args.contains("-y"); // Example: -y disables prompts
        RepoResolution resolution = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (resolution == null || resolution.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH) {
            String warn = (resolution != null && resolution.getMessage() != null) ? resolution.getMessage() : "WARNING: No VGL repository found in this directory or any parent.\nHint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            return 1;
        }


        // Print LOCAL and REMOTE sections in aligned, truncated, or verbose format
        Git git = resolution.getGit();
        String localDir = resolution.getVglRepo().getRepoRoot().toString();
        String localBranch = null;
        String remoteUrl = null;
        String remoteBranch = null;
        java.util.Properties cfg = resolution.getVglRepo().getConfig();
        localBranch = cfg.getProperty("local.branch", "main");
        remoteUrl = cfg.getProperty("remote.url", "");
        remoteBranch = cfg.getProperty("remote.branch", "main");

        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        // Helper for truncation
        java.util.function.BiFunction<String, Integer, String> truncatePath = (path, maxLen) -> {
            if (verbose || veryVerbose || path.length() <= maxLen) return path;
            int leftLen = (maxLen - 3) / 2;
            int rightLen = maxLen - 3 - leftLen;
            return path.substring(0, leftLen) + "..." + path.substring(path.length() - rightLen);
        };
        int maxPathLen = 35;
        String separator = " :: ";
        String displayLocalDir = truncatePath.apply(localDir, maxPathLen);
        String displayRemoteUrl = (remoteUrl != null && !remoteUrl.isBlank()) ? truncatePath.apply(remoteUrl, maxPathLen) : "(none)";
        int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());

        // Declare hasRemote before REMOTE section, initialize to false
        boolean hasRemote = remoteUrl != null && !remoteUrl.isEmpty();

        // LOCAL section
        String localLabel = "LOCAL:";
        String remoteLabel = "REMOTE:";
        String commitsLabel = "COMMITS:";
        String filesLabel = "FILES:";
        int labelWidth = Math.max(Math.max(localLabel.length(), remoteLabel.length()), Math.max(commitsLabel.length(), filesLabel.length()));
        String localLabelPad = com.vgl.cli.utils.FormatUtils.padRight(localLabel, labelWidth + 1); // +1 for space
        String remoteLabelPad = com.vgl.cli.utils.FormatUtils.padRight(remoteLabel, labelWidth + 1);
        String commitsLabelPad = com.vgl.cli.utils.FormatUtils.padRight(commitsLabel, labelWidth + 1);
        String filesLabelPad = com.vgl.cli.utils.FormatUtils.padRight(filesLabel, labelWidth + 1);

        if (veryVerbose || verbose) {
            // LOCAL section with expanded path and trailing branch
            System.out.println(localLabelPad + localDir + separator + (localBranch != null ? localBranch : "(none)"));
            // Branches subsection for LOCAL
            try {
                if (git != null) {
                    List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                    System.out.println("  -- Branches:");
                    String currentBranch = git.getRepository().getBranch();
                    for (org.eclipse.jgit.lib.Ref branch : branches) {
                        String name = branch.getName();
                        String shortName = name.replaceFirst("refs/heads/", "");
                        if (shortName.equals(currentBranch)) {
                            System.out.println("  * " + shortName + " (current)");
                        } else {
                            System.out.println("    " + shortName);
                        }
                    }
                }
            } catch (Exception ignore) {}
        } else {
            System.out.println(localLabelPad + com.vgl.cli.utils.FormatUtils.padRight(displayLocalDir, maxLen) + separator + (localBranch != null ? localBranch : "(none)"));
        }

        // REMOTE section
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            if (veryVerbose || verbose) {
                System.out.println(remoteLabelPad + remoteUrl + separator + (remoteBranch != null ? remoteBranch : "(none)"));
                // Branches subsection for REMOTE
                try {
                    if (git != null && hasRemote) {
                        List<org.eclipse.jgit.lib.Ref> remoteBranches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                        System.out.println("  -- Remote Branches:");
                        String currentRemoteBranch = remoteBranch != null ? remoteBranch : "main";
                        for (org.eclipse.jgit.lib.Ref branch : remoteBranches) {
                            String name = branch.getName();
                            String shortName = name.replaceFirst("refs/remotes/origin/", "");
                            if (shortName.equals(currentRemoteBranch)) {
                                System.out.println("  * " + shortName + " (current)");
                            } else {
                                System.out.println("    " + shortName);
                            }
                        }
                    }
                } catch (Exception ignore) {}
            } else {
                System.out.println(remoteLabelPad + com.vgl.cli.utils.FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + (remoteBranch != null ? remoteBranch : "(none)"));
            }
        } else {
            // Always print REMOTE in the format (none) :: (none), aligned
            if (veryVerbose || verbose) {
                System.out.println(remoteLabelPad + "(none)" + separator + "(none)");
            } else {
                System.out.println(remoteLabelPad + com.vgl.cli.utils.FormatUtils.padRight("(none)", maxLen) + separator + "(none)");
            }
        }

        // (verbosity flags already declared above)

        // Print COMMITS section with Commit/Push/Merge counts
        int commitCount = 0, pushCount = 0, pullCount = 0;
        java.util.Map<String, String> filesToCommit = new java.util.LinkedHashMap<>();
            // Keep a copy of the original filesToCommit for summary counts
            java.util.Map<String, String> filesToCommitForSummary = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> filesToPush = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> filesToPull = new java.util.LinkedHashMap<>();
        if (git == null) {
            System.out.println(commitsLabelPad + "(no commits yet)");
        } else {
            boolean unbornRepo = !Utils.hasCommits(git.getRepository());
            if (unbornRepo) {
                System.out.println(commitsLabelPad + "(no commits yet)");
            } else {
                // Compute files to commit (unstaged/staged), push (committed, not pushed), merge (incoming)
                org.eclipse.jgit.api.Status status = git.status().call();
                if (status != null) {
                    for (String p : status.getModified()) filesToCommit.put(p, "M");
                    for (String p : status.getChanged()) filesToCommit.put(p, "M");
                    for (String p : status.getAdded()) filesToCommit.put(p, "A");
                    for (String p : status.getRemoved()) filesToCommit.put(p, "D");
                    for (String p : status.getMissing()) filesToCommit.put(p, "D");
                    // Copy to summary map before any push/pull filtering
                    filesToCommitForSummary.putAll(filesToCommit);
                }
                // Compute files to push (committed, not pushed)
                org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
                String effectiveRemoteBranch = remoteBranch;
                // Update hasRemote if needed
                if (!hasRemote) {
                    try {
                        org.eclipse.jgit.lib.StoredConfig jgitCfg = git.getRepository().getConfig();
                        String gitRemoteUrl = jgitCfg.getString("remote", "origin", "url");
                        if (gitRemoteUrl != null && !gitRemoteUrl.isEmpty()) {
                            hasRemote = true;
                            String currentBranch = git.getRepository().getBranch();
                            String trackedBranch = jgitCfg.getString("branch", currentBranch, "merge");
                            if (trackedBranch != null) effectiveRemoteBranch = trackedBranch.replaceFirst("refs/heads/", "");
                            else effectiveRemoteBranch = currentBranch;
                        }
                    } catch (Exception ignore) {}
                }
                org.eclipse.jgit.lib.ObjectId remoteHead = null;
                if (hasRemote) {
                    remoteHead = git.getRepository().resolve("origin/" + effectiveRemoteBranch);
                }
                if (localHead != null) {
                    if (remoteHead == null) {
                        // Remote branch does not exist yet: all files in all local commits are to be pushed
                        Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().add(localHead).call();
                        java.util.Set<String> pushedFiles = new java.util.HashSet<>();
                        for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.AbstractTreeIterator oldTree;
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            if (commit.getParentCount() > 0) {
                                oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                ((org.eclipse.jgit.treewalk.CanonicalTreeParser)oldTree).reset(reader, commit.getParent(0).getTree());
                            } else {
                                oldTree = new org.eclipse.jgit.treewalk.EmptyTreeIterator();
                            }
                            newTree.reset(reader, commit.getTree());
                            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                                df.setRepository(git.getRepository());
                                df.setDetectRenames(true);
                                java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                    String newPath = diff.getNewPath();
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                    String statusLetter = switch (diff.getChangeType()) {
                                        case ADD -> "A";
                                        case MODIFY -> "M";
                                        case DELETE -> "D";
                                        case RENAME, COPY -> "R";
                                        default -> "M";
                                    };
                                    filesToPush.put(filePath, statusLetter);
                                    pushedFiles.add(filePath);
                                }
                            }
                            reader.close();
                        }
                        // Remove files that are in filesToPush from filesToCommit (mutually exclusive)
                        for (String pushed : filesToPush.keySet()) {
                            filesToCommit.remove(pushed);
                        }
                    } else if (!localHead.equals(remoteHead)) {
                        Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPush = git.log().add(localHead).not(remoteHead).call();
                        for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPush) {
                            if (commit.getParentCount() > 0) {
                                org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                                org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                                oldTree.reset(reader, commit.getParent(0).getTree());
                                newTree.reset(reader, commit.getTree());
                                try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                                    df.setRepository(git.getRepository());
                                    df.setDetectRenames(true);
                                    java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                                    for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                        String newPath = diff.getNewPath();
                                        String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                        String statusLetter = switch (diff.getChangeType()) {
                                            case ADD -> "A";
                                            case MODIFY -> "M";
                                            case DELETE -> "D";
                                            case RENAME, COPY -> "R";
                                            default -> "M";
                                        };
                                        if (!filesToPush.containsKey(filePath)) {
                                            filesToPush.put(filePath, statusLetter);
                                        }
                                    }
                                }
                                reader.close();
                            }
                        }
                        // Remove files that are in filesToPush from filesToCommit (mutually exclusive)
                        for (String pushed : filesToPush.keySet()) {
                            filesToCommit.remove(pushed);
                        }
                    }
                }
                // Compute files to pull (incoming)
                if (localHead != null && remoteHead != null && !localHead.equals(remoteHead)) {
                    Iterable<org.eclipse.jgit.revwalk.RevCommit> commitsToPull = git.log().add(remoteHead).not(localHead).call();
                    for (org.eclipse.jgit.revwalk.RevCommit commit : commitsToPull) {
                        if (commit.getParentCount() > 0) {
                            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            org.eclipse.jgit.treewalk.CanonicalTreeParser newTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                            oldTree.reset(reader, commit.getParent(0).getTree());
                            newTree.reset(reader, commit.getTree());
                            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                                df.setRepository(git.getRepository());
                                df.setDetectRenames(true);
                                java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, newTree);
                                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                    String newPath = diff.getNewPath();
                                    String filePath = diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE ? diff.getOldPath() : newPath;
                                    String statusLetter = switch (diff.getChangeType()) {
                                        case ADD -> "A";
                                        case MODIFY -> "M";
                                        case DELETE -> "D";
                                        case RENAME, COPY -> "R";
                                        default -> "M";
                                    };
                                    filesToPull.put(filePath, statusLetter);
                                }
                            }
                            reader.close();
                        }
                    }
                }
                // The commit count should match the number of files in filesToCommit
                commitCount = filesToCommit.size();
                pushCount = filesToPush.size();
                pullCount = filesToPull.size();
                System.out.println(commitsLabelPad + commitCount + " to Commit, " + pushCount + " to Push, " + pullCount + " to Pull");
            }
        }

        // Print FILES section
        Set<String> tracked = new LinkedHashSet<>();
        Set<String> untracked = new LinkedHashSet<>();
        Set<String> undecided = new LinkedHashSet<>();
        Set<String> ignored = new LinkedHashSet<>();
        // The following sets will be built from filesToCommit to match VGL's model
        // Build summary sets directly from filesToCommit (after all filtering) to guarantee sync with verbose output
        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        Set<String> renamed = new LinkedHashSet<>();
        java.util.Set<String> workingRenameTargets = new java.util.HashSet<>();
        if (git != null) {
            try {
                org.eclipse.jgit.api.Status status = git.status().call();
                if (status != null) {
                    StatusCommandHelpers.resolveFileCategories(status, git.getRepository(), tracked, untracked, undecided, ignored);
                    // Build summary sets from filesToCommit (after all filtering)
                    added.clear();
                    modified.clear();
                    removed.clear();
                    renamed.clear();
                    for (var e : filesToCommit.entrySet()) {
                        switch (e.getValue()) {
                            case "A" -> added.add(e.getKey());
                            case "M" -> modified.add(e.getKey());
                            case "D" -> removed.add(e.getKey());
                            case "R" -> renamed.add(e.getKey());
                        }
                    }
                    // Robustly handle working-tree renames: ensure targets are only in tracked, sources are removed from all
                    java.util.Map<String, String> workingRenames = com.vgl.cli.commands.helpers.StatusSyncFiles.computeWorkingRenames(git);
                    java.util.Set<String> workingRenameSources = new java.util.HashSet<>(workingRenames.keySet());
                    workingRenameTargets = new java.util.HashSet<>(workingRenames.values());
                    // Remove all sources from all sets
                    tracked.removeAll(workingRenameSources);
                    untracked.removeAll(workingRenameSources);
                    undecided.removeAll(workingRenameSources);
                    // Remove all targets from undecided and untracked
                    undecided.removeAll(workingRenameTargets);
                    untracked.removeAll(workingRenameTargets);
                    // Add all targets to tracked if not ignored
                    for (String target : workingRenameTargets) {
                        if (!ignored.contains(target)) {
                            tracked.add(target);
                        }
                    }
                    // Ensure targets of working renames are not in undecided
                    for (String target : workingRenameTargets) {
                        undecided.remove(target);
                    }
                }
            } catch (Exception ignore) {}
        }
        // Guarantee all working rename targets are in tracked before printing summary
        for (String target : workingRenameTargets) {
            if (!ignored.contains(target)) {
                if (!tracked.contains(target)) {
                    tracked.add(target);
                }
            }
        }
        // Print summary counts for Added, Modified, Renamed, Deleted using the correct sets
        System.out.print(filesLabelPad);
        int filesPadLen = filesLabelPad.length();
        StatusFileSummary.printFileSummary(
            modified.size(), // numModified
            added.size(),    // numAdded
            removed.size(),  // numRemoved
            renamed.size(),  // numReplaced
            0, // numToMerge (not used)
            undecided, tracked, untracked, ignored,
            filesPadLen
        );

        // Print verbose/very-verbose file and commit subsections if requested
        if (verbose || veryVerbose) {
            // Print verbose file sections
            System.out.println("  -- Files to Commit:");
            if (filesToCommit.isEmpty()) System.out.println("  (none)");
            else for (var e : filesToCommit.entrySet()) System.out.println("  " + e.getValue() + " " + e.getKey());

            System.out.println("  -- Files to Push:");
            if (!hasRemote) {
                System.out.println("  (no remote)");
            } else if (filesToPush.isEmpty()) {
                System.out.println("  (none)");
            } else {
                for (var e : filesToPush.entrySet()) System.out.println("  " + e.getValue() + " " + e.getKey());
            }

            System.out.println("  -- Files to Pull:");
            if (!hasRemote) {
                System.out.println("  (no remote)");
            } else if (filesToPull.isEmpty()) {
                System.out.println("  (none)");
            } else {
                for (var e : filesToPull.entrySet()) System.out.println("  " + e.getValue() + " " + e.getKey());
            }
        }
        if (veryVerbose) {
            // Print detailed file lists
            StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, (resolution.getVglRepo() != null ? resolution.getVglRepo().getRepoRoot().toString() : "."), new java.util.ArrayList<>());
        }
        return 0;
    }

}

