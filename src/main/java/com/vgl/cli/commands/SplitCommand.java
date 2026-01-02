package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class SplitCommand implements Command {
    @Override
    public String name() {
        return "split";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.splitUsage());
            return 0;
        }

        boolean into = args.contains("-into");
        boolean from = args.contains("-from");
        if (into == from) {
            System.err.println(Messages.splitUsage());
            return 1;
        }

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        boolean hasExplicitTarget = explicitTargetDir != null;
        Path startDir = hasExplicitTarget ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        String branch = ArgsHelper.branchFromArgsOrNull(args);
        if (branch == null || branch.isBlank()) {
            System.err.println(Messages.splitUsage());
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            String current;
            try {
                current = repo.getBranch();
            } catch (Exception e) {
                current = null;
            }

            if (into) {
                Ref existing = repo.findRef("refs/heads/" + branch);
                if (existing != null) {
                    System.err.println("Warning: Branch already exists: " + branch);
                    return 0;
                }

                // Create branch from current HEAD and switch.
                git.checkout().setCreateBranch(true).setName(branch).call();

                VglConfig.writeProps(repoRoot, props -> {
                    props.setProperty(VglConfig.KEY_LOCAL_BRANCH, branch);
                    var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
                    branches.add(branch);
                    VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
                });

                System.out.println(Messages.createdAndSwitchedBranch(branch));
                StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, hasExplicitTarget);
                return 0;
            }

            // -from: best-effort "create current switch-state branch from source".
            // The target branch name is the current local branch recorded in .vgl (or current git branch).
            Properties vglProps = VglConfig.readProps(repoRoot);
            String targetBranch = vglProps.getProperty(VglConfig.KEY_LOCAL_BRANCH, current != null ? current : "main");

            Ref existing = repo.findRef("refs/heads/" + targetBranch);
            if (existing != null) {
                System.err.println("Warning: Target branch already exists: " + targetBranch);
                return 0;
            }

            Ref source = repo.findRef("refs/heads/" + branch);
            if (source == null) {
                System.err.println(Messages.branchNotFound(branch));
                return 1;
            }

            git.checkout().setCreateBranch(true).setName(targetBranch).setStartPoint("refs/heads/" + branch).call();

            VglConfig.writeProps(repoRoot, props -> {
                props.setProperty(VglConfig.KEY_LOCAL_BRANCH, targetBranch);
                var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
                branches.add(targetBranch);
                VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
            });

            System.out.println(Messages.createdAndSwitchedBranch(targetBranch));
            StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, hasExplicitTarget);
            return 0;
        }
    }
}
