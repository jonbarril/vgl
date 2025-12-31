package com.vgl.cli.commands.helpers;

public final class Usage {
    private Usage() {}

    public static String root() {
        return String.join("\n",
            "Usage:",
            "  vgl <command> [args]",
            "",
            "Commands:",
            "  create",
            "  delete",
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
}
