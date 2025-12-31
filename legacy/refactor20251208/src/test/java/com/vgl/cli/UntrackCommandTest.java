package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class UntrackCommandTest {

    @Test
    void untrackRemovesFileFromIndex(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "untrack", "file.txt");
            assertThat(output).contains("Untracked: file.txt");
            // File should not be staged
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).doesNotContain("file.txt");
            }
        }
    }

    @Test
    void untrackNonexistentFileShowsError(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "untrack", "nofile.txt");
            assertThat(output).contains("not tracked");
        }
    }
}
