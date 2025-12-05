package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for status -v and -vv output formatting with arrows.
 */
public class StatusVerboseTest {
    private static int currentTest = 0;
    private static final int TOTAL_TESTS = 6;
    private static void printProgress(String testName) {
        currentTest++;
        System.out.println("[StatusVerboseTest " + currentTest + "/" + TOTAL_TESTS + ": " + testName + "]...");
        System.out.flush();
    }

    @Test
    void uncommittedChangesShowWithoutArrow(@TempDir Path tmp) throws Exception {
            printProgress("uncommittedChangesShowWithoutArrow");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create initial commit
            repo.writeFile("test.txt", "initial content");
            repo.gitAdd("test.txt");
            repo.gitCommit("Initial commit");
            
            // Modify file (uncommitted)
            repo.writeFile("test.txt", "modified content");
            
            String output = repo.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("  M test.txt");  // No arrow prefix
            assertThat(output).doesNotContain("↑ M test.txt");
        }
    }
    
    @Test
    void committedButNotPushedShowsUpArrow(@TempDir Path tmp) throws Exception {
            printProgress("committedButNotPushedShowsUpArrow");
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (@SuppressWarnings("unused") Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
        }
        
        // Create local repo
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Create and push initial commit first
            repo.writeFile("file1.txt", "content");
            repo.gitAdd("file1.txt");
            repo.gitCommit("First commit");
            
            try (Git git = repo.getGit()) {
                // Push to remote
                git.push()
                    .setRemote(remoteRepo.toUri().toString())
                    .add("master")
                    .call();
            }
            
            // Set up remote tracking
            repo.setupRemoteTracking(remoteRepo.toUri().toString(), "master");
            
            // Create another commit (not pushed)
            repo.writeFile("file2.txt", "new file");
            repo.gitAdd("file2.txt");
            repo.gitCommit("Second commit - not pushed");
            
            String output = repo.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("↑ A file2.txt");  // Up arrow for committed but not pushed
        }
    }
    
    @Test
    void remoteChangesShowDownArrow(@TempDir Path tmp) throws Exception {
            printProgress("remoteChangesShowDownArrow");
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (@SuppressWarnings("unused") Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
        }
        
        // Create first local repo and push
        Path local1 = tmp.resolve("local1");
        Files.createDirectories(local1);
        
        try (VglTestHarness.VglTestRepo repo1 = VglTestHarness.createRepo(local1)) {
            repo1.writeFile("shared.txt", "initial");
            repo1.gitAdd("shared.txt");
            repo1.gitCommit("Initial commit");
            
            try (Git git = repo1.getGit()) {
                git.push()
                    .setRemote(remoteRepo.toUri().toString())
                    .add("master")
                    .call();
            }
            
            repo1.setupRemoteTracking(remoteRepo.toUri().toString(), "master");
            
            // Add more commits
            repo1.writeFile("file1.txt", "from repo1");
            repo1.gitAdd("file1.txt");
            repo1.gitCommit("Add file1");
            
            try (Git git = repo1.getGit()) {
                git.push()
                    .setRemote(remoteRepo.toUri().toString())
                    .add("master")
                    .call();
                
                // Update remote tracking ref
                org.eclipse.jgit.lib.RefUpdate refUpdate = git.getRepository().updateRef("refs/remotes/origin/master");
                refUpdate.setNewObjectId(git.getRepository().resolve("refs/heads/master"));
                refUpdate.update();
            }
        }
        
        // Clone to second local repo
        Path local2 = tmp.resolve("local2");
        try (Git git = Git.cloneRepository()
                .setURI(remoteRepo.toUri().toString())
                .setDirectory(local2.toFile())
                .call()) {
            
            // Fetch to update remote tracking branch
            git.fetch().call();
            
            // Reset to previous commit to simulate being behind
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef("HEAD~1").call();
            git.getRepository().getConfig().save();
        }
        
        // Now check status in local2
        try (VglTestHarness.VglTestRepo repo2 = VglTestHarness.createRepo(local2)) {
            // Set up remote tracking
            repo2.setupRemoteTracking(remoteRepo.toUri().toString(), "master");
            
            String output = repo2.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Merge:");
            assertThat(output).contains("↓ A file1.txt");  // Down arrow for remote changes to pull
        }
    }
    
    @Test
    void noRemoteShowsAppropriateMessage(@TempDir Path tmp) throws Exception {
            printProgress("noRemoteShowsAppropriateMessage");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("test.txt", "content");
            repo.gitAdd("test.txt");
            repo.gitCommit("Initial commit");
            
            String output = repo.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("  (none)");  // Or (remote branch not found) depending on state
            assertThat(output).contains("-- Files to Merge:");
            // Both sections should appear
        }
    }
    
    @Test
    void mixedUncommittedAndCommittedChanges(@TempDir Path tmp) throws Exception {
            printProgress("mixedUncommittedAndCommittedChanges");
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (@SuppressWarnings("unused") Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
        }
        
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Create and push initial commit
            repo.writeFile("file1.txt", "initial");
            repo.gitAdd("file1.txt");
            repo.gitCommit("Initial");
            
            try (Git git = repo.getGit()) {
                git.push()
                    .setRemote(remoteRepo.toUri().toString())
                    .add("master")
                    .call();
            }
            
            // Set up remote tracking
            repo.setupRemoteTracking(remoteRepo.toUri().toString(), "master");
            
            // Create committed but not pushed change
            repo.writeFile("file2.txt", "committed");
            repo.gitAdd("file2.txt");
            repo.gitCommit("Add file2");
            
            // Create uncommitted change (modified, not added/committed)
            repo.writeFile("file1.txt", "modified content");
            
            String output = repo.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("↑ A file2.txt");  // Committed but not pushed, up arrow
            assertThat(output).contains("  M file1.txt");  // Modified but not committed, no arrow
        }
    }
}
