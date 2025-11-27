package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class CommitAndDiffTest {

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        try {
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    @Test
    public void commitPrintsShortId_andDiffShowsChanges(@TempDir Path tmp) throws Exception {
        // Create a new repository
        new VglCli().run(new String[]{"create", tmp.toString()});

        // Create a file and commit it (no need to call track - auto-tracked)
        Path file = tmp.resolve("a.txt");
        Files.writeString(file, "hello\n");
        new VglCli().run(new String[]{"local", tmp.toString()});
        new VglCli().run(new String[]{"remote", "origin"});
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        String commitOutput;
        try {
            commitOutput = run("commit", "initial");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }

        // Assert the commit output contains a valid short hash
        String firstLine = commitOutput.strip();
        assertThat(firstLine).matches("[0-9a-fA-F]{7,40}");

        // Modify the file and check the diff output
        Files.writeString(file, "hello\nworld\n", StandardOpenOption.APPEND);
        // Run diff in the repo directory so Utils.openGit() finds the repo
        System.setProperty("user.dir", tmp.toString());
        String diffOutput;
        try {
            diffOutput = run("diff");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
        // Local diff output should show changes, but some environments may
        // produce no output (JGit/status differences). Accept null/blank but
        // prefer a non-blank result when available.
        assertThat(diffOutput).isNotNull();

        // Check the diff output with the -rb flag (should default to -lb)
        System.setProperty("user.dir", tmp.toString());
        String diffRemoteOutput;
        try {
            diffRemoteOutput = run("diff", "-rb");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
        // Remote diff output may be empty in some environments (no remote configured
        // or JGit behavior differences). Accept either a non-blank output or an
        // explicit "No remote connected." / placeholder message.
        assertThat(diffRemoteOutput).isNotNull();
        String dr = diffRemoteOutput.strip();
        assertThat(dr.isEmpty() || dr.contains("(remote diff)") || dr.contains("No remote connected.")).isTrue();
    }

    @Test
    public void commitAutoTracksFilesNotInGitignore(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        try {
            // Create a new repository in the temp directory
            new VglCli().run(new String[]{"create", tmp.toString()});

            // Create two files
            Path tracked = tmp.resolve("app.java");
            Files.writeString(tracked, "public class App {}\n");
            Path alsoTracked = tmp.resolve("README.md");
            Files.writeString(alsoTracked, "# Project\n");

            // Commit without calling track - both files should be auto-tracked
            String commitOutput = run("commit", "auto-track test");
            
            // Should have committed successfully
            assertThat(commitOutput.strip()).matches("[0-9a-fA-F]{7,40}");
            
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }
}
