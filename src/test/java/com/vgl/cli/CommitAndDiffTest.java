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

        // Create a file, track it, and commit it
        Path file = tmp.resolve("a.txt");
        Files.writeString(file, "hello\n");
        new VglCli().run(new String[]{"local", tmp.toString()}); // Updated from "focus" to "local"
        new VglCli().run(new String[]{"track", "a.txt"});
        new VglCli().run(new String[]{"remote", "origin"}); // Updated from "connect" to "remote"
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
        assertThat(diffOutput).isNotBlank();

        // Check the diff output with the -rb flag (should default to -lb)
        System.setProperty("user.dir", tmp.toString());
        String diffRemoteOutput;
        try {
            diffRemoteOutput = run("diff", "-rb");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
        assertThat(diffRemoteOutput).isNotBlank();
    }
}
