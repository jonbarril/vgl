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
        try {
            System.setOut(new PrintStream(baos, true, "UTF-8"));
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setOut(oldOut);
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
        String commitOutput = run("commit", "initial");

        // Assert the commit output contains a valid short hash
        String firstLine = commitOutput.strip();
        assertThat(firstLine).matches("[0-9a-fA-F]{7,40}");

        // Modify the file and check the diff output
        Files.writeString(file, "hello\nworld\n", StandardOpenOption.APPEND);
        String diffOutput = run("diff");
        assertThat(diffOutput).isNotBlank();

        // Check the diff output with the -rb flag (should default to -lb)
        String diffRemoteOutput = run("diff", "-rb");
        assertThat(diffRemoteOutput).isNotBlank();
    }
}
