package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.StoredConfig;

public class PullCommand implements Command {
    @Override
    public String name() {
        return "pull";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.pullUsage());
            return 0;
        }

        boolean force = ArgsHelper.hasFlag(args, "-f");
        boolean noop = ArgsHelper.hasFlag(args, "-noop");

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        Path startDir = (explicitTargetDir != null) ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        if (noop) {
            System.out.println(Messages.pullDryRun());
            return 0;
        }

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        String remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");

        try (Git git = GitUtils.openGit(repoRoot)) {
            // Ensure origin exists if .vgl has a remote URL.
            StoredConfig cfg = git.getRepository().getConfig();
            String originUrl = cfg.getString("remote", "origin", "url");
            if ((originUrl == null || originUrl.isBlank()) && vglRemoteUrl != null && !vglRemoteUrl.isBlank()) {
                cfg.setString("remote", "origin", "url", vglRemoteUrl);
                cfg.save();
                originUrl = vglRemoteUrl;
            }
            if (originUrl == null || originUrl.isBlank()) {
                System.err.println(Messages.pushNoRemoteConfigured());
                return 1;
            }

            Status status = git.status().call();
            boolean dirty = !status.getAdded().isEmpty()
                || !status.getChanged().isEmpty()
                || !status.getModified().isEmpty()
                || !status.getRemoved().isEmpty()
                || !status.getMissing().isEmpty();

            if (dirty) {
                System.err.println(Messages.WARN_REPO_DIRTY_OR_AHEAD);
                if (!force) {
                    if (!Utils.confirm("Continue? [y/N] ")) {
                        System.out.println(Messages.pullCancelled());
                        return 0;
                    }
                }
            }

            PullResult r = git.pull().setRemote("origin").setRemoteBranchName(remoteBranch).call();
            if (r.isSuccessful()) {
                boolean upToDate = false;
                try {
                    MergeResult mr = r.getMergeResult();
                    if (mr != null && mr.getMergeStatus() == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
                        upToDate = true;
                    }
                } catch (Exception ignored) {
                    upToDate = false;
                }

                if (upToDate) {
                    System.out.println(Messages.pullNoChangesNoConflicts());
                } else {
                    System.out.println(Messages.pullCompleted());
                }
                return 0;
            }

            System.err.println(Messages.pullHadConflicts());
            return 1;
        }
    }
}
