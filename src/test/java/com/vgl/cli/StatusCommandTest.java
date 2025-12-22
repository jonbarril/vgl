package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class StatusCommandTest {
    @Test
    void statusShowsSections(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            String output = repo.runCommand("status", "-v");
            assertThat(output).contains("LOCAL");
            assertThat(output).contains("REMOTE");
            assertThat(output).contains("COMMITS");
            assertThat(output).contains("FILES");
            // New summary format
            assertThat(output).containsPattern("COMMITS\\s+\\d+ to Commit, \\d+ to Push, \\d+ to Merge");
        }
    }

    @Test
    void statusVerboseShowsFileDetails(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            String output = repo.runCommand("status", "-vv");
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("-- Files to Push:");
            assertThat(output).contains("-- Files to Merge:");
        }
    }
}
