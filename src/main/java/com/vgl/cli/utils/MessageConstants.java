package com.vgl.cli.utils;

/**
 * Centralized user-facing message constants for VGL CLI and tests.
 */
public final class MessageConstants {
                            // Commit command messages
                            public static final String MSG_COMMIT_USAGE = "Usage: vgl commit \"msg\" | [-new|-add] \"msg\"";
                            public static final String MSG_COMMIT_NOTHING_TO_COMMIT = "Nothing to commit.";
                            public static final String MSG_COMMIT_ERR_CONFLICT_MARKERS_HEADER = "Error: The following files contain unresolved conflict markers:";
                            public static final String MSG_COMMIT_ERR_CONFLICT_MARKERS_EXAMPLE = "\nConflict markers look like:\n  <<<<<<< HEAD\n  =======\n  >>>>>>> branch-name";
                            public static final String MSG_COMMIT_ERR_CONFLICT_MARKERS_RESOLVE = "\nEdit these files to resolve conflicts before committing.";
                        // Merge command legacy/test compatibility messages
                        public static final String MSG_MERGE_ERR_MUST_SPECIFY_BRANCH = "Error: Must specify source or target branch.";
                        public static final String MSG_MERGE_LEGACY_SUCCESS = "Merged branch '%s' into '%s'.";
                    // Push command messages
                    public static final String MSG_PUSH_USAGE = "Usage: vgl push [-noop]";
                    public static final String MSG_PUSH_SUCCESS = "Push complete";
                    public static final String MSG_PUSH_NO_REMOTE = "No remote configured";
                    public static final String MSG_PUSH_NO_REPO = "No VGL repository can be resolved from the current working directory.\nHint: Run 'vgl create' to initialize a new repo here.";
                    public static final String MSG_PUSH_DRY_RUN = "(dry run) would push local branch to remote";

                    // Pull command messages
                    public static final String MSG_PULL_USAGE = "Usage: vgl pull [-noop] [-f]";
                    public static final String MSG_PULL_SUCCESS = "Pull complete";
                    public static final String MSG_PULL_CONFLICT = "Pull had conflicts or failed.";
                    public static final String MSG_PULL_UNCOMMITTED_WARNING = "Warning: You have uncommitted changes.";
                    public static final String MSG_PULL_CANCELLED = "Pull cancelled.";
                    public static final String MSG_PULL_DRY_RUN = "(dry run) would pull from remote";
                // Delete command messages
                public static final String MSG_DELETE_USAGE = "Usage: vgl delete [-lr [DIR]] [-lb [BRANCH]] [-rb [BRANCH]] | [-bb [BRANCH]]\nExamples:\n  vgl delete -lr              Delete local repository from switch state\n  vgl delete -lr ../old       Delete specified local repository\n  vgl delete -lb              Delete local branch from switch state\n  vgl delete -lb oldbranch    Delete specified local branch\n  vgl delete -bb              Delete both branches from switch state";
                public static final String MSG_DELETE_REMOTE_NOT_IMPLEMENTED = "Warning: Deleting remote repositories is not yet implemented. For now, use your repository hosting tools (GitHub, GitLab, etc.).";
                public static final String MSG_DELETE_BRANCH_SUCCESS_PREFIX = "Deleted branch '";
                public static final String MSG_ERR_DELETE_CURRENT_BRANCH = "Error: Cannot delete the currently checked out branch '";
                public static final String MSG_ERR_DIR_NOT_FOUND = "Error: Directory does not exist: ";
                public static final String MSG_ERR_NOT_A_GIT_REPO = "Error: Not a Git repository: ";
            public static final String MSG_TRACK_USAGE = "Usage: vgl track <glob...> | -all";
            public static final String MSG_TRACK_SUCCESS_PREFIX = "Tracking: ";
        // Track/Untrack command messages
        public static final String MSG_UNTRACK_USAGE = "Usage: vgl untrack <glob...> | -all";
        public static final String MSG_UNTRACK_SUCCESS_PREFIX = "Untracked: ";
        public static final String MSG_ERR_FILE_NOT_TRACKED = "Error: File is not tracked: ";
        public static final String MSG_ERR_ALL_FILES_IGNORED = "All matching files are ignored by git.";
    private MessageConstants() {}

    public static final String MSG_NO_REPO_PREFIX = "Error: No git repository found in: ";
    public static final String MSG_NO_REPO_HELP = "Run 'vgl create <path>' to make one.";
    public static final String MSG_NO_REPO_WARNING_PREFIX = "Warning: No local repository found in: ";
    public static final String MSG_NESTED_REPO_WARNING = "Note: Repository will be nested under parent repo at: ";
    public static final String MSG_NO_REPO_RESOLVED = "No VGL repository can be resolved from the current working directory.\nHint: Run 'vgl create' to initialize a new repo here.";
    public static final String MSG_CHECKOUT_SUCCESS_PREFIX = "Checked out branch: ";
    public static final String MSG_BRANCH_DOES_NOT_EXIST = "Error: Branch does not exist: ";
    public static final String MSG_COMMIT_SUCCESS = "Commit successful";
    public static final String MSG_NO_FILES_SPECIFIED = "No files specified";
    public static final String MSG_ERR_NO_REMOTE_URL = "Error: No remote URL specified and none in switch state.";
    public static final String MSG_ERR_REMOTE_URL_HELP = "Use 'vgl checkout -rr URL' or configure remote with 'vgl switch -rr URL'.";
    public static final String MSG_ERR_UNCOMMITTED_CHANGES = "Warning: Directory already exists with uncommitted changes: ";
    public static final String MSG_ERR_OVERWRITE_CHANGES = "Checking out will overwrite these changes:";

