package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class StatusFilteringTest {

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
    void statusVerboseShowsCommitMessages(@TempDir Path tmp) throws Exception {
        // Create repo (run from current dir)
        run("create", tmp.toString());
        Path file = tmp.resolve("test.txt");
        Files.writeString(file, "content");
        
        // Set user.dir to tmp for commands that need to run in the repo
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "test commit message");

            String output = run("status", "-v");
            
            // -v should show truncated commit message
            assertThat(output).contains("test commit message");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusVeryVerboseShowsFullCommitMessages(@TempDir Path tmp) throws Exception {
        // Create repo and make a commit with multiline message
        run("create", tmp.toString());
        Path file = tmp.resolve("test.txt");
        Files.writeString(file, "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "test commit\n\nDetailed description here");

            String output = run("status", "-vv");
            
            // -vv should show full commit message
            assertThat(output).contains("test commit");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusWithFileFilterShowsOnlyMatchingFiles(@TempDir Path tmp) throws Exception {
        // Create repo with multiple files
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.java"), "java");
        Files.writeString(tmp.resolve("test.txt"), "txt");
        Files.writeString(tmp.resolve("other.java"), "java2");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.java", "test.txt", "other.java");
            
            // Modify files
            Files.writeString(tmp.resolve("test.java"), "modified", StandardOpenOption.APPEND);
            Files.writeString(tmp.resolve("test.txt"), "modified", StandardOpenOption.APPEND);

            String output = run("status", "-v", "test.java");
            
            // Should show only test.java
            assertThat(output).contains("test.java");
            assertThat(output).doesNotContain("other.java");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusWithGlobFilterShowsMatchingFiles(@TempDir Path tmp) throws Exception {
        // Create repo with multiple files
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.java"), "java");
        Files.writeString(tmp.resolve("test.txt"), "txt");
        Files.writeString(tmp.resolve("other.java"), "java2");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.java", "test.txt", "other.java");
            
            // Modify files
            Files.writeString(tmp.resolve("test.java"), "modified", StandardOpenOption.APPEND);
            Files.writeString(tmp.resolve("other.java"), "modified", StandardOpenOption.APPEND);

            String output = run("status", "-v", "*.java");
            
            // Should show both java files
            assertThat(output).contains("test.java");
            assertThat(output).contains("other.java");
            assertThat(output).doesNotContain("test.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusVeryVerboseShowsAllTrackedFiles(@TempDir Path tmp) throws Exception {
        // Create repo with clean and modified files
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("clean.txt"), "clean");
        Files.writeString(tmp.resolve("modified.txt"), "original");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "clean.txt", "modified.txt");
            run("commit", "initial");
            
            // Modify one file
            Files.writeString(tmp.resolve("modified.txt"), "changed");

            String output = run("status", "-vv");
            
            // -vv should show both clean and modified files
            assertThat(output).contains("clean.txt");
            assertThat(output).contains("modified.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusShowsIgnoredFilesWithVeryVerbose(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());
        
        // Create ignored file (should be in default .gitignore)
        Files.writeString(tmp.resolve("test.log"), "log content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            String output = run("status", "-vv");
            
            // -vv should show ignored files
            assertThat(output).contains("-- Ignored Files:");
            assertThat(output).contains("test.log");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
