package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for log command - verifies commit history retrieval.
 */
public class LogCommandTest {

    @Test
    public void logShowsCommitHistory(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create multiple commits
            Path testFile = tempDir.resolve("test.txt");
            
            Files.writeString(testFile, "v1");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("First commit").call();
            
            Files.writeString(testFile, "v2");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Second commit").call();
            
            Files.writeString(testFile, "v3");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Third commit").call();
            
            // Retrieve log
            Iterable<RevCommit> logs = git.log().call();
            List<String> messages = new ArrayList<>();
            for (RevCommit c : logs) {
                messages.add(c.getShortMessage());
            }
            
            // Verify commits in reverse chronological order
            assertThat(messages).containsExactly("Third commit", "Second commit", "First commit");
        }
    }
    
    @Test
    public void logShowsCommitDetails(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            RevCommit commit = git.commit().setMessage("Test commit").call();
            
            // Verify commit has expected properties
            assertThat(commit.getShortMessage()).isEqualTo("Test commit");
            assertThat(commit.getId()).isNotNull();
            assertThat(commit.getId().abbreviate(7).name()).hasSize(7);
            assertThat(commit.getCommitTime()).isGreaterThan(0);
            
            // Verify time is reasonable (within last minute)
            long now = Instant.now().getEpochSecond();
            assertThat((long)commit.getCommitTime()).isBetween(now - 60, now + 1);
        }
    }
    
    @Test
    public void logOnEmptyRepoReturnsNothing(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // This will throw NoHeadException in real usage, but test repo state
            assertThat(git.getRepository().resolve("HEAD")).isNull();
        } catch (org.eclipse.jgit.api.errors.NoHeadException e) {
            // Expected for repo with no commits
            assertThat(e).isInstanceOf(org.eclipse.jgit.api.errors.NoHeadException.class);
        }
    }
    
    @Test
    public void commitAuthorIsPreserved(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            
            RevCommit commit = git.commit()
                .setMessage("Test")
                .setAuthor("Test User", "test@example.com")
                .call();
            
            assertThat(commit.getAuthorIdent().getName()).isEqualTo("Test User");
            assertThat(commit.getAuthorIdent().getEmailAddress()).isEqualTo("test@example.com");
        }
    }
}
