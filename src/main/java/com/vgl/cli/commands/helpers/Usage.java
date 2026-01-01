package com.vgl.cli.commands.helpers;

public final class Usage {
    private Usage() {}

    public static String root() {
        return String.join("\n",
            "Usage:",
            "  vgl <command> [args]",
            "",
            "Commands:",
            "  commit",
            "  create",
            "  delete",
            "  switch",
            "  track",
            "  untrack",
            "  status",
            "  help"
        );
    }

    public static String create() {
        return String.join("\n",
            "Usage:",
            "  vgl create [-f] [-lr DIR] [-lb BRANCH|-bb BRANCH]"
        );
    }

    public static String delete() {
        return String.join("\n",
            "Usage:",
            "  vgl delete [-f] [-lr DIR] [-lb BRANCH|-bb BRANCH]"
        );
    }

    public static String status() {
        return String.join("\n",
            "Usage:",
            "  vgl status [-v|-vv] [-local] [-remote] [-commits] [-files] [COMMIT|GLOB|*]"
        );
    }

    public static String track() {
        return String.join("\n",
            "Usage:",
            "  vgl track <glob...> | -all"
        );
    }

    public static String untrack() {
        return String.join("\n",
            "Usage:",
            "  vgl untrack <glob...> | -all"
        );
    }

    public static String switchCmd() {
        return String.join("\n",
            "Usage:",
            "  vgl switch [-lr DIR] [-lb BRANCH|-bb BRANCH]"
        );
    }

    public static String commit() {
        return String.join("\n",
            "Usage:",
            "  vgl commit [-lr DIR] MESSAGE",
            "  vgl commit [-lr DIR] -new MESSAGE",
            "  vgl commit [-lr DIR] -add MESSAGE"
        );
    }
}
