package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class DiffFilteringTest {

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
    void diffWithSpecificFileShowsOnlyThatFile(@TempDir Path tmp) throws Exception {
        // Create repo with multiple modified files
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("file1.txt"), "content1");
        Files.writeString(tmp.resolve("file2.txt"), "content2");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "file1.txt", "file2.txt");
            run("commit", "initial");
            
            // Modify both files
            Files.writeString(tmp.resolve("file1.txt"), "modified1");
            Files.writeString(tmp.resolve("file2.txt"), "modified2");

            String output = run("diff", "-lb", "file1.txt");
            
            // Should only show file1.txt
            assertThat(output).contains("file1.txt");
            assertThat(output).doesNotContain("file2.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void diffWithGlobPatternShowsMatchingFiles(@TempDir Path tmp) throws Exception {
        // Create repo with different file types
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("test.java"), "java");
        Files.writeString(tmp.resolve("test.txt"), "txt");
        Files.writeString(tmp.resolve("other.java"), "java2");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.java", "test.txt", "other.java");
            run("commit", "initial");
            
            // Modify all files
            Files.writeString(tmp.resolve("test.java"), "modified");
            Files.writeString(tmp.resolve("test.txt"), "modified");
            Files.writeString(tmp.resolve("other.java"), "modified");

            String output = run("diff", "-lb", "*.java");
            
            // Should show only java files
            assertThat(output).contains("test.java");
            assertThat(output).contains("other.java");
            assertThat(output).doesNotContain("test.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void diffWithNoFiltersShowsAllChanges(@TempDir Path tmp) throws Exception {
        // Create repo with multiple files
        run("create", tmp.toString());
        Files.writeString(tmp.resolve("file1.txt"), "content1");
        Files.writeString(tmp.resolve("file2.txt"), "content2");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "file1.txt", "file2.txt");
            run("commit", "initial");
            
            // Modify both
            Files.writeString(tmp.resolve("file1.txt"), "modified1");
            Files.writeString(tmp.resolve("file2.txt"), "modified2");

            String output = run("diff");
            
            // Should show all modified files
            assertThat(output).contains("file1.txt");
            assertThat(output).contains("file2.txt");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
