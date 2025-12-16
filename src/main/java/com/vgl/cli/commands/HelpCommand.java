package com.vgl.cli.commands;

import com.vgl.cli.utils.Utils;
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
            "Note: See Flags (help -v/-vv) for flag combinations and defaults.",
            "",
            "Commands:",
            "  create [-f]                        Create repo/branch, then switch",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (TBD: use hosting tools)",
            "  delete [-f]                        Delete repo/branch",
            "    -lr [DIR] -lb [BRANCH]             (local repo/branch)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (TBD: use hosting tools)",
            "  switch [-f]                        Switch to repo/branch",
            "    -lb [BRANCH]                       (local repo/branch)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (remote repo/branch)",
            "  split -from|-into                  Clone and switch to new branch",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL] -rb [BRANCH]             (TBD: use hosting tools)",
            "  merge -into|-from                  Merge branches, then switch",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (remote repo/branch)",
            "  checkout [-f]                      Clone remote, switch to local",
            "    -lb [BRANCH]                       (local branch)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL] -rb [BRANCH]             (remote repo/branch)",
            "  checkin -draft|-final              Push and create pull request",
            "",
            "  track GLOB... | untrack GLOB...    Add/remove files from version control",
            "    -all                               Track all undecided files",
            "  commit \"MESSAGE\"|[-new|-add] \"MSG\" Commit changes / amend last commit",
            "  restore [-f] [GLOB|*]              Replace working files with:",
            "    -lr [DIR] -lb [BRANCH] [COMMIT]    local branch/commit",
            "    -rr [URL] -rb [BRANCH] [COMMIT]    remote branch/commit",
            "  pull [-f] [-noop]                  Merge remote changes into local branch",
            "  push [-noop]                       Replace remote branch from local changes",
            "  sync [-noop]                       Pull then push",
            "  abort                              Cancel ongoing merge or pull",
            "",
            "  status [-v|-vv] [COMMIT|GLOB|*]    Show workspace/repo/file status",
            "  diff [GLOB|*]                      Compare files between any two:",
            "    -lr [DIR] -lb [BRANCH] [COMMIT]    local branch/commit",
            "    -bb BRANCH                         (same name for local+remote)",
            "    -rr [URL] -rb [BRANCH] [COMMIT]    remote branch/commit",
            "  log [-v|-vv] [-graph]              Show commit history",
            "  help [-v|-vv]                      Show this help"
        ));

        if (args.contains("-v") || args.contains("-vv")) {
            helpText.append("\n\nFlags:\n");
            helpText.append(String.join("\n",
                "  Note: In general, if no repo/branch  (-lr, -lb, -rr, -rb) is specified then",
                "        the corresponding switch state is used. If a switch state has not been set yet,",
                "        DIR defaults to the local repo resolved from the CWD, local and remote BRANCH",
                "        defaults to 'main', and URL has no default. Branch flags can accept no name,",
                "        a BRANCH name and/or a COMMIT on the branch.",
                "",
                "  CWD           Current workspace directory",
                "  -lr DIR       Local repository directory",
                "  -lb BRANCH    Local branch (default: 'main')",
                "  -rr URL       Remote repository URL",
                "  -rb BRANCH    Remote branch (default: 'main')",
                "  -bb BRANCH    Both local and remote branch (default: 'main')",
                "  -f            Bypass confirmation prompts",
                "  -noop         Dry run (no changes)",
                "  -v, -vv       Verbose output (more detail)"
            ));
            helpText.append("\n\nGlob Patterns:\n");
            helpText.append(String.join("\n",
                "  *.log              All .log files         -> app.log, build.log",
                "  file?.txt          Single-char wildcard   -> file1.txt, fileA.txt",
                "  **/*.py            Recursive search       -> main.py, lib/util.py",
                "  *.{png,jpg}        Multiple extensions    -> cat.png, dog.jpg"
            ));
        }

        if (args.contains("-vv")) {
            helpText.append("\n\nUsage:\n");
            helpText.append(String.join("\n",
                "  Working Locally:",
                "    - Use 'create -lr DIR' to make a new repo (defaults to 'main').",
                "    - Or 'switch -lr DIR -lb BRANCH' to work in an existing repo.",
                "    - Your workspace holds your files, where changes stay until you 'commit' them.",
                "    - Use 'split' for experiments, 'merge' to combine, 'delete' to clean up.",
                "",
                "  Remote Collaboration (Optional):",
                "    - Use 'switch -rr URL -rb BRANCH' to connect to a remote repo.",
                "    - 'push' sends local commits; 'pull' fetches and merges remote changes.",
                "    - 'sync' does both. 'checkin' creates a pull request (PR) for review.",
                "",
                "  Inspecting Your Work:",
                "    - 'status' shows switch state, uncommitted changes, sync state, and files.",
                "    - 'diff' reviews changes or compares branches.",
                "    - 'log' shows commit history.",
                "    - 'restore' undoes changes, returning files to last commit."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
