package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.Utils;
import java.nio.file.Path;

/** Centralizes common one-line warnings to keep commands and tests consistent. */
public final class CommandWarnings {
    private CommandWarnings() {}

    /**
     * Print a warning/hint message, and optionally prompt the user for a single-letter choice.
     *
     * <p>In non-interactive mode or when {@code force} is true, no prompt is printed and
     * {@code defaultChoice} is returned.
     */
    public static char warnHintAndMaybePromptChoice(
        String warningAndHintMessage,
        boolean force,
        String prompt,
        char defaultChoice,
        char... allowedChoices
    ) {
        return Utils.warnHintAndMaybePromptChoice(
            warningAndHintMessage,
            force,
            prompt,
            defaultChoice,
            allowedChoices
        );
    }

    public static void warnTargetRepoNotCurrent(Path targetRepoRoot) {
        System.err.println(Messages.warnTargetRepoNotCurrent(targetRepoRoot));
    }
}
