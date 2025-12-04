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
            "  split -into|-from                     Clone branch from source, then switch",
            "    [-lr [DIR]] [-lb [BRANCH]]            defaults to switch state",
            "    [-rr [URL]] [-rb [BRANCH]]            defaults to switch state",
            "    [-bb [BRANCH]]                        defaults to switch state",
            "  merge -into|-from                     Merge from source or into target branch",
            "    [-lr [DIR]] [-lb [BRANCH]]            defaults to switch state",
            "    [-rr [URL]] [-rb [BRANCH]]            defaults to switch state",
            "    [-bb [BRANCH]]                        defaults to switch state",
            "  track GLOB... | untrack GLOB...       Add/remove files from version control",
            "  commit \"MESSAGE\" | [-new|-add] \"MSG\"  Commit changes / amend last commit",
            "  restore [-f]                          Revert working files to local or remote",
            "    [-lb|-rb] [COMMIT|GLOB|*]             specify branch and optional file filter",
            "  pull [-f] [-noop]                     Merge remote changes into local branch",
            "  push [-noop]                          Replace remote branch with local changes",
            "  sync [-noop]                          Pull then push",
            "  checkin -draft|-final                 Push and create pull request",
            "  abort                                 Cancel ongoing pull merge",
            "",
            "  status [-v|-vv] [COMMIT|GLOB|*]       Show workspace/repo/file status",
            "  diff [COMMIT|GLOB|*]                  Compare working files with switch state branches",
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
                "  -noop            Dry run without making changes",
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
                "    Use 'create -lr DIR' to make a new local repository (defaults to main",
                "    branch), or 'switch -lr DIR -lb BRANCH' to work in existing ones.",
                "    Your workspace is where your working files live. Changes exist only in",
                "    the workspace until you 'commit' them to the local branch.",
                "    Use 'split' to create a new branch for experiments, 'merge' to combine",
                "    branches, and 'delete' to clean up unused branches and repositories.",
                "    Use 'jump' to toggle between your current and previous switch state",
                "    (where you're working locally and remotely).",
                "",
                "  Adding Remote Collaboration (Optional):",
                "    Use 'switch -rr URL -rb BRANCH' to connect to a remote repository for",
                "    cloud backup and team collaboration. Use 'push' to send your local commits",
                "    to the remote and 'pull' to fetch and merge remote changes. Use 'sync' to",
                "    do both. For teamwork, use 'checkin' to push and create a pull request",
                "    (PR) as either a draft or final submission, giving teammates a web",
                "    interface to review your changes before merging.",
                "",
                "  Inspecting Your Work:",
                "    Use 'status' to see your current and previous switch state, uncommitted",
                "    changes, sync state with remote, and file-level details. Use 'diff' to",
                "    review working file changes or compare branches. Use 'log' to view the",
                "    commit history. The 'restore' command undoes changes to working files,",
                "    returning them to their last committed state."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
