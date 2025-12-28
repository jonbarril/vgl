package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class RestoreCommandTest {
    @Test
    void restoreFileToHead(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "original");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            repo.writeFile("file.txt", "modified");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "restore", "file.txt");
            assertThat(output).contains("Restored: file.txt");
            assertThat(repo.readFile("file.txt")).isEqualTo("original");
        }
    }

    @Test
    void restoreNonexistentFileShowsError(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "restore", "nofile.txt");
            assertThat(output).contains("not found");
        }
    }
}
