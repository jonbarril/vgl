package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StatusVerboseOutput;
import com.vgl.cli.utils.GitAuth;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

public class RestoreCommand implements Command {
    @Override
    public String name() {
        return "restore";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.restoreUsage());
            return 0;
        }

        boolean force = ArgsHelper.hasFlag(args, "-f");

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

        Properties vglProps = VglConfig.readProps(repoRoot);
        boolean useRemote = args.contains("-rb") || args.contains("-rr");

        String localBranch = ArgsHelper.valueAfterFlag(args, "-lb");
        if (args.contains("-lb") && (localBranch == null || localBranch.isBlank())) {
            localBranch = "main";
        }

        String remoteBranch = ArgsHelper.valueAfterFlag(args, "-rb");
        if (args.contains("-rb") && (remoteBranch == null || remoteBranch.isBlank())) {
            remoteBranch = "main";
        }
        if (remoteBranch == null || remoteBranch.isBlank()) {
            remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");
        }

        String treeish;
        if (useRemote) {
            treeish = "refs/remotes/origin/" + remoteBranch;
        } else if (localBranch != null && !localBranch.isBlank()) {
            treeish = "refs/heads/" + localBranch;
        } else {
            treeish = "HEAD";
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            if (useRemote) {
                try {
                    GitAuth.applyCredentialsIfPresent(git.fetch().setRemote("origin")).call();
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            ObjectId treeId = repo.resolve(treeish + "^{tree}");
            if (treeId == null) {
                System.err.println("Error: Cannot resolve " + treeish);
                return 1;
            }

            Set<String> allPaths = listTreeFiles(repo, treeId);
            Set<String> toRestore = new LinkedHashSet<>();
            for (String p : allPaths) {
                if (GlobUtils.matchesAny(p, globs)) {
                    toRestore.add(p);
                }
            }

            if (toRestore.isEmpty()) {
                System.out.println(Messages.restoreNoMatches());
                return 0;
            }

            System.out.println("Files to restore:");
            StatusVerboseOutput.printCompactList("", toRestore, Utils.formatPath(repoRoot), List.of());

            if (!force) {
                if (!Utils.confirm("Continue? [y/N] ")) {
                    System.out.println(Messages.restoreCancelled());
                    return 0;
                }
            }

            int restored = 0;
            for (String filePath : toRestore) {
                TreeWalk tw = TreeWalk.forPath(repo, filePath, treeId);
                if (tw == null) {
                    continue;
                }
                byte[] data = repo.open(tw.getObjectId(0)).getBytes();
                Path target = repoRoot.resolve(filePath);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(target, data);
                restored++;
            }

            System.out.println(Messages.restoredCount(restored));
            return 0;
        }
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

    private static Set<String> listTreeFiles(Repository repo, ObjectId treeId) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        if (repo == null || treeId == null) {
            return out;
        }
        try (TreeWalk walk = new TreeWalk(repo)) {
            walk.addTree(treeId);
            walk.setRecursive(true);
            while (walk.next()) {
                out.add(walk.getPathString());
            }
        }
        return out;
    }
}
