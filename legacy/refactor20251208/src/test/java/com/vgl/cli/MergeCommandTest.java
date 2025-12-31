package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import com.vgl.cli.utils.MessageConstants;

import java.nio.file.Path;

public class MergeCommandTest {
    @Test
    void mergeBranchIntoCurrent(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "main");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "merge", "-lb", "feature");
            assertThat(output).contains(String.format(MessageConstants.MSG_MERGE_LEGACY_SUCCESS, "feature", "main"));
        }
    }

    @Test
    void mergeWithRemoteBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            // Create the feature branch explicitly for test compatibility
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "main");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "merge", "-rb", "feature");
            assertThat(output).contains(String.format(MessageConstants.MSG_MERGE_LEGACY_SUCCESS, "feature", "main"));
        }
    }

    @Test
    void mergeErrorsOnMissingBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "merge");
            assertThat(output).contains("Must specify"); // Partial match for error message
        }
    }
}
