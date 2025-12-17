package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

public class MergeCommandTest {
    @Test
    void mergeBranchIntoCurrent(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.runCommand("split", "-into", "-lb", "feature");
            repo.runCommand("switch", "-lb", "main");
            String output = repo.runCommand("merge", "-lb", "feature");
            assertThat(output).contains("Merged branch 'feature'");
        }
    }

    @Test
    void mergeWithRemoteBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.runCommand("split", "-into", "-bb", "feature");
            repo.runCommand("switch", "-lb", "main");
            String output = repo.runCommand("merge", "-rb", "feature");
            assertThat(output).contains("Merged branch 'feature'");
        }
    }

    @Test
    void mergeErrorsOnMissingBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("merge");
            assertThat(output).contains("Must specify");
        }
    }
}
