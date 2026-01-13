package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.StatusFileSummary;
import com.vgl.cli.commands.helpers.StatusVerboseOutput;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.RepoValidation;
import com.vgl.cli.utils.RepoPreflight;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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

        String contextDiscoveryQuery = parseOptionalValue(args, "-context");
        if (contextDiscoveryQuery != null && !contextDiscoveryQuery.isBlank()) {
            return runContextDiscovery(contextDiscoveryQuery.trim());
        }

        boolean showContext = args.contains("-context");
        boolean showChanges = args.contains("-changes") || args.contains("-commits");
        boolean showHistory = args.contains("-history");
        boolean showFiles = args.contains("-files");

        boolean anySectionFlag = showContext || showChanges || showHistory || showFiles;

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        RepoResolution resolved = resolveRepoForStatus(cwd);
        if (resolved == null) {
            return 1;
        }

        Path repoRoot = resolved.repoRoot;

        if (!RepoPreflight.preflight(repoRoot)) {
            return 1;
        }

        RepoValidation.Result validation = RepoValidation.validateRepoAt(repoRoot);
        if (validation instanceof RepoValidation.Result.None) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return 1;
        }
        if (validation instanceof RepoValidation.Result.Malformed m) {
            System.err.println(Messages.malformedRepo(repoRoot, m.problem()));
            return 1;
        }
        if (validation instanceof RepoValidation.Result.InconsistentBranches) {
            // Non-fatal: VGL prefers the actual Git branch at runtime.
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

            String localDir = Utils.formatPath(repoRoot);
            String displayLocalDir = (verbose || veryVerbose) ? localDir : FormatUtils.truncateMiddle(localDir, maxPathLen);
            String remoteUrlDisplaySource = (remoteUrl != null && !remoteUrl.isBlank())
                ? FormatUtils.normalizeRemoteUrlForDisplay(remoteUrl)
                : "";
            String displayRemoteUrl = (remoteUrlDisplaySource != null && !remoteUrlDisplaySource.isBlank())
                ? ((verbose || veryVerbose) ? remoteUrlDisplaySource : FormatUtils.truncateMiddle(remoteUrlDisplaySource, maxPathLen))
                : "(none)";

            int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());

            String changesLabel = "CHANGES:";
            String historyLabel = "HISTORY:";
            String filesLabel = "FILES:";
            int labelWidth = Math.max(Math.max(changesLabel.length(), historyLabel.length()), filesLabel.length());

            String changesLabelPad = FormatUtils.padRight(changesLabel, labelWidth + 1);
            String historyLabelPad = FormatUtils.padRight(historyLabel, labelWidth + 1);
            String filesLabelPad = FormatUtils.padRight(filesLabel, labelWidth + 1);

            StatusComputation computed = computeStatus(git, repoRoot, remoteUrl, remoteBranch);
            CommitDeltas deltas = computeCommitDeltas(git, remoteUrl, remoteBranch);

            if (anySectionFlag) {
                if (showContext) {
                    printContextSection(git, repoRoot, displayLocalDir, localBranch, vglLocalBranches, displayRemoteUrl, remoteUrlDisplaySource, remoteBranch, verbose, veryVerbose, separator, maxLen);
                }
                if (showChanges) {
                    printChangesSection(changesLabelPad, computed, deltas, verbose, veryVerbose);
                }
                if (showHistory) {
                    printHistorySection(historyLabelPad, deltas, verbose, veryVerbose);
                }
                if (showFiles) {
                    printFilesSection(filesLabelPad, computed, verbose, veryVerbose, repoRoot, List.of());
                }
            } else {
                printContextSection(git, repoRoot, displayLocalDir, localBranch, vglLocalBranches, displayRemoteUrl, remoteUrlDisplaySource, remoteBranch, verbose, veryVerbose, separator, maxLen);
                printChangesSection(changesLabelPad, computed, deltas, verbose, veryVerbose);
                printHistorySection(historyLabelPad, deltas, verbose, veryVerbose);
                printFilesSection(filesLabelPad, computed, verbose, veryVerbose, repoRoot, List.of());
            }

            return 0;
        } catch (Exception e) {
            System.err.println(Messages.malformedRepo(repoRoot, e.getMessage()));
            return 1;
        }
    }

    private static String parseOptionalValue(List<String> args, String optionName) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        for (int i = 0; i < args.size(); i++) {
            if (!optionName.equals(args.get(i))) {
                continue;
            }
            if (i + 1 >= args.size()) {
                return "";
            }
            String next = args.get(i + 1);
            if (next == null || next.isBlank() || next.startsWith("-")) {
                return "";
            }
            return next;
        }
        return null;
    }

    private static int runContextDiscovery(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return 1;
        }

        GithubTarget target = GithubTarget.tryParse(userQuery.trim());
        if (target == null) {
            System.err.println("Could not discover remotes at: " + userQuery.trim());
            System.err.println("Reason: only GitHub URLs are supported for -context URL.");
            System.err.println(
                "Hint: Use a GitHub org/user URL like https://github.com/org, or a repo URL like https://github.com/org/repo."
            );
            return 1;
        }

        String displayRoot = "github.com/" + target.owner + (target.repo != null ? ("/" + target.repo) : "");
        System.out.println("Remote repos discovered at: " + displayRoot);

        try {
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            if (target.repo != null) {
                List<String> branches = githubListBranches(client, mapper, target.owner, target.repo);
                System.out.println("  " + target.repo + ":");
                System.out.println("    Branches:");
                com.vgl.cli.commands.helpers.StatusVerboseOutput.printWrappedColumns(branches, "      ");
                System.out.println();
                System.out.println("Then: vgl switch -rr https://github.com/" + target.owner + "/" + target.repo + " -rb <branch>");
                return 0;
            }

            List<String> repos = githubListRepos(client, mapper, target.owner);
            if (repos.isEmpty()) {
                System.out.println("  (none)");
                return 0;
            }

            for (String repo : repos) {
                if (repo == null || repo.isBlank()) {
                    continue;
                }
                System.out.println("  " + repo + ":");
                try {
                    List<String> branches = githubListBranches(client, mapper, target.owner, repo);
                    System.out.println("    Branches:");
                    com.vgl.cli.commands.helpers.StatusVerboseOutput.printWrappedColumns(branches, "      ");
                } catch (Exception e) {
                    System.out.println("    Branches:");
                    System.out.println("      (unknown)");
                }
            }

            System.out.println();
            System.out.println("Then: vgl switch -rr https://github.com/" + target.owner + "/<repo> -rb <branch>");
            return 0;
        } catch (Exception e) {
            System.err.println("Could not discover remotes at: " + userQuery.trim());
            System.err.println("Reason: " + e.getMessage());
            System.err.println("Hint: If you hit GitHub rate limits, try again later or use a narrower URL (owner/repo)."
            );
            return 1;
        }
    }

    private static String formatBranchSummary(List<String> branches) {
        if (branches == null || branches.isEmpty()) {
            return "(none)";
        }
        List<String> cleaned = new ArrayList<>();
        for (String b : branches) {
            if (b != null && !b.isBlank()) {
                cleaned.add(b.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return "(none)";
        }
        java.util.Collections.sort(cleaned);
        int max = 6;
        if (cleaned.size() <= max) {
            return String.join(" ", cleaned);
        }
        return String.join(" ", cleaned.subList(0, max)) + " ...";
    }

    private static List<String> githubListRepos(HttpClient client, ObjectMapper mapper, String owner) throws Exception {
        // GitHub supports both users and orgs; try /users first, then /orgs.
        String[] endpoints = new String[] {
            "https://api.github.com/users/" + owner + "/repos?per_page=100&type=all",
            "https://api.github.com/orgs/" + owner + "/repos?per_page=100&type=all"
        };

        for (String url : endpoints) {
            HttpResponse<String> response = httpGet(client, url);
            if (response.statusCode() == 404) {
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub API returned " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            List<String> repos = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode n : root) {
                    if (n == null) {
                        continue;
                    }
                    JsonNode name = n.get("name");
                    if (name != null && name.isTextual()) {
                        repos.add(name.asText());
                    }
                }
            }
            java.util.Collections.sort(repos);
            return repos;
        }

        return List.of();
    }

    private static List<String> githubListBranches(HttpClient client, ObjectMapper mapper, String owner, String repo) throws Exception {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/branches?per_page=100";
        HttpResponse<String> response = httpGet(client, url);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API returned " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<String> branches = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode n : root) {
                if (n == null) {
                    continue;
                }
                JsonNode name = n.get("name");
                if (name != null && name.isTextual()) {
                    branches.add(name.asText());
                }
            }
        }
        return branches;
    }

    private static HttpResponse<String> httpGet(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "vgl")
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class GithubTarget {
        final String owner;
        final String repo;

        private GithubTarget(String owner, String repo) {
            this.owner = owner;
            this.repo = repo;
        }

        static GithubTarget tryParse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String s = raw.trim();

            // git@github.com:owner/repo(.git)
            if (s.startsWith("git@") && s.contains("github.com") && s.contains(":")) {
                int colon = s.indexOf(':');
                String path = s.substring(colon + 1);
                return fromPath(path);
            }

            String url = s;
            if (!url.contains("://")) {
                url = "https://" + url;
            }
            URI uri;
            try {
                uri = URI.create(url);
            } catch (Exception e) {
                return null;
            }
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase("github.com")) {
                return null;
            }
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            return fromPath(path);
        }

        private static GithubTarget fromPath(String pathRaw) {
            if (pathRaw == null) {
                return null;
            }
            String path = pathRaw.trim();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.isBlank()) {
                return null;
            }
            String[] parts = path.split("/");
            List<String> segs = new ArrayList<>();
            for (String p : parts) {
                if (p != null && !p.isBlank()) {
                    segs.add(p);
                }
            }
            if (segs.isEmpty()) {
                return null;
            }
            String owner = segs.get(0);
            if (owner.isBlank()) {
                return null;
            }
            String repo = null;
            if (segs.size() >= 2) {
                repo = segs.get(1);
                if (repo.endsWith(".git")) {
                    repo = repo.substring(0, repo.length() - 4);
                }
                if (repo.isBlank()) {
                    repo = null;
                }
            }
            return new GithubTarget(owner, repo);
        }
    }

    private static void printContextSection(
        Git git,
        Path repoRoot,
        String displayLocalDir,
        String localBranch,
        Set<String> vglLocalBranches,
        String displayRemoteUrl,
        String remoteUrl,
        String remoteBranch,
        boolean verbose,
        boolean veryVerbose,
        String separator,
        int maxLen
    ) {
        System.out.println("CONTEXT:");

        int contextLabelWidth = Math.max("Local:".length(), "Remote:".length());
        String localLabelPad = "  " + FormatUtils.padRight("Local:", contextLabelWidth + 1);
        String remoteLabelPad = "  " + FormatUtils.padRight("Remote:", contextLabelWidth + 1);

        printLocalSection(
            git,
            repoRoot,
            displayLocalDir,
            localBranch,
            vglLocalBranches,
            verbose,
            veryVerbose,
            localLabelPad,
            separator,
            maxLen
        );
        printRemoteSection(git, displayRemoteUrl, remoteUrl, remoteBranch, verbose, veryVerbose, remoteLabelPad, separator, maxLen);
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
            System.out.println(labelPad + displayLocalDir + separator + (localBranch != null ? localBranch : "(none)"));
            if (git != null) {
                try {
                    System.out.println("    Branches:");
                    Repository repo = git.getRepository();
                    String current = safeGitBranch(repo);

                    boolean headResolves;
                    try {
                        headResolves = repo.resolve(Constants.HEAD) != null;
                    } catch (Exception e) {
                        headResolves = false;
                    }

                    java.util.Set<String> union = new java.util.LinkedHashSet<>();
                    java.util.List<String> gitBranches = listLocalBranches(git);
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

                    StatusVerboseOutput.printWrappedColumns(entries, "      ");
                } catch (Exception ignored) {
                    // best-effort
                    System.out.println("    Branches:");
                    String fallback = (localBranch != null && !localBranch.isBlank()) ? localBranch : "main";
                    StatusVerboseOutput.printWrappedColumns(java.util.List.of("* " + fallback), "      ");
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
                    System.out.println("    Branches:");
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
                    StatusVerboseOutput.printWrappedColumns(entries, "      ");
                } catch (Exception ignored) {
                    // best-effort
                    System.out.println("    Branches:");
                    String currentRemote = (remoteBranch != null && !remoteBranch.isBlank()) ? remoteBranch : "main";
                    StatusVerboseOutput.printWrappedColumns(java.util.List.of("* " + currentRemote), "      ");
                }
            }
            return;
        }

        System.out.println(labelPad + FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + (remoteBranch != null ? remoteBranch : "(none)"));
    }

    private static void printChangesSection(
        String changesLabelPad,
        StatusComputation computed,
        CommitDeltas deltas,
        boolean verbose,
        boolean veryVerbose
    ) {
        int commitsToPush = (deltas != null) ? deltas.localOnly.size() : 0;
        int commitsToPull = (deltas != null && deltas.hasComparableRemote) ? deltas.remoteOnly.size() : 0;

        int filesToCommit = (computed != null && computed.filesToCommit != null) ? computed.filesToCommit.size() : 0;

        System.out.println(
            changesLabelPad
                + "Commit " + filesToCommit + " " + pluralize(filesToCommit, "file", "files")
                + ", Push " + commitsToPush + " " + pluralize(commitsToPush, "commit", "commits")
                + ", Pull " + commitsToPull + " " + pluralize(commitsToPull, "commit", "commits")
        );

        if (!(verbose || veryVerbose)) {
            return;
        }

        printCommitMap("-- Files to Commit:", computed.filesToCommit);

        if (veryVerbose) {
            printCommitList("-- Commits to Push:", deltas != null ? deltas.localOnly : java.util.List.of(), true);
            printCommitList("-- Commits to Pull:", deltas != null ? deltas.remoteOnly : java.util.List.of(), true);
        }
    }

    private static void printHistorySection(
        String historyLabelPad,
        CommitDeltas deltas,
        boolean verbose,
        boolean veryVerbose
    ) {
        int localCount = (deltas != null) ? deltas.localOnly.size() : 0;
        int remoteCount = (deltas != null && deltas.hasComparableRemote) ? deltas.remoteOnly.size() : 0;

        System.out.println(
            historyLabelPad
                + localCount + " local " + pluralize(localCount, "commit", "commits")
                + ", "
                + remoteCount + " remote " + pluralize(remoteCount, "commit", "commits")
        );

        if (!(verbose || veryVerbose)) {
            return;
        }

        boolean showDetail = veryVerbose;
        printCommitList("-- Local-only Commits:", deltas != null ? deltas.localOnly : java.util.List.of(), showDetail);
        printCommitList("-- Remote-only Commits:", deltas != null ? deltas.remoteOnly : java.util.List.of(), showDetail);
    }

    private static void printCommitList(String header, java.util.List<RevCommit> commits, boolean veryVerbose) {
        System.out.println(header);
        if (commits == null || commits.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        for (RevCommit c : commits) {
            System.out.println("  " + (veryVerbose ? formatCommitLineVeryVerbose(c) : formatCommitLine(c, false)));
        }
    }

    private static String pluralize(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
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

    private static String formatCommitLineVeryVerbose(RevCommit commit) {
        if (commit == null) {
            return "";
        }
        String id = commit.getName();
        String shortId = (id != null && id.length() >= 7) ? id.substring(0, 7) : (id != null ? id : "");

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault());
        String date = fmt.format(java.time.Instant.ofEpochSecond(commit.getCommitTime()));

        String author = (commit.getAuthorIdent() != null) ? commit.getAuthorIdent().getName() : "";
        String msg = commit.getFullMessage();
        String oneLine = (msg == null) ? "" : msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();

        if (!author.isBlank()) {
            if (oneLine.isBlank()) {
                return shortId + "  " + date + "  " + author;
            }
            return shortId + "  " + date + "  " + author + "  " + oneLine;
        }
        if (oneLine.isBlank()) {
            return shortId + "  " + date;
        }
        return shortId + "  " + date + "  " + oneLine;
    }

    private static CommitDeltas computeCommitDeltas(Git git, String remoteUrlFromVgl, String remoteBranchFromVgl) {
        if (git == null) {
            return new CommitDeltas(false, false, java.util.List.of(), java.util.List.of());
        }

        Repository repo = git.getRepository();
        if (repo == null || !GitUtils.hasCommits(repo)) {
            return new CommitDeltas(false, false, java.util.List.of(), java.util.List.of());
        }

        boolean hasRemoteConfigured = remoteUrlFromVgl != null && !remoteUrlFromVgl.isBlank();
        if (!hasRemoteConfigured) {
            try {
                String originUrl = repo.getConfig().getString("remote", "origin", "url");
                if (originUrl != null && !originUrl.isBlank()) {
                    hasRemoteConfigured = true;
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }

        String remoteBranch = (remoteBranchFromVgl != null && !remoteBranchFromVgl.isBlank()) ? remoteBranchFromVgl : "main";

        org.eclipse.jgit.lib.ObjectId localHead;
        org.eclipse.jgit.lib.ObjectId remoteHead;
        try {
            localHead = repo.resolve("HEAD");
            remoteHead = hasRemoteConfigured ? repo.resolve("origin/" + remoteBranch) : null;
        } catch (Exception e) {
            localHead = null;
            remoteHead = null;
        }

        boolean hasComparableRemote = hasRemoteConfigured && remoteHead != null;

        if (!hasComparableRemote) {
            java.util.List<RevCommit> local = new java.util.ArrayList<>();
            try {
                for (RevCommit c : git.log().call()) {
                    local.add(c);
                }
            } catch (Exception ignored) {
                // best-effort
            }
            return new CommitDeltas(hasRemoteConfigured, false, local, java.util.List.of());
        }

        java.util.List<RevCommit> toPush = new java.util.ArrayList<>();
        java.util.List<RevCommit> toPull = new java.util.ArrayList<>();

        try {
            Iterable<RevCommit> it = (localHead != null) ? git.log().add(localHead).not(remoteHead).call() : java.util.List.of();
            for (RevCommit c : it) {
                toPush.add(c);
            }
        } catch (Exception ignored) {
            // best-effort
        }

        try {
            Iterable<RevCommit> it = (localHead != null) ? git.log().add(remoteHead).not(localHead).call() : java.util.List.of();
            for (RevCommit c : it) {
                toPull.add(c);
            }
        } catch (Exception ignored) {
            // best-effort
        }

        return new CommitDeltas(true, true, toPush, toPull);
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
                Utils.formatPath(repoRoot),
                filters
            );
        }

        if (veryVerbose) {
            StatusVerboseOutput.printVeryVerbose(
                computed.tracked,
                computed.untracked,
                computed.ignored,
                Utils.formatPath(repoRoot),
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

    private static List<String> listLocalBranches(Git git) {
        if (git == null) {
            return List.of();
        }
        try {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Ref r : git.branchList().call()) {
                if (r == null) {
                    continue;
                }
                String name = r.getName();
                if (name == null || !name.startsWith(Constants.R_HEADS)) {
                    continue;
                }
                String shortName = name.substring(Constants.R_HEADS.length());
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

    private static StatusComputation computeStatus(Git git, Path repoRoot, String remoteUrl, String remoteBranch) throws Exception {
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

    private record CommitDeltas(
        boolean hasRemoteConfigured,
        boolean hasComparableRemote,
        java.util.List<RevCommit> localOnly,
        java.util.List<RevCommit> remoteOnly
    ) {}
}
