package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Path;

/**
 * Focused unit tests for the DiffCommand, covering basic and edge cases.
 */
public class DiffCommandTest {
    @Test
    void diffShowsAddedFile(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("added.txt", "new content\n");
            String out = repo.runCommand("diff");
            assertThat(out).contains("ADD added.txt");
        }
    }

    @Test
    void diffShowsModifiedFile(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "a\n");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "init");
            repo.writeFile("file.txt", "a\nb\n");
            String out = repo.runCommand("diff");
            assertThat(out).contains("MODIFIED: file.txt");
            assertThat(out).contains("+ b");
        }
    }

    @Test
    void diffNoChangesShowsNoChanges(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "abc\n");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "init");
            String out = repo.runCommand("diff");
            assertThat(out).contains("(no changes)");
        }
    }

    @Test
    void diffWithNoRepoShowsError(@TempDir Path tmp) throws Exception {
        // No repo created
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            String out = repo.runCommandExpectingFailure("diff");
            assertThat(out).containsIgnoringCase("no git repository");
        }
    }
}
