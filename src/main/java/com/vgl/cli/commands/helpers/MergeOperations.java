package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.MergeAnalysis;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Common merge operation helpers for commands that perform merges (pull, merge, sync).
 * Provides consistent handling of merge previews, confirmations, and reporting.
 */
public final class MergeOperations {
    private MergeOperations() {}

    /**
     * Result of a merge preview check.
     */
    public static class MergeCheckResult {
        public final boolean shouldProceed;
        public final MergeAnalysis.MergePreview preview;
        public final String message;

        public MergeCheckResult(boolean shouldProceed, MergeAnalysis.MergePreview preview, String message) {
            this.shouldProceed = shouldProceed;
            this.preview = preview;
            this.message = message;
        }
    }

    /**
     * Checks if the workspace is dirty (has uncommitted changes).
     */
    public static boolean isWorkingTreeDirty(Status status) {
        return !status.getAdded().isEmpty()
            || !status.getChanged().isEmpty()
            || !status.getModified().isEmpty()
            || !status.getRemoved().isEmpty()
            || !status.getMissing().isEmpty();
    }

    /**
     * Analyzes a merge and handles noop/preview reporting with optional uncommitted file count.
     * Returns whether the merge should proceed (false if cancelled or noop).
     */
    public static MergeCheckResult checkMerge(
        Git git,
        ObjectId sourceId,
        ObjectId headId,
        Status status,
        boolean noop,
        boolean force,
        boolean verbose
    ) throws Exception {
        
        // Check if already up to date
        if (headId.equals(sourceId)) {
            int uncommittedCount = countUncommittedFiles(status);
            if (noop) {
                String msg = uncommittedCount > 0
                    ? "Dry run: 0 commit(s), 0 file(s) affected (0 with conflicts, 0 without conflicts). " + uncommittedCount + " uncommitted file(s)."
                    : "Dry run: no changes, no conflicts (pull).";
                return new MergeCheckResult(false, null, msg);
            } else {
                return new MergeCheckResult(false, null, Messages.pullNoChangesNoConflicts());
            }
        }

        // Analyze merge
        MergeAnalysis.MergePreview preview = MergeAnalysis.analyzeMerge(git, sourceId);

        // Handle noop (dry run)
        if (noop) {
            int uncommittedCount = countUncommittedFiles(status);
            String summary = "Dry run: " + MergeAnalysis.formatSummary(preview);
            if (uncommittedCount > 0) {
                summary += ". " + uncommittedCount + " uncommitted file(s)";
            }
            summary += ".";
            return new MergeCheckResult(false, preview, summary);
        }

        // Check for uncommitted changes
        boolean dirty = isWorkingTreeDirty(status);
        if (dirty) {
            System.err.println(Messages.WARN_REPO_DIRTY_OR_AHEAD);
            if (!force) {
                if (!Utils.confirm("Continue? [y/N] ")) {
                    return new MergeCheckResult(false, preview, Messages.pullCancelled());
                }
            }
        }

        // Report merge preview
        String previewMsg = verbose
            ? MergeAnalysis.formatDetailed(preview)
            : MergeAnalysis.formatSummary(preview);
        System.out.println(previewMsg);

        // Prompt if conflicts expected (unless forced or non-interactive)
        if (!force && preview.filesWithConflicts > 0 && Utils.isInteractive()) {
            if (!Utils.confirm("Proceed with merge? [y/N] ")) {
                return new MergeCheckResult(false, preview, "Merge cancelled.");
            }
        }

        return new MergeCheckResult(true, preview, null);
    }

    /**
     * Counts uncommitted files in status.
     */
    private static int countUncommittedFiles(Status status) {
        return status.getAdded().size()
            + status.getChanged().size()
            + status.getModified().size()
            + status.getRemoved().size()
            + status.getMissing().size();
    }
}
