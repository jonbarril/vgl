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
            "  create [DIR] [-b BRANCH]              Create local repo, set as current workspace",
            "  checkout URL [-b BRANCH]              Clone remote repo+branch, set as current workspace",
            "  local [DIR] [-b BRANCH]               Switch to existing local repo+branch",
            "  remote [URL] [-b BRANCH]              Configure remote repo+branch for push/pull",
            "",
            "  track GLOB... | untrack GLOB...       Add/remove files from version control",
            "  commit \"MESSAGE\" | -new \"MESSAGE\"     Commit changes / amend last commit message",
            "  restore -lb|-rb [COMMIT|GLOB|*]       Revert working files to saved state",
            "",
            "  pull [-noop]                          Fetch and merge remote changes",
            "  push [-noop]                          Upload committed changes to remote",
            "  sync [-noop]                          Pull then push (shortcut)",
            "  checkin -draft|-final                 Push and generate PR URL",
            "  abort                                 Cancel ongoing merge",
            "",
            "  status [-v|-vv] [COMMIT|GLOB|*]       Show workspace/repo/file status",
            "  diff [-lb|-rb] [COMMIT|GLOB|*]        Compare working files with saved versions",
            "  log [-v|-vv] [-graph]                 Show commit history",
            "  help [-v|-vv]                         Show this help"
        ));

        if (args.contains("-v") || args.contains("-vv")) {
            helpText.append("\n\nFlags:\n");
            helpText.append(String.join("\n",
                "  -b BRANCH        Branch name (default: main)",
                "  -lb, -rb         Compare with local or remote branch",
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
                "    Use 'create' to make a new local repository in a directory (or 'local' to",
                "    switch to an existing one). Your workspace is where your working files live.",
                "    Changes you make exist only in the workspace until you 'commit' them to the",
                "    local repository's branch (default: main). This records a snapshot you can",
                "    return to later. You can work entirely locally without any remote setup.",
                "",
                "  Adding Remote Collaboration (Optional):",
                "    Use 'remote' to configure a remote repository URL for cloud backup and team",
                "    collaboration. Then 'push' sends your local commits to the remote and 'pull'",
                "    fetches and merges remote changes. Use 'sync' to do both. The 'checkout'",
                "    command combines cloning a remote repository with local setup. For team work,",
                "    use 'checkin' to push and create a pull request (PR), which gives teammates",
                "    a web form to review your changes before merging.",
                "",
                "  Inspecting Your Work:",
                "    Use 'status' to see your current workspace state, uncommitted changes, and",
                "    repository configuration. Use 'diff' to review actual file changes compared",
                "    to your local or remote branch. Use 'log' to view the commit history. The",
                "    'restore' command undoes workspace changes, returning files to their last",
                "    committed state."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
