package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class BranchSwitchingTest {

    private static String runWithInput(String input, String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream oldIn = System.in;
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes("UTF-8")));
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    private static String run(String... args) throws Exception {
        return runWithInput("", args);
    }

    @Test
    void localCommandValidatesBranchExists(@TempDir Path tmp) throws Exception {
        // Create repo with a commit (so branch exists)
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");

            // Try to switch to non-existent branch
            String output = run("local", tmp.toString(), "-b", "nonexistent");
            
            assertThat(output).contains("does not exist");
            assertThat(output).contains("vgl create -b");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void createCommandCreatesBranchInExistingRepo(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");

            // Create new branch in existing repo
            String output = run("create", tmp.toString(), "-b", "newbranch");
            
            assertThat(output).contains("Created new local branch: newbranch");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void localCommandWarnsAboutUncommittedChanges(@TempDir Path tmp) throws Exception {
        // Create repo with two branches
        run("create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");
            run("create", tmp.toString(), "-b", "branch2");
            run("local", tmp.toString(), "-b", "main");
            
            // Make uncommitted changes
            Files.writeString(tmp.resolve("test.txt"), "modified content");

            // Try to switch branch - should warn
            String output = runWithInput("n\n", "local", tmp.toString(), "-b", "branch2");
            
            assertThat(output).contains("uncommitted changes");
            assertThat(output).contains("Continue? (y/N):");
            assertThat(output).contains("cancelled");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void localCommandSwitchesBranchWithConfirmation(@TempDir Path tmp) throws Exception {
        // Create repo with two branches
        run("create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");
            run("create", tmp.toString(), "-b", "branch2");
            run("local", tmp.toString(), "-b", "main");
            
            // Make uncommitted changes
            Files.writeString(tmp.resolve("test.txt"), "modified content");

            // Confirm switch
            String output = runWithInput("y\n", "local", tmp.toString(), "-b", "branch2");
            
            assertThat(output).contains("Switched to local repository");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void localCommandActuallySwitchesGitBranch(@TempDir Path tmp) throws Exception {
        // Create repo with two branches
        run("create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "main content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "on main");
            
            run("create", tmp.toString(), "-b", "branch2");
            Files.writeString(tmp.resolve("test.txt"), "branch2 content");
            run("commit", "on branch2");

            // Switch back to main
            run("local", tmp.toString(), "-b", "main");
            
            // File should have main content
            String content = Files.readString(tmp.resolve("test.txt"));
            assertThat(content).isEqualTo("main content");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void createCommandWarnsAboutUncommittedChanges(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");
            
            // Make uncommitted changes
            Files.writeString(tmp.resolve("test.txt"), "modified");

            // Try to create/switch to new branch
            String output = runWithInput("n\n", "create", tmp.toString(), "-b", "newbranch");
            
            assertThat(output).contains("uncommitted changes");
            assertThat(output).contains("cancelled");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
