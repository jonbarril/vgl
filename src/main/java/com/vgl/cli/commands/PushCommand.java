package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;

public class PushCommand implements Command {
    @Override
    public String name() {
        return "push";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.pushUsage());
            return 0;
        }

        boolean noop = ArgsHelper.hasFlag(args, "-noop");

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        Path startDir = (explicitTargetDir != null) ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        if (noop) {
            System.out.println(Messages.pushDryRun());
            return 0;
        }

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglRemoteUrl = vglProps.getProperty(VglConfig.KEY_REMOTE_URL, "");
        String remoteBranch = vglProps.getProperty(VglConfig.KEY_REMOTE_BRANCH, "main");

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            // Ensure origin exists if .vgl has a remote URL.
            StoredConfig cfg = repo.getConfig();
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

            String localBranch;
            try {
                localBranch = repo.getBranch();
            } catch (Exception e) {
                localBranch = vglProps.getProperty(VglConfig.KEY_LOCAL_BRANCH, "main");
            }

            git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec(localBranch + ":" + remoteBranch))
                .call();

            System.out.println(Messages.pushed());
            return 0;
        }
    }
}
