package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

public class CommitAndDiffTest {

    @Test
    public void commitPrintsShortId_andDiffShowsChanges(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create .vgl config for commit command
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("a.txt", "hello\n");
            repo.runCommand("track", "a.txt");
            String commitOutput = repo.runCommand("commit", "initial");

            // Assert the commit output contains a valid short hash
            assertThat(commitOutput).containsPattern("[0-9a-fA-F]{7,40}");

            // Modify the file and check the diff output
            repo.writeFile("a.txt", "hello\nworld\n");
            String diffOutput = repo.runCommand("diff");
            
            // Verify diff shows the file and actual content changes
            assertThat(diffOutput).contains("MODIFIED: a.txt");
            assertThat(diffOutput).contains("+ world");

            // Check the diff output with the -rb flag (should default to -lb)
            String diffRemoteOutput = repo.runCommand("diff", "-rb");
            
            assertThat(diffRemoteOutput).isNotNull();
            String dr = diffRemoteOutput.strip();
            assertThat(dr.isEmpty() || dr.contains("(remote diff)") || dr.contains("No remote connected.")).isTrue();
        }
    }
    
    @Test
    public void diffShowsAddedLines(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("test.txt", "line1\nline2\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Add new lines
            repo.writeFile("test.txt", "line1\nline2\nline3\nline4\n");
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("+ line3");
            assertThat(diffOutput).contains("+ line4");
        }
    }
    
    @Test
    public void diffShowsDeletedLines(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("test.txt", "line1\nline2\nline3\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Delete a line
            repo.writeFile("test.txt", "line1\nline3\n");
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("- line2");
        }
    }
    
    @Test
    public void diffShowsModifiedLines(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("test.txt", "original content\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Modify the line
            repo.writeFile("test.txt", "modified content\n");
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("- original content");
            assertThat(diffOutput).contains("+ modified content");
        }
    }
    
    @Test
    public void diffWithNoChangesShowsNothing(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("test.txt", "content\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // No changes made
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).contains("(no changes)");
        }
    }
}
