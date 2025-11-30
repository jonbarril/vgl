package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import java.util.List;

public class HelpCommand implements Command {
    @Override public String name() { return "help"; }

    @Override public int run(List<String> args) {
        StringBuilder helpText = new StringBuilder();
        helpText.append(String.join("\n",
            "Voodoo Gitless (" + Utils.versionFromRuntime() + ") -- Git for mortals",
            "",
            "Commands:",
            "  create",
            "    [-lr DIR] [-lb BRANCH]              Create new local repository and/or",
            "                                          branch and switch to it",
            "    [-bb BRANCH]                        Create branch locally and remotely",
            "                                          and switch to it",
            "    [-rr URL] [-rb BRANCH]              (TBD) Create new remote repository",
            "                                          and/or branch and switch to it -",
            "                                          for now use repository hosting",
            "                                          tools",
            "  delete",
            "    [-lr [DIR]] [-lb [BRANCH]]          Delete local repository and/or",
            "                                          branch (default: switch state)",
            "    [-bb [BRANCH]]                      Delete both local and remote branch",
            "                                          (default: switch state)",
            "    [-rb [BRANCH]]                      Delete remote branch (default:",
            "                                          switch state)",
            "  checkout",
            "    [-rr [URL]] [-rb [BRANCH]]          Clone remote repository and/or",
            "                                          branch into switch state",
            "                                          (default: switch state)",
            "  switch [-lr DIR][-rr URL] [[-lb BRANCH][-rb BRANCH]]|[-bb BRANCH]]",
            "                                        Switch to different repos and",
            "                                          branches (shortcut)",
            "  jump                                  Toggles to previous switch state",
            "  split [-lr DIR][-lb BRANCH]                    Creates a new local repo/branch from the current",
            "                                                 ones, and switches to them (shortcut)",
            "  merge [-lr DIR][-lb BRANCH]                    Merges local repo/branch into the current ones,",
            "                                                 and optionally deletes them (shortcut)",
            "  track GLOB... | untrack GLOB...                Add/remove files from version control",
            "  commit \"MESSAGE\" | [-new|-add] \"MESSAGE\"       Commit changes / amend last commit (before push)",
            "  restore -lb|-rb [COMMIT|GLOB|*]                Revert working files to local or remote versions",
            "  pull [-noop]                                   Merge remote changes into local branch",
            "  push [-noop]                                   Replace remote branch with local changes",
            "  sync [-noop]                                   Pull then push (shortcut)",
            "  checkin -draft|-final                          Push and generate a pull request (PR)",
            "  abort                                          Cancel ongoing pull merge, undoing any changes",
            "",
            "  status [-v|-vv] [COMMIT|GLOB|*]                Show workspace/repo/file status",
            "  diff [-lb|-rb] [COMMIT|GLOB|*]                 Compare working files with local or remote",
            "                                                 versions",
            "  log [-v|-vv] [-graph]                          Show commit history",
            "  help [-v|-vv]                                  Show this help"
        ));

        if (args.contains("-v") || args.contains("-vv")) {
            helpText.append("\n\nFlags:\n");
            helpText.append(String.join("\n",
                "  -lr DIR          Local repository directory",
                "  -lb BRANCH       Local branch name (default:'main')",
                "  -rr URL          Remote repository URL",
                "  -rb BRANCH       Remote branch name (default:'main')",
                "  -bb BRANCH       Both local and remote branchnames (default:'main')",
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
                "    Use 'create -lr DIR' to make a new repository (main branch), or",
                "    'switch -lr DIR -lb BRANCH' to work in existing ones. Your workspace is",
                "    where your working files live. Changes exist only in the workspace until",
                "    you 'commit' them to the local repository's branch. Use 'split' to create",
                "    a new branch for experiments,'merge' to combine branches, and 'jump' to toggle between workspaces.",
                "",
                "  Adding Remote Collaboration (Optional):",
                "    Use 'switch -rr URL -rb BRANCH' to use a remote repository for cloud",
                "    backup and team collaboration. Then 'push' sends your local commits to the",
                "    remote and 'pull' fetches and merges remote changes. Use 'sync' to do both.",
                "    For team work, use 'checkin' to push and create a pull request (PR), as",
                "    a draft or final version, which gives teammates a web form to review your",
                "    changes before merging.",
                "",
                "  Inspecting Your Work:",
                "    Use 'status' to see your current and jump workspace contexts, uncommitted",
                "    changes, and sync state with remote. Use 'diff' to review actual file changes",
                "    compared to your local or remote branch. Use 'log' to view the commit history.",
                "    The 'restore' command undoes workspace changes, returning files to their last",
                "    committed state."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
