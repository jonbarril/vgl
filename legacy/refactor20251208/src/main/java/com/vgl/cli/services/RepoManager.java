package com.vgl.cli.services;

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
        // Ensure .gitignore exists and includes .vgl (atomic write)
        Path gi = dir.resolve(".gitignore");
        String newGitignoreContent = GITIGNORE_CONTENT;
        if (Files.exists(gi)) {
            String content = Files.readString(gi);
            if (!content.contains(".vgl")) {
                newGitignoreContent = ".vgl\n" + content;
            } else {
                newGitignoreContent = content;
            }
        }
        // Write atomically
        Path tmpGi = dir.resolve(".gitignore.tmp");
        if (!Files.exists(tmpGi.getParent())) {
            Files.createDirectories(tmpGi.getParent());
        }
        Files.writeString(tmpGi, newGitignoreContent);
        try {
            Files.move(tmpGi, gi, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpGi, gi, StandardCopyOption.REPLACE_EXISTING);
        }

        // Write .vgl config atomically
        Path vglFile = dir.resolve(".vgl");
        if (vglProps == null) vglProps = new Properties();
        vglProps.setProperty("local.dir", dir.toAbsolutePath().normalize().toString());
        if (!vglProps.containsKey("local.branch")) vglProps.setProperty("local.branch", branch);
        Path tmpVgl = dir.resolve(".vgl.tmp");
        if (!Files.exists(tmpVgl.getParent())) {
            Files.createDirectories(tmpVgl.getParent());
        }
        try (java.io.OutputStream out = Files.newOutputStream(tmpVgl)) {
            vglProps.store(out, "VGL Configuration");
        }
        try {
            Files.move(tmpVgl, vglFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpVgl, vglFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return git;
    }
    /**
     * Update the .vgl config in the given repo directory.
     * Overwrites or creates the .vgl file with the given properties.
     */
    public static void updateVglConfig(Path dir, Properties vglProps) throws IOException {
        Path vglFile = dir.resolve(".vgl");
        Path tmpVgl = dir.resolve(".vgl.tmp");
        if (!Files.exists(tmpVgl.getParent())) {
            Files.createDirectories(tmpVgl.getParent());
        }
        try (java.io.OutputStream out = Files.newOutputStream(tmpVgl)) {
            vglProps.store(out, "VGL Configuration");
        }
        try {
            Files.move(tmpVgl, vglFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpVgl, vglFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
