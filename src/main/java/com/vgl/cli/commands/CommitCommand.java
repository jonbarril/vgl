package com.vgl.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;

import com.vgl.cli.services.RepoResolution;
import com.vgl.cli.services.VglRepo;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.MessageConstants;

public class CommitCommand implements Command {
    @Override public String name(){ return "commit"; }

    @Override
    public int run(List<String> args) throws Exception {
        // Auto-stage all changes before commit (Gitless-style)
        Git git = null;
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = !args.contains("-y");
        RepoResolution resolution = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (resolution == null || resolution.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH) {
            String missingRepoMsg = (resolution != null && resolution.getMessage() != null)
                ? resolution.getMessage()
                : MessageConstants.MSG_NO_REPO_RESOLVED;
            System.err.println(missingRepoMsg);
            return 1;
        }
        git = resolution.getGit();
        if (git != null) {
            git.add().addFilepattern(".").call();
        }

        String msg;
        boolean amend = false;
        List<String> rest = new ArrayList<>(args);
        if (!rest.isEmpty() && ("-new".equals(rest.get(0)) || "-add".equals(rest.get(0))) && rest.size() >= 2) {
            amend = true;
            msg = rest.get(1);
        } else if (!rest.isEmpty()) {
            msg = rest.get(0);
        } else {
            System.err.println(MessageConstants.MSG_COMMIT_USAGE);
            return 1;
        }

        com.vgl.cli.services.RepoResolution repoRes = RepoResolver.resolveForCommand();
        if (repoRes.getVglRepo() == null || repoRes.getGit() == null) {
            String missingRepoMsg = MessageConstants.MSG_NO_REPO_RESOLVED;
            System.err.println(missingRepoMsg);
            return 1;
        }
        try (VglRepo vglRepo = repoRes.getVglRepo()) {
            git = vglRepo.getGit();

            // First get status to see what needs to be added. Some JGit environments
            // can throw MissingObjectException when the index or HEAD is in a transient
            // state (especially after rename operations). Be defensive: if status
            // fails, fall back to staging everything and continue.
            Status preStatus = null;
            try {
                preStatus = git.status().call();
            } catch (Exception ignore) {
                preStatus = null;
            }

            // Update undecided files in .vgl before commit
            try {
                if (preStatus != null) vglRepo.updateUndecidedFilesFromWorkingTree(git, preStatus);
                else vglRepo.updateUndecidedFilesFromWorkingTree(git);
            } catch (Exception ignore) {}

            // Stage untracked files (respects .gitignore automatically) if we have status
            if (preStatus != null) {
                if (!preStatus.getUntracked().isEmpty()) {
                    for (String untracked : preStatus.getUntracked()) {
                        try { git.add().addFilepattern(untracked).call(); } catch (Exception ignore) {}
                    }
                }
            }

            // Stage everything except .vgl: additions and modifications
            git.add().addFilepattern(".").call();
            // Unstage .vgl if it was staged
            Path repoRoot = git.getRepository().getWorkTree().toPath();
            Path vglPath = repoRoot.resolve(".vgl");
            if (Files.exists(vglPath)) {
                git.rm().addFilepattern(".vgl").setCached(true).call();
            }
            // Stage deletions
            git.add().setUpdate(true).addFilepattern(".").call();

            Status s = null;
            try {
                s = git.status().call();
            } catch (Exception ignore) {
                s = null;
            }
            // Exclude git-ignored files from status checks
            org.eclipse.jgit.lib.Repository repo = git.getRepository();
            boolean nothingToCommit = false;
            if (s == null) {
                // Conservative: assume there is something to commit when status unavailable
                nothingToCommit = false;
            } else {
                // Extract status fields to local variables for clarity and to avoid repeated calls
                java.util.Set<String> added = s.getAdded();
                java.util.Set<String> changed = s.getChanged();
                java.util.Set<String> removed = s.getRemoved();
                java.util.Set<String> modified = s.getModified();
                java.util.Set<String> missing = s.getMissing();
                java.util.Set<String> untracked = s.getUntracked();
                nothingToCommit = (
                    added.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    changed.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    removed.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    modified.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    missing.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    untracked.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0
                );
            }
            if (nothingToCommit) {
                System.out.println(MessageConstants.MSG_COMMIT_NOTHING_TO_COMMIT);
                return 1;
            }

            // Check for unresolved conflict markers
            List<String> filesWithConflicts = new ArrayList<>();
            // Use the same local variables as above if available
            java.util.Set<String> added = (s != null) ? s.getAdded() : java.util.Collections.emptySet();
            java.util.Set<String> changed = (s != null) ? s.getChanged() : java.util.Collections.emptySet();
            java.util.Set<String> modified = (s != null) ? s.getModified() : java.util.Collections.emptySet();
            for (String file : added) {
                if (!com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(file), repo) && hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            for (String file : changed) {
                if (!com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(file), repo) && hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            for (String file : modified) {
                if (!com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(file), repo) && hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            if (!filesWithConflicts.isEmpty()) {
                System.err.println(MessageConstants.MSG_COMMIT_ERR_CONFLICT_MARKERS_HEADER);
                filesWithConflicts.forEach(f -> System.err.println("  " + f));
                System.err.println(MessageConstants.MSG_COMMIT_ERR_CONFLICT_MARKERS_EXAMPLE);
                System.err.println(MessageConstants.MSG_COMMIT_ERR_CONFLICT_MARKERS_RESOLVE);
                return 1;
            }

            // Final check: if status is clean, skip commit. Be defensive if status call fails.
            Status finalStatus = null;
            try { finalStatus = git.status().call(); } catch (Exception ignore) { finalStatus = null; }
            boolean finalNothingToCommit = false;
            if (finalStatus == null) {
                finalNothingToCommit = false;
            } else {
                java.util.Set<String> fAdded = finalStatus.getAdded();
                java.util.Set<String> fChanged = finalStatus.getChanged();
                java.util.Set<String> fRemoved = finalStatus.getRemoved();
                java.util.Set<String> fModified = finalStatus.getModified();
                java.util.Set<String> fMissing = finalStatus.getMissing();
                java.util.Set<String> fUntracked = finalStatus.getUntracked();
                finalNothingToCommit = (
                    fAdded.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    fChanged.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    fRemoved.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    fModified.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    fMissing.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0 &&
                    fUntracked.stream().filter(f -> !com.vgl.cli.utils.Utils.isGitIgnored(repoRoot.resolve(f), repo)).count() == 0
                );
            }
            if (finalNothingToCommit) {
                System.out.println(MessageConstants.MSG_COMMIT_NOTHING_TO_COMMIT);
                return 1;
            }

                RevCommit rc = git.commit()
                    .setMessage(msg)
                    .setAmend(amend)
                    .call();
                // First line: exactly 7-char short SHA
                String short7 = rc.getId().abbreviate(7).name();
                System.out.println(short7);
                return 0;
        }
    }

    private boolean hasConflictMarkers(Path file) {
        try {
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return false;
            }
            String content = Files.readString(file);
            return content.contains("<<<<<<<") || content.contains(">>>>>>>") || 
                   (content.contains("=======") && (content.contains("<<<<<<<") || content.contains(">>>>>>>")));
        } catch (IOException e) {
            // If we can't read the file, don't block the commit
            return false;
        }
    }
}
