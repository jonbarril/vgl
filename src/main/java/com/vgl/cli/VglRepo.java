package com.vgl.cli;

import org.eclipse.jgit.api.Git;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Represents a VGL repository with both git and config.
 * Combines Git instance with .vgl configuration.
 */
public class VglRepo implements Closeable {
    private final Git git;
    private final Path repoRoot;
    private final Properties config;
    
    /**
     * Create a VglRepo instance.
     * @param git The Git instance (must not be null)
     * @param config The loaded configuration (may be empty if no .vgl file)
     */
    public VglRepo(Git git, Properties config) {
        if (git == null) {
            throw new IllegalArgumentException("Git instance cannot be null");
        }
        this.git = git;
        this.repoRoot = git.getRepository().getWorkTree().toPath();
        this.config = config != null ? config : new Properties();
    }
    
    public Git getGit() {
        return git;
    }
    
    public Path getRepoRoot() {
        return repoRoot;
    }
    
    public Properties getConfig() {
        return config;
    }
    
    /**
     * Get a configuration value with a default.
     */
    public String getConfigOrDefault(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
    
    @Override
    public void close() throws IOException {
        if (git != null) {
            git.close();
        }
    }
    
    /**
     * Load .vgl config file from the repository root.
     * @param repoRoot The root directory of the git repository
     * @return Loaded properties, or empty properties if no .vgl file
     */
    static Properties loadConfig(Path repoRoot) {
        Properties config = new Properties();
        
        if (repoRoot == null) {
            return config;
        }
        
        Path vglFile = repoRoot.resolve(".vgl");
        if (!Files.exists(vglFile)) {
            return config;
        }
        
        // Check if .git still exists (orphaned .vgl check)
        if (!Files.exists(repoRoot.resolve(".git"))) {
            System.err.println("Warning: Found .vgl but no .git directory.");
            System.err.println("The .git repository may have been deleted or moved.");
            return config;
        }
        
        // Load the config
        try (InputStream in = Files.newInputStream(vglFile)) {
            config.load(in);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load .vgl configuration file.");
        }
        
        return config;
    }
}
