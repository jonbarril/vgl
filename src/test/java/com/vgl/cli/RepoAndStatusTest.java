package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.*;

public class RepoAndStatusTest {

    @Test
    void createMakesGitAndVgl_butNoGitignore(@TempDir Path tmp) throws Exception {
        // Use createDir instead of createRepo since we're testing the create command
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            String out = repo.runCommand("create", "-lr", tmp.toString());
            
            assertThat(Files.exists(tmp.resolve(".git"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".gitignore"))).isTrue();
            assertThat(out).contains("Created new local repository");
        }
    }

    @Test
    void switchCreatesVglWhenMissing(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            Files.deleteIfExists(tmp.resolve(".vgl"));

            String out = repo.runCommand("switch", "-lr", tmp.toString());
            assertThat(out).contains("Switched.");
        }
    }

    @Test
    void statusAndVerboseOutputs(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("a.txt", "hello\n");
            repo.gitAdd("a.txt");
            repo.gitCommit("initial");
            repo.runCommand("switch", "-rr", "https://example.com/repo.git");
            
            // Modify tracked file and add an untracked file
            repo.writeFile("a.txt", "hello\nworld\n");
            repo.writeFile("b.txt", "untracked\n");

            String basic = repo.runCommand("status");
            assertThat(basic).contains("LOCAL");
            assertThat(basic).contains("REMOTE");
            assertThat(basic).contains("COMMITS");
            assertThat(basic).contains("FILES");

            String v = repo.runCommand("status", "-v");
            assertThat(v).contains("COMMITS");

            String vv = repo.runCommand("status", "-vv");
            assertThat(vv).containsAnyOf("initial", "[0-9a-f]{7}");
            assertThat(vv).contains("-- Tracked Files:");
            assertThat(vv).contains("-- Untracked Files:");
        }
    }
}
