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
        if (args.contains("-lr")) {
            System.err.println(Usage.switchCmd());
            return 1;
        }

        if (args.isEmpty()) {
            Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
            if (repoRoot == null) {
                return 1;
            }

            StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, false);
            return 0;
        }

        String localBranch = ArgsHelper.branchFromArgsOrNull(args);
        String remoteUrlArg = ArgsHelper.valueAfterFlag(args, "-rr");
        String remoteBranchArg = ArgsHelper.valueAfterFlag(args, "-rb");

        boolean hasBb = args.contains("-bb");
        if (hasBb && args.contains("-lb")) {
            System.err.println(Usage.switchCmd());
            return 1;
        }
        if (hasBb && args.contains("-rb")) {
            System.err.println(Usage.switchCmd());
            return 1;
        }

        boolean wantsAnyChange =
            (localBranch != null && !localBranch.isBlank()) ||
            (remoteUrlArg != null && !remoteUrlArg.isBlank()) ||
            (args.contains("-rb"));

        if (!wantsAnyChange) {
            System.err.println(Usage.switchCmd());
            return 1;
        }

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        Properties priorProps = VglConfig.readProps(repoRoot);

        // Resolve remote URL/branch changes.
        // Rules:
        // - -rr sets remote URL.
        // - -rb sets remote branch; if -rr not provided, uses existing remote URL.
        // - Missing remote branch defaults to existing remote branch, else "main".
        String remoteUrlToSet = null;
        if (remoteUrlArg != null && !remoteUrlArg.isBlank()) {
            remoteUrlToSet = remoteUrlArg.trim();
        }

        String existingRemoteUrl = priorProps.getProperty(VglConfig.KEY_REMOTE_URL, "").trim();
        String existingRemoteBranch = priorProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "").trim();
        if (existingRemoteBranch.isBlank()) {
            existingRemoteBranch = "main";
        }

        String remoteBranchToSet = null;
        if (args.contains("-rb")) {
            // If -rb present but no value, treat as default main.
            if (remoteBranchArg == null || remoteBranchArg.isBlank()) {
                remoteBranchToSet = "main";
            } else {
                remoteBranchToSet = remoteBranchArg.trim();
            }
        } else if (hasBb && localBranch != null && !localBranch.isBlank()) {
            remoteBranchToSet = localBranch;
        }

        boolean wantsRemoteBranchChange = remoteBranchToSet != null && !remoteBranchToSet.isBlank();
        boolean wantsRemoteUrlChange = remoteUrlToSet != null && !remoteUrlToSet.isBlank();

        if (wantsRemoteBranchChange && !wantsRemoteUrlChange) {
            // -rb without -rr requires an existing remote URL.
            if (existingRemoteUrl.isBlank()) {
                System.err.println(Messages.pushNoRemoteConfigured());
                return 1;
            }
        }

        // If we are setting -rr but not explicitly setting -rb, preserve existing branch.
        String resolvedRemoteBranchToSet = remoteBranchToSet;
        boolean resolvedWantsRemoteBranchChange = wantsRemoteBranchChange;
        if (wantsRemoteUrlChange && !resolvedWantsRemoteBranchChange) {
            resolvedRemoteBranchToSet = existingRemoteBranch;
            resolvedWantsRemoteBranchChange = true;
        }

        final String finalRemoteUrlToSet = remoteUrlToSet;
        final boolean finalWantsRemoteUrlChange = wantsRemoteUrlChange;
        final String finalRemoteBranchToSet = resolvedRemoteBranchToSet;
        final boolean finalWantsRemoteBranchChange = resolvedWantsRemoteBranchChange;

        boolean changedLocal = false;
        String priorBranch = null;
        boolean knownInVgl = false;
        boolean existed = false;
        boolean headResolves = false;

        if (localBranch != null && !localBranch.isBlank()) {
            knownInVgl = VglConfig.getStringSet(priorProps, VglConfig.KEY_LOCAL_BRANCHES).contains(localBranch);

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
                    repo.updateRef(Constants.HEAD).link("refs/heads/" + localBranch);
                    changedLocal = true;
                } else {
                    Ref ref = repo.findRef("refs/heads/" + localBranch);
                    if (ref == null) {
                        // Also allow passing a fully qualified ref.
                        ref = repo.findRef(localBranch);
                    }
                    if (ref == null) {
                        System.err.println(Messages.branchNotFound(localBranch));
                        return 1;
                    }
                    existed = true;
                    git.checkout().setName(localBranch).call();
                    changedLocal = true;
                }
            }
        }

        VglConfig.ensureGitignoreHasVgl(repoRoot);
        VglConfig.writeProps(repoRoot, props -> {
            if (localBranch != null && !localBranch.isBlank()) {
                props.setProperty(VglConfig.KEY_LOCAL_BRANCH, localBranch);
                var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
                branches.add(localBranch);
                VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
            }
            if (finalWantsRemoteUrlChange) {
                props.setProperty(VglConfig.KEY_REMOTE_URL, finalRemoteUrlToSet);
            }
            if (finalWantsRemoteBranchChange) {
                props.setProperty(VglConfig.KEY_REMOTE_BRANCH, finalRemoteBranchToSet);
            }
        });

        if (changedLocal) {
            if (priorBranch != null && priorBranch.equals(localBranch)) {
                System.out.println(Messages.alreadyOnBranch(localBranch));
            } else if (knownInVgl || (headResolves && existed)) {
                System.out.println(Messages.switchedToExistingBranch(localBranch));
            } else {
                System.out.println(Messages.switchedBranch(localBranch));
            }
        }

        // Always report the (possibly unchanged) switch state for commands that may switch state.
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(repoRoot, false);

        return 0;
    }
}
