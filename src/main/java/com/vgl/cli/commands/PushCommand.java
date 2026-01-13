package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitAuth;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
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

            Status status = git.status().call();
            boolean dirty = !status.getAdded().isEmpty()
                || !status.getChanged().isEmpty()
                || !status.getModified().isEmpty()
                || !status.getRemoved().isEmpty()
                || !status.getMissing().isEmpty();

            if (dirty) {
                System.err.println(Messages.pushWarnUncommittedChanges());
            }

            if (hasUndecidedFiles(repoRoot, status)) {
                System.err.println(Messages.commitUndecidedFilesHint());
            }

            String localBranch;
            try {
                localBranch = repo.getBranch();
            } catch (Exception e) {
                localBranch = vglProps.getProperty(VglConfig.KEY_LOCAL_BRANCH, "main");
            }

            GitAuth.applyCredentialsIfPresent(git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec(localBranch + ":" + remoteBranch)))
                .call();

            System.out.println(Messages.pushed());
            return 0;
        }
    }

    private static boolean hasUndecidedFiles(Path repoRoot, Status status) {
        if (repoRoot == null || status == null) {
            return false;
        }
        try {
            Set<String> gitUntracked = status.getUntracked();
            if (gitUntracked == null || gitUntracked.isEmpty()) {
                return false;
            }

            java.util.Set<String> nested = GitUtils.listNestedRepos(repoRoot);
            Properties props = VglConfig.readProps(repoRoot);
            Set<String> decidedTracked = VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES);
            Set<String> decidedUntracked = VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES);

            for (String p : gitUntracked) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                String norm = p.replace('\\', '/');
                if (".vgl".equals(norm) || ".git".equals(norm) || ".gitignore".equals(norm)) {
                    continue;
                }

                boolean insideNested = false;
                for (String n : nested) {
                    if (norm.equals(n) || norm.startsWith(n + "/")) {
                        insideNested = true;
                        break;
                    }
                }
                if (insideNested) {
                    continue;
                }

                if (decidedTracked.contains(norm) || decidedUntracked.contains(norm)) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
