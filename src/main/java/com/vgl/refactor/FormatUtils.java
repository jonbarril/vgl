package com.vgl.refactor;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.List;

public final class FormatUtils {
    private FormatUtils() {}

    public static void printSwitchState(VglCli vgl, boolean verbose, boolean veryVerbose) {
        // Use the existing Utils.printSwitchState implementation shape but keep here
        // to allow migration off Utils.
        com.vgl.cli.Utils.printSwitchState(vgl, verbose, veryVerbose);
    }

    public static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }
}
