package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import java.util.List;

/**
 * HelpCommand displays usage information.
 * 
 * Note: Help text was refactored for compactness on 2025-12-04.
 * Original version with multi-line descriptions is preserved in git history (commit before this change).
 */
public class HelpCommand implements Command {
    @Override public String name() { return "help"; }

    @Override public int run(List<String> args) {
        StringBuilder helpText = new StringBuilder();
        helpText.append(String.join("\n",
            "Voodoo Gitless (" + Utils.versionFromRuntime() + ") -- Git for mortals",
            "",
            "Commands:",
            // Group 1: repo/branch management
            "  create [-f]                           Create local/remote repo/branch, then switch",
            "    [-lr DIR] [-lb BRANCH]                defaults to switch state",
            "    [-bb BRANCH]                          defaults to switch state",
            "    [-rr URL] [-rb BRANCH]                TBD - use hosting tools",
            "  delete [-f]                           Delete local/remote repo/branch",
            "    [-lr [DIR]] [-lb [BRANCH]]            defaults to switch state",
            "    [-bb [BRANCH]]                        defaults to switch state",
            "    [-rr [URL]] [-rb [BRANCH]]            TBD - use hosting tools",
            "  checkout [-f]                         Clone remote, then switch to local",
            "    [-rr [URL]] [-rb [BRANCH]]            defaults to switch state",
            "  switch [-f]                           Switch repos and branches",
            "    [-lr DIR] [-lb BRANCH]                defaults to switch state",
            "    [-bb BRANCH]                          defaults to switch state",
            "    [-rr URL] [-rb BRANCH]                defaults to switch state",
            "  jump                                  Toggle to previous switch state",
            "",
            // Group 2: branch/merge/split
            "  split -into|-from                     Clone branch from source, then switch",
            "    [-lr [DIR]] [-lb [BRANCH]]            defaults to switch state",
            "    [-rr [URL]] [-rb [BRANCH]]            defaults to switch state",
            "    [-bb [BRANCH]]                        defaults to switch state",
            "  merge -into|-from                     Merge from source or into target branch",
            "    [-lr [DIR]] [-lb [BRANCH]]            defaults to switch state",
            "    [-rr [URL]] [-rb [BRANCH]]            defaults to switch state",
            "    [-bb [BRANCH]]                        defaults to switch state",
            "",
            // Group 3: file tracking/commit/restore
            "  track/untrack GLOB|* ...              Add/remove files from version control",
            "    -all                                  or, all undecided files",
            "  commit \"MESSAGE\" | [-new|-add] \"MSG\"  Commit changes / amend last commit",
            "  restore [-f] [GLOB|* ...]             Revert working files to local or remote",
            "    [-lb|-rb] [COMMIT|GLOB|* ...]         specify branch and optional file filter",
            "",
            // Group 4: sync/abort
            "  pull [-f] [-noop]                     Merge remote changes into local branch",
            "  push [-noop]                          Replace remote branch with local changes",
            "  sync [-noop]                          Pull then push",
            "  checkin -draft|-final                 Push and create pull request",
            "  abort                                 Cancel ongoing pull merge",
            "",
            // Group 5: status/diff/log/help
            "  status [-v|-vv] [COMMIT|GLOB|* ...]   Show workspace/repo/file status",
            "  diff [COMMIT|GLOB|* ...]              Compare working files with switch state branches",
            "    [-lb|-rb]                             working vs local or remote branch",
            "    [-lb -rb]                             local branch vs remote branch",
            "  log [-v|-vv] [-graph]                 Show commit history",
            "  help [-v|-vv]                         Show this help"
        ));

        if (args.contains("-v") || args.contains("-vv")) {
            helpText.append("\n\nFlags:\n");
            helpText.append(String.join("\n",
                "  -lr DIR          Local repository directory",
                "  -lb BRANCH       Local branch name (default:'main')",
                "  -rr URL          Remote repository URL",
                "  -rb BRANCH       Remote branch name (default:'main')",
                "  -bb BRANCH       Both local and remote branchnames (default:'main')",
                "  -f               Bypass confirmation prompts (create, commit...)",
                "  -noop            No operation, dry run without making changes",
                "  -v, -vv          Verbose output with increasing detail",
                "",
                "Glob Patterns:",
                "  *.log            All .log files              → app.log, build.log",
                "  file?.txt        Single-char wildcard        → file1.txt, fileA.txt",
                "  **/*.py          Recursive subdirectories    → main.py, lib/util.py",
                "  *.{png,jpg}      Multiple extensions         → cat.png, dog.jpg"
            ));
        }

        if (args.contains("-vv")) {
            helpText.append("\n\nOverview:\n");
            helpText.append(String.join("\n",
                "  Working Locally:",
                "    Use 'create -lr DIR' to make a new repository (defaults to main).",
                "    Or use 'switch -lr DIR -lb BRANCH' to work inside an existing one.",
                "    Your workspace holds your working files; changes remain in the",
                "    workspace until you 'commit' them to the local branch.",
                "    Use 'split' to create an experimental branch, 'merge' to combine",
                "    branches, and 'delete' to remove unused branches or repos.",
                "    Use 'jump' to toggle between your current and previous switch",
                "    state (where you're working locally and remotely).",
                "",
                "  Adding Remote Collaboration (Optional):",
                "    Use 'switch -rr URL -rb BRANCH' to connect to a remote repo for",
                "    cloud backup and team collaboration. Use 'push' to send local",
                "    commits to the remote and 'pull' to fetch and merge remote",
                "    changes. Use 'sync' to perform both actions in sequence.",
                "    For code review workflows, use 'checkin' to push and create a",
                "    pull request (PR). A PR can be a draft or final submission so",
                "    teammates can review changes before merging.",
                "",
                "  Inspecting Your Work:",
                "    Use 'status' to view switch state, uncommitted changes, sync",
                "    status with remote, and file-level details. Use 'diff' to",
                "    review working-file changes or compare branches. Use 'log' to",
                "    examine commit history. Use 'restore' to undo working-file",
                "    changes and return files to their last committed state."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
