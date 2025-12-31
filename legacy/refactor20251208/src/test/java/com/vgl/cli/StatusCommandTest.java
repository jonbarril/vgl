package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class StatusCommandTest {
            @Test
            void dummyTrackedFileShowsAsAdded(@TempDir Path tmp) throws Exception {
                        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
                            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                            repo.writeFile("dummy.txt", "dummy");
                            VglTestHarness.runVglCommand(repo.getPath(), "track", "dummy.txt");
                            repo.gitAdd("dummy.txt"); // Stage the file
                            repo.writeFile("undecided.txt", "undecided");
                            repo.writeFile("ignored.log", "ignored");
                            repo.writeFile(".gitignore", "ignored.log\n");
                            // Add a nested repo
                            java.nio.file.Path nested = repo.getPath().resolve("repo0");
                            java.nio.file.Files.createDirectories(nested);
                            VglTestHarness.runGitCommand(nested, "init");
                            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
                            String norm = output.replace("\r\n", "\n").trim();
                            System.err.println("[DUMMY OUTPUT]" + norm);
                            assertThat(norm).contains("dummy.txt");
                        }
            }
        @Test
        void statusCoversAllFileStates(@TempDir Path tmp) throws Exception {
            // Setup: create repo, .gitignore, .vgl, tracked, undecided, ignored, nested repo
            try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
                VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                // Tracked file
                java.nio.file.Path trackedPath = repo.writeFile("tracked.txt", "tracked");
                // Ensure file is flushed and visible
                java.nio.file.Files.exists(trackedPath); // Force file system sync
                VglTestHarness.runVglCommand(repo.getPath(), "track", "tracked.txt");
                repo.gitAdd("tracked.txt"); // Stage the tracked file so JGit status reports it as Added
                // Undecided file
                repo.writeFile("undecided.txt", "undecided");
                // Ignored file
                repo.writeFile("ignored.log", "ignored");
                repo.writeFile(".gitignore", "ignored.log\n");
                // Nested repo
                java.nio.file.Path nested = repo.getPath().resolve("repo0");
                java.nio.file.Files.createDirectories(nested);
                VglTestHarness.runGitCommand(nested, "init");
                // Ensure .vgl exists
                assert java.nio.file.Files.exists(repo.getPath().resolve(".vgl"));

                // Run status -vv for full output
                String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
                String norm = output.replace("\r\n", "\n").trim();

                // Assert summary counts
                assertThat(norm).containsPattern("FILES:.*1 Added, 0 Modified, 0 Renamed, 0 Deleted");
                assertThat(norm).containsPattern("1 Undecided, 1 Tracked, 0 Untracked, 3 Ignored");

                // Assert file names in verbose sections
                assertThat(norm).contains("-- Undecided Files:");
                assertThat(norm).contains("undecided.txt");
                assertThat(norm).contains("-- Tracked Files:");
                assertThat(norm).contains("tracked.txt");
                assertThat(norm).contains("-- Ignored Files:");
                assertThat(norm).contains("ignored.log");
                assertThat(norm).contains(".vgl");
                assertThat(norm).contains("repo0 (repo)");

                // .vgl and nested repo must not appear in Tracked/Undecided/Untracked
                String trackedSection = norm.substring(norm.indexOf("-- Tracked Files:"), norm.indexOf("-- Untracked Files:"));
                assertThat(trackedSection).doesNotContain(".vgl").doesNotContain("repo0");
                String undecidedSection = norm.substring(norm.indexOf("-- Undecided Files:"), norm.indexOf("-- Tracked Files:"));
                assertThat(undecidedSection).doesNotContain(".vgl").doesNotContain("repo0");

                // Now untrack tracked.txt and check status updates
                VglTestHarness.runVglCommand(repo.getPath(), "untrack", "tracked.txt");
                String out2 = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
                String norm2 = out2.replace("\r\n", "\n").trim();
                assertThat(norm2).containsPattern("0 Tracked");
                assertThat(norm2).contains("tracked.txt"); // Should now be undecided
            }
        }
    @Test
    void statusShowsSections(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-v");
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput).contains("LOCAL");
            assertThat(normalizedOutput).contains("REMOTE");
            assertThat(normalizedOutput).contains("COMMITS");
            assertThat(normalizedOutput).contains("FILES");
            // New summary format
            assertThat(normalizedOutput).containsPattern("COMMITS:?\\s*\\d+ to Commit, \\d+ to Push, \\d+ to Pull");
            // Verbose subsections for -v
            assertThat(normalizedOutput).contains("-- Files to Commit:");
            assertThat(normalizedOutput).contains("-- Files to Push:");
            assertThat(normalizedOutput).contains("-- Files to Pull:");
        }
    }

    @Test
    void statusVerboseShowsFileDetails(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput).contains("-- Files to Commit:");
            assertThat(normalizedOutput).contains("-- Files to Push:");
            assertThat(normalizedOutput).contains("-- Files to Pull:");
        }
    }
}
