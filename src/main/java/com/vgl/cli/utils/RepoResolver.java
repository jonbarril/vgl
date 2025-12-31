package com.vgl.cli.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

/**
 * Shared repo resolution logic for commands that operate on the current repo.
 *
 * <p>Behavior is spec-driven (see docs/VglUseCases.md) and matches the existing status conversion
 * prompts/hints.
 */
public final class RepoResolver {
    private RepoResolver() {}

    public static Path resolveRepoRootForCommand(Path startDir) throws Exception {
        Path repoRoot = RepoUtils.findNearestRepoRoot(startDir);
        if (repoRoot == null) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return null;
        }
        repoRoot = repoRoot.toAbsolutePath().normalize();

        boolean hasGit = Files.exists(repoRoot.resolve(".git"));
        boolean hasVgl = Files.isRegularFile(repoRoot.resolve(".vgl"));

        if (!hasGit && !hasVgl) {
            System.err.println(Messages.statusNoRepoFoundHint());
            return null;
        }

        // If only one of (.git, .vgl) exists, offer to create the other.
        if (hasGit && !hasVgl) {
            if (!Utils.isInteractive()) {
                System.err.println(Messages.statusGitOnlyRepoHint(repoRoot));
                return null;
            }
            if (!Utils.confirm(Messages.statusConvertGitToVglPrompt(repoRoot))) {
                System.err.println(Messages.statusGitOnlyRepoHint(repoRoot));
                return null;
            }
            try (Git git = GitUtils.openGit(repoRoot)) {
                String branch = safeGitBranch(git.getRepository());
                final String localBranch = (branch == null || branch.isBlank()) ? "main" : branch;
                VglConfig.ensureGitignoreHasVgl(repoRoot);
                VglConfig.writeProps(repoRoot, props -> props.setProperty(VglConfig.KEY_LOCAL_BRANCH, localBranch));
            }
            hasVgl = true;
        } else if (!hasGit && hasVgl) {
            if (!Utils.isInteractive()) {
                System.err.println(Messages.statusVglOnlyRepoHint(repoRoot));
                return null;
            }
            if (!Utils.confirm(Messages.statusInitGitFromVglPrompt(repoRoot))) {
                System.err.println(Messages.statusVglOnlyRepoHint(repoRoot));
                return null;
            }
            Properties props = VglConfig.readProps(repoRoot);
            String branch = props.getProperty(VglConfig.KEY_LOCAL_BRANCH, "main");
            try (Git ignored = Git.init().setDirectory(repoRoot.toFile()).setInitialBranch(branch).call()) {
                // initialized
            }
            hasGit = true;
        }

        if (!hasGit || !hasVgl) {
            // User refused conversion or conversion failed.
            return null;
        }

        return repoRoot;
    }

    private static String safeGitBranch(Repository repo) {
        try {
            return repo.getBranch();
        } catch (Exception e) {
            return null;
        }
    }
}