        // Merge command messages
        public static final String MSG_MERGE_USAGE = "Usage: vgl merge -from|-into [-lr [DIR]] [-lb [BRANCH]] [-rr [URL]] [-rb [BRANCH]] [-bb [BRANCH]]";
        public static final String MSG_MERGE_EXAMPLES_HEADER = "Examples:";
        public static final String MSG_MERGE_EXAMPLE_1 = "  vgl merge -from -lb feature    Merge 'feature' branch into switch state";
        public static final String MSG_MERGE_EXAMPLE_2 = "  vgl merge -into -lb main       Merge switch state branch into 'main'";
        public static final String MSG_MERGE_EXAMPLE_3 = "  vgl merge -from -bb feature    Merge feature from both local and remote";
        public static final String MSG_MERGE_ERR_BOTH_DIRECTIONS = "Error: Cannot specify both -from and -into.";
        public static final String MSG_MERGE_ERR_UNCOMMITTED = "Error: You have uncommitted changes.";
        public static final String MSG_MERGE_ERR_COMMIT_FIRST = "Commit them before merging:";
        public static final String MSG_MERGE_ERR_NO_REMOTE = "Error: No remote configured.";
        public static final String MSG_MERGE_ERR_MUST_SPECIFY_DIRECTION = "Error: Must specify either -from or -into.";
        public static final String MSG_MERGE_ERR_MISSING_BRANCH = "Error: Missing source or target branch.";
        public static final String MSG_MERGE_ERR_CONFIGURE_REMOTE = "Use 'vgl switch -rr URL' to configure a remote first.";
        public static final String MSG_MERGE_ERR_BRANCH_NOT_EXIST = "Error: Branch '%s' does not exist.";
        public static final String MSG_MERGE_ERR_TARGET_BRANCH_NOT_EXIST = "Error: Target branch '%s' does not exist.";
        public static final String MSG_MERGE_ERR_BRANCH_SELF = "Error: Cannot merge branch '%s' into itself.";
        public static final String MSG_MERGE_ERR_MUST_SPECIFY_FROM = "Error: Must specify -lb or -rb with -from.";
        public static final String MSG_MERGE_ERR_MUST_SPECIFY_INTO = "Error: Must specify -lb with -into.";
        public static final String MSG_MERGE_MERGING = "Merging '%s' into '%s'...";
        public static final String MSG_MERGE_FAST_FORWARD = "Fast-forward merge successful.";
        public static final String MSG_MERGE_MERGED = "Merge successful.";
        public static final String MSG_MERGE_ALREADY_UP_TO_DATE = "Already up to date.";
        public static final String MSG_MERGE_CONFLICTS = "Merge conflicts detected!";
        public static final String MSG_MERGE_CONFLICT_FILES = "The following files have conflicts:";
        public static final String MSG_MERGE_CONFLICT_RESOLVE = "\nResolve conflicts manually, then:\n  1. Edit the files to resolve conflicts\n  2. Use 'vgl commit \"Merge message\"' to complete the merge\n  OR use 'vgl abort' to cancel the merge";
        public static final String MSG_MERGE_FAILED = "Merge failed: %s";
        public static final String MSG_MERGE_STATUS = "Merge status: %s";
        public static final String MSG_MERGE_SUCCESS = "Merged.";

        // Checkin command messages
        public static final String MSG_CHECKIN_USAGE = "Usage: vgl checkin -draft|-final";
        public static final String MSG_CHECKIN_ERR_NO_REPO = "No VGL repository can be resolved from the current working directory.\nHint: Run 'vgl create' to initialize a new repo here.";
        public static final String MSG_CHECKIN_ERR_NOT_FOUND_BOTH = "Error: Could not resolve both local and remote repositories.";
        public static final String MSG_CHECKIN_PR_GITHUB = "Open your PR: https://github.com/%s/compare/main...%s?expand=1%s";
        public static final String MSG_CHECKIN_PR_OTHER = "Remote is not GitHub; open a PR in your provider.";
        public static final String MSG_CHECKIN_ERR_NO_FILES = "No files specified";
    public static final String MSG_ERR_DIR_EXISTS = "Directory already exists: ";
    public static final String MSG_ERR_USE_LOCAL = "Use 'vgl local' to switch to this repository.";
    public static final String MSG_ERR_DIR_NOT_EMPTY = "Directory already exists and is not empty: ";
    public static final String MSG_ERR_CHECKOUT_CANCELLED = "Checkout cancelled.";

    // Switch command messages
    public static final String MSG_SWITCH_USAGE = "Usage: vgl switch [-lr DIR] [-lb BRANCH] [-bb BRANCH] [-rr URL] [-rb BRANCH]";
    public static final String MSG_SWITCH_EXAMPLES_HEADER = "Examples:";
    public static final String MSG_SWITCH_EXAMPLE_1 = "  vgl switch -lr ../other -lb develop    Switch to different repo and branch";
    public static final String MSG_SWITCH_EXAMPLE_2 = "  vgl switch -lb feature                  Switch branch in current repo";
    public static final String MSG_SWITCH_EXAMPLE_3 = "  vgl switch -bb develop                  Switch both local and remote to develop";
    public static final String MSG_SWITCH_EXAMPLE_4 = "  vgl switch -rr https://... -rb main     Configure remote";
    public static final String MSG_SWITCH_UNCOMMITTED_WARNING = "Warning: You have uncommitted changes in your working directory.";
    public static final String MSG_SWITCH_MAY_LOSE_CHANGES = "Switching branches may cause these changes to be lost.";
    public static final String MSG_SWITCH_CANCELLED = "Switch cancelled.";
    public static final String MSG_SWITCH_SUCCESS_PREFIX = "Switched to branch '";
}