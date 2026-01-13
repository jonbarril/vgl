package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.Messages;

public final class Usage {
    private Usage() {}

    public static String root() {
        return String.join("\n",
            "Usage:",
            "  vgl <command> [args]",
            "",
            "Commands:",
            "  abort",
            "  pull",
            "  push",
            "  sync",
            "  restore",
            "  diff",
            "  log",
            "  commit",
            "  checkin",
            "  checkout",
            "  create",
            "  delete",
            "  merge",
            "  split",
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
            "  vgl status [-v|-vv] [-context [URL]] [-changes] [-history] [-files]"
        );
    }

    public static String track() {
        return Messages.trackUsage();
    }

    public static String untrack() {
        return Messages.untrackUsage();
    }

    public static String abort() {
        return Messages.abortUsage();
    }

    public static String pull() {
        return Messages.pullUsage();
    }

    public static String push() {
        return Messages.pushUsage();
    }

    public static String sync() {
        return Messages.syncUsage();
    }

    public static String restore() {
        return Messages.restoreUsage();
    }

    public static String diff() {
        return Messages.diffUsage();
    }

    public static String log() {
        return Messages.logUsage();
    }

    public static String merge() {
        return Messages.mergeUsage();
    }

    public static String split() {
        return Messages.splitUsage();
    }

    public static String checkout() {
        return Messages.checkoutUsage();
    }

    public static String checkin() {
        return Messages.checkinUsage();
    }

    public static String switchCmd() {
        return String.join("\n",
            "Usage:",
            "  vgl switch",
            "  vgl switch [-lb BRANCH|-bb BRANCH] [-rr URL] [-rb BRANCH]"
        );
    }

    public static String commit() {
        return String.join("\n",
            "Usage:",
            "  vgl commit [-f] [-lr DIR] MESSAGE",
            "  vgl commit [-f] [-lr DIR] -new MESSAGE",
            "  vgl commit [-f] [-lr DIR] -add MESSAGE"
        );
    }
}
