package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class PushCommandTest {
    @Test
    void pushBranchToRemote(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            VglTestHarness.setupRemoteRepo(repo, tmp.resolve("remote.git"), "main");
            String output = repo.runCommand("push");
            assertThat(output).contains("Push complete");
        }
    }

    @Test
    void pushShowsErrorWithoutRemote(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("push");
            assertThat(output).contains("No remote configured");
        }
    }
}
