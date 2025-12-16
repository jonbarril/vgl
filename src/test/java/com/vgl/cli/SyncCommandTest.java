package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class SyncCommandTest {
    @Test
    void syncPushesAndPulls(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            // Setup remote
            VglTestHarness.setupRemoteRepo(repo, tmp.resolve("remote.git"), "main");
            String output = repo.runCommand("sync");
            assertThat(output).contains("Sync complete");
        }
    }

    @Test
    void syncShowsErrorWithoutRemote(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("sync");
            assertThat(output).contains("No remote configured");
        }
    }
}
