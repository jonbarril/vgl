package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.nio.file.*;

public class StatusVerbosityTest {
    private static int currentTest = 0;
    private static final int TOTAL_TESTS = 3;
    private static void printProgress(String testName) {
        currentTest++;
        System.out.println("[StatusVerbosityTest " + currentTest + "/" + TOTAL_TESTS + ": " + testName + "]...");
        System.out.flush();
    }

    private static String run(Path dir, String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", dir.toString());
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setProperty("user.dir", oldUserDir);
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    @Test
    void statusShowsBasicSections(@TempDir Path tmp) throws Exception {
            printProgress("statusShowsBasicSections");
        // Create a test repository with a commit
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            // Create initial commit
            Path testFile = tmp.resolve("test.txt");
            Files.writeString(testFile, "hello");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("initial").call();
        }
        
        // Create .vgl config
        new VglCli(); // This will create the .vgl in tmp directory
        
        String output = run(tmp, "status");
        
        // New compact format shows these sections
        assertThat(output).contains("LOCAL");
        assertThat(output).contains("REMOTE");
        assertThat(output).contains("STATE");
        assertThat(output).contains("FILES");
    }

    @Test
    void statusVerboseShowsCommitInfo(@TempDir Path tmp) throws Exception {
            printProgress("statusVerboseShowsCommitInfo");
        // Create a test repository with a commit
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            Path testFile = tmp.resolve("test.txt");
            Files.writeString(testFile, "hello");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("initial").call();
        }
        
        String output = run(tmp, "status", "-v");
        
        // -v should show commit hashes
        assertThat(output).containsPattern("[0-9a-f]{7}");
    }

    @Test
    void statusVeryVerboseShowsTrackedSection(@TempDir Path tmp) throws Exception {
            printProgress("statusVeryVerboseShowsTrackedSection");
        // Create a test repository with a commit and an undecided file
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            Path trackedFile = tmp.resolve("tracked.txt");
            Path undecidedFile = tmp.resolve("undecided.txt");
            Files.writeString(trackedFile, "tracked");
            Files.writeString(undecidedFile, "undecided");
            git.add().addFilepattern("tracked.txt").call();
            git.commit().setMessage("initial").call();
        }

        String output = run(tmp, "status", "-vv");

        // -vv should always show these sections
        assertThat(output).contains("-- Tracked Files:");
        assertThat(output).contains("-- Untracked Files:");
        assertThat(output).contains("-- Ignored Files:");
        assertThat(output).contains("-- Undecided Files:");
        // Should list undecided.txt in undecided section
        assertThat(output).containsPattern("-- Undecided Files:\s+undecided.txt");
    }
}
