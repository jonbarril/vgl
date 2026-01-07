package com.vgl.cli.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Utility for analyzing potential workspace changes from a merge operation.
 */
public final class MergeAnalysis {
    private MergeAnalysis() {}

    public static class MergePreview {
        public final int sourceCommits;
        public final int filesAffected;
        public final int filesWithConflicts;
        public final int filesWithoutConflicts;
        public final Set<String> conflictingFiles;
        public final Set<String> nonConflictingFiles;

        public MergePreview(
            int sourceCommits,
            int filesAffected,
            int filesWithConflicts,
            int filesWithoutConflicts,
            Set<String> conflictingFiles,
            Set<String> nonConflictingFiles
        ) {
            this.sourceCommits = sourceCommits;
            this.filesAffected = filesAffected;
            this.filesWithConflicts = filesWithConflicts;
            this.filesWithoutConflicts = filesWithoutConflicts;
            this.conflictingFiles = conflictingFiles;
            this.nonConflictingFiles = nonConflictingFiles;
        }
    }

    /**
     * Analyzes a merge between the current HEAD and a source commit (e.g., from a remote branch).
     * Returns statistics about what would change without performing the actual merge.
     */
    public static MergePreview analyzeMerge(Git git, ObjectId sourceId) throws Exception {
        Repository repo = git.getRepository();
        ObjectId headId = repo.resolve("HEAD");
        if (headId == null || sourceId == null) {
            return new MergePreview(0, 0, 0, 0, Set.of(), Set.of());
        }

        // Count commits that would be merged (source commits not in HEAD's history)
        int sourceCommits = countNewCommits(repo, headId, sourceId);

        // Use a test merger to detect conflicts
        Merger merger = MergeStrategy.RECURSIVE.newMerger(repo, true);
        boolean canMerge = merger.merge(headId, sourceId);

        Set<String> conflictingFiles = new HashSet<>();
        if (!canMerge && merger instanceof ResolveMerger) {
            ResolveMerger rm = (ResolveMerger) merger;
            // Get unmerged paths (conflicting files)
            if (rm.getUnmergedPaths() != null) {
                conflictingFiles.addAll(rm.getUnmergedPaths());
            }
        }

        // Count all affected files by diffing the trees
        Set<String> allAffectedFiles = getAffectedFiles(repo, headId, sourceId);

        Set<String> nonConflictingFiles = new HashSet<>(allAffectedFiles);
        nonConflictingFiles.removeAll(conflictingFiles);

        int filesAffected = allAffectedFiles.size();
        int filesWithConflicts = conflictingFiles.size();
        int filesWithoutConflicts = nonConflictingFiles.size();

        return new MergePreview(
            sourceCommits,
            filesAffected,
            filesWithConflicts,
            filesWithoutConflicts,
            conflictingFiles,
            nonConflictingFiles
        );
    }

    private static int countNewCommits(Repository repo, ObjectId base, ObjectId source) {
        int count = 0;
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit baseCommit = walk.parseCommit(base);
            RevCommit sourceCommit = walk.parseCommit(source);

            walk.markStart(sourceCommit);
            walk.markUninteresting(baseCommit);

            for (RevCommit commit : walk) {
                count++;
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return count;
    }

    private static Set<String> getAffectedFiles(Repository repo, ObjectId oldId, ObjectId newId) throws Exception {
        Set<String> files = new HashSet<>();
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            CanonicalTreeParser newTree = new CanonicalTreeParser();

            try (var reader = repo.newObjectReader()) {
                oldTree.reset(reader, repo.parseCommit(oldId).getTree());
                newTree.reset(reader, repo.parseCommit(newId).getTree());
            }

            List<DiffEntry> diffs = df.scan(oldTree, newTree);
            for (DiffEntry entry : diffs) {
                String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                    ? entry.getOldPath()
                    : entry.getNewPath();
                if (path != null && !path.isBlank()) {
                    files.add(path);
                }
            }
        }
        return files;
    }

    /**
     * Formats a merge preview for user output (summary line).
     */
    public static String formatSummary(MergePreview preview) {
        return String.format(
            "%d commit(s), %d file(s) affected (%d with conflicts, %d without conflicts)",
            preview.sourceCommits,
            preview.filesAffected,
            preview.filesWithConflicts,
            preview.filesWithoutConflicts
        );
    }

    /**
     * Formats a merge preview for detailed output (with file lists).
     */
    public static String formatDetailed(MergePreview preview) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatSummary(preview));
        sb.append("\n");

        if (!preview.conflictingFiles.isEmpty()) {
            sb.append("\n-- Files with Conflicts:\n");
            for (String f : preview.conflictingFiles) {
                sb.append("  ! ").append(f).append("\n");
            }
        }

        if (!preview.nonConflictingFiles.isEmpty()) {
            sb.append("\n-- Files without Conflicts:\n");
            for (String f : preview.nonConflictingFiles) {
                sb.append("  M ").append(f).append("\n");
            }
        }

        return sb.toString();
    }
}
