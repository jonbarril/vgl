package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/**
 * Central utility for creating and managing VGL repositories.
 * Ensures .git, .vgl, and .gitignore are created/updated in sync and valid per use cases.
 * Used by commands (create, switch, jump, etc.) and test harness.
 */
public class RepoManager {
    private static final String GITIGNORE_CONTENT = String.join("\n",
        "# VGL configuration",
        ".vgl",
        "# Compiled class files",
        "*.class",
        "# Log files",
        "*.log",
        "# Build directories",
        "/build/",
        "/out/",
        "# IDE files",
        ".idea/",
        ".vscode/",
        "# Gradle",
        ".gradle/",
        "# Mac files",
        ".DS_Store"
    );

    /**
     * Create a valid VGL repository at the given path.
     * - Initializes .git if missing
     * - Creates/updates .vgl config
     * - Ensures .gitignore exists and includes .vgl
     * Returns the Git instance for further use.
     */
    public static Git createVglRepo(Path dir, String branch, Properties vglProps) throws IOException {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        boolean gitExisted = Files.exists(dir.resolve(".git"));
        Git git;
        try {
            if (!gitExisted) {
                git = Git.init().setDirectory(dir.toFile()).setInitialBranch(branch).call();
            } else {
                git = Git.open(dir.toFile());
            }
        } catch (Exception e) {
            throw new IOException("Failed to initialize or open git repo: " + e.getMessage(), e);
        }
        // Ensure .gitignore exists and includes .vgl
        Path gi = dir.resolve(".gitignore");
        if (!Files.exists(gi)) {
            Files.writeString(gi, GITIGNORE_CONTENT);
        } else {
            // Ensure .vgl is present in .gitignore
            String content = Files.readString(gi);
            if (!content.contains(".vgl")) {
                Files.writeString(gi, ".vgl\n" + content);
            }
        }
        // Write .vgl config
        Path vglFile = dir.resolve(".vgl");
        if (vglProps == null) vglProps = new Properties();
        vglProps.setProperty("local.dir", dir.toAbsolutePath().normalize().toString());
        if (!vglProps.containsKey("local.branch")) vglProps.setProperty("local.branch", branch);
        try (java.io.OutputStream out = Files.newOutputStream(vglFile)) {
            vglProps.store(out, "VGL Configuration");
        }
        return git;
    }
    /**
     * Update the .vgl config in the given repo directory.
     * Overwrites or creates the .vgl file with the given properties.
     */
    public static void updateVglConfig(Path dir, Properties vglProps) throws IOException {
        Path vglFile = dir.resolve(".vgl");
        try (java.io.OutputStream out = Files.newOutputStream(vglFile)) {
            vglProps.store(out, "VGL Configuration");
        }
    }
}
