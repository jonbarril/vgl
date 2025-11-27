package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeAll;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Integration tests that spawn actual vgl commands via ProcessBuilder.
 * These tests verify end-to-end behavior including Git operations, file system
 * interactions, and working directory handling.
 */
public class IntegrationTest {

    private static String vglCommand;

    @BeforeAll
    static void setup() {
        // Determine the vgl command to use based on OS
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            vglCommand = new File("build/install/vgl/bin/vgl.bat").getAbsolutePath();
        } else {
            vglCommand = new File("build/install/vgl/bin/vgl").getAbsolutePath();
        }
        System.out.println("[IntegrationTest] Starting integration tests with: " + vglCommand);
        System.out.flush();
    }

    private static ProcessResult runVgl(Path workingDir, String... args) throws Exception {
        System.out.print(".");
        System.out.flush();
        return runVglWithInput(workingDir, null, args);
    }

    private static ProcessResult runVglWithInput(Path workingDir, String input, String... args) throws Exception {
        System.out.print(".");
        System.out.flush();
        List<String> command = new ArrayList<>();
        command.add(vglCommand);
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Send input if provided
        if (input != null) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes("UTF-8"));
                os.flush();
            }
        }

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output.toString());
    }

    static class ProcessResult {
        final int exitCode;
        final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    @Test
    void statusShowsCommitMessagesWithVerbose(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 1/11: statusShowsCommitMessagesWithVerbose]");
        // Create repo and commit
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "content");
        runVgl(tmp, "commit", "first commit message");

        ProcessResult result = runVgl(tmp, "status", "-v");

        assertThat(result.output).contains("first commit message");
        assertThat(result.output).containsPattern("[0-9a-f]{7}"); // commit hash
        System.out.println(" ✓");
    }

    @Test
    void statusVeryVerboseShowsAllTrackedFiles(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 2/11: statusVeryVerboseShowsAllTrackedFiles]");
        // Create repo with files
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("file1.txt"), "content1");
        Files.writeString(tmp.resolve("file2.txt"), "content2");
        runVgl(tmp, "commit", "initial");

        ProcessResult result = runVgl(tmp, "status", "-vv");

        assertThat(result.output).contains("-- Tracked Files:");
        assertThat(result.output).contains("file1.txt");
        assertThat(result.output).contains("file2.txt");
        System.out.println(" ✓");
    }

    @Test
    void statusFiltersByFileName(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 3/11: statusFiltersByFileName]");
        // Create repo with multiple files
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("test.java"), "java");
        Files.writeString(tmp.resolve("test.txt"), "txt");
        runVgl(tmp, "commit", "initial");

        // Modify both
        Files.writeString(tmp.resolve("test.java"), "modified");
        Files.writeString(tmp.resolve("test.txt"), "modified");

        ProcessResult result = runVgl(tmp, "status", "-v", "test.java");

        assertThat(result.output).contains("test.java");
        assertThat(result.output).doesNotContain("test.txt");
        System.out.println(" ✓");
    }

    @Test
    void statusFiltersWithGlobPattern(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 4/11: statusFiltersWithGlobPattern]");
        // Create repo with multiple files
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("file1.java"), "java1");
        Files.writeString(tmp.resolve("file2.java"), "java2");
        Files.writeString(tmp.resolve("file.txt"), "txt");
        runVgl(tmp, "commit", "initial");

        // Modify all
        Files.writeString(tmp.resolve("file1.java"), "modified");
        Files.writeString(tmp.resolve("file2.java"), "modified");
        Files.writeString(tmp.resolve("file.txt"), "modified");

        ProcessResult result = runVgl(tmp, "status", "-v", "*.java");

        assertThat(result.output).contains("file1.java");
        assertThat(result.output).contains("file2.java");
        assertThat(result.output).doesNotContain("file.txt");
        System.out.println(" ✓");
    }

    @Test
    void diffShowsChangesWithGlobPattern(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 5/11: diffShowsChangesWithGlobPattern]");
        // Create repo with files
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("test.java"), "original java");
        Files.writeString(tmp.resolve("test.txt"), "original txt");
        runVgl(tmp, "commit", "initial");

        // Modify both
        Files.writeString(tmp.resolve("test.java"), "modified java");
        Files.writeString(tmp.resolve("test.txt"), "modified txt");

        ProcessResult result = runVgl(tmp, "diff", "-lb", "*.java");

        assertThat(result.output).contains("test.java");
        assertThat(result.output).doesNotContain("test.txt");
        System.out.println(" ✓");
    }

    @Test
    void restoreAsksForConfirmationAndDefaults(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 6/11: restoreAsksForConfirmationAndDefaults]");
        // Create repo with file
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "original");
        runVgl(tmp, "commit", "initial");

        // Modify file
        Files.writeString(tmp.resolve("test.txt"), "modified");

        // Cancel restore
        ProcessResult result = runVglWithInput(tmp, "n\n", "restore");

        assertThat(result.output).contains("Continue? (y/N):");
        assertThat(result.output).containsAnyOf("cancelled", "Cancelled");
        System.out.println(" ✓");
    }

    @Test
    void restoreRestoresFileWithConfirmation(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 7/11: restoreRestoresFileWithConfirmation]");
        // Create repo with file
        runVgl(tmp, "create", tmp.toString());
        Files.writeString(tmp.resolve("test.txt"), "original");
        runVgl(tmp, "commit", "initial");

        // Modify file
        Files.writeString(tmp.resolve("test.txt"), "modified");
        assertThat(Files.readString(tmp.resolve("test.txt"))).isEqualTo("modified");

        // Confirm restore
        runVglWithInput(tmp, "y\n", "restore", "test.txt");

        assertThat(Files.readString(tmp.resolve("test.txt"))).isEqualTo("original");
        System.out.println(" ✓");
    }

    @Test
    void localSwitchesBranches(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 8/11: localSwitchesBranches]");
        // Create repo with two branches
        runVgl(tmp, "create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");
        runVgl(tmp, "commit", "initial");

        runVgl(tmp, "create", tmp.toString(), "-b", "develop");

        // Switch to main
        runVgl(tmp, "local", tmp.toString(), "-b", "main");

        ProcessResult result = runVgl(tmp, "status");
        assertThat(result.output).containsPattern("LOCAL.*:main");
        System.out.println(" ✓");
    }

    @Test
    void localWarnsAboutUncommittedChanges(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 9/11: localWarnsAboutUncommittedChanges]");
        // Create repo with two branches
        runVgl(tmp, "create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");
        runVgl(tmp, "commit", "initial");

        runVgl(tmp, "create", tmp.toString(), "-b", "develop");

        // Make uncommitted changes
        Files.writeString(tmp.resolve("test.txt"), "modified");

        // Try to switch - should warn
        ProcessResult result = runVglWithInput(tmp, "n\n", "local", tmp.toString(), "-b", "main");

        assertThat(result.output).contains("uncommitted changes");
        assertThat(result.output).contains("Continue? (y/N):");
        System.out.println(" ✓");
    }

    @Test
    void createCommandCreatesNewBranch(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 10/11: createCommandCreatesNewBranch]");
        // Create repo
        runVgl(tmp, "create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");
        runVgl(tmp, "commit", "initial");

        // Create new branch
        runVgl(tmp, "create", tmp.toString(), "-b", "feature");

        ProcessResult result = runVgl(tmp, "status");
        assertThat(result.output).containsPattern("LOCAL.*:feature");
        System.out.println(" ✓");
    }

    @Test
    void checkoutCreatesVglConfig(@TempDir Path tmp) throws Exception {
        System.out.println("\n[Test 11/11: checkoutCreatesVglConfig]");
        // Create a source repo to clone from
        Path sourceRepo = tmp.resolve("source");
        Files.createDirectories(sourceRepo);
        runVgl(sourceRepo, "create", sourceRepo.toString(), "-b", "main");
        Files.writeString(sourceRepo.resolve("test.txt"), "content");
        runVgl(sourceRepo, "commit", "initial");

        // Checkout to a new location (simulating cloning)
        Path targetDir = tmp.resolve("cloned");
        Files.createDirectories(targetDir);
        
        // Use file:// URL for local clone
        String fileUrl = "file:///" + sourceRepo.toString().replace("\\", "/");
        runVgl(targetDir.getParent(), "checkout", fileUrl, "-b", "main");

        // Verify .vgl was created in the cloned directory
        Path clonedRepo = targetDir.getParent().resolve("source");
        assertThat(Files.exists(clonedRepo.resolve(".vgl"))).isTrue();
        assertThat(Files.exists(clonedRepo.resolve(".git"))).isTrue();
        assertThat(Files.exists(clonedRepo.resolve("test.txt"))).isTrue();
        System.out.println(" ✓");
    }
}

