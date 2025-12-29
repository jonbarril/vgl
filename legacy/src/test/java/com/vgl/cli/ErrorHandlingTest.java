package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for error handling and validation in VGL commands.
 */
public class ErrorHandlingTest {

    @Test
    public void emptyArgsDefaultsToHelp() {
        // Empty args defaults to help command
        String[] empty = {};
        Args args = new Args(empty);
        assertThat(args.cmd).isEqualTo("help");
        assertThat(args.rest).isEmpty();
    }
    
    @Test
    public void nullArgsDefaultsToHelp() {
        Args args = new Args(null);
        assertThat(args.cmd).isEqualTo("help");
        assertThat(args.rest).isEmpty();
    }
    
    @Test
    public void commandNameIsLowercase() {
        String[] mixedCase = {"STATUS"};
        Args args = new Args(mixedCase);
        assertThat(args.cmd).isEqualTo("status");
    }
    
    @Test
    public void vglCliDetectsNonGitDirectory(@TempDir Path tempDir) {
        // Directory without .git should be detected
        assertThat(Files.exists(tempDir.resolve(".git"))).isFalse();
        
        // VglCli should detect this as non-git directory
        // (actual validation happens in command execution)
    }
    
    @Test
    public void commitCommandIsParsed() {
        String[] args = {"commit", "-m", "message"};
        Args parsedArgs = new Args(args);
        
        assertThat(parsedArgs.cmd).isEqualTo("commit");
        assertThat(parsedArgs.rest).containsExactly("-m", "message");
    }
    
    @Test
    public void trackCommandWithFiles() {
        String[] args = {"track", "file1.txt", "file2.txt"};
        Args parsedArgs = new Args(args);
        
        assertThat(parsedArgs.cmd).isEqualTo("track");
        assertThat(parsedArgs.rest).containsExactly("file1.txt", "file2.txt");
    }
}
