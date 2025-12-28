package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

public class DeleteCommandTest {
    @Test
    void deleteBranchRemovesBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            // Switch to 'main' before deleting 'feature'
            VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "main");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-lb", "feature", "-f");
            assertThat(output).contains("Deleted branch 'feature'");
            assertThat(repo.getBranches()).doesNotContain("feature");
        }
    }

    @Test
    void deleteErrorsOnMissingBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-lb", "nonexistent");
            assertThat(output).contains("does not exist");
        }
    }

    @Test
    void deleteWithRemoteFlag(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-bb", "feature");
            // Setup remote for remote branch deletion
            Path remotePath = tmp.resolve("remote-repo");
            VglTestHarness.setupRemoteRepo(repo, remotePath, "feature");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-rb", "feature", "-f");
            assertThat(output).contains("Deleted branch 'feature'");
        }
    }

    @Test
    void deleteOnlyLocalBranchExists(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "main");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-lb", "feature", "-f");
            assertThat(output).contains("Deleted branch 'feature'");
            assertThat(repo.getBranches()).doesNotContain("feature");
        }
    }

    @Test
    void deleteOnlyRemoteBranchExists(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-bb", "feature");
            VglTestHarness.runVglCommand(repo.getPath(), "delete", "-lb", "feature", "-f"); // delete local
            // Setup remote for remote branch deletion
            Path remotePath = tmp.resolve("remote-repo");
            VglTestHarness.setupRemoteRepo(repo, remotePath, "feature");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-rb", "feature", "-f");
            assertThat(output).contains("Deleted branch 'feature'");
        }
    }

    @Test
    void deleteNeitherBranchExists(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-lb", "ghost", "-rb", "ghost", "-f");
            assertThat(output).contains("does not exist");
        }
    }

    @Test
    void deleteBothBranchesExist(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-bb", "feature");
            VglTestHarness.runVglCommand(repo.getPath(), "switch", "-lb", "main");
            // Setup remote for remote branch deletion
            Path remotePath = tmp.resolve("remote-repo");
            VglTestHarness.setupRemoteRepo(repo, remotePath, "feature");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "delete", "-bb", "feature", "-f");
            assertThat(output).contains("Deleted branch 'feature'");
            assertThat(repo.getBranches()).doesNotContain("feature");
        }
    }
}
