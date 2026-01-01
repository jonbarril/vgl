package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.CommandWarnings;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.commands.helpers.SwitchStateOutput;
import com.vgl.cli.commands.helpers.Usage;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class SwitchCommand implements Command {
    @Override
    public String name() {
        return "switch";
    }

    @Override
    public int run(List<String> args) throws Exception {
        String branch = ArgsHelper.branchFromArgsOrNull(args);
        if (branch == null || branch.isBlank()) {
            System.err.println(Usage.switchCmd());
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

            try {
                headResolves = repo.resolve(Constants.HEAD) != null;
            } catch (Exception e) {
                headResolves = false;
            }

            if (!headResolves) {
                // Unborn repository: avoid checkout failures by linking HEAD.
                repo.updateRef(Constants.HEAD).link("refs/heads/" + branch);
            } else {
                Ref ref = repo.findRef("refs/heads/" + branch);
                if (ref == null) {
                    // Also allow passing a fully qualified ref.
                    ref = repo.findRef(branch);
                }
                if (ref == null) {
                    System.err.println(Messages.branchNotFound(branch));
                    return 1;
                }
                existed = true;
                git.checkout().setName(branch).call();
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
        } else if (knownInVgl || (headResolves && existed)) {
            System.out.println(Messages.switchedToExistingBranch(branch));
        } else {
            System.out.println(Messages.switchedBranch(branch));
        }

        // Always report the (possibly unchanged) switch state for commands that may switch state.
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, hasExplicitTarget);

        return 0;
    }
}
