package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.StatusLocalSection;
import com.vgl.cli.commands.helpers.StatusRemoteSection;
import com.vgl.cli.commands.helpers.StatusCommitsSection;
import com.vgl.cli.commands.helpers.StatusFilesSection;
import com.vgl.cli.services.RepoResolution;

import org.eclipse.jgit.api.Git;

import java.util.List;
public class StatusCommand implements Command {
    @Override
    public String name() {
        return "status";
    }

    @Override
    public int run(List<String> args) throws Exception {
        System.out.println("[DEBUG] StatusCommand.run called. CWD=" + java.nio.file.Paths.get("").toAbsolutePath() + ", args=" + args);
        System.out.flush();

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

        // Section labels and padding
        String localLabel = "LOCAL:";
        String remoteLabel = "REMOTE:";
        String commitsLabel = "COMMITS:";
        String filesLabel = "FILES:";
        int labelWidth = Math.max(Math.max(localLabel.length(), remoteLabel.length()), Math.max(commitsLabel.length(), filesLabel.length()));
        String localLabelPad = com.vgl.cli.utils.FormatUtils.padRight(localLabel, labelWidth + 1); // +1 for space
        String remoteLabelPad = com.vgl.cli.utils.FormatUtils.padRight(remoteLabel, labelWidth + 1);
        String commitsLabelPad = com.vgl.cli.utils.FormatUtils.padRight(commitsLabel, labelWidth + 1);
        String filesLabelPad = com.vgl.cli.utils.FormatUtils.padRight(filesLabel, labelWidth + 1);

        // LOCAL section (refactored)
        StatusLocalSection.printLocalSection(
            git,
            localDir,
            localBranch,
            verbose,
            veryVerbose,
            localLabelPad,
            separator,
            maxLen
        );

        // REMOTE section (refactored)
        StatusRemoteSection.printRemoteSection(
            git,
            remoteUrl,
            remoteBranch,
            hasRemote,
            verbose,
            veryVerbose,
            remoteLabelPad,
            separator,
            maxLen
        );

        // (verbosity flags already declared above)

        // COMMITS section (refactored)
        java.util.Map<String, String> filesToCommit = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> filesToPush = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> filesToPull = new java.util.LinkedHashMap<>();
        StatusCommitsSection.printCommitsSection(
            git,
            commitsLabelPad,
            remoteUrl,
            remoteBranch,
            localBranch,
            verbose,
            veryVerbose,
            filesToCommit,
            filesToPush,
            filesToPull
        );

        // --- Detect working renames via JGit diff between HEAD and working tree ---
        try {
            org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
            if (head != null) {
                org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
                org.eclipse.jgit.revwalk.RevCommit headCommit = rw.parseCommit(head);
                org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
                org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                oldTree.reset(reader, headCommit.getTree());
                org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository());
                try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(new java.io.ByteArrayOutputStream())) {
                    df.setRepository(git.getRepository());
                    df.setDetectRenames(true);
                    java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, workingTreeIt);
                    for (org.eclipse.jgit.diff.DiffEntry d : diffs) {
                        if (d.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.RENAME || d.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.COPY) {
                            filesToCommit.remove(d.getOldPath());
                            filesToCommit.remove(d.getNewPath());
                            filesToCommit.put(d.getNewPath(), "R");
                            System.out.println("[DEBUG] Detected working rename: " + d.getOldPath() + " -> " + d.getNewPath());
                        }
                    }
                }
                reader.close();
                rw.close();
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] Exception during working rename diff: " + e);
        }

        // Debug: print filesToCommit before working rename heuristic
        System.out.println("[DEBUG] filesToCommit before working rename heuristic: " + filesToCommit);
        System.out.flush();
        // --- Integrate robust working rename detection (from StatusSyncFiles) ---
        // Remove any old/new paths for detected working renames and add as 'R'
        java.util.Map<String, String> workingRenames = com.vgl.cli.commands.helpers.StatusSyncFiles.computeWorkingRenames(git);
        for (java.util.Map.Entry<String, String> r : workingRenames.entrySet()) {
            String oldPath = r.getKey();
            String newPath = r.getValue();
            filesToCommit.remove(oldPath);
            filesToCommit.remove(newPath);
            filesToCommit.put(newPath, "R");
        }
        // Heuristic: for every A+D pair, treat as rename (if not already handled)
        java.util.List<String> debugAdded = new java.util.ArrayList<>();
        java.util.List<String> debugRemovedOrMissing = new java.util.ArrayList<>();
        java.util.List<String> added = new java.util.ArrayList<>();
        java.util.List<String> removedOrMissing = new java.util.ArrayList<>();
        for (var e : filesToCommit.entrySet()) {
            if ("A".equals(e.getValue())) added.add(e.getKey());
            if ("D".equals(e.getValue()) || "Missing".equals(e.getValue())) removedOrMissing.add(e.getKey());
            if ("A".equals(e.getValue())) debugAdded.add(e.getKey());
            if ("D".equals(e.getValue()) || "Missing".equals(e.getValue())) debugRemovedOrMissing.add(e.getKey());
        }
        System.out.println("[DEBUG] Added list for rename heuristic: " + debugAdded);
        System.out.println("[DEBUG] RemovedOrMissing list for rename heuristic: " + debugRemovedOrMissing);
        System.out.flush();
        int pairs = Math.min(added.size(), removedOrMissing.size());
        for (int i = 0; i < pairs; i++) {
            String addPath = added.get(i);
            String delPath = removedOrMissing.get(i);
            filesToCommit.remove(delPath);
            filesToCommit.remove(addPath);
            filesToCommit.put(addPath, "R");
        }
        System.out.println("[DEBUG] filesToCommit after working rename heuristic: " + filesToCommit);
        System.out.flush();
        // --- End working rename integration ---

        // FILES section (refactored)
        java.util.Set<String> tracked = new java.util.LinkedHashSet<>();
        java.util.Set<String> untracked = new java.util.LinkedHashSet<>();
        java.util.Set<String> undecided = new java.util.LinkedHashSet<>();
        java.util.Set<String> ignored = new java.util.LinkedHashSet<>();
        StatusFilesSection.printFilesSection(
            git,
            filesLabelPad,
            filesToCommit,
            undecided,
            tracked,
            untracked,
            ignored,
            verbose,
            veryVerbose,
            maxLen
        );
        return 0;
    }

}

