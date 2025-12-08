package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class StatusRenameSubdirTest {

    @Test
    void renameInSubdirectoryShowsAsRenamedNotUndecided(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create and commit initial file
            repo.writeFile("subdir/q.txt", "hello");
            repo.gitAdd("subdir/q.txt");
            repo.gitCommit("initial commit");

            // Rename file in working tree
            Path oldPath = tmp.resolve("subdir/q.txt");
            Path newPath = tmp.resolve("subdir/qq.txt");
            Files.move(oldPath, newPath);

            // Run status -vv and capture output
            String out = repo.runCommand("status", "-vv");

            // Should show renamed target as R and not list new file under Undecided
            assertThat(out).containsPattern("R\\s+subdir/qq.txt");
            assertThat(out).doesNotContain("Undecided Files:\n  subdir/qq.txt");
        }
    }

    @Test
    void commitFormattingRespectsVAndVV(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Commit with a multi-line message
            repo.writeFile("a.txt", "x");
            repo.gitAdd("a.txt");
            // Use a message with newline
            repo.gitCommit("Multi-line\ncommit message line 2");

            String outV = repo.runCommand("status", "-v");
            // -v should show short id followed by a single-line message (no newlines)
            assertThat(outV).containsPattern("[0-9a-f]{7} .+commit message line 2");

            String outVV = repo.runCommand("status", "-vv");
            // -vv should show full message (including newline content)
            assertThat(outVV).contains("commit message line 2");
        }
    }
}
