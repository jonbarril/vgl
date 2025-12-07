package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusNoHeadTest {
    private static int currentTest = 0;
    private static final int TOTAL_TESTS = 1;
    private static void printProgress(String testName) {
        currentTest++;
        System.out.println("[StatusNoHeadTest " + currentTest + "/" + TOTAL_TESTS + ": " + testName + "]...");
        System.out.flush();
    }

    @Test
    void statusHandlesNoCommits(@TempDir Path tmp) throws Exception {
        printProgress("statusHandlesNoCommits");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Do NOT create any commits
            String output = repo.runCommand("status", "-v");
            assertThat(output).contains("(no commits yet)");
            // Should still show FILES line
            assertThat(output).contains("FILES");
        }
    }
}
