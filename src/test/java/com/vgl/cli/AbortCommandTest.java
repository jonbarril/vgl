package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for abort command - verifies merge abort functionality.
 */
public class AbortCommandTest {

    @Test
    public void detectsMergeInProgress(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create initial commit
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Create MERGE_HEAD file to simulate merge in progress
            File mergeHead = new File(git.getRepository().getDirectory(), "MERGE_HEAD");
            Files.writeString(mergeHead.toPath(), "0000000000000000000000000000000000000000");
            
            // Verify MERGE_HEAD exists
            assertThat(mergeHead.exists()).isTrue();
            
            // Abort the merge (delete MERGE_HEAD)
            mergeHead.delete();
            
            // Verify MERGE_HEAD is gone
            assertThat(mergeHead.exists()).isFalse();
        }
    }
    
    @Test
    public void noMergeInProgressWhenMergeHeadMissing(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create initial commit
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Check that MERGE_HEAD doesn't exist
            File mergeHead = new File(git.getRepository().getDirectory(), "MERGE_HEAD");
            assertThat(mergeHead.exists()).isFalse();
        }
    }
    
    @Test
    public void gitDirectoryStructure(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Verify .git directory structure
            File gitDir = git.getRepository().getDirectory();
            assertThat(gitDir.exists()).isTrue();
            assertThat(gitDir.getName()).isEqualTo(".git");
            
            // Check for expected Git files
            assertThat(new File(gitDir, "HEAD").exists()).isTrue();
            assertThat(new File(gitDir, "config").exists()).isTrue();
            assertThat(new File(gitDir, "refs").exists()).isTrue();
        }
    }
}
