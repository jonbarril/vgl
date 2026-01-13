package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitAuth;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
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

/**
 * Diff between the working tree and a local/remote reference.
 *
 * <p>Supported modes (matches help text intent):
 * <ul>
 *   <li>Default: working tree vs HEAD</li>
 *   <li>-rb: working tree vs origin/&lt;branch&gt;</li>
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

        // Remote-to-remote diff: `diff -rr URL0 -rb B0 -rr URL1 -rb B1` compares working trees via temp clones.
        List<String> remoteUrls = valuesAfterFlagAll(args, "-rr");
        List<String> remoteBranches = valuesAfterFlagAll(args, "-rb");
        if (remoteUrls.size() >= 2) {
            String url1 = remoteUrls.get(0);
            String url2 = remoteUrls.get(1);
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
                leftClone = cloneRemoteToTemp(url1, b1);
                rightClone = cloneRemoteToTemp(url2, b2);

                if (noop) {
                    int changed = countWorkingTreeDiffBetweenRoots(leftClone, rightClone, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }

                boolean any = diffWorkingTrees(leftClone, rightClone, globs);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            } finally {
                deleteTreeQuietly(leftClone);
                deleteTreeQuietly(rightClone);
            }
        }

        // Cross-repo diff: `diff -lr R0 -lr R1` compares working trees.
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

            boolean any = diffWorkingTrees(left, right, globs);
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
                    System.err.println("Error: Cannot resolve diff endpoints.");
                    return 1;
                }
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = diffTrees(repo, t1, t2, globs);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        List<String> localBranches = valuesAfterFlagAll(args, "-lb");
        if (localBranches.size() >= 2) {
            String b1 = localBranches.get(0);
            String b2 = localBranches.get(1);
            List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;
            try (Git git = GitUtils.openGit(repoRoot)) {
                Repository repo = git.getRepository();
                ObjectId t1 = resolveTree(repo, "refs/heads/" + b1);
                ObjectId t2 = resolveTree(repo, "refs/heads/" + b2);
                if (t1 == null || t2 == null) {
                    System.err.println("Error: Cannot resolve diff endpoints.");
                    return 1;
                }
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = diffTrees(repo, t1, t2, globs);
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
                    System.err.println("Error: Cannot resolve diff endpoints.");
                    return 1;
                }
                if (noop) {
                    int changed = countTreeDiff(repo, t1, t2, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = diffTrees(repo, t1, t2, globs);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }
        }

        // Fall back to the original single-endpoint modes (working tree vs local/remote reference).
        List<String> globs = positionals.isEmpty() ? List.of("*") : positionals;

        String localBranch = ArgsHelper.branchFromArgsOrNull(args);
        String remoteBranch = ArgsHelper.valueAfterFlag(args, "-rb");
        if (args.contains("-rb") && (remoteBranch == null || remoteBranch.isBlank())) {
            remoteBranch = "main";
        }

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        if (remoteUrls.size() == 1 && remoteUrls.get(0) != null && !remoteUrls.get(0).isBlank()) {
            vglRemoteUrl = remoteUrls.get(0);
        }
        if (remoteBranch == null || remoteBranch.isBlank()) {
            remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");
        }

        boolean hasRemote = args.contains("-rb") || args.contains("-rr") || (vglRemoteUrl != null && !vglRemoteUrl.isBlank());
        boolean hasLocalBranch = localBranch != null && !localBranch.isBlank();

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            if (hasRemote) {
                if (vglRemoteUrl == null || vglRemoteUrl.isBlank()) {
                    System.err.println(Messages.pushNoRemoteConfigured());
                    return 1;
                }
                configureOriginRemote(repo, vglRemoteUrl);
                // best-effort fetch so origin/* exists for comparisons
                try {
                    git.fetch().setRemote("origin").call();
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            ObjectId oldTreeId;
            ObjectId newTreeId;
            FileTreeIterator workingTree = new FileTreeIterator(repo);

            if (hasRemote && hasLocalBranch) {
                oldTreeId = resolveTree(repo, "refs/heads/" + localBranch);
                newTreeId = resolveTree(repo, "refs/remotes/origin/" + remoteBranch);
                if (oldTreeId == null || newTreeId == null) {
                    System.err.println("Error: Cannot resolve diff endpoints.");
                    return 1;
                }
                if (noop) {
                    int changed = countTreeDiff(repo, oldTreeId, newTreeId, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = diffTrees(repo, oldTreeId, newTreeId, globs);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }

            if (hasRemote) {
                oldTreeId = resolveTree(repo, "refs/remotes/origin/" + remoteBranch);
                if (oldTreeId == null) {
                    System.err.println("Error: Cannot resolve origin/" + remoteBranch);
                    return 1;
                }
                if (noop) {
                    int changed = countTreeToWorkingDiff(repo, oldTreeId, workingTree, globs);
                    System.out.println(Messages.diffDryRunSummary(changed));
                    return 0;
                }
                boolean any = diffTreeToWorking(repo, oldTreeId, workingTree, globs);
                if (!any) {
                    System.out.println("No differences.");
                }
                return 0;
            }

            // Default: local ref vs working tree
            String ref = hasLocalBranch ? ("refs/heads/" + localBranch) : "HEAD";
            oldTreeId = resolveTree(repo, ref);
            if (oldTreeId == null) {
                System.err.println("Error: Cannot resolve " + ref);
                return 1;
            }

            if (noop) {
                int changed = countTreeToWorkingDiff(repo, oldTreeId, workingTree, globs);
                System.out.println(Messages.diffDryRunSummary(changed));
                return 0;
            }
            boolean any = diffTreeToWorking(repo, oldTreeId, workingTree, globs);
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
        try {
            try (Git ignored = GitAuth.applyCredentialsIfPresent(Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(dir.toFile())
                .setBranch("refs/heads/" + b))
                .call()) {
                // cloned
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (GitAuth.credentialsProviderFromEnvOrNull() == null && GitAuth.isMissingCredentialsProviderMessage(msg)) {
                throw new IllegalStateException(
                    "Authentication is required but no credentials are configured.\n" + GitAuth.authEnvHint(),
                    e
                );
            }
            throw e;
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

    private static List<String> valuesAfterFlagAll(List<String> args, String flag) {
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

    private static boolean diffWorkingTrees(Path leftRoot, Path rightRoot, List<String> globs) throws IOException {
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
        for (String rel : allPaths) {
            byte[] a = left.get(rel);
            byte[] b = right.get(rel);
            if (Arrays.equals(a, b)) {
                continue;
            }
            any = true;
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
            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.setContext(3);
                df.format(edits, at, bt);
            }
        }
        return any;
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

    private static boolean diffTreeToWorking(Repository repo, ObjectId oldTreeId, FileTreeIterator workingTree, List<String> globs) throws Exception {
        boolean any = false;
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);

            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(oldTree, workingTree);
                for (DiffEntry d : diffs) {
                    if (!matchesAny(d, globs)) {
                        continue;
                    }
                    df.format(d);
                    any = true;
                }
            }
        }
        return any;
    }

    private static boolean diffTrees(Repository repo, ObjectId oldTreeId, ObjectId newTreeId, List<String> globs) throws Exception {
        boolean any = false;
        try (ObjectReader reader = repo.newObjectReader()) {
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldTreeId);
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newTreeId);

            try (DiffFormatter df = new DiffFormatter(System.out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);

                List<DiffEntry> diffs = df.scan(oldTree, newTree);
                for (DiffEntry d : diffs) {
                    if (!matchesAny(d, globs)) {
                        continue;
                    }
                    df.format(d);
                    any = true;
                }
            }
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
