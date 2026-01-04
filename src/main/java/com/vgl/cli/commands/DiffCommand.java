package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
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

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        Path startDir = (explicitTargetDir != null) ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        List<String> globs = collectPositionals(args);
        if (globs.isEmpty()) {
            globs = List.of("*");
        }

        String localBranch = ArgsHelper.branchFromArgsOrNull(args);
        String remoteBranch = ArgsHelper.valueAfterFlag(args, "-rb");
        if (args.contains("-rb") && (remoteBranch == null || remoteBranch.isBlank())) {
            remoteBranch = "main";
        }

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        if (remoteBranch == null || remoteBranch.isBlank()) {
            remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");
        }

        boolean hasRemote = args.contains("-rb") || args.contains("-rr") || (vglRemoteUrl != null && !vglRemoteUrl.isBlank());
        boolean hasLocalBranch = localBranch != null && !localBranch.isBlank();

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            if (hasRemote) {
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
            boolean any = diffTreeToWorking(repo, oldTreeId, workingTree, globs);
            if (!any) {
                System.out.println("No differences.");
            }
            return 0;
        }
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
