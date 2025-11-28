package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for Utils.expandGlobs() glob pattern expansion.
 * These are integration tests that work with the actual project files.
 */
public class GlobExpansionTest {

    @Test
    public void expandsStarPatternFindsProjectFiles() throws IOException {
        // * matches all files recursively from current directory
        List<String> expanded = Utils.expandGlobs(List.of("*"));
        
        // Should find at least some project files
        assertThat(expanded).isNotEmpty();
    }
    
    @Test
    public void emptyGlobsReturnsEmpty() throws IOException {
        List<String> expanded = Utils.expandGlobs(List.of());
        assertThat(expanded).isEmpty();
    }
    
    @Test
    public void expandsGradleFiles() throws IOException {
        // Find gradle files
        List<String> expanded = Utils.expandGlobs(List.of("*.gradle.kts"));
        
        // Should find build.gradle.kts and settings.gradle.kts
        assertThat(expanded).hasSizeGreaterThanOrEqualTo(1);
    }
    
    @Test
    public void expandsJavaFilesRecursively() throws IOException {
        // Find all Java files recursively
        List<String> expanded = Utils.expandGlobs(List.of("**/*.java"));
        
        // Should find source files
        assertThat(expanded).isNotEmpty();
        assertThat(expanded).allMatch(f -> f.endsWith(".java"));
    }
    
    @Test
    public void nullGlobsReturnsEmpty() throws IOException {
        List<String> expanded = Utils.expandGlobs(null);
        assertThat(expanded).isEmpty();
    }
    
    @Test
    public void nonExistentPatternReturnsEmpty() throws IOException {
        // Pattern that won't match anything
        List<String> expanded = Utils.expandGlobs(List.of("*.nonexistentextension12345"));
        assertThat(expanded).isEmpty();
    }
}
