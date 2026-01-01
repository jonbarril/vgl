package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.StatusFileSummary;
import com.vgl.cli.commands.helpers.StatusVerboseOutput;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.RepoValidation;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

public class StatusCommand implements Command {
    @Override
    public String name() {
        return "status";
    }

    @Override
    public int run(List<String> args) throws Exception {
        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        boolean showLocal = args.contains("-local");
        boolean showRemote = args.contains("-remote");
        boolean showCommits = args.contains("-commits");
        boolean showFiles = args.contains("-files");
        boolean anySectionFlag = showLocal || showRemote || showCommits || showFiles;

        List<String> filters = extractFilters(args);

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        RepoResolution resolved = resolveRepoForStatus(cwd);
        if (resolved == null) {
            return 1;
        }

        Path repoRoot = resolved.repoRoot;

        RepoValidation.Result validation = RepoValidation.validateRepoAt(repoRoot);
        if (validation instanceof RepoValidation.Result.None) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return 1;
        }
        if (validation instanceof RepoValidation.Result.Malformed m) {
            System.err.println(Messages.malformedRepo(repoRoot, m.problem()));
            return 1;
        }

        Properties vglProps = readVglProps(repoRoot);
        String vglLocalBranch = vglProps.getProperty("local.branch", "main");
        String remoteUrl = vglProps.getProperty("remote.url", "");
        String remoteBranch = vglProps.getProperty("remote.branch", "main");

        try (Git git = GitUtils.openGit(repoRoot)) {
            String gitBranch = safeGitBranch(git.getRepository());
            String localBranch = (gitBranch != null && !gitBranch.isBlank()) ? gitBranch : vglLocalBranch;

            Set<String> vglLocalBranches = VglConfig.getStringSet(vglProps, VglConfig.KEY_LOCAL_BRANCHES);
            if (localBranch != null && !localBranch.isBlank()) {
                vglLocalBranches.add(localBranch);
            }

            int maxPathLen = 35;
            String separator = " :: ";

            String displayLocalDir = (verbose || veryVerbose) ? repoRoot.toString() : FormatUtils.truncateMiddle(repoRoot.toString(), maxPathLen);
            String displayRemoteUrl = (remoteUrl != null && !remoteUrl.isBlank())
                ? ((verbose || veryVerbose) ? remoteUrl : FormatUtils.truncateMiddle(remoteUrl, maxPathLen))
                : "(none)";

            int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());

            String localLabel = "LOCAL:";
            String remoteLabel = "REMOTE:";
            String commitsLabel = "COMMITS:";
            String filesLabel = "FILES:";
            int labelWidth = Math.max(Math.max(localLabel.length(), remoteLabel.length()), Math.max(commitsLabel.length(), filesLabel.length()));

            String localLabelPad = FormatUtils.padRight(localLabel, labelWidth + 1);
            String remoteLabelPad = FormatUtils.padRight(remoteLabel, labelWidth + 1);
            String commitsLabelPad = FormatUtils.padRight(commitsLabel, labelWidth + 1);
            String filesLabelPad = FormatUtils.padRight(filesLabel, labelWidth + 1);

            StatusComputation computed = computeStatus(git, repoRoot, remoteUrl, remoteBranch, filters);

            if (anySectionFlag) {
                if (showLocal) {
                    printLocalSection(git, repoRoot, displayLocalDir, localBranch, vglLocalBranches, verbose, veryVerbose, localLabelPad, separator, maxLen);
                }
                if (showRemote) {
                    printRemoteSection(git, displayRemoteUrl, remoteUrl, remoteBranch, verbose, veryVerbose, remoteLabelPad, separator, maxLen);
                }
                if (showCommits) {
                    printCommitsSection(git, commitsLabelPad, remoteUrl, remoteBranch, localBranch, computed, verbose, veryVerbose);
                }
                if (showFiles) {
                    printFilesSection(filesLabelPad, computed, verbose, veryVerbose, repoRoot, filters);
                }
            } else {
                printLocalSection(git, repoRoot, displayLocalDir, localBranch, vglLocalBranches, verbose, veryVerbose, localLabelPad, separator, maxLen);
                printRemoteSection(git, displayRemoteUrl, remoteUrl, remoteBranch, verbose, veryVerbose, remoteLabelPad, separator, maxLen);
                printCommitsSection(git, commitsLabelPad, remoteUrl, remoteBranch, localBranch, computed, verbose, veryVerbose);
                printFilesSection(filesLabelPad, computed, verbose, veryVerbose, repoRoot, filters);
            }

