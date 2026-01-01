package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.RepoUtils;
import java.nio.file.Path;

/** Shared, consistent state-change reporting helpers for state-changing commands. */
public final class StateChangeOutput {
    private StateChangeOutput() {}

    /**
     * Prints the target repo's switch state, and if {@code warnIfNotCurrent} is true, emits the
     * standard warning when the target repo is not the current repo resolved from CWD.
     */
    public static void printSwitchStateAndWarnIfNotCurrent(Path targetRepoRoot, boolean warnIfNotCurrent) throws Exception {
        if (targetRepoRoot == null) {
            return;
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cwdRepoRoot = RepoUtils.findNearestRepoRoot(cwd);
        boolean isCurrent = cwdRepoRoot != null && cwdRepoRoot.toAbsolutePath().normalize().equals(targetRepoRoot.toAbsolutePath().normalize());

        if (warnIfNotCurrent && !isCurrent) {
            CommandWarnings.warnTargetRepoNotCurrent(targetRepoRoot.toAbsolutePath().normalize());
            // We warned that the current switch state is unchanged, so print the current repo state.
            // If there is no current repo resolved from CWD, fall back to the target repo state.
            SwitchStateOutput.print(cwdRepoRoot != null ? cwdRepoRoot : targetRepoRoot);
            return;
        }

        SwitchStateOutput.print(targetRepoRoot);
    }
}
