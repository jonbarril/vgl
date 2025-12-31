package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;

public class AbortCommandTest {

    @Test
    void abortDuringMergeRemovesMergeHead(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            // Simulate a merge in progress (create MERGE_HEAD manually)
            java.nio.file.Files.writeString(repo.getPath().resolve(".git").resolve("MERGE_HEAD"), "0000000000000000000000000000000000000000");
            assertThat(java.nio.file.Files.exists(repo.getPath().resolve(".git").resolve("MERGE_HEAD"))).isTrue();
            // Run abort command
            String output = VglTestHarness.runVglCommand(repo.getPath(), "abort");
            assertThat(output).containsIgnoringCase("aborted");
            assertThat(java.nio.file.Files.exists(repo.getPath().resolve(".git").resolve("MERGE_HEAD"))).isFalse();
        }
    }

    @Test
    void abortWhenNoMergeInProgressShowsMessage(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            // No MERGE_HEAD present
            String output = VglTestHarness.runVglCommand(repo.getPath(), "abort");
            assertThat(output).containsIgnoringCase("no merge in progress");
        }
    }
}
