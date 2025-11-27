package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class StatusVerbosityTest {

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
    void statusShowsBasicInfo(@TempDir Path tmp) throws Exception {
        // Create a new repository
        new VglCli().run(new String[]{"create", tmp.toString()});
        
        Path file = tmp.resolve("test.txt");
        Files.writeString(file, "content");
        new VglCli().run(new String[]{"local", tmp.toString()});
        new VglCli().run(new String[]{"remote", "https://github.com/test/repo.git"});
        
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        String output;
        try {
            output = run("status");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
        
        // Basic status should show local and remote info
        assertThat(output).contains("LOCAL");
        assertThat(output).contains("REMOTE");
        assertThat(output).contains("STATE");
        assertThat(output).contains("FILES");
    }

    @Test
    void statusVerboseShowsCommitHashes(@TempDir Path tmp) throws Exception {
        // Create repository and make a commit
        new VglCli().run(new String[]{"create", tmp.toString()});
        Path file = tmp.resolve("test.txt");
        Files.writeString(file, "content");
        new VglCli().run(new String[]{"local", tmp.toString()});
        new VglCli().run(new String[]{"remote", "https://github.com/test/repo.git"});
        
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        try {
            run("commit", "test message");
            String output = run("status", "-v");
            
            // -v should show commit hash (7-40 hex chars)
            assertThat(output).containsPattern("[0-9a-f]{7,40}");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void statusVeryVerboseShowsTrackedFiles(@TempDir Path tmp) throws Exception {
        // Create repository with tracked files
        new VglCli().run(new String[]{"create", tmp.toString()});
        Path file1 = tmp.resolve("file1.txt");
        Path file2 = tmp.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        new VglCli().run(new String[]{"local", tmp.toString()});
        new VglCli().run(new String[]{"remote", "https://github.com/test/repo.git"});
        
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmp.toString());
        try {
            run("commit", "initial");
            String output = run("status", "-vv");
            
            // -vv should show tracked files section
            assertThat(output).contains("-- Tracked Files:");
            assertThat(output).contains("file1.txt");
            assertThat(output).contains("file2.txt");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }
}
