package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusNoHeadTest {
    private static void printProgress(String testName) {
        TestProgress.print(StatusNoHeadTest.class, testName);
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
