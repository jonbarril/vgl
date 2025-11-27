package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import java.util.List;

public class HelpCommand implements Command {
    @Override public String name() { return "help"; }

    @Override public int run(List<String> args) {
        StringBuilder helpText = new StringBuilder();
        helpText.append(String.join("\n",
            "Voodoo Gitless  (" + Utils.versionFromRuntime() + ") -- Git for mortals",
            "Commands:",
            "  create [<dir>] [-b <branch>]          Create local repo (warn if exists or nested), set local",
            "  checkout <url> [-b <branch>]          Clone local repo+branch from remote, set local/remote",
            "  local [<dir>] [-b <branch>]           Set local repo+branch (warn if missing or nested)",
            "  remote [<url>] [-b <branch>]          Set remote repo+branch (warn if missing)",
            "  track <glob...> | untrack <glob...>   File control (uses .gitignore rules)",
            "  commit \"msg\" | -new \"new msg\"         New commit / modify last message (pre-push)",
            "  restore -lb|-rb [<commit|glob|*>]     Restore working files from local/remote branch",
            "  pull [-noop]                          Merge remote into working files",
            "  push [-noop]                          Replace remote with committed local files (warn if conflict)",
            "  checkin -draft|-final                 Push + PR intent (prints URL template)",
            "  sync [-noop]                          Pull then push (warn if conflict)",
            "  abort                                 Cancel merge in progress",
            "  status [-v|-vv] [<commit|glob|*>]     Dashboard with repo/commit/file details",
            "  diff [-lb|-rb] [<commit|glob|*>]      Compare working files to local/remote branch",
            "  log [-v|-vv] [-graph]                 Creation+commit history, graph optional",
            "  help [-v|-vv]                         Usage help"
        ));

        if (args.contains("-v") || args.contains("-vv")) {
            helpText.append("\nFlags:\n");
            helpText.append(String.join("\n",
                "  -b <branch>      Specify branch name",
                "  -noop            No-op / dry run (no local/remote changes)",
                "  -v|-vv           Verbose and very verbose info",
                "  <commit>         Commit ID",
                "  <glob>           Expands wildcard patterns into matching filenames",
                "      *.log        -> app.log build.log",
                "      file?.txt    -> file1.txt fileA.txt",
                "      [A-Z]*       -> Docs Makefile README",
                "      **/*.py      -> main.py lib/util.py",
                "      *.{png,jpg}  -> cat.png dog.jpg",
                "      \"*.md\"       -> *.md"
            ));
        }

        if (args.contains("-vv")) {
            helpText.append("\nOverview:\n");
            helpText.append(String.join("\n",
                "  Use 'create' to specify the root directory for a new local repository and",
                "  its associated branch (default: 'main'). This is also where your workspace",
                "  files live. Use 'local' instead to switch the local repo and its",
                "  associated branch (default: 'main').",
                "",
                "  Changes in the workspace exist only in the file system. To record them",
                "  in the local repository, you must 'commit' them.",
                "",
                "  Optionally use 'remote' to specify an existing remote repository and its",
                "  associated branch (default: 'main'). You can then 'pull' and merge remote",
                "  changes into the local repo and 'push' local commits to the remote repository.",
                "",
                "  Use 'status' anytime to see the current state of your workspace, files and",
                "  repositories, 'diff' to compare workspace changes with the local or remote",
                "  repository branch, and 'log' to view the commit history."
            ));
        }

        System.out.println(helpText.toString());
        return 0;
    }
}
