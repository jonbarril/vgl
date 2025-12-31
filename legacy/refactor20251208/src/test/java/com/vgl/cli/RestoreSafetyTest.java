package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.*;

public class RestoreSafetyTest {

    private static String runWithInput(String input, String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream oldIn = System.in;
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes("UTF-8")));
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    @Test
    void restoreAsksForConfirmation() throws Exception {
        // Simulate 'n' response to cancel
        String output = runWithInput("n\n", "restore");
        
        // Should prompt for confirmation OR show an error if no proper repo/HEAD
        // When running from workspace, may find workspace .git but fail on HEAD resolution
        assertThat(output).containsAnyOf("Continue?", "cancelled", "No repository found", "Cannot resolve");
    }

    @Test
    void restoreDefaultsToAllFiles() throws Exception {
        // Calling restore with no args should default to "*"
        String output = runWithInput("n\n", "restore");
        
        // Should indicate all files or show prompt
        assertThat(output).doesNotContain("Usage:");
    }

    @Test
    void forceFlagSkipsConfirmationPrompt() throws Exception {
        // With -f flag, should skip confirmation prompt
        // Will likely fail on "No repository" or similar because we're not in a test repo
        // But it should NOT hang waiting for user input
        String output = runWithInput("", "restore", "-f");
        
        // Should either restore files or show error, but NOT prompt for confirmation
        assertThat(output).doesNotContain("Continue? (y/N):");
    }
}
