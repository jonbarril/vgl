package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class SwitchCommandTest {
    @Test
    void switchToBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "feature");
            assertThat(output).contains("Switched to branch 'feature'");
        }
    }

    @Test
    void switchToNonexistentBranchShowsError(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "nope");
            assertThat(output).contains("does not exist");
        }
    }
}
