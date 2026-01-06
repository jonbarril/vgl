package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class MergeCommand implements Command {
    @Override
    public String name() {
        return "merge";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.mergeUsage());
            return 0;
        }

        // merge is local-only (branches in the local repo)
        if (args.contains("-rr") || args.contains("-rb")) {
            System.err.println(Messages.mergeUsage());
            return 1;
        }

        boolean into = args.contains("-into");
        boolean from = args.contains("-from") || (!into);
        if (into && args.contains("-from")) {
            System.err.println(Messages.mergeUsage());
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
            System.err.println(Messages.mergeUsage());
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            boolean headResolves;
            try {
                headResolves = repo.resolve(Constants.HEAD) != null;
            } catch (Exception e) {
                headResolves = false;
            }
            if (!headResolves) {
                System.err.println("Error: Repository has no commits yet.");
                return 1;
            }

            Status status = git.status().call();
            boolean dirty = !status.getAdded().isEmpty()
                || !status.getChanged().isEmpty()
                || !status.getModified().isEmpty()
                || !status.getRemoved().isEmpty()
                || !status.getMissing().isEmpty()
                ;
            if (dirty) {
                System.err.println("Error: Working tree has uncommitted changes.");
                return 1;
            }

            String originalBranch;
            try {
                originalBranch = repo.getBranch();
            } catch (Exception e) {
                System.err.println("Error: Cannot determine current branch.");
                return 1;
            }

            String sourceBranch;
            String targetBranch;
            if (into) {
                sourceBranch = originalBranch;
                targetBranch = branch;

                Ref targetRef = repo.findRef("refs/heads/" + targetBranch);
                if (targetRef == null) {
                    System.err.println(Messages.branchNotFound(targetBranch));
                    return 1;
                }

                if (!targetBranch.equals(originalBranch)) {
                    try {
                        git.checkout().setName(targetBranch).call();
                    } catch (Exception e) {
                        System.err.println("Error: Cannot switch to branch: " + targetBranch);
                        return 1;
                    }
                }

                VglConfig.writeProps(repoRoot, props -> {
                    props.setProperty(VglConfig.KEY_LOCAL_BRANCH, targetBranch);
                    var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
                    branches.add(targetBranch);
                    VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
                });
            } else {
                sourceBranch = branch;
                targetBranch = originalBranch;

                Ref srcRef = repo.findRef("refs/heads/" + sourceBranch);
                if (srcRef == null) {
                    System.err.println(Messages.branchNotFound(sourceBranch));
                    return 1;
                }
                if (sourceBranch.equals(targetBranch)) {
                    System.err.println("Error: Cannot merge a branch into itself: " + sourceBranch);
                    return 1;
                }
            }

            ObjectId srcId = repo.resolve("refs/heads/" + sourceBranch);
            if (srcId == null) {
                System.err.println(Messages.branchNotFound(sourceBranch));
                return 1;
            }

            MergeResult r = git.merge().include(srcId).call();
            if (r.getMergeStatus() != null && r.getMergeStatus().isSuccessful()) {
                System.out.println("Merged " + sourceBranch + " into " + targetBranch + ".");
                StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, hasExplicitTarget);
                return 0;
            }

            System.err.println("Warning: Merge completed with conflicts.");
            StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, hasExplicitTarget);
            return 1;
        }
    }
}
