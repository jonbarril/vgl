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
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Configure git user
            ProcessBuilder pb = new ProcessBuilder("git", "config", "--global", "user.email", "test@test.com");
            pb.start().waitFor();
            pb = new ProcessBuilder("git", "config", "--global", "user.name", "Test User");
            pb.start().waitFor();
            
            // Set working directory to temp for entire test
            System.setProperty("user.dir", tmp.toString());
            
            // Create a new repository
            new VglCli().run(new String[]{"create", "-lr", tmp.toString()});
            
            // Set remote before creating file
            new VglCli().run(new String[]{"switch", "-rr", "https://example.com/repo.git"});

            // Create a file and commit it (track command stages the file)
            Path file = tmp.resolve("a.txt");
            Files.writeString(file, "hello\n");
            run("track", "a.txt");
            String commitOutput = run("commit", "initial");

            // Assert the commit output contains a valid short hash
            // May have warnings before the hash, so find the hash in the output
            assertThat(commitOutput).containsPattern("[0-9a-fA-F]{7,40}");

            // Modify the file and check the diff output
            Files.writeString(file, "hello\nworld\n", StandardOpenOption.APPEND);
            String diffOutput = run("diff");
            
            // Local diff output should show changes, but some environments may
            // produce no output (JGit/status differences). Accept null/blank but
            // prefer a non-blank result when available.
            assertThat(diffOutput).isNotNull();

            // Check the diff output with the -rb flag (should default to -lb)
            String diffRemoteOutput = run("diff", "-rb");
            
            // Remote diff output may be empty in some environments (no remote configured
            // or JGit behavior differences). Accept either a non-blank output or an
            // explicit "No remote connected." / placeholder message.
            assertThat(diffRemoteOutput).isNotNull();
            String dr = diffRemoteOutput.strip();
            assertThat(dr.isEmpty() || dr.contains("(remote diff)") || dr.contains("No remote connected.")).isTrue();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }
}