            return 0;
        } catch (Exception e) {
            System.err.println(Messages.malformedRepo(repoRoot, e.getMessage()));
            return 1;
        }
    }


    private static void printLocalSection(
        Git git,
        Path repoRoot,
        String displayLocalDir,
        String localBranch,
        Set<String> vglLocalBranches,
        boolean verbose,
        boolean veryVerbose,
        String labelPad,
        String separator,
        int maxLen
    ) {
        boolean showBranches = verbose || veryVerbose;
        if (showBranches) {
            System.out.println(labelPad + repoRoot + separator + (localBranch != null ? localBranch : "(none)"));
            if (git != null) {
                try {
                    System.out.println("-- Branches:");
                    Repository repo = git.getRepository();
                    String current = safeGitBranch(repo);

                    boolean headResolves;
                    try {
                        headResolves = repo.resolve(Constants.HEAD) != null;
                    } catch (Exception e) {
                        headResolves = false;
                    }

                    java.util.Set<String> union = new java.util.LinkedHashSet<>();
                    java.util.List<String> gitBranches = listBranchesByPrefix(repo, Constants.R_HEADS);
                    union.addAll(gitBranches);

                    // Only include .vgl "known branches" when the repo is unborn or Git has no branch refs.
                    if (!headResolves || gitBranches.isEmpty()) {
                        if (vglLocalBranches != null) {
                            union.addAll(vglLocalBranches);
                        }
                    }

                    // Always ensure we show the branch we believe we're on.
                    String effectiveCurrent = (current != null && !current.isBlank()) ? current
                        : (localBranch != null && !localBranch.isBlank()) ? localBranch
                        : "main";
                    union.add(effectiveCurrent);

                    java.util.List<String> branchNames = new java.util.ArrayList<>(union);
                    java.util.Collections.sort(branchNames);

                    java.util.List<String> entries = new java.util.ArrayList<>();
                    for (String b : branchNames) {
                        if (b == null || b.isBlank()) {
                            continue;
                        }
                        if (b.equals(effectiveCurrent)) {
                            entries.add("* " + b);
                        } else {
                            entries.add(b);
                        }
                    }

                    StatusVerboseOutput.printCompactEntries("", entries);
                } catch (Exception ignored) {
                    // best-effort
                    System.out.println("-- Branches:");
                    String fallback = (localBranch != null && !localBranch.isBlank()) ? localBranch : "main";
                    StatusVerboseOutput.printCompactEntries("", java.util.List.of("* " + fallback));
                }
            }
            return;
        }

        System.out.println(labelPad + FormatUtils.padRight(displayLocalDir, maxLen) + separator + (localBranch != null ? localBranch : "(none)"));
    }

    private static void printRemoteSection(
        Git git,
        String displayRemoteUrl,
        String remoteUrl,
        String remoteBranch,
        boolean verbose,
        boolean veryVerbose,
        String labelPad,
        String separator,
        int maxLen
    ) {
        boolean hasRemote = remoteUrl != null && !remoteUrl.isBlank();
        if (!hasRemote) {
            System.out.println(labelPad + FormatUtils.padRight("(none)", maxLen) + separator + "(none)");
            return;
        }

        boolean showBranches = verbose || veryVerbose;
        if (showBranches) {
            System.out.println(labelPad + remoteUrl + separator + (remoteBranch != null ? remoteBranch : "(none)"));
            if (git != null) {
                try {
                    System.out.println("-- Remote Branches:");
                    String currentRemote = (remoteBranch != null && !remoteBranch.isBlank()) ? remoteBranch : "main";
                    Repository repo = git.getRepository();
                    List<String> branchNames = listBranchesByPrefix(repo, Constants.R_REMOTES + "origin/");

                    java.util.Set<String> union = new java.util.LinkedHashSet<>(branchNames);
                    union.add(currentRemote);
                    java.util.List<String> sorted = new java.util.ArrayList<>(union);
                    java.util.Collections.sort(sorted);
                    java.util.List<String> entries = new java.util.ArrayList<>();
                    for (String b : sorted) {
                        if (b == null || b.isBlank()) {
                            continue;
                        }
                        entries.add(b.equals(currentRemote) ? "* " + b : b);
                    }
                    StatusVerboseOutput.printCompactEntries("", entries);
                } catch (Exception ignored) {
                    // best-effort
                    System.out.println("-- Remote Branches:");
                    String currentRemote = (remoteBranch != null && !remoteBranch.isBlank()) ? remoteBranch : "main";
                    StatusVerboseOutput.printCompactEntries("", java.util.List.of("* " + currentRemote));
                }
            }
            return;
        }

        System.out.println(labelPad + FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + (remoteBranch != null ? remoteBranch : "(none)"));
    }

    private static void printCommitsSection(
        Git git,
        String commitsLabelPad,
        String remoteUrl,
        String remoteBranch,
        String localBranch,
        StatusComputation computed,
        boolean verbose,
        boolean veryVerbose
    ) {
        System.out.println(commitsLabelPad + computed.filesToCommit.size() + " to Commit, " + computed.filesToPush.size() + " to Push, " + computed.filesToPull.size() + " to Pull");

        int numAdded = (int) computed.filesToCommit.values().stream().filter(v -> "A".equals(v)).count();
        int numModified = (int) computed.filesToCommit.values().stream().filter(v -> "M".equals(v)).count();
        int numRenamed = (int) computed.filesToCommit.values().stream().filter(v -> "R".equals(v)).count();
        int numDeleted = (int) computed.filesToCommit.values().stream().filter(v -> "D".equals(v)).count();

        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < commitsLabelPad.length(); i++) {
            pad.append(' ');
        }
        System.out.println(pad + StatusFileSummary.getSummaryCountsLine(numAdded, numModified, numRenamed, numDeleted));

        if (!(verbose || veryVerbose)) {
            return;
        }

        printCommitLists(git, remoteUrl, remoteBranch, localBranch, veryVerbose);

        printCommitMap("-- Files to Commit:", computed.filesToCommit);
        printCommitMap("-- Files to Push:", computed.filesToPush);
        printCommitMap("-- Files to Pull:", computed.filesToPull);
    }

    private static void printCommitLists(Git git, String remoteUrlFromVgl, String remoteBranchFromVgl, String localBranch, boolean veryVerbose) {
        if (git == null) {
            return;
        }
        if (!GitUtils.hasCommits(git.getRepository())) {
            // Unborn repo.
            System.out.println("-- Commits:");
            System.out.println("  (none)");
            return;
        }

        boolean hasRemote = remoteUrlFromVgl != null && !remoteUrlFromVgl.isBlank();
        String remoteBranch = (remoteBranchFromVgl != null && !remoteBranchFromVgl.isBlank()) ? remoteBranchFromVgl : "main";

        if (!hasRemote) {
            try {
                String originUrl = git.getRepository().getConfig().getString("remote", "origin", "url");
                if (originUrl != null && !originUrl.isBlank()) {
                    hasRemote = true;
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }

        org.eclipse.jgit.lib.ObjectId localHead;
        org.eclipse.jgit.lib.ObjectId remoteHead;
        try {
            localHead = git.getRepository().resolve("HEAD");
            remoteHead = hasRemote ? git.getRepository().resolve("origin/" + remoteBranch) : null;
        } catch (Exception e) {
            return;
        }

        if (!hasRemote || remoteHead == null) {
            // No remote configured: show local commits.
            System.out.println("-- Commits:");
            try {
                boolean any = false;
                for (RevCommit c : git.log().call()) {
                    any = true;
                    System.out.println("  " + formatCommitLine(c, veryVerbose));
                }
                if (!any) {
                    System.out.println("  (none)");
                }
            } catch (Exception ignored) {
                System.out.println("  (none)");
            }
            return;
        }

        // Remote configured: show commits to push and pull.
        System.out.println("-- Commits to Push:");
        try {
            boolean any = false;
            Iterable<RevCommit> toPush = (localHead != null) ? git.log().add(localHead).not(remoteHead).call() : java.util.List.of();
            for (RevCommit c : toPush) {
                any = true;
                System.out.println("  " + formatCommitLine(c, veryVerbose));
            }
            if (!any) {
                System.out.println("  (none)");
            }
        } catch (Exception ignored) {
            System.out.println("  (none)");
        }

        System.out.println("-- Commits to Pull:");
        try {
            boolean any = false;
            Iterable<RevCommit> toPull = (localHead != null) ? git.log().add(remoteHead).not(localHead).call() : java.util.List.of();
            for (RevCommit c : toPull) {
                any = true;
                System.out.println("  " + formatCommitLine(c, veryVerbose));
            }
            if (!any) {
                System.out.println("  (none)");
            }
        } catch (Exception ignored) {
            System.out.println("  (none)");
        }
    }

    private static String formatCommitLine(RevCommit commit, boolean veryVerbose) {
        if (commit == null) {
            return "";
        }
        String id = commit.getName();
        String shortId = (id != null && id.length() >= 7) ? id.substring(0, 7) : (id != null ? id : "");
        String msg = commit.getFullMessage();
        String oneLine = (msg == null) ? "" : msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();

        if (!veryVerbose) {
            oneLine = truncateEnd(oneLine, 60);
        }

        if (oneLine.isBlank()) {
            return shortId;
        }
        return shortId + " " + oneLine;
    }

    private static String truncateEnd(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (maxLen <= 0 || s.length() <= maxLen) {
            return s;
        }
        if (maxLen <= 3) {
            return s.substring(0, maxLen);
        }
        return s.substring(0, maxLen - 3) + "...";
    }

    private static void printFilesSection(
        String filesLabelPad,
        StatusComputation computed,
        boolean verbose,
        boolean veryVerbose,
        Path repoRoot,
        List<String> filters
    ) {
        System.out.println(
            filesLabelPad
                + StatusFileSummary.getSummaryCategoriesLine(
                    computed.undecided.size(),
                    computed.tracked.size(),
                    computed.untracked.size(),
                    computed.ignored.size()
                )
        );

        if (verbose || veryVerbose) {
            StatusVerboseOutput.printCompactList(
                "-- Undecided Files:",
                computed.undecided,
                repoRoot.toString(),
                filters
            );
        }

        if (veryVerbose) {
            StatusVerboseOutput.printVeryVerbose(
                computed.tracked,
                computed.untracked,
                computed.ignored,
                repoRoot.toString(),
                filters
            );
        }
    }

    private static void printCommitMap(String header, Map<String, String> map) {
        System.out.println(header);
        if (map.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        // Compact horizontal output: entries like "A foo.txt" wrapped at stable width.
        // Sort by path (directory grouping) rather than by status letter.
        java.util.List<String> entries = new java.util.ArrayList<>();
        map.forEach((path, letter) -> {
            if (path == null || path.isBlank()) {
                return;
            }
            String l = (letter == null || letter.isBlank()) ? "M" : letter;
            entries.add(l + " " + path);
        });

        if (entries.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        entries.sort((a, b) -> {
            String ap = (a != null && a.length() > 2) ? a.substring(2) : a;
            String bp = (b != null && b.length() > 2) ? b.substring(2) : b;
            int c = String.valueOf(ap).compareTo(String.valueOf(bp));
            if (c != 0) {
                return c;
            }
            return String.valueOf(a).compareTo(String.valueOf(b));
        });

        StatusVerboseOutput.printCompactEntries("", entries);
    }

    private static List<String> listBranchesByPrefix(Repository repo, String prefix) {
        if (repo == null || prefix == null) {
            return List.of();
        }
        try {
            var refs = repo.getRefDatabase().getRefsByPrefix(prefix);
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Ref r : refs) {
                if (r == null) {
                    continue;
                }
                String name = r.getName();
                if (name == null || !name.startsWith(prefix)) {
                    continue;
                }
                String shortName = name.substring(prefix.length());
                if (!shortName.isBlank()) {
                    out.add(shortName);
                }
            }
            java.util.Collections.sort(out);
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static StatusComputation computeStatus(Git git, Path repoRoot, String remoteUrl, String remoteBranch, List<String> filters) throws Exception {
        Map<String, String> filesToCommit = new LinkedHashMap<>();
        Map<String, String> filesToPush = new LinkedHashMap<>();
        Map<String, String> filesToPull = new LinkedHashMap<>();

        Set<String> tracked = new LinkedHashSet<>();
        Set<String> untracked = new LinkedHashSet<>();
        Set<String> undecided = new LinkedHashSet<>();
        Set<String> ignored = new LinkedHashSet<>();

        Repository repo = git.getRepository();

        Status status;
        try {
            status = git.status().call();
        } catch (Exception e) {
            status = null;
        }

        // Build tracked files from HEAD (git-tracked), ignoring unborn repos.
        try {
            tracked.addAll(GitUtils.listHeadFiles(repo));
        } catch (Exception ignoredEx) {
            // best-effort
        }

        // Also consider index-level tracking (e.g. newly added files not yet in HEAD).
        if (status != null) {
            try {
                for (String p : status.getAdded()) {
                    if (p == null || p.isBlank()) {
                        continue;
                    }
                    tracked.add(p.replace('\\', '/'));
                }
            } catch (Exception ignoredEx) {
                // best-effort
            }
        }

        // VGL decisions from .vgl.
        Properties vglProps = readVglProps(repoRoot);
        Set<String> vglTracked = VglConfig.getPathSet(vglProps, VglConfig.KEY_TRACKED_FILES);
        Set<String> vglUntracked = VglConfig.getPathSet(vglProps, VglConfig.KEY_UNTRACKED_FILES);
        // Tracked decision wins if a path appears in both.
        vglUntracked.removeAll(vglTracked);

        // Ignored: include JGit ignored plus VGL metadata and nested repos.
        ignored.add(".vgl");
        ignored.add(".git");

        Set<String> nestedRepos = new LinkedHashSet<>();
        try {
            nestedRepos.addAll(GitUtils.listNestedRepos(repoRoot));
            for (String nested : nestedRepos) {
                ignored.add(nested);
            }
        } catch (Exception ignoredEx) {
            // best-effort
        }
        if (status != null) {
            try {
                ignored.addAll(status.getIgnoredNotInIndex());
            } catch (Exception ignoredEx) {
                // best-effort
            }
        }

        // Apply VGL track override: tracked files are never considered ignored.
        for (String p : vglTracked) {
            if (p == null || p.isBlank()) {
                continue;
            }
            ignored.remove(p);
        }

        // Determine undecided/untracked from git untracked + VGL decisions.
        Set<String> gitUntracked = new LinkedHashSet<>();
        if (status != null) {
            try {
                gitUntracked.addAll(status.getUntracked());
            } catch (Exception ignoredEx) {
                // best-effort
            }
        }
        // Remove anything clearly ignored.
        gitUntracked.remove(".vgl");
        for (String p : gitUntracked) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String norm = p.replace('\\', '/');
            if (isInsideAnyNestedRepo(norm, nestedRepos)) {
                continue;
            }

            // Explicit decisions take precedence over default classification.
            if (vglTracked.contains(norm)) {
                tracked.add(norm);
                continue;
            }

            if (vglUntracked.contains(norm)) {
                // Untracked files revert to ignored if they match ignore rules.
                if (!isPathIgnored(norm, ignored)) {
                    untracked.add(norm);
                }
                continue;
            }

            if (isPathIgnored(norm, ignored)) {
                continue;
            }

            undecided.add(norm);
        }

        // Include decided VGL paths even if Git does not currently report them as untracked.
        // Nested repo paths remain excluded from tracked/untracked/undecided lists.
        for (String p : vglTracked) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (isInsideAnyNestedRepo(p, nestedRepos)) {
                continue;
            }
            tracked.add(p);
        }
        for (String p : vglUntracked) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (isInsideAnyNestedRepo(p, nestedRepos)) {
                continue;
            }
            if (vglTracked.contains(p)) {
                continue;
            }
            if (!isPathIgnored(p, ignored)) {
                untracked.add(p);
            }
        }

        // Nested repo paths are always treated as ignored and excluded from other categories.
        tracked.removeIf(p -> isInsideAnyNestedRepo(p, nestedRepos));
        undecided.removeIf(p -> isInsideAnyNestedRepo(p, nestedRepos));
        untracked.removeIf(p -> isInsideAnyNestedRepo(p, nestedRepos));

        // Files to commit: changes to tracked files only.
        if (status != null) {
            addAll(filesToCommit, status.getAdded(), "A");
            addAll(filesToCommit, status.getChanged(), "M");
            addAll(filesToCommit, status.getModified(), "M");
            addAll(filesToCommit, status.getRemoved(), "D");
            addAll(filesToCommit, status.getMissing(), "D");
        }
        // Never include undecided/untracked in commit list.
        for (String u : gitUntracked) {
            filesToCommit.remove(u);
        }
        for (String p : vglUntracked) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (vglTracked.contains(p)) {
                continue;
            }
            filesToCommit.remove(p);
        }

        // Push/pull: if a remote is configured and both sides exist, compute file-diff summaries.
        computePushPullFileDiffs(git, remoteUrl, remoteBranch, filesToPush, filesToPull);

        // Apply optional path filter to all output-driving collections.
        if (filters != null && !filters.isEmpty()) {
            filesToCommit = filterCommitMap(filesToCommit, filters);
            filesToPush = filterCommitMap(filesToPush, filters);
            filesToPull = filterCommitMap(filesToPull, filters);

            tracked = filterSet(tracked, filters);
            undecided = filterSet(undecided, filters);
            untracked = filterSet(untracked, filters);
            // ignored stays unfiltered: it mostly contains metadata and nested repos.
        }

        return new StatusComputation(filesToCommit, filesToPush, filesToPull, tracked, untracked, undecided, ignored);
    }

    private static boolean isInsideAnyNestedRepo(String relPath, Set<String> nestedRepos) {
        if (relPath == null || nestedRepos == null || nestedRepos.isEmpty()) {
            return false;
        }
        String p = relPath.replace('\\', '/');
        for (String n : nestedRepos) {
            if (n == null || n.isBlank()) {
                continue;
            }
            String nn = n.replace('\\', '/');
            if (p.equals(nn) || p.startsWith(nn + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPathIgnored(String path, Set<String> ignored) {
        if (ignored == null || ignored.isEmpty()) {
            return false;
        }
        // Ignored entries may be decorated (e.g. "dir (repo)")
        for (String ig : ignored) {
            if (ig == null) {
                continue;
            }
            String normalized = ig;
            if (normalized.endsWith(" (repo)")) {
                normalized = normalized.substring(0, normalized.length() - " (repo)".length());
            }
            normalized = normalized.replace('\\', '/');
            String p = path.replace('\\', '/');
            if (p.equals(normalized) || p.startsWith(normalized + "/")) {
                return true;
            }
        }
        return false;
    }

    private static void addAll(Map<String, String> out, Set<String> paths, String letter) {
        if (paths == null) {
            return;
        }
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            out.put(p, letter);
        }
    }

    private static void computePushPullFileDiffs(
        Git git,
        String remoteUrlFromVgl,
        String remoteBranchFromVgl,
        Map<String, String> filesToPush,
        Map<String, String> filesToPull
    ) {
        if (git == null) {
            return;
        }

        String remoteUrl = remoteUrlFromVgl;
        String remoteBranch = remoteBranchFromVgl;

        boolean hasRemote = remoteUrl != null && !remoteUrl.isBlank();
        if (!hasRemote) {
            try {
                String originUrl = git.getRepository().getConfig().getString("remote", "origin", "url");
                if (originUrl != null && !originUrl.isBlank()) {
                    hasRemote = true;
                    remoteUrl = originUrl;
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }

        if (!hasRemote) {
            return;
        }

        String branch = (remoteBranch != null && !remoteBranch.isBlank()) ? remoteBranch : "main";

        var repo = git.getRepository();
        org.eclipse.jgit.lib.ObjectId localHead;
        org.eclipse.jgit.lib.ObjectId remoteHead;
        try {
            localHead = repo.resolve("HEAD");
            remoteHead = repo.resolve("origin/" + branch);
        } catch (Exception e) {
            return;
        }
        if (localHead == null || remoteHead == null) {
            return;
        }

        if (localHead.equals(remoteHead)) {
            return;
        }

        try {
            Iterable<RevCommit> toPush = git.log().add(localHead).not(remoteHead).call();
            for (RevCommit commit : toPush) {
                addCommitDiffFiles(repo, commit, filesToPush);
            }
        } catch (Exception ignored) {
            // best-effort
        }

        try {
            Iterable<RevCommit> toPull = git.log().add(remoteHead).not(localHead).call();
            for (RevCommit commit : toPull) {
                addCommitDiffFiles(repo, commit, filesToPull);
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static void addCommitDiffFiles(Repository repo, RevCommit commit, Map<String, String> out) throws IOException {
        try (RevWalk walk = new RevWalk(repo); ObjectReader reader = repo.newObjectReader()) {
            RevCommit parsed = walk.parseCommit(commit.getId());

            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, parsed.getTree());

            org.eclipse.jgit.treewalk.AbstractTreeIterator oldTree;
            if (parsed.getParentCount() > 0) {
                RevCommit parent = walk.parseCommit(parsed.getParent(0).getId());
                CanonicalTreeParser oldParser = new CanonicalTreeParser();
                oldParser.reset(reader, parent.getTree());
                oldTree = oldParser;
            } else {
                oldTree = new EmptyTreeIterator();
            }

            try (DiffFormatter df = new DiffFormatter(new java.io.ByteArrayOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                for (DiffEntry d : diffs) {
                    String letter = switch (d.getChangeType()) {
                        case ADD -> "A";
                        case MODIFY -> "M";
                        case DELETE -> "D";
                        case RENAME, COPY -> "R";
                        default -> "M";
                    };
                    String path = d.getChangeType() == DiffEntry.ChangeType.DELETE ? d.getOldPath() : d.getNewPath();
                    if (path != null && !path.isBlank()) {
                        out.putIfAbsent(path, letter);
                    }
                }
            }
        }
    }

    private static Map<String, String> filterCommitMap(Map<String, String> map, List<String> filters) {
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            if (matchesAnyFilter(e.getKey(), filters)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static Set<String> filterSet(Set<String> set, List<String> filters) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : set) {
            if (matchesAnyFilter(p, filters)) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean matchesAnyFilter(String path, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (String f : filters) {
            if (f == null || f.isBlank()) {
                continue;
            }
            if (f.contains("*") || f.contains("?")) {
                String regex = f.replace(".", "\\.").replace("*", ".*").replace("?", ".");
                if (path.matches(regex)) {
                    return true;
                }
            } else {
                if (path.equals(f) || path.startsWith(f + "/") || path.contains("/" + f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> extractFilters(List<String> args) {
        List<String> filters = new ArrayList<>();
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if (a.startsWith("-")) {
                continue;
            }
            filters.add(a);
        }
        return filters;
    }

    private static String safeGitBranch(Repository repo) {
        try {
            return repo.getBranch();
        } catch (Exception e) {
            return null;
        }
    }

    private static Properties readVglProps(Path repoRoot) {
        Properties props = new Properties();
        Path vgl = repoRoot.resolve(".vgl");
        if (!Files.isRegularFile(vgl)) {
            return props;
        }
        try (var in = Files.newInputStream(vgl)) {
            props.load(in);
        } catch (Exception ignored) {
            // best-effort
        }
        return props;
    }

    private static void writeVglProps(Path repoRoot, java.util.function.Consumer<Properties> mutator) throws java.io.IOException {
        Properties props = readVglProps(repoRoot);
        mutator.accept(props);
        Path vgl = repoRoot.resolve(".vgl");
        try (var out = Files.newOutputStream(vgl)) {
            props.store(out, "VGL state");
        }
    }

    private static void ensureGitignoreHasVgl(Path repoRoot) throws IOException {
        Path gitignore = repoRoot.resolve(".gitignore");
        String content = "";
        if (Files.isRegularFile(gitignore)) {
            content = Files.readString(gitignore, StandardCharsets.UTF_8);
        }

        if (!content.contains(".vgl")) {
            StringBuilder updated = new StringBuilder();
            if (!content.isBlank()) {
                updated.append(content);
                if (!content.endsWith("\n")) {
                    updated.append("\n");
                }
            }
            updated.append(".vgl\n");
            Files.writeString(gitignore, updated.toString(), StandardCharsets.UTF_8);
        }
    }

    private static RepoResolution resolveRepoForStatus(Path startDir) throws Exception {
        Path repoRoot = RepoUtils.findNearestRepoRoot(startDir);
        if (repoRoot == null) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return null;
        }
        repoRoot = repoRoot.toAbsolutePath().normalize();

        // Support worktree-style repos where .git is a file.
        boolean hasGit = Files.exists(repoRoot.resolve(".git"));
        boolean hasVgl = Files.isRegularFile(repoRoot.resolve(".vgl"));

        if (!hasGit && !hasVgl) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return null;
        }

        // If only one of (.git, .vgl) exists, offer to create the other.
        if (hasGit && !hasVgl) {
            if (!Utils.isInteractive()) {
                System.err.println(Messages.statusGitOnlyRepoHint(repoRoot));
                return null;
            }
            if (!Utils.confirm(Messages.statusConvertGitToVglPrompt(repoRoot))) {
                System.err.println(Messages.statusGitOnlyRepoHint(repoRoot));
                return null;
            }
            try (Git git = GitUtils.openGit(repoRoot)) {
                String branch = safeGitBranch(git.getRepository());
                final String localBranch = (branch == null || branch.isBlank()) ? "main" : branch;
                ensureGitignoreHasVgl(repoRoot);
                writeVglProps(repoRoot, props -> props.setProperty("local.branch", localBranch));
            }
            hasVgl = true;
        } else if (!hasGit && hasVgl) {
            if (!Utils.isInteractive()) {
                System.err.println(Messages.statusVglOnlyRepoHint(repoRoot));
                return null;
            }
            if (!Utils.confirm(Messages.statusInitGitFromVglPrompt(repoRoot))) {
                System.err.println(Messages.statusVglOnlyRepoHint(repoRoot));
                return null;
            }
            Properties props = readVglProps(repoRoot);
            String branch = props.getProperty("local.branch", "main");
            try (Git ignored = Git.init().setDirectory(repoRoot.toFile()).setInitialBranch(branch).call()) {
                // initialized
            }
            hasGit = true;
        }

        if (!hasGit || !hasVgl) {
            // User refused conversion or conversion failed.
            return null;
        }

        return new RepoResolution(repoRoot);
    }

    private record RepoResolution(Path repoRoot) {}

    private record StatusComputation(
        Map<String, String> filesToCommit,
        Map<String, String> filesToPush,
        Map<String, String> filesToPull,
        Set<String> tracked,
        Set<String> untracked,
        Set<String> undecided,
        Set<String> ignored
    ) {}
}
