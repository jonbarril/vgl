package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

/**
 * Unit tests for the TrackCommand - verifies that the 'vgl track' command
 * properly stages files for commit.
 */
public class TrackCommandTest {
    private static void printProgress(String testName) {
        TestProgress.print(TrackCommandTest.class, testName);
    }

    @Test
    void trackSingleFile(@TempDir Path tmp) throws Exception {
            printProgress("trackSingleFile");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create a file
            repo.writeFile("test.txt", "content");
            
            // Track it
            String output = repo.runCommand("track", "test.txt");
            assertThat(output).contains("Tracking: test.txt");
            
            // Verify it's staged using JGit
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("test.txt");
            }

            // Verify CLI-level status agrees (should not list as Untracked)
            String out = repo.runCommand("status", "-vv");
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("test.txt");
        }
    }

    @Test
    void trackMultipleFiles(@TempDir Path tmp) throws Exception {
            printProgress("trackMultipleFiles");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("file1.txt", "content1");
            repo.writeFile("file2.txt", "content2");
            
            String output = repo.runCommand("track", "file1.txt", "file2.txt");
            
            assertThat(output).contains("Tracking: file1.txt file2.txt");
            
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("file1.txt", "file2.txt");
            }

            String out = repo.runCommand("status", "-vv");
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("file1.txt", "file2.txt");
        }
    }

    @Test
    void trackWithGlobPattern(@TempDir Path tmp) throws Exception {
            printProgress("trackWithGlobPattern");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("file1.java", "java1");
            repo.writeFile("file2.java", "java2");
            repo.writeFile("file.txt", "txt");
            
            String output = repo.runCommand("track", "*.java");
            // Should show the actual files tracked, not the glob pattern
            assertThat(output).contains("Tracking: file1.java file2.java");
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("file1.java", "file2.java");
                assertThat(status.getAdded()).doesNotContain("file.txt");
            }

            String out = repo.runCommand("status", "-vv");
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("file1.java", "file2.java");
        }
    }

    @Test
    void trackFileInSubdirectory(@TempDir Path tmp) throws Exception {
            printProgress("trackFileInSubdirectory");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("src/main/App.java", "code");
            
            String output = repo.runCommand("track", "src/main/App.java");
            
            assertThat(output).contains("Tracking:");
            
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("src/main/App.java");
            }

            String out = repo.runCommand("status", "-vv");
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("src/main/App.java");
        }
    }

    @Test
    void trackModifiedFile(@TempDir Path tmp) throws Exception {
            printProgress("trackModifiedFile");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Use createRepo since we need to commit first
            
            // Create and commit a file
            repo.writeFile("test.txt", "original");
            repo.gitAdd("test.txt");
            repo.gitCommit("initial");
            
            // Modify it
            repo.writeFile("test.txt", "modified");
            
            // Track the modification
            String output = repo.runCommand("track", "test.txt");
            
            assertThat(output).contains("Tracking: test.txt");
            
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getChanged()).contains("test.txt");
            }

            String out = repo.runCommand("status", "-vv");
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("test.txt");
        }
    }

    @Test
    void trackWithoutRepo(@TempDir Path tmp) throws Exception {
            printProgress("trackWithoutRepo");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            // Don't initialize repo, just create a file
            repo.writeFile("test.txt", "content");
            
            String output = repo.runCommand("track", "test.txt");
            
            assertThat(output).contains(Utils.MSG_NO_REPO_WARNING_PREFIX);
        }
    }

    @Test
    void trackNonexistentFile(@TempDir Path tmp) throws Exception {
            printProgress("trackNonexistentFile");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            // Try to track a file that doesn't exist
            String output = repo.runCommand("track", "nonexistent.txt");
            
            // Git will just silently ignore it (no matches for glob)
            assertThat(output).containsAnyOf("Tracking:", "No matching files");
        }
    }

    @Test
    void trackAllFilesWithDot(@TempDir Path tmp) throws Exception {
            printProgress("trackAllFilesWithDot");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("file1.txt", "content1");
            repo.writeFile("file2.txt", "content2");
            repo.writeFile("src/code.java", "code");
            
            String output = repo.runCommand("track", ".");
            
            assertThat(output).contains("Tracking: .");
            
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("file1.txt", "file2.txt", "src/code.java");
            }

            String out = repo.runCommand("status", "-vv");
            // If user tracked '.', the files should not appear in Untracked
            assertThat(getStatusSectionLines(out, "Untracked Files")).doesNotContain("file1.txt", "file2.txt", "src/code.java");
        }
    }

    private java.util.List<String> getStatusSectionLines(String statusOutput, String sectionName) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String marker = "-- " + sectionName + ":";
        String[] split = statusOutput.split("\r?\n");
        boolean inSection = false;
        for (String l : split) {
            if (l.startsWith("-- ") && inSection) break; // end of section
            if (inSection) {
                String t = l.trim();
                if (!t.isEmpty()) lines.add(t);
            }
            if (l.startsWith(marker)) {
                inSection = true;
            }
        }
        return lines;
    }
}
