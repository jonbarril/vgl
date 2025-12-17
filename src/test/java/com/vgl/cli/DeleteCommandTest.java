package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

public class DeleteCommandTest {
    @Test
    void deleteBranchRemovesBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.runCommand("split", "-into", "-lb", "feature");
            String output = repo.runCommand("delete", "-lb", "feature");
            assertThat(output).contains("Deleted branch 'feature'");
            assertThat(repo.getBranches()).doesNotContain("feature");
        }
    }

    @Test
    void deleteErrorsOnMissingBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("delete", "-lb", "nonexistent");
            assertThat(output).contains("does not exist");
        }
    }

    @Test
    void deleteWithRemoteFlag(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.runCommand("split", "-into", "-bb", "feature");
            String output = repo.runCommand("delete", "-lb", "feature", "-remote");
            assertThat(output).contains("Deleted branch 'feature'");
        }
    }
}
