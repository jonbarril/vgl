package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.nio.file.*;

public class StatusVerbosityTest {
    private static void printProgress(String testName) {
        TestProgress.print(StatusVerbosityTest.class, testName);
    }

    // Use VglTestHarness.runVglCommand for CLI invocation

    @Test
    void statusShowsBasicSections(@TempDir Path tmp) throws Exception {
        printProgress("statusShowsBasicSections");
        // Create a test repository with a commit using VglTestHarness helpers
        try (Git git = VglTestHarness.createGitRepo(tmp)) {
            Path testFile = tmp.resolve("test.txt");
            Files.writeString(testFile, "hello");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("initial").call();
            // Create .vgl config with more properties
            java.util.Properties props = new java.util.Properties();
            props.setProperty("local.dir", tmp.toString().replace('\\', '/'));
            props.setProperty("local.branch", "main");
            props.setProperty("remote.url", "none");
            props.setProperty("remote.branch", "none");
            VglTestHarness.createVglConfig(tmp, props);
        }

        String output = VglTestHarness.runVglCommand(tmp, "status");

        // New compact format shows these sections
        assertThat(output).contains("LOCAL");
        assertThat(output).contains("REMOTE");
        assertThat(output).contains("COMMITS");
        assertThat(output).contains("FILES");
    }

    @Test
    void statusVerboseShowsCommitInfo(@TempDir Path tmp) throws Exception {
        printProgress("statusVerboseShowsCommitInfo");
        // Create a test repository with a commit using VglTestHarness helpers
        try (Git git = VglTestHarness.createGitRepo(tmp)) {
            Path testFile = tmp.resolve("test.txt");
            Files.writeString(testFile, "hello");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("initial").call();
            // Create .vgl config
            java.util.Properties props = new java.util.Properties();
            props.setProperty("local.dir", tmp.toString().replace('\\', '/'));
            VglTestHarness.createVglConfig(tmp, props);
        }

        String output = VglTestHarness.runVglCommand(tmp, "status", "-v");

        // -v should show commit hashes
        assertThat(output).containsPattern("[0-9a-f]{7}");
    }

    @Test
    void statusVeryVerboseShowsTrackedSection(@TempDir Path tmp) throws Exception {
        printProgress("statusVeryVerboseShowsTrackedSection");
        // Create a test repository with a commit and an undecided file using VglTestHarness helpers
        try (Git git = VglTestHarness.createGitRepo(tmp)) {
            Path trackedFile = tmp.resolve("tracked.txt");
            Path undecidedFile = tmp.resolve("undecided.txt");
            Files.writeString(trackedFile, "tracked");
            Files.writeString(undecidedFile, "undecided");
            git.add().addFilepattern("tracked.txt").call();
            git.commit().setMessage("initial");
            // Create .vgl config
            java.util.Properties props = new java.util.Properties();
            props.setProperty("local.dir", tmp.toString().replace('\\', '/'));
            VglTestHarness.createVglConfig(tmp, props);
        }

        String output = VglTestHarness.runVglCommand(tmp, "status", "-vv");

        // -vv should always show these sections
        assertThat(output).contains("-- Tracked Files:");
        assertThat(output).contains("-- Untracked Files:");
        assertThat(output).contains("-- Ignored Files:");
        assertThat(output).contains("-- Undecided Files:");
        // Should list undecided.txt in undecided section (as an indented entry)
        assertThat(output).contains("  undecided.txt");
    }
}
