package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

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
    
    @Test
    public void diffDefaultComparesWorkingVsLocal(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString(), "-f");
            repo.writeFile("test.txt", "committed\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            repo.writeFile("test.txt", "working\n");
            
            // No flags should default to working vs local
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("- committed");
            assertThat(diffOutput).contains("+ working");
        }
    }
    
    @Test
    public void diffRbComparesWorkingFilesVsRemote(@TempDir Path tmp) throws Exception {
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Create initial commit
            repo.runCommand("create", "-lr", localRepo.toString(), "-f");
            repo.writeFile("test.txt", "initial content\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Set up remote repo with push and fetch
            Path remoteRepo = tmp.resolve("remote.git");
            VglTestHarness.setupRemoteRepo(repo, remoteRepo, "main");
            
            // Modify working file (not committed)
            repo.writeFile("test.txt", "modified content\n");
            
            // diff -rb should show working changes vs remote
            String diffOutput = repo.runCommand("diff", "-rb");
            
            assertThat(diffOutput).contains("Fetching from remote");
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("- initial content");
            assertThat(diffOutput).contains("+ modified content");
        }
    }
    
    @Test
    public void diffLbRbComparesLocalVsRemoteBranches(@TempDir Path tmp) throws Exception {
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Create initial commit
            repo.runCommand("create", "-lr", localRepo.toString(), "-f");
            repo.writeFile("test.txt", "remote version\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Set up remote repo with push and fetch
            Path remoteRepo = tmp.resolve("remote.git");
            VglTestHarness.setupRemoteRepo(repo, remoteRepo, "main");
            
            // Make local commit (ahead of remote)
            repo.writeFile("test.txt", "local version\n");
            repo.runCommand("commit", "update");
            
            // diff -lb -rb should compare local vs remote
            String diffOutput = repo.runCommand("diff", "-lb", "-rb");
            
            assertThat(diffOutput).contains("Fetching from remote");
            assertThat(diffOutput).contains("MODIFIED: test.txt");
            assertThat(diffOutput).contains("- remote version");
            assertThat(diffOutput).contains("+ local version");
        }
    }
    
    @Test
    public void diffLbRbShowsNoDiffWhenInSync(@TempDir Path tmp) throws Exception {
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Create initial commit
            repo.runCommand("create", "-lr", localRepo.toString(), "-f");
            repo.writeFile("test.txt", "content\n");
            repo.runCommand("track", "test.txt");
            repo.runCommand("commit", "initial");
            
            // Set up remote repo with push and fetch (in sync)
            Path remoteRepo = tmp.resolve("remote.git");
            VglTestHarness.setupRemoteRepo(repo, remoteRepo, "main");
            
            // diff -lb -rb should show branches are identical
            String diffOutput = repo.runCommand("diff", "-lb", "-rb");
            
            assertThat(diffOutput).contains("Fetching from remote");
            assertThat(diffOutput).contains("branches are identical");
        }
    }
}
