package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Utility for creating and updating VGL and Git repository state.
 * Used by both CLI commands and test harness for consistent setup and state management.
 */
public class RepoCreateUtil {
    /**
     * Initialize a Git repository at the given path if one does not exist.
     * @param repoPath Path to initialize .git in
     * @return Git instance for the repo
     */
    public static Git initGitRepo(Path repoPath) throws Exception {
        if (!Files.exists(repoPath.resolve(".git"))) {
            return Git.init().setDirectory(repoPath.toFile()).setInitialBranch("main").call();
        }
        return Git.open(repoPath.toFile());
    }

    /**
     * Create or update a .vgl config at the given path, using the provided properties.
     * @param repoPath Path to the repo root (should contain .git)
     * @param props Properties to write to .vgl
     */
    public static void writeVglConfig(Path repoPath, Properties props) throws IOException {
        Path vglFile = repoPath.resolve(".vgl");
        try (var out = Files.newOutputStream(vglFile)) {
            props.store(out, "VGL Configuration");
        }
    }

    /**
     * Update a single property in the .vgl config, creating it if needed.
     * @param repoPath Path to the repo root
     * @param key Property key
     * @param value Property value
     */
    public static void updateVglProperty(Path repoPath, String key, String value) throws IOException {
        Path vglFile = repoPath.resolve(".vgl");
        Properties props = new Properties();
        if (Files.exists(vglFile)) {
            try (var in = Files.newInputStream(vglFile)) {
                props.load(in);
            }
        }
        props.setProperty(key, value);
        writeVglConfig(repoPath, props);
    }

    /**
     * Utility to update Git config (user, branch, remote, etc.)
     * @param git Git instance
     * @param section Config section (e.g., "user", "remote")
     * @param name Config name (e.g., "email", "url")
     * @param value Config value
     */
    public static void updateGitConfig(Git git, String section, String name, String value) throws Exception {
        git.getRepository().getConfig().setString(section, null, name, value);
        git.getRepository().getConfig().save();
    }
}
