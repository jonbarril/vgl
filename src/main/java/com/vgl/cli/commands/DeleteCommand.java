package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoValidation;
import com.vgl.cli.utils.Utils;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class DeleteCommand implements Command {
    @Override public String name() { return "delete"; }

    @Override
    public int run(List<String> args) throws Exception {
        boolean force = ArgsHelper.hasFlag(args, "-f");
        Path targetDir = ArgsHelper.pathAfterFlag(args, "-lr");

        if (targetDir == null) {
            targetDir = Path.of(System.getProperty("user.dir"));
        }
        if (targetDir == null) {
            targetDir = Path.of(System.getProperty("user.dir"));
        }
        targetDir = targetDir.toAbsolutePath().normalize();

        String branch = ArgsHelper.branchFromArgsOrNull(args);

        // Branch deletion is relative to the nearest repo root from the provided target directory (or CWD).
        if (branch != null) {
            Path branchStart = targetDir;
            if (branchStart == null) {
                branchStart = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            }
            Path branchRepoRoot = RepoUtils.findNearestRepoRoot(branchStart);
            if (branchRepoRoot == null) {
                System.err.println(Messages.ERR_NO_REPO_FOUND);
                return 1;
            }
            return deleteBranch(branchRepoRoot, branch, force);
        }

        RepoValidation.Result validation = RepoValidation.validateRepoAt(targetDir);
        if (validation instanceof RepoValidation.Result.None) {
            System.err.println(Messages.noRepoAtTarget(targetDir));
            return 1;
        }

        Path repoRoot = (validation instanceof RepoValidation.Result.Valid v) ? v.repoRoot()
            : ((RepoValidation.Result.Malformed) validation).repoRoot();

        boolean hasGitDir = (validation instanceof RepoValidation.Result.Valid v) ? v.hasGitDir()
            : ((RepoValidation.Result.Malformed) validation).hasGitDir();

        if (validation instanceof RepoValidation.Result.Malformed m) {
            System.err.println(Messages.malformedRepo(repoRoot, m.problem()));
        }

        // Repo deletion flow (UseCases): confirm deletion, warn if uncommitted/unpushed, optionally delete contents.
        if (!force) {
            if (!Utils.confirm(Messages.deleteRepoPrompt(repoRoot))) {
                System.err.println(Messages.deleteRepoRefusing());
                return 1;
            }

            if (hasGitDir && !(validation instanceof RepoValidation.Result.Malformed)) {
                RepoRisk risk = computeRepoRisk(repoRoot);
                if (risk.hasUncommittedChanges || risk.hasUnpushedCommits) {
                    if (!Utils.confirm(Messages.deleteRepoDirtyOrAheadPrompt())) {
                        System.err.println(Messages.deleteRepoRefusing());
                        return 1;
                    }
                }
            } else {
                // If we can't reliably inspect status (no .git or malformed state), still give a safety prompt.
                if (!Utils.confirm(Messages.deleteRepoDirtyOrAheadPrompt())) {
                    System.err.println(Messages.deleteRepoRefusing());
                    return 1;
                }
            }
        }

        boolean deleteContents = false;
        if (!force && Utils.isInteractive()) {
            deleteContents = Utils.confirm(Messages.deleteRepoContentsPrompt(repoRoot));
        }

        if (deleteContents) {
            deletePathRecursively(repoRoot);
            System.out.println(Messages.deletedRepoContents(repoRoot));
            return 0;
        }

        Path dotGit = repoRoot.resolve(".git");
        if (Files.isDirectory(dotGit)) {
            deletePathRecursively(dotGit);
        } else {
            Files.deleteIfExists(dotGit);
        }
        Files.deleteIfExists(repoRoot.resolve(".vgl"));
        Files.deleteIfExists(repoRoot.resolve(".gitignore"));
        System.out.println(Messages.deletedRepoMetadata(repoRoot));
        return 0;
    }

    private static RepoRisk computeRepoRisk(Path repoRoot) {
        try (Git git = GitUtils.openGit(repoRoot)) {
            Status status = git.status().call();
            boolean uncommitted = !status.isClean();

            boolean unpushed = false;
            try {
                String branch = git.getRepository().getBranch();
                BranchTrackingStatus tracking = BranchTrackingStatus.of(git.getRepository(), branch);
                if (tracking != null) {
                    unpushed = tracking.getAheadCount() > 0;
                }
            } catch (Exception ignored) {
                // If tracking can't be determined (no commits, no upstream, etc.), treat as not unpushed.
            }

            return new RepoRisk(uncommitted, unpushed);
        } catch (Exception e) {
            // If we can't inspect, be conservative and treat as risky.
            return new RepoRisk(true, true);
        }
    }

    private record RepoRisk(boolean hasUncommittedChanges, boolean hasUnpushedCommits) {}

    private static int deleteBranch(Path repoRoot, String branch, boolean force) throws Exception {
        try (Git git = GitUtils.openGit(repoRoot)) {
            Repository repo = git.getRepository();

            String current = safeCurrentBranch(repo);
            if (current != null && branch.equals(current)) {
                System.err.println(Messages.refusingDeleteCurrentBranch(branch));
                return 1;
            }

            Ref toDelete = repo.findRef("refs/heads/" + branch);
            if (toDelete == null) {
                // Also allow passing a fully qualified ref.
                toDelete = repo.findRef(branch);
            }
            if (toDelete == null) {
                System.err.println(Messages.branchNotFound(branch));
                return 1;
            }

            boolean hasUnpushedCommits = false;
            try {
                BranchTrackingStatus tracking = BranchTrackingStatus.of(repo, branch);
                if (tracking != null) {
                    hasUnpushedCommits = tracking.getAheadCount() > 0;
                }
            } catch (Exception ignored) {
                // best-effort
            }

            boolean mergedIntoCurrent = true;
            try {
                var headId = repo.resolve(Constants.HEAD);
                var branchId = repo.resolve(toDelete.getName());
                if (headId == null || branchId == null) {
                    mergedIntoCurrent = false;
                } else {
                    try (RevWalk walk = new RevWalk(repo)) {
                        RevCommit head = walk.parseCommit(headId);
                        RevCommit tip = walk.parseCommit(branchId);
                        mergedIntoCurrent = walk.isMergedInto(tip, head);
                    }
                }
            } catch (Exception ignored) {
                mergedIntoCurrent = false;
            }

            boolean risky = hasUnpushedCommits || !mergedIntoCurrent;
            boolean deleteForce = force;
            if (risky && !force) {
                if (!mergedIntoCurrent) {
                    System.err.println(Messages.WARN_DELETE_BRANCH_NOT_MERGED_PREFIX + branch);
                }
                if (hasUnpushedCommits) {
                    System.err.println(Messages.WARN_DELETE_BRANCH_HAS_UNPUSHED_COMMITS_PREFIX + branch);
                }
                if (!Utils.confirm(Messages.deleteBranchRiskPrompt(branch))) {
                    System.err.println(Messages.deleteRepoRefusing());
                    return 1;
                }
                deleteForce = true;
            }

            git.branchDelete().setBranchNames(branch).setForce(deleteForce).call();
            System.out.println(Messages.deletedBranch(branch));
            return 0;
        }
    }

    private static String safeCurrentBranch(Repository repo) {
        if (repo == null) {
            return null;
        }
        try {
            String full = repo.getFullBranch();
            if (full == null) {
                return null;
            }
            if (full.startsWith(Constants.R_HEADS)) {
                return full.substring(Constants.R_HEADS.length());
            }
            // Detached or unexpected.
            return repo.getBranch();
        } catch (Exception e) {
            return null;
        }
    }

    private static void deletePathRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                clearReadOnly(file);
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                clearReadOnly(dir);
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void clearReadOnly(Path path) {
        try {
            Files.setAttribute(path, "dos:readonly", false);
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
