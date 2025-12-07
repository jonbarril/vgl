package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Tests track behavior when the repository has no commits (unborn HEAD).
 */
public class TrackNoHeadTest {
    private static int currentTest = 0;
    private static final int TOTAL_TESTS = 2;
    private static void printProgress(String testName) {
        currentTest++;
        System.out.println("[TrackNoHeadTest " + currentTest + "/" + TOTAL_TESTS + ": " + testName + "]...");
        System.out.flush();
    }

    @Test
    void trackFileInRepoWithNoCommits(@TempDir Path tmp) throws Exception {
        printProgress("trackFileInRepoWithNoCommits");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create a file but do not commit (repo has no commits)
            repo.writeFile("nohead.txt", "data");

            String out = repo.runCommand("track", "nohead.txt");
            assertThat(out).contains("Tracking: nohead.txt");

            // Verify that git sees it as added in the index
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("nohead.txt");
            }
        }
    }

    @Test
    void trackAllUsesVglUndecidedWhenNoCommits(@TempDir Path tmp) throws Exception {
        printProgress("trackAllUsesVglUndecidedWhenNoCommits");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create file and .vgl with undecided.files entry
            repo.writeFile("undecided.txt", "x");
            repo.writeFile(".vgl", "undecided.files=undecided.txt");

            String out = repo.runCommand("track", "-all");
            assertThat(out).contains("Tracking: undecided.txt");

            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("undecided.txt");
            }
        }
    }
}
