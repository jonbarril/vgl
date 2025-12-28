package com.vgl.cli.utils;

import com.vgl.cli.VglCli;

public final class FormatUtils {
    private FormatUtils() {}

    public static void printSwitchState(VglCli vgl, boolean verbose, boolean veryVerbose) {
        // Use the existing Utils.printSwitchState implementation shape but keep here
        // to allow migration off Utils.
        com.vgl.cli.utils.RepoUtils.printSwitchState(vgl, verbose, veryVerbose);
    }

    public static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }
}
