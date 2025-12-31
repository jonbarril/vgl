package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.TestProgress;
import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Path;

/**
 * Tests track behavior when the repository has no commits (unborn HEAD).
 */
public class TrackNoHeadTest {
    private static void printProgress(String testName) {
        TestProgress.print(TrackNoHeadTest.class, testName);
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
