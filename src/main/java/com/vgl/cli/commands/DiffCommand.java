package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.DiffHelper;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitAuth;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GitRemoteOps;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Diff between the workspace and a local/remote reference.
 *
 * <p>Supported modes (matches help text intent):
 * <ul>
 *   <li>Default: workspace vs HEAD</li>
 *   <li>-rb: workspace vs origin/&lt;branch&gt;</li>
 *   <li>-lb and -rb: local branch vs origin/&lt;branch&gt;</li>
 * </ul>
 */
public class DiffCommand implements Command {
    @Override
    public String name() {
        return "diff";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.diffUsage());
            return 0;
        }

        boolean noop = ArgsHelper.hasFlag(args, "-noop");

        List<String> positionals = collectPositionals(args);

        int verbosityLevel = args.contains("-vv") ? 2 : (args.contains("-v") ? 1 : 0);
        boolean showAll = args.contains("-all");

        // Remote-to-remote diff: `diff -rr URL0 -rb B0 -rr URL1 -rb B1` compares workspaces via temp clones.
        List<String> remoteUrls = valuesAfterFlagAllAllowMissing(args, "-rr");
        List<String> remoteBranches = valuesAfterFlagAllDefaultMain(args, "-rb");
        if (remoteUrls.size() >= 2) {
            String url1 = remoteUrls.get(0);
            String url2 = remoteUrls.get(1);
            if (url1 == null || url1.isBlank() || url2 == null || url2.isBlank()) {
                System.err.println(Messages.diffUsage());
                return 1;
            }
            String b1 = (remoteBranches.size() >= 1 && remoteBranches.get(0) != null && !remoteBranches.get(0).isBlank())
                ? remoteBranches.get(0)
                : "main";
            String b2 = (remoteBranches.size() >= 2 && remoteBranches.get(1) != null && !remoteBranches.get(1).isBlank())
                ? remoteBranches.get(1)
                : "main";

            List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;

            Path leftClone = null;
            Path rightClone = null;
            try {
                try {
                    leftClone = cloneRemoteToTemp(url1, b1);
                } catch (Exception e) {
                    if (e instanceof IllegalStateException && "auth required".equals(e.getMessage())) {
                        return 1;
                    }
                    if (GitAuth.handleMissingCredentialsProvider(e, url1, System.err)) {
                        return 1;
                    }
                    throw e;
                }
                try {
                    rightClone = cloneRemoteToTemp(url2, b2);
                } catch (Exception e) {
                    if (e instanceof IllegalStateException && "auth required".equals(e.getMessage())) {
                        return 1;
                    }
                    if (GitAuth.handleMissingCredentialsProvider(e, url2, System.err)) {
                        return 1;
                    }
                    throw e;
                }

                    if (noop) {
                        int changed = countWorkingTreeDiffBetweenRoots(leftClone, rightClone, globs);
                        System.out.println(Messages.diffDryRunSummary(changed));
                        return 0;
                    }

                    boolean truncate = !(args.contains("-v") || args.contains("-vv"));
                    String leftDisplay = "Remote: " + (truncate ? FormatUtils.truncateMiddle(FormatUtils.normalizeRemoteUrlForDisplay(url1), 35) : FormatUtils.normalizeRemoteUrlForDisplay(url1)) + " :: " + (b1 == null ? "main" : b1);
                    String rightDisplay = "Remote: " + (truncate ? FormatUtils.truncateMiddle(FormatUtils.normalizeRemoteUrlForDisplay(url2), 35) : FormatUtils.normalizeRemoteUrlForDisplay(url2)) + " :: " + (b2 == null ? "main" : b2);
                    System.out.println("Source:");
                    System.out.println("A: " + leftDisplay);
                    System.out.println("B: " + rightDisplay);

                    boolean any = DiffHelper.diffWorkingTrees(leftClone, rightClone, globs, DiffHelper.computeVerbosity(args), showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            } finally {
                deleteTreeQuietly(leftClone);
                deleteTreeQuietly(rightClone);
            }
        }

        // Cross-repo diff: `diff -lr R0 -lr R1` compares workspaces.
        List<Path> localRepoDirs = pathsAfterFlagAll(args, "-lr");
        if (localRepoDirs.size() >= 2) {
            Path left = localRepoDirs.get(0).toAbsolutePath().normalize();
            Path right = localRepoDirs.get(1).toAbsolutePath().normalize();
            List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;
            if (noop) {
                int changed = countWorkingTreeDiffBetweenRoots(left, right, globs);
                System.out.println(Messages.diffDryRunSummary(changed));
                return 0;
            }

            boolean truncate = !(args.contains("-v") || args.contains("-vv"));
            String leftDisplay = "Local: " + (truncate ? FormatUtils.truncateMiddle(Utils.formatPath(left), 35) : Utils.formatPath(left));
            String rightDisplay = "Local: " + (truncate ? FormatUtils.truncateMiddle(Utils.formatPath(right), 35) : Utils.formatPath(right));
            System.out.println("Source:");
            System.out.println("A: " + leftDisplay);
            System.out.println("B: " + rightDisplay);

            boolean any = DiffHelper.diffWorkingTrees(left, right, globs, DiffHelper.computeVerbosity(args), showAll);
            if (!any) {
                System.out.println("No differences.");
            }
            return 0;
        }

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        Path startDir = (explicitTargetDir != null) ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        // Commit-to-workspace diff: `diff COMMIT [GLOB ...]`.
        if (positionals.size() >= 1 && isCommitish(positionals.get(0))
            && !(positionals.size() >= 2 && isCommitish(positionals.get(1)))) {
            String commit = positionals.get(0);
            List<String> globs = (positionals.size() > 1) ? positionals.subList(1, positionals.size()) : List.of("*");
            try (Git git = GitUtils.openGit(repoRoot)) {
                Repository repo = git.getRepository();
                ObjectId commitTreeId = resolveTree(repo, commit);
                if (commitTreeId == null) {
                    System.err.println("ERROR: Cannot resolve " + commit);
                    return 1;
                }

                FileTreeIterator workingTree = new FileTreeIterator(repo);

                boolean humanReadable = verbosityLevel < 2;
                if (humanReadable && !noop) {
                    // 1 source => A is workspace, B is the specified source
                    boolean truncate = !(args.contains("-v") || args.contains("-vv"));
                    String localPath = Utils.formatPath(repoRoot);
                    String displayLocalPath = truncate ? FormatUtils.truncateMiddle(localPath, 35) : localPath;
                    String shortCommit = (commit.length() <= 10) ? commit : commit.substring(0, 10);
                    System.out.println("Source:");
                    System.out.println("A: (workspace)");
                    System.out.println("B: Local: " + displayLocalPath + " :: " + shortCommit);
                }

                DiffHelper.Verbosity dVerb = DiffHelper.computeVerbosity(args);
                if (noop) {
                    int changed = countWorkingToTreeDiff(repo, workingTree, commitTreeId, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }

                boolean any = DiffHelper.diffWorkingToTree(repo, workingTree, commitTreeId, globs, dVerb, showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        // Commit-to-commit diff: `diff COMMIT1 COMMIT2`.
        if (positionals.size() >= 2 && isCommitish(positionals.get(0)) && isCommitish(positionals.get(1))) {
            String commit1 = positionals.get(0);
            String commit2 = positionals.get(1);
            List<String> globs = (positionals.size() > 2) ? positionals.subList(2, positionals.size()) : List.of("*");
            try (Git git = GitUtils.openGit(repoRoot)) {
                Repository repo = git.getRepository();
                ObjectId t1 = resolveTree(repo, commit1);
                ObjectId t2 = resolveTree(repo, commit2);
                if (t1 == null || t2 == null) {
                    System.err.println("ERROR: Cannot resolve diff endpoints.");
                    return 1;
                }
                // Proceed to tree diff printing (respecting verbosity flags)
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = DiffHelper.diffTrees(repo, t1, t2, globs, DiffHelper.computeVerbosity(args), showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        List<String> localBranches = valuesAfterFlagAllDefaultMain(args, "-lb");
        if (localBranches.size() >= 2) {
            String b1 = localBranches.get(0);
            String b2 = localBranches.get(1);
            List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;
            try (Git git = GitUtils.openGit(repoRoot)) {
                Repository repo = git.getRepository();
                ObjectId t1 = resolveTree(repo, "refs/heads/" + b1);
                ObjectId t2 = resolveTree(repo, "refs/heads/" + b2);
                if (t1 == null || t2 == null) {
                    System.err.println("ERROR: Cannot resolve diff endpoints.");
                    return 1;
                }
                // Proceed to tree diff printing (respecting verbosity flags)
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = DiffHelper.diffTrees(repo, t1, t2, globs, DiffHelper.computeVerbosity(args), showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        if (localBranches.size() == 1) {
            String b = localBranches.get(0);
            List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;
            try (Git git = GitUtils.openGit(repoRoot)) {
                Repository repo = git.getRepository();
                ObjectId t1 = resolveTree(repo, "HEAD");
                ObjectId t2 = resolveTree(repo, "refs/heads/" + b);
                if (t1 == null || t2 == null) {
                    System.err.println("ERROR: Cannot resolve diff endpoints.");
                    return 1;
                }
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = DiffHelper.diffTrees(repo, t1, t2, globs, DiffHelper.computeVerbosity(args), showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        // Fall back to the original single-endpoint modes (workspace vs local/remote reference).
        // Default behavior: workspace vs local repo/branch from switch state.
        List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;

        Properties vglProps = VglConfig.readProps(repoRoot);
        String switchLocalBranch = vglProps.getProperty(VglConfig.KEY_LOCAL_BRANCH, "main");
        if (switchLocalBranch == null || switchLocalBranch.isBlank()) {
            switchLocalBranch = "main";
        }

        String localBranch = null;
        if (args.contains("-bb")) {
            String v = ArgsHelper.valueAfterFlag(args, "-bb");
            localBranch = (v == null || v.isBlank()) ? switchLocalBranch : v;
        } else if (args.contains("-lb")) {
            String v = ArgsHelper.valueAfterFlag(args, "-lb");
            localBranch = (v == null || v.isBlank()) ? switchLocalBranch : v;
        }

        boolean remoteRequested = args.contains("-rb") || args.contains("-rr");
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        String vglRemoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");
        if (vglRemoteBranch == null || vglRemoteBranch.isBlank()) {
            vglRemoteBranch = "main";
        }

        // If -rr is present but URL is missing, default to switch state remote URL.
        String explicitRemoteUrl = null;
        if (args.contains("-rr")) {
            String v = ArgsHelper.valueAfterFlag(args, "-rr");
            explicitRemoteUrl = (v == null || v.isBlank()) ? null : v;
        }
        String remoteUrlToUse = (explicitRemoteUrl != null) ? explicitRemoteUrl : vglRemoteUrl;

        // If -rb is present but branch is missing, default to switch state remote branch.
        String explicitRemoteBranch = null;
        if (args.contains("-rb")) {
            String v = ArgsHelper.valueAfterFlag(args, "-rb");
            explicitRemoteBranch = (v == null || v.isBlank()) ? null : v;
        }
        String remoteBranch = (explicitRemoteBranch != null) ? explicitRemoteBranch : vglRemoteBranch;

        boolean hasRemote = remoteRequested;
        boolean hasLocalBranch = localBranch != null && !localBranch.isBlank();

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            if (hasRemote) {
                if (remoteUrlToUse == null || remoteUrlToUse.isBlank()) {
                    System.err.println(Messages.pushNoRemoteConfigured());
                    return 1;
                }
                configureOriginRemote(repo, remoteUrlToUse);
                // best-effort fetch so origin/* exists for comparisons
                try {
                    GitRemoteOps.fetchOrigin(repoRoot, git, remoteUrlToUse, /*required*/false, System.err);
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            ObjectId oldTreeId;
            ObjectId newTreeId;
            FileTreeIterator workingTree = new FileTreeIterator(repo);

            boolean humanReadable = verbosityLevel < 2;
            DiffHelper.Verbosity dVerb = DiffHelper.computeVerbosity(args);

            if (hasRemote && hasLocalBranch) {
                if (humanReadable && !noop) {
                    printCompareSourceHeader(args, /*mode*/"local-vs-remote", repoRoot, localBranch, remoteUrlToUse, remoteBranch);
                }
                oldTreeId = resolveTree(repo, "refs/heads/" + localBranch);
                newTreeId = resolveTree(repo, "refs/remotes/origin/" + remoteBranch);
                if (oldTreeId == null || newTreeId == null) {
                    System.err.println("ERROR: Cannot resolve diff endpoints.");
                    return 1;
                }
                // Proceed to tree diff printing (respecting verbosity flags)
                if (noop) {
                    int changed = countTreeDiff(repo, oldTreeId, newTreeId, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = DiffHelper.diffTrees(repo, oldTreeId, newTreeId, globs, dVerb, showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }

            if (hasRemote) {
                if (humanReadable && !noop) {
                        printCompareSourceHeader(args, /*mode*/"remote-vs-working", repoRoot, localBranch, remoteUrlToUse, remoteBranch);
                    }
                oldTreeId = resolveTree(repo, "refs/remotes/origin/" + remoteBranch);
                if (oldTreeId == null) {
                    System.err.println("ERROR: Cannot resolve origin/" + remoteBranch);
                    return 1;
                }
                // Proceed to tree diff printing (respecting verbosity flags)
                if (noop) {
                    int changed = countTreeToWorkingDiff(repo, oldTreeId, workingTree, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = DiffHelper.diffTreeToWorking(repo, oldTreeId, workingTree, globs, dVerb, showAll);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }

            // Default: local ref vs workspace (switch state)
            if (humanReadable && !noop) {
                printCompareSourceHeader(args, /*mode*/"local-vs-working", repoRoot, localBranch, remoteUrlToUse, remoteBranch);
            }
            String ref = hasLocalBranch ? ("refs/heads/" + localBranch) : ("refs/heads/" + switchLocalBranch);
            oldTreeId = resolveTree(repo, ref);
            if (oldTreeId == null) {
                System.err.println("ERROR: Cannot resolve " + ref);
                return 1;
            }
            // Proceed to tree diff printing (respecting verbosity flags)
            
            if (noop) {
                int changed = countTreeToWorkingDiff(repo, oldTreeId, workingTree, globs);
                System.out.println(Messages.diffDryRunSummary(changed));
                return 0;
            }
            boolean any = DiffHelper.diffTreeToWorking(repo, oldTreeId, workingTree, globs, dVerb, showAll);
            if (!any) {
                System.out.println("No differences.");
            }
            return 0;
        }
    }

    private static void configureOriginRemote(Repository repo, String remoteUrl) {
        if (repo == null || remoteUrl == null || remoteUrl.isBlank()) {
            return;
        }
        try {
            StoredConfig cfg = repo.getConfig();
            cfg.setString("remote", "origin", "url", remoteUrl);
            // Ensure a fetch refspec so origin/* is populated.
            cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            cfg.save();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static Path cloneRemoteToTemp(String remoteUrl, String branch) throws Exception {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("remoteUrl is blank");
        }
        String b = (branch == null || branch.isBlank()) ? "main" : branch;
        Path base = tempBaseDir();
        Path dir = Files.createTempDirectory(base, "vgl-diff-remote-");
        boolean ok = GitRemoteOps.cloneInto(dir, remoteUrl, b, System.err);
        if (!ok) {
            deleteTreeQuietly(dir);
            // Auth was already reported; keep a consistent signal for callers.
            throw new IllegalStateException("auth required");
        }
        return dir;
    }

    private static Path tempBaseDir() throws IOException {
        String baseProp = System.getProperty("vgl.test.base");
        if (baseProp != null && !baseProp.isBlank()) {
            Path base = Path.of(baseProp).toAbsolutePath().normalize();
            Files.createDirectories(base);
            return base;
        }
        return Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null) {
            return;
        }
        try {
            if (!Files.exists(root)) {
                return;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception ignored) {
                        // best-effort
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.deleteIfExists(dir);
                    } catch (Exception ignored) {
                        // best-effort
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }

    

    private static int countTreeDiff(Repository repo, ObjectId oldTreeId, ObjectId newTreeId, List<String> globs) throws Exception {
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                int count = 0;
                for (DiffEntry d : diffs) {
                    if (matchesAny(d, globs)) {
                        count++;
                    }
                }
                return count;
            }
        }
    }

    private static int countTreeToWorkingDiff(Repository repo, ObjectId oldTreeId, FileTreeIterator workingTree, List<String> globs) throws Exception {
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> diffs = df.scan(oldTree, workingTree);
                int count = 0;
                for (DiffEntry d : diffs) {
                    if (matchesAny(d, globs)) {
                        count++;
                    }
                }
                return count;
            }
        }
    }

    private static int countWorkingToTreeDiff(Repository repo, FileTreeIterator workingTree, ObjectId newTreeId, List<String> globs) throws Exception {
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                List<DiffEntry> diffs = df.scan(workingTree, newTree);
                int count = 0;
                for (DiffEntry d : diffs) {
                    if (matchesAny(d, globs)) {
                        count++;
                    }
                }
                return count;
            }
        }
    }

    private static boolean isCommitish(String token) {
        if (token == null) {
            return false;
        }
        String t = token.trim();
        if (t.length() < 7 || t.length() > 40) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static List<String> valuesAfterFlagAllDefaultMain(List<String> args, String flag) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (!flag.equals(token)) {
                continue;
            }
            String v = (i + 1 < args.size()) ? args.get(i + 1) : null;
            if (v == null || v.startsWith("-")) {
                out.add("main");
            } else {
                out.add(v);
            }
        }
        return out;
    }

    private static List<String> valuesAfterFlagAllAllowMissing(List<String> args, String flag) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (!flag.equals(token)) {
                continue;
            }
            String v = (i + 1 < args.size()) ? args.get(i + 1) : null;
            if (v == null || v.startsWith("-")) {
                out.add(null);
            } else {
                out.add(v);
            }
        }
        return out;
    }

    private static List<Path> pathsAfterFlagAll(List<String> args, String flag) {
        List<Path> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (!flag.equals(token)) {
                continue;
            }
            String v = (i + 1 < args.size()) ? args.get(i + 1) : null;
            if (v == null || v.startsWith("-")) {
                continue;
            }
            out.add(Path.of(v));
        }
        return out;
    }

    private static boolean diffWorkingTrees(Path leftRoot, Path rightRoot, List<String> globs, int verbosityLevel) throws IOException {
        Map<String, byte[]> left = snapshotFiles(leftRoot, globs);
        Map<String, byte[]> right = snapshotFiles(rightRoot, globs);

        List<String> allPaths = new ArrayList<>();
        allPaths.addAll(left.keySet());
        for (String p : right.keySet()) {
            if (!left.containsKey(p)) {
                allPaths.add(p);
            }
        }
        allPaths.sort(String::compareTo);

        boolean any = false;
        int totalAdded = 0;
        int totalRemoved = 0;
        Map<String, int[]> perFileCounts = new HashMap<>();

        for (String rel : allPaths) {
            byte[] a = left.get(rel);
            byte[] b = right.get(rel);
            if (Arrays.equals(a, b)) {
                continue;
            }
            any = true;

            if (verbosityLevel >= 1) {
                String aName = "a/" + rel;
                String bName = "b/" + rel;
                System.out.println("diff --git " + aName + " " + bName);
                if (a == null) {
                    System.out.println("new file mode 100644");
                    System.out.println("--- /dev/null");
                    System.out.println("+++ " + bName);
                } else if (b == null) {
                    System.out.println("deleted file mode 100644");
                    System.out.println("--- " + aName);
                    System.out.println("+++ /dev/null");
                } else {
                    System.out.println("--- " + aName);
                    System.out.println("+++ " + bName);
                }

                if (a != null && RawText.isBinary(a) || b != null && RawText.isBinary(b)) {
                    System.out.println("Binary files differ");
                    continue;
                }

                RawText at = new RawText(a != null ? a : new byte[0]);
                RawText bt = new RawText(b != null ? b : new byte[0]);
                DiffAlgorithm alg = new HistogramDiff();
                EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
                if (verbosityLevel >= 2) {
                    try (DiffFormatter df = new DiffFormatter(System.out)) {
                        df.setContext(3);
                        df.format(edits, at, bt);
                    }
                } else {
                    int added = 0;
                    int removed = 0;
                    for (org.eclipse.jgit.diff.Edit e : edits) {
                        removed += Math.max(0, e.getEndA() - e.getBeginA());
                        added += Math.max(0, e.getEndB() - e.getBeginB());
                    }
                    perFileCounts.put(rel, new int[] {added, removed});
                    totalAdded += added;
                    totalRemoved += removed;
                }
            } else {
                // verbosityLevel == 0: collect counts without printing file headers now
                if (a != null && RawText.isBinary(a) || b != null && RawText.isBinary(b)) {
                    perFileCounts.put(rel, new int[] {0, 0});
                    continue;
                }
                RawText at = new RawText(a != null ? a : new byte[0]);
                RawText bt = new RawText(b != null ? b : new byte[0]);
                DiffAlgorithm alg = new HistogramDiff();
                EditList edits = alg.diff(RawTextComparator.DEFAULT, at, bt);
                int added = 0;
                int removed = 0;
                for (org.eclipse.jgit.diff.Edit e : edits) {
                    removed += Math.max(0, e.getEndA() - e.getBeginA());
                    added += Math.max(0, e.getEndB() - e.getBeginB());
                }
                perFileCounts.put(rel, new int[] {added, removed});
                totalAdded += added;
                totalRemoved += removed;
            }
        }

        if (verbosityLevel == 0 && any) {
            System.out.println(totalFileSummary(perFileCounts, totalAdded, totalRemoved));
            for (String rel : allPaths) {
                if (!perFileCounts.containsKey(rel)) continue;
                int[] ar = perFileCounts.get(rel);
                String kind = (left.get(rel) == null) ? "A" : (right.get(rel) == null) ? "D" : "M";
                System.out.println("  " + kind + " " + rel + " (+" + ar[0] + "/-" + ar[1] + ")");
            }
            System.out.println("\nUse `vgl diff -v` to see full diffs.");
        }

        return any;
    }

    private static String totalFileSummary(Map<String,int[]> perFileCounts, int totalAdded, int totalRemoved) {
        int files = perFileCounts.size();
        if (files == 1) {
            return "1 file(s) changed";
        }
        return files + " file(s) changed - +" + totalAdded + "/-" + totalRemoved;
    }

    private static int countWorkingTreeDiffBetweenRoots(Path leftRoot, Path rightRoot, List<String> globs) throws IOException {
        Map<String, byte[]> left = snapshotFiles(leftRoot, globs);
        Map<String, byte[]> right = snapshotFiles(rightRoot, globs);

        List<String> allPaths = new ArrayList<>();
        allPaths.addAll(left.keySet());
        for (String p : right.keySet()) {
            if (!left.containsKey(p)) {
                allPaths.add(p);
            }
        }

        int count = 0;
        for (String rel : allPaths) {
            byte[] a = left.get(rel);
            byte[] b = right.get(rel);
            if (!Arrays.equals(a, b)) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, byte[]> snapshotFiles(Path root, List<String> globs) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        if (root == null) {
            return out;
        }
        if (!Files.exists(root)) {
            return out;
        }
        Path absRoot = root.toAbsolutePath().normalize();

        Files.walk(absRoot)
            .filter(Files::isRegularFile)
            .forEach(p -> {
                Path rel = absRoot.relativize(p);
                String relStr = rel.toString().replace('\\', '/');
                if (relStr.startsWith(".git/") || relStr.equals(".vgl")) {
                    return;
                }
                if (!GlobUtils.matchesAny(relStr, globs)) {
                    return;
                }
                try {
                    out.put(relStr, Files.readAllBytes(p));
                } catch (IOException ignored) {
                    // best-effort
                }
            });

        return out;
    }

    private static ObjectId resolveTree(Repository repo, String treeish) {
        if (repo == null || treeish == null || treeish.isBlank()) {
            return null;
        }
        try {
            return repo.resolve(treeish + "^{tree}");
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean diffTreeToWorking(Repository repo, ObjectId oldTreeId, FileTreeIterator workingTree, List<String> globs, int verbosityLevel) throws Exception {
        boolean any = false;
        int totalAdded = 0;
        int totalRemoved = 0;
        Map<String, int[]> perFileCounts = new HashMap<>();
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(oldTree, workingTree);
                for (DiffEntry d : diffs) {
                    if (!matchesAny(d, globs)) {
                        continue;
                    }
                    any = true;
                    if (verbosityLevel >= 1) {
                        try (DiffFormatter dfOut = new DiffFormatter(System.out)) {
                            dfOut.setRepository(repo);
                            dfOut.setDetectRenames(true);
                            dfOut.format(d);
                        }
                    } else {
                        // summary mode - compute edits counts
                        try (DiffFormatter dfHdr = new DiffFormatter(OutputStream.nullOutputStream())) {
                            dfHdr.setRepository(repo);
                            dfHdr.setDetectRenames(true);
                            var fh = dfHdr.toFileHeader(d);
                            EditList edits = fh.toEditList();
                            int added = 0;
                            int removed = 0;
                            for (org.eclipse.jgit.diff.Edit e : edits) {
                                removed += Math.max(0, e.getEndA() - e.getBeginA());
                                added += Math.max(0, e.getEndB() - e.getBeginB());
                            }
                            String path = d.getNewPath();
                            if (path == null || path.equals("/dev/null")) {
                                path = d.getOldPath();
                            }
                            perFileCounts.put(path, new int[] {added, removed});
                            totalAdded += added;
                            totalRemoved += removed;
                        }
                    }
                }
            }
        }

        if (verbosityLevel == 0 && any) {
            System.out.println(totalFileSummary(perFileCounts, totalAdded, totalRemoved));
            for (Map.Entry<String,int[]> e : perFileCounts.entrySet()) {
                int[] ar = e.getValue();
                // infer change type: additions where old path is /dev/null not tracked here, keep M/A/D generic
                System.out.println("  M " + e.getKey() + " (+" + ar[0] + "/-" + ar[1] + ")");
            }
            System.out.println("\nUse `vgl diff -v` to see full diffs.");
        }
        return any;
    }

    private static void printCompareSourceHeader(List<String> args, String mode, Path repoRoot, String localBranch, String remoteUrl, String remoteBranch) {
        printCompareSourceHeader(args, mode, repoRoot, localBranch, remoteUrl, remoteBranch, null);
    }

    private static void printCompareSourceHeader(
        List<String> args,
        String mode,
        Path repoRoot,
        String localBranch,
        String remoteUrl,
        String remoteBranch,
        String commit
    ) {
        // Print A/B source lines on separate lines. Truncate paths/URLs in default mode
        // to match the compact status/switch output; do not truncate when -v or -vv present.
        boolean truncate = !(args.contains("-v") || args.contains("-vv"));

        String localPath = (repoRoot == null) ? "(unknown)" : Utils.formatPath(repoRoot);
        String displayLocalPath = truncate ? FormatUtils.truncateMiddle(localPath, 35) : localPath;

        String displayRemoteUrl = FormatUtils.normalizeRemoteUrlForDisplay(remoteUrl == null ? "" : remoteUrl);
        displayRemoteUrl = truncate ? FormatUtils.truncateMiddle(displayRemoteUrl, 35) : displayRemoteUrl;

        switch (mode) {
            case "local-vs-remote": {
                String left = "Local: " + displayLocalPath + " :: " + ((localBranch == null || localBranch.isBlank()) ? "main" : localBranch);
                String right = "Remote: " + (displayRemoteUrl.isBlank() ? "(none)" : displayRemoteUrl) + " :: " + ((remoteBranch == null || remoteBranch.isBlank()) ? "main" : remoteBranch);
                System.out.println("Source:");
                System.out.println("A: " + left);
                System.out.println("B: " + right);
                break;
            }
            case "remote-vs-working": {
                String left = "Remote: " + (displayRemoteUrl.isBlank() ? "(none)" : displayRemoteUrl) + " :: " + ((remoteBranch == null || remoteBranch.isBlank()) ? "main" : remoteBranch);
                String right = "(workspace)";
                System.out.println("Source:");
                System.out.println("A: " + left);
                System.out.println("B: " + right);
                break;
            }
            case "commit-vs-working": {
                String shortCommit = (commit == null) ? "(unknown)" : (commit.length() <= 10 ? commit : commit.substring(0, 10));
                String left = "Local: " + displayLocalPath + " :: " + shortCommit;
                String right = "(workspace)";
                System.out.println("Source:");
                System.out.println("A: " + left);
                System.out.println("B: " + right);
                break;
            }
            case "local-vs-working":
            default: {
                String left = "Local: " + displayLocalPath + " :: " + ((localBranch == null || localBranch.isBlank()) ? "main" : localBranch);
                String right = "(workspace)";
                System.out.println("Source:");
                System.out.println("A: " + left);
                System.out.println("B: " + right);
                break;
            }
        }
    }

    private static boolean diffTrees(Repository repo, ObjectId oldTreeId, ObjectId newTreeId, List<String> globs, int verbosityLevel) throws Exception {
        boolean any = false;
        int totalAdded = 0;
        int totalRemoved = 0;
        Map<String, int[]> perFileCounts = new HashMap<>();
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);

            try (DiffFormatter df = new DiffFormatter(OutputStream.nullOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                for (DiffEntry d : diffs) {
                    if (!matchesAny(d, globs)) {
                        continue;
                    }
                    any = true;
                    if (verbosityLevel >= 1) {
                        try (DiffFormatter dfOut = new DiffFormatter(System.out)) {
                            dfOut.setRepository(repo);
                            dfOut.setDetectRenames(true);
                            dfOut.format(d);
                        }
                    } else {
                        var fh = df.toFileHeader(d);
                        EditList edits = fh.toEditList();
                        int added = 0;
                        int removed = 0;
                        for (org.eclipse.jgit.diff.Edit e : edits) {
                            removed += Math.max(0, e.getEndA() - e.getBeginA());
                            added += Math.max(0, e.getEndB() - e.getBeginB());
                        }
                        String path = d.getNewPath();
                        if (path == null || path.equals("/dev/null")) {
                            path = d.getOldPath();
                        }
                        perFileCounts.put(path, new int[] {added, removed});
                        totalAdded += added;
                        totalRemoved += removed;
                    }
                }
            }
        }

        if (verbosityLevel == 0 && any) {
            System.out.println(totalFileSummary(perFileCounts, totalAdded, totalRemoved));
            for (Map.Entry<String,int[]> e : perFileCounts.entrySet()) {
                int[] ar = e.getValue();
                System.out.println("  M " + e.getKey() + " (+" + ar[0] + "/-" + ar[1] + ")");
            }
            System.out.println("\nUse `vgl diff -v` to see full diffs.");
        }
        return any;
    }

    private static boolean matchesAny(DiffEntry d, List<String> globs) {
        if (d == null) {
            return false;
        }
        String a = d.getOldPath();
        String b = d.getNewPath();
        return GlobUtils.matchesAny(a, globs) || GlobUtils.matchesAny(b, globs);
    }

    private static List<String> collectPositionals(List<String> args) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }

        List<String> flagsWithValue = List.of("-lr", "-lb", "-bb", "-rr", "-rb");
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null) {
                continue;
            }
            if (token.startsWith("-")) {
                if (flagsWithValue.contains(token)) {
                    i++;
                }
                continue;
            }
            out.add(token);
        }
        return out;
    }
}
