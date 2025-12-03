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

    @Test
    void uncommittedChangesShowWithoutArrow(@TempDir Path tmp) throws Exception {
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
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
            remoteGit.close();
        }
        
        // Create local repo
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Configure remote
            try (Git git = repo.getGit()) {
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteRepo.toUri().toString());
                git.getRepository().getConfig().setString("branch", "main", "remote", "origin");
                git.getRepository().getConfig().setString("branch", "main", "merge", "refs/heads/main");
                git.getRepository().getConfig().save();
            }
            
            // Create and commit a file
            repo.writeFile("file1.txt", "content");
            repo.gitAdd("file1.txt");
            repo.gitCommit("First commit");
            
            // Push to remote
            try (Git git = repo.getGit()) {
                git.push().setRemote("origin").call();
            }
            
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
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
            remoteGit.close();
        }
        
        // Create first local repo and push
        Path local1 = tmp.resolve("local1");
        Files.createDirectories(local1);
        
        try (VglTestHarness.VglTestRepo repo1 = VglTestHarness.createRepo(local1)) {
            try (Git git = repo1.getGit()) {
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteRepo.toUri().toString());
                git.getRepository().getConfig().setString("branch", "main", "remote", "origin");
                git.getRepository().getConfig().setString("branch", "main", "merge", "refs/heads/main");
                git.getRepository().getConfig().save();
            }
            
            repo1.writeFile("shared.txt", "initial");
            repo1.gitAdd("shared.txt");
            repo1.gitCommit("Initial commit");
            
            try (Git git = repo1.getGit()) {
                git.push().setRemote("origin").call();
            }
            
            // Add more commits
            repo1.writeFile("file1.txt", "from repo1");
            repo1.gitAdd("file1.txt");
            repo1.gitCommit("Add file1");
            
            try (Git git = repo1.getGit()) {
                git.push().setRemote("origin").call();
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
            
            // Get first commit only (simulate being behind)
            git.checkout().setStartPoint("HEAD~1").setCreateBranch(true).setName("main").call();
            git.getRepository().getConfig().setString("branch", "main", "remote", "origin");
            git.getRepository().getConfig().setString("branch", "main", "merge", "refs/heads/main");
            git.getRepository().getConfig().save();
        }
        
        // Now check status in local2
        try (VglTestHarness.VglTestRepo repo2 = VglTestHarness.createRepo(local2)) {
            String output = repo2.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Merge:");
            assertThat(output).contains("↓ A file1.txt");  // Down arrow for remote changes to pull
        }
    }
    
    @Test
    void noRemoteShowsAppropriateMessage(@TempDir Path tmp) throws Exception {
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
        // Create a bare remote repo
        Path remoteRepo = tmp.resolve("remote");
        Files.createDirectories(remoteRepo);
        try (Git remoteGit = Git.init().setDirectory(remoteRepo.toFile()).setBare(true).call()) {
            remoteGit.close();
        }
        
        Path localRepo = tmp.resolve("local");
        Files.createDirectories(localRepo);
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(localRepo)) {
            // Configure remote
            try (Git git = repo.getGit()) {
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteRepo.toUri().toString());
                git.getRepository().getConfig().setString("branch", "main", "remote", "origin");
                git.getRepository().getConfig().setString("branch", "main", "merge", "refs/heads/main");
                git.getRepository().getConfig().save();
            }
            
            // Create and push initial commit
            repo.writeFile("file1.txt", "initial");
            repo.gitAdd("file1.txt");
            repo.gitCommit("Initial");
            
            try (Git git = repo.getGit()) {
                git.push().setRemote("origin").call();
            }
            
            // Create committed but not pushed change
            repo.writeFile("file2.txt", "committed");
            repo.gitAdd("file2.txt");
            repo.gitCommit("Add file2");
            
            // Create uncommitted change
            repo.writeFile("file3.txt", "uncommitted");
            
            String output = repo.runCommand("status", "-vv");
            
            assertThat(output).contains("-- Files to Commit:");
            assertThat(output).contains("↑ A file2.txt");  // Committed, up arrow
            assertThat(output).contains("  ? file3.txt");  // Uncommitted, no arrow (untracked)
        }
    }
}
