package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class RestoreCommandTest {
    @Test
    void restoreFileToHead(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "original");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.writeFile("file.txt", "modified");
            String output = repo.runCommand("restore", "file.txt");
            assertThat(output).contains("Restored: file.txt");
            assertThat(repo.readFile("file.txt")).isEqualTo("original");
        }
    }

    @Test
    void restoreNonexistentFileShowsError(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("restore", "nofile.txt");
            assertThat(output).contains("not found");
        }
    }
}
