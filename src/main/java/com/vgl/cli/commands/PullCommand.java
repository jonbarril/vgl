package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.MergeOperations;
import com.vgl.cli.utils.GitAuth;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GitRemoteOps;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
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

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        String remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();
            // Ensure origin exists if .vgl has a remote URL.
            String originUrl = GitRemoteOps.ensureOriginConfigured(repo, vglRemoteUrl);
            if (originUrl == null || originUrl.isBlank()) {
                System.err.println(Messages.pushNoRemoteConfigured());
                return 1;
            }

            Status status = git.status().call();

            // Fetch remote changes to analyze merge
            GitRemoteOps.FetchOutcome fetchOutcome = GitRemoteOps.fetchOrigin(repoRoot, git, originUrl, /*required*/true, System.err);
            if (fetchOutcome == GitRemoteOps.FetchOutcome.AUTH_FAILED) {
                return 1;
            }
            boolean fetchedViaNative = fetchOutcome == GitRemoteOps.FetchOutcome.OK_NATIVE;
            org.eclipse.jgit.lib.ObjectId remoteId = repo.resolve("refs/remotes/origin/" + remoteBranch);
            org.eclipse.jgit.lib.ObjectId headId = repo.resolve("HEAD");

            if (remoteId == null) {
                System.err.println("ERROR: Cannot resolve remote branch: " + remoteBranch);
                return 1;
            }

            if (headId == null) {
                System.err.println("ERROR: Repository has no commits yet.");
                return 1;
            }

            // Check merge and handle noop/preview
            boolean verbose = ArgsHelper.hasFlag(args, "-v") || ArgsHelper.hasFlag(args, "-vv");
            MergeOperations.MergeCheckResult check = MergeOperations.checkMerge(
                git, remoteId, headId, status, noop, force, verbose
            );

            if (check.message != null) {
                System.out.println(check.message);
            }

            if (!check.shouldProceed) {
                return 0;
            }

            // Perform the pull
            if (fetchedViaNative) {
                MergeResult merge = git.merge()
                    .setFastForward(MergeCommand.FastForwardMode.FF)
                    .include(remoteId)
                    .call();

                if (merge != null && merge.getMergeStatus() != null && merge.getMergeStatus().isSuccessful()) {
                    System.out.println(Messages.pullCompleted());
                    return 0;
                }

                System.err.println(Messages.pullHadConflicts());
                return 1;
            }

            PullResult r;
            try {
                r = GitAuth.applyCredentialsIfPresent(
                    git.pull().setRemote("origin").setRemoteBranchName(remoteBranch)
                ).call();
            } catch (Exception e) {
                if (GitAuth.handleMissingCredentialsProvider(e, originUrl, System.err)) {
                    return 1;
                }
                throw e;
            }
            if (r.isSuccessful()) {
                System.out.println(Messages.pullCompleted());
                return 0;
            }

            System.err.println(Messages.pullHadConflicts());
            return 1;
        }
    }
}
