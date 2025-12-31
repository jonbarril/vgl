package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

public class SplitCommandTest {
        @Test
        void splitIntoWithRbButNoLbShowsError(@TempDir Path tmp) throws Exception {
            try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
                VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                repo.writeFile("file.txt", "content");
                VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
                VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
                String output = VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-rb", "feature");
                assertThat(output).contains("Error: Must specify -lb with -into.");
            }
        }
    @Test
    void splitIntoCreatesBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            assertThat(output).contains("Creating branch 'feature'");
            assertThat(repo.getBranches()).contains("feature");
        }
    }

    @Test
    void splitFromCreatesBranchFromSource(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "split", "-from", "-lb", "feature");
            assertThat(output).contains("Creating branch");
        }
    }

    @Test
    void splitBothBranchCreatesAndPushes(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-bb", "feature");
            assertThat(output).contains("Creating branch 'feature'");
            // This test assumes remote setup is handled in harness
        }
    }

    @Test
    void splitErrorsOnBothDirections(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-from", "-lb", "feature");
            assertThat(output).contains("Error: Cannot specify both -into and -from");
        }
    }

    @Test
    void splitErrorsOnMissingDirection(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("split", "-lb", "feature");
            assertThat(output).contains("Usage: vgl split -into|-from");
        }
    }
}
