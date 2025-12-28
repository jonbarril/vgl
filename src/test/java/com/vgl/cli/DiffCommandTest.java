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
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("added.txt", "new content\n");
            String out = VglTestHarness.runVglCommand(repo.getPath(), "diff");
            String normalizedOut = out.replace("\r\n", "\n").trim();
            assertThat(normalizedOut).contains("ADD added.txt");
        }
    }

    @Test
    void diffShowsModifiedFile(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "a\n");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "init");
            repo.writeFile("file.txt", "a\nb\n");
            String out = VglTestHarness.runVglCommand(repo.getPath(), "diff");
            String normalizedOut = out.replace("\r\n", "\n").trim();
            assertThat(normalizedOut).contains("MODIFIED: file.txt");
            assertThat(normalizedOut).contains("+ b");
        }
    }

    @Test
    void diffNoChangesShowsNoChanges(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "abc\n");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "init");
            String out = VglTestHarness.runVglCommand(repo.getPath(), "diff");
            String normalizedOut = out.replace("\r\n", "\n").trim();
            assertThat(normalizedOut).contains("(no changes)");
        }
    }

    @Test
    void diffWithNoRepoShowsError(@TempDir Path tmp) throws Exception {
        // No repo created
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            String out = VglTestHarness.runVglCommand(repo.getPath(), "diff");
            String normalizedOut = out.replace("\r\n", "\n").trim();
            assertThat(normalizedOut).containsIgnoringCase("no git repository");
        }
    }
}
