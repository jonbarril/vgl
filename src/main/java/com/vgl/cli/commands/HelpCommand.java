package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import java.util.List;

public class HelpCommand implements Command {
    @Override public String name() { return "help"; }
    @Override public int run(List<String> args) {
        String txt = String.join("\n",
            "Voodoo Gitless  (" + Utils.versionFromRuntime() + ") -- Git for mortals",
            "Commands:",
            "  create <path>[@branch]                 Create local repo (warn if nested), set focus",
            "  create @<branch> | create -b <branch>  Create local branch, set focus",
            "  checkout <url>[@branch] | -b <branch>  Clone local repo+branch from remote, set focus/connect",
            "  local <repo>[@branch] | -b <branch>    Set local repo+branch (warn if nested)",
            "  remote <url>[@branch] | -b <branch>    Set remote repo+branch",
            "  track <glob...> | untrack <glob...>    File control (uses .gitignore rules)",
            "  commit \"msg\" | -m \"new msg\"            New commit / modify last message (pre-push)",
            "  restore -lb|-rb <commit|glob|*>        Restore working files from local/remote branch",
            "  pull [-dr]                             Merge remote into working files",
            "  push [-dr]                             Replace remote with committed local (warn if conflict)",
            "  checkin -draft|-final                  Push + PR intent (prints URL template)",
            "  sync [-dr]                             Pull then push (warn if conflict)",
            "  abort                                  Cancel merge in progress",
            "  status [-v|-vv] | <commit>             Dashboard / commit details",
            "  diff -rb | [<path...>]                 Compare working files to local/remote branch",
            "  log [-v|-vv] [-gr]                     Creation+commit history, graph optional",
            "Flags:",
            "  @                Branch name/spec separator",
            "  -b <branch>      Alternative branch spec",
            "  -dr              Dry run (no local/remote changes)",
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
