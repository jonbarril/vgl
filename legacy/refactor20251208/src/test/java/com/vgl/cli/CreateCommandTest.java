package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

public class CreateCommandTest {
    @Test
    public void cliCreatesRepoInEmptyDir(@TempDir Path tempDir) throws Exception {
        // Run the CLI as a subprocess in an empty directory
        String output = VglTestHarness.runVglCommand(tempDir, "create");
        // Should create .git and .vgl
        assertThat(Files.exists(tempDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(tempDir.resolve(".vgl"))).isTrue();
        assertThat(output).contains("Created new local repository");
        assertThat(output).contains("Operation complete");
    }

    @Test
    public void cliNoOpIfRepoExists(@TempDir Path tempDir) throws Exception {
        // Create repo first
        VglTestHarness.createGitRepo(tempDir);
        String output = VglTestHarness.runVglCommand(tempDir, "create");
        assertThat(output).contains("VGL repository already exists");
    }

    @Test
    public void cliCreatesBranchIfSpecified(@TempDir Path tempDir) throws Exception {
        VglTestHarness.createGitRepo(tempDir);
        String output = VglTestHarness.runVglCommand(tempDir, "create", "-lb", "feature");
        assertThat(output).contains("Created new local branch: feature");
        assertThat(output).contains("Switched to branch: feature");
    }

    @Test
    public void cliWarnsOnNestedRepo(@TempDir Path tempDir) throws Exception {
        // Create parent repo
        VglTestHarness.createGitRepo(tempDir);
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        String output = VglTestHarness.runVglCommand(nested, "create");
        System.out.println("[DEBUG CLI OUTPUT]\n" + output + "[END DEBUG CLI OUTPUT]");
        assertThat(output).contains("nested under parent repo");
    }

    @Test
    public void cliForceSkipsNestedWarning(@TempDir Path tempDir) throws Exception {
        VglTestHarness.createGitRepo(tempDir);
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        String output = VglTestHarness.runVglCommand(nested, "create", "-f");
        System.out.println("[DEBUG CLI OUTPUT]\n" + output + "[END DEBUG CLI OUTPUT]");
        assertThat(output).doesNotContain("cancelled");
        assertThat(Files.exists(nested.resolve(".git"))).isTrue();
        assertThat(Files.exists(nested.resolve(".vgl"))).isTrue();
    }
}