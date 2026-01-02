package com.vgl.cli.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.eclipse.jgit.api.Git;

public final class RepoValidation {
    private RepoValidation() {}

    public static Result validateRepoAt(Path dir) {
        if (dir == null) {
            return Result.none("Target directory is null");
        }

        Path normalized = dir.toAbsolutePath().normalize();
        boolean hasGitDir = Files.exists(normalized.resolve(".git"));
        boolean hasVglFile = Files.isRegularFile(normalized.resolve(".vgl"));

        if (!hasGitDir && !hasVglFile) {
            return Result.none("No .git or .vgl found");
        }

        StringBuilder fatalProblems = new StringBuilder();
        String vglLocalBranch = null;

        if (hasVglFile) {
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(normalized.resolve(".vgl"))) {
                    props.load(in);
                }
                vglLocalBranch = props.getProperty("local.branch");
            } catch (IOException e) {
                fatalProblems.append(".vgl is unreadable: ").append(e.getMessage());
            }
        }

        String gitBranch = null;
        if (hasGitDir) {
            try (Git git = GitUtils.openGit(normalized)) {
                try {
                    gitBranch = git.getRepository().getBranch();
                } catch (Exception e) {
                    // Repo may have no commits; still treat as a Git repo.
                    gitBranch = null;
                }
            } catch (Exception e) {
                if (!fatalProblems.isEmpty()) {
                    fatalProblems.append("; ");
                }
                fatalProblems.append(".git is not a valid git repo: ").append(e.getMessage());
            }
        }

        if (!fatalProblems.isEmpty()) {
            return Result.malformed(normalized, hasGitDir, hasVglFile, fatalProblems.toString());
        }

        if (hasGitDir && hasVglFile && vglLocalBranch != null && gitBranch != null) {
            if (!vglLocalBranch.equals(gitBranch)) {
                return Result.inconsistentBranches(normalized, hasGitDir, hasVglFile, vglLocalBranch, gitBranch);
            }
        }

        return Result.valid(normalized, hasGitDir, hasVglFile);
    }

    public sealed interface Result permits Result.None, Result.Valid, Result.Malformed, Result.InconsistentBranches {
        static None none(String reason) {
            return new None(reason);
        }

        static Valid valid(Path repoRoot, boolean hasGitDir, boolean hasVglFile) {
            return new Valid(repoRoot, hasGitDir, hasVglFile);
        }

        static Malformed malformed(Path repoRoot, boolean hasGitDir, boolean hasVglFile, String problem) {
            return new Malformed(repoRoot, hasGitDir, hasVglFile, problem);
        }

        static InconsistentBranches inconsistentBranches(
            Path repoRoot,
            boolean hasGitDir,
            boolean hasVglFile,
            String vglLocalBranch,
            String gitBranch
        ) {
            return new InconsistentBranches(repoRoot, hasGitDir, hasVglFile, vglLocalBranch, gitBranch);
        }

        record None(String reason) implements Result {}

        record Valid(Path repoRoot, boolean hasGitDir, boolean hasVglFile) implements Result {}

        record Malformed(Path repoRoot, boolean hasGitDir, boolean hasVglFile, String problem) implements Result {}

        record InconsistentBranches(
            Path repoRoot,
            boolean hasGitDir,
            boolean hasVglFile,
            String vglLocalBranch,
            String gitBranch
        ) implements Result {}
    }
}
