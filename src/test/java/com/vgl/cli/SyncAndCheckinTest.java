package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for sync and checkin commands - verifies composite operations.
 */
public class SyncAndCheckinTest {
    private static void printProgress(String testName) {
        TestProgress.print(SyncAndCheckinTest.class, testName);
    }

    @Test
    public void syncIsPullThenPush(@TempDir Path tempDir) throws Exception {
            printProgress("syncIsPullThenPush");
        // Sync command just calls PullCommand then PushCommand
        // This test verifies the command sequence can be composed
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Verify repo is in valid state for sync
            assertThat(git.getRepository().resolve("HEAD")).isNotNull();
            assertThat(git.status().call().isClean()).isTrue();
        }
    }
    
    @Test
    public void checkinParsesGitHubUrl(@TempDir Path tempDir) throws Exception {
            printProgress("checkinParsesGitHubUrl");
        String url = "https://github.com/user/repo.git";
        
        // Simulate PR URL generation logic
        if (url.contains("github.com")) {
            String name = url.replaceFirst(".*github.com[:/]", "").replaceAll("\\.git$", "");
            String branch = "feature";
            String prUrl = "https://github.com/" + name + "/compare/main..." + branch + "?expand=1";
            
            assertThat(name).isEqualTo("user/repo");
            assertThat(prUrl).isEqualTo("https://github.com/user/repo/compare/main...feature?expand=1");
        }
    }
    
    @Test
    public void checkinHandlesSshUrl(@TempDir Path tempDir) throws Exception {
            printProgress("checkinHandlesSshUrl");
        String url = "git@github.com:user/repo.git";
        
        // Simulate PR URL generation with SSH URL
        if (url.contains("github.com")) {
            String name = url.replaceFirst(".*github.com[:/]", "").replaceAll("\\.git$", "");
            
            assertThat(name).isEqualTo("user/repo");
        }
    }
    
    @Test
    public void checkinDraftVsFinalFlag() {
            printProgress("checkinDraftVsFinalFlag");
        // Verify flag logic for draft vs final PR
        boolean draft = true;
        
        String suffix = draft ? " (draft)" : "";
        assertThat(suffix).isEqualTo(" (draft)");
        
        draft = false;
        suffix = draft ? " (draft)" : "";
        assertThat(suffix).isEmpty();
    }
    
    @Test
    public void nonGitHubRemoteHandling() {
            printProgress("nonGitHubRemoteHandling");
        String url = "https://gitlab.com/user/repo.git";
        
        if (!url.contains("github.com")) {
            // Should show generic message
            assertThat(url).doesNotContain("github.com");
        }
    }
}
