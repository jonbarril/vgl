package com.vgl.cli;

import com.vgl.cli.commands.StatusFileSummary;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusFileSummaryTest {
    @Test
    public void fileSummaryUsesAddedModifiedRenamedDeletedOrder() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, "UTF-8"));
            // Signature: (numModified, numAdded, numRemoved, numReplaced, numToMerge, undecidedSet, trackedSet, untrackedSet, ignoredSet)
            // We want Added=5, Modified=2, Renamed=1, Deleted=3
            StatusFileSummary.printFileSummary(2, 5, 3, 1, 0, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
            String out = baos.toString(StandardCharsets.UTF_8.name());
            assertThat(out).contains("FILES   5 Added, 2 Modified, 1 Renamed, 3 Deleted");
            assertThat(out).contains("0 Undecided, 0 Tracked, 0 Untracked, 0 Ignored");
        } finally {
            System.setOut(oldOut);
        }
    }
}
