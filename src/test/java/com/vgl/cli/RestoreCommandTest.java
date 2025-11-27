package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class RestoreCommandTest {

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
    void restoreWithNoArgsDefaultsToAll(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo with committed file
            run("create", tmp.toString());
            Files.writeString(tmp.resolve("test.txt"), "original");
            run("track", "test.txt");
            run("commit", "initial");
            
            // Modify file
            Files.writeString(tmp.resolve("test.txt"), "modified");

            // Restore with no args and cancel
            String output = runWithInput("n\n", "restore");
            
            assertThat(output).contains("test.txt");
            assertThat(output).contains("Continue? (y/N):");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void restoreWithGlobPatternShowsMatchingFiles(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo with multiple files
            run("create", tmp.toString());
            Files.writeString(tmp.resolve("test.java"), "java");
            Files.writeString(tmp.resolve("test.txt"), "txt");
            run("track", "test.java", "test.txt");
            run("commit", "initial");
            
            // Modify both
            Files.writeString(tmp.resolve("test.java"), "modified java");
            Files.writeString(tmp.resolve("test.txt"), "modified txt");

            // Restore only java files
            String output = runWithInput("n\n", "restore", "*.java");
            
            assertThat(output).contains("test.java");
            assertThat(output).doesNotContain("test.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void restoreActuallyRestoresFilesWithConfirmation(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo with committed file
            run("create", tmp.toString());
            Files.writeString(tmp.resolve("test.txt"), "original");
            run("track", "test.txt");
            run("commit", "initial");
            
            // Modify file
            Files.writeString(tmp.resolve("test.txt"), "modified");
            assertThat(Files.readString(tmp.resolve("test.txt"))).isEqualTo("modified");

            // Restore with confirmation
            runWithInput("y\n", "restore", "test.txt");
            
            // File should be restored
            assertThat(Files.readString(tmp.resolve("test.txt"))).isEqualTo("original");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void restoreCancelsWithoutModifyingFiles(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo with committed file
            run("create", tmp.toString());
            Files.writeString(tmp.resolve("test.txt"), "original");
            run("track", "test.txt");
            run("commit", "initial");
            
            // Modify file
            Files.writeString(tmp.resolve("test.txt"), "modified");

            // Cancel restore
            String output = runWithInput("n\n", "restore", "test.txt");
            
            assertThat(output).contains("cancelled");
            // File should still be modified
            assertThat(Files.readString(tmp.resolve("test.txt"))).isEqualTo("modified");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void restoreWithSpecificFileRestoresOnlyThatFile(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo with multiple files
            run("create", tmp.toString());
            Files.writeString(tmp.resolve("file1.txt"), "original1");
            Files.writeString(tmp.resolve("file2.txt"), "original2");
            run("track", "file1.txt", "file2.txt");
            run("commit", "initial");
            
            // Modify both
            Files.writeString(tmp.resolve("file1.txt"), "modified1");
            Files.writeString(tmp.resolve("file2.txt"), "modified2");

            // Restore only file1
            runWithInput("y\n", "restore", "file1.txt");
            
            assertThat(Files.readString(tmp.resolve("file1.txt"))).isEqualTo("original1");
            assertThat(Files.readString(tmp.resolve("file2.txt"))).isEqualTo("modified2");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
