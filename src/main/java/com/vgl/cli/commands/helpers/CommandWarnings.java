package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.Messages;
import java.nio.file.Path;

/** Centralizes common one-line warnings to keep commands and tests consistent. */
public final class CommandWarnings {
    private CommandWarnings() {}

    public static void warnTargetRepoNotCurrent(Path targetRepoRoot) {
        System.err.println(Messages.warnTargetRepoNotCurrent(targetRepoRoot));
    }
}
