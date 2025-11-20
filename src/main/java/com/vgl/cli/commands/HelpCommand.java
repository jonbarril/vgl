package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import java.util.List;

public class HelpCommand implements Command {
    @Override public String name() { return "help"; }
    @Override public int run(List<String> args) {
        String txt = String.join("\n",
            "Voodoo Gitless  (" + Utils.versionFromRuntime() + ") -- Git for mortals",
            "Commands:",
            "  create [<dir>] [-b <branch>]          Create local repo (warn if exists or nested), set local",
            "  checkout <url> [-b <branch>]          Clone local repo+branch from remote, set local/remote",
            "  local [<dir>] [-b <branch>]           Set local repo+branch (warn if missing or nested)",
            "  remote [<url>] [-b <branch>]          Set remote repo+branch (warn if missing)",
            "  track <glob...> | untrack <glob...>   File control (uses .gitignore rules)",
            "  commit \"msg\" | -new \"new msg\"     New commit / modify last message (pre-push)",
            "  restore -lb|-rb <commit|glob|*>       Restore working files from local/remote branch",
            "  pull [-noop]                          Merge remote into working files",
            "  push [-noop]                          Replace remote with committed local files (warn if conflict)",
            "  checkin -draft|-final                 Push + PR intent (prints URL template)",
            "  sync [-noop]                          Pull then push (warn if conflict)",
            "  abort                                 Cancel merge in progress",
            "  status [-v|-vv] | <commit>            Dashboard with commit details",
            "  diff [-lb|-rb] [<file...>|<glob>]     Compare working files to local/remote branch",
            "  log [-v|-vv] [-gr]                    Creation+commit history, graph optional",
            "Flags:",
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
        );
        System.out.println(txt);
        return 0;
    }
}
