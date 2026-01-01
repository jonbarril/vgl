package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Constants;

public class CreateCommand implements Command {
    @Override public String name() { return "create"; }

    @Override
    public int run(List<String> args) throws Exception {
        boolean force = ArgsHelper.hasFlag(args, "-f");

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        boolean hasExplicitTarget = explicitTargetDir != null;

        Path targetDir = hasExplicitTarget
            ? explicitTargetDir
            : Path.of(System.getProperty("user.dir"));
        targetDir = targetDir.toAbsolutePath().normalize();

        String requestedBranch = ArgsHelper.branchFromArgsOrNull(args);

        // Branch create/switch mode:
        // - If -lr DIR points to an existing repo, operate on that repo.
        // - If no -lr is provided, operate on the nearest repo from CWD.
        // - If -lr DIR does not exist yet, fall through to repo creation using requestedBranch.
        if (requestedBranch != null) {
            Path branchStartDir = hasExplicitTarget ? targetDir : Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path nearest = RepoUtils.findNearestRepoRoot(branchStartDir);
            boolean targetLooksLikeRepo = Files.exists(targetDir.resolve(".git")) || Files.isRegularFile(targetDir.resolve(".vgl"));
            if (!hasExplicitTarget && nearest == null) {
                System.err.println(Messages.ERR_NO_REPO_FOUND);
                return 1;
            }
            if (!hasExplicitTarget) {
                return createOrSwitchLocalBranch(branchStartDir, requestedBranch);
            }
            if (targetLooksLikeRepo) {
                return createOrSwitchLocalBranch(branchStartDir, requestedBranch);
            }
        }

        String localBranch = requestedBranch;
        if (localBranch == null || localBranch.isBlank()) {
            localBranch = "main";
        }
        final String finalLocalBranch = localBranch;

        // Repo creation flow.

        // Nested repo check
        if (RepoUtils.isNestedUnderExistingRepo(targetDir) && !force) {
            Path parentRepo = RepoUtils.findNearestRepoRoot(targetDir.getParent());
            if (!Utils.confirm(Messages.nestedRepoPrompt(parentRepo))) {
                System.err.println(Messages.createRepoRefusingNested());
                return 1;
            }
        }

        // Existing repo check (at target)
        if (Files.exists(targetDir.resolve(".git")) || Files.isRegularFile(targetDir.resolve(".vgl"))) {
            System.err.println(Messages.repoAlreadyExists(targetDir));
            return 1;
        }

        Files.createDirectories(targetDir);

        try (Git ignored = Git.init().setDirectory(targetDir.toFile()).setInitialBranch(finalLocalBranch).call()) {
            // repo created
        }

        VglConfig.ensureGitignoreHasVgl(targetDir);
        VglConfig.writeProps(targetDir, props -> props.setProperty(VglConfig.KEY_LOCAL_BRANCH, finalLocalBranch));

        System.out.println(Messages.createdRepo(targetDir, finalLocalBranch));

        // Always report the (possibly unchanged) switch state for commands that may switch state.
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(targetDir, hasExplicitTarget);
        return 0;
    }

    private static int createOrSwitchLocalBranch(Path startDir, String branch) throws Exception {
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        Properties priorProps = VglConfig.readProps(repoRoot);
        boolean knownInVgl = VglConfig.getStringSet(priorProps, VglConfig.KEY_LOCAL_BRANCHES).contains(branch);

        String priorBranch = null;
        boolean existed = false;
        boolean headResolves = false;

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            try {
                priorBranch = repo.getBranch();
            } catch (Exception ignored) {
                priorBranch = null;
            }

            Ref ref = repo.findRef("refs/heads/" + branch);
            existed = ref != null;

            try {
                headResolves = repo.resolve(Constants.HEAD) != null;
            } catch (Exception e) {
                headResolves = false;
            }

            if (!headResolves) {
                // Unborn repository: avoid JGit checkout failure ("Ref HEAD cannot be resolved")
                // by making HEAD point at the requested branch without creating an empty commit.
                repo.updateRef(Constants.HEAD).link("refs/heads/" + branch);
            } else {
                var checkout = git.checkout().setName(branch);
                if (!existed) {
                    checkout.setCreateBranch(true);
                }
                checkout.call();
            }
        }

        VglConfig.ensureGitignoreHasVgl(repoRoot);
        VglConfig.writeProps(repoRoot, props -> {
            props.setProperty(VglConfig.KEY_LOCAL_BRANCH, branch);
            var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
            branches.add(branch);
            VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
        });

        if (priorBranch != null && priorBranch.equals(branch)) {
            System.out.println(Messages.alreadyOnBranch(branch));
        } else if (knownInVgl || existed) {
            System.out.println(Messages.switchedToExistingBranch(branch));
        } else {
            System.out.println(Messages.createdAndSwitchedBranch(branch));
        }

        // Always report the (possibly unchanged) switch state for commands that may switch state.
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, true);
        return 0;
    }
}
