package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;

public class AbortCommand implements Command {
    @Override
    public String name() {
        return "abort";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.abortUsage());
            return 0;
        }

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();
            RepositoryState state = repo.getRepositoryState();
            if (!isAbortable(state) && !hasAbortStateFiles(repo)) {
                System.out.println(Messages.abortNothingToAbort());
                return 0;
            }

            // Best-effort abort: hard-reset to ORIG_HEAD when possible, then clear state files.
            try {
                git.reset().setMode(ResetType.HARD).setRef("ORIG_HEAD").call();
            } catch (Exception ignored) {
                // ORIG_HEAD may not exist for some operations; continue with file cleanup.
            }

            cleanupAbortStateFiles(repo);

            System.out.println(Messages.abortCompleted());
            return 0;
        }
    }

    private static boolean isAbortable(RepositoryState state) {
        if (state == null) {
            return false;
        }

        return switch (state) {
            case MERGING, MERGING_RESOLVED,
                 REBASING, REBASING_INTERACTIVE, REBASING_MERGE,
                 CHERRY_PICKING, CHERRY_PICKING_RESOLVED,
                 REVERTING, REVERTING_RESOLVED
                -> true;
            default -> false;
        };
    }

    private static boolean hasAbortStateFiles(Repository repo) {
        Path gitDir = repo.getDirectory().toPath();
        return Files.exists(gitDir.resolve("MERGE_HEAD"))
            || Files.exists(gitDir.resolve("CHERRY_PICK_HEAD"))
            || Files.exists(gitDir.resolve("REVERT_HEAD"))
            || Files.isDirectory(gitDir.resolve("rebase-apply"))
            || Files.isDirectory(gitDir.resolve("rebase-merge"));
    }

    private static void cleanupAbortStateFiles(Repository repo) {
        Path gitDir = repo.getDirectory().toPath();

        deleteIfExists(gitDir.resolve("MERGE_HEAD"));
        deleteIfExists(gitDir.resolve("MERGE_MSG"));
        deleteIfExists(gitDir.resolve("MERGE_MODE"));

        deleteIfExists(gitDir.resolve("CHERRY_PICK_HEAD"));
        deleteIfExists(gitDir.resolve("REVERT_HEAD"));

        deleteDirIfExists(gitDir.resolve("rebase-apply"));
        deleteDirIfExists(gitDir.resolve("rebase-merge"));
    }

    private static void deleteIfExists(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static void deleteDirIfExists(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                .forEach(AbortCommand::deleteIfExists);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
