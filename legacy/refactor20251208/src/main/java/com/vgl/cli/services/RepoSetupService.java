package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Service for .vgl config creation and interactive repo setup logic.
 */
public class RepoSetupService {
    /**
     * Create a .vgl config file in the given repo root.
     * @param repoRoot Path to the Git repo root
     * @return VglRepo instance if successful, null otherwise
     */
    public VglRepo createVglConfig(Path repoRoot) throws IOException {
        Properties cfg = new Properties();
        cfg.setProperty("local.dir", repoRoot.toAbsolutePath().toString());
        Path vglPath = repoRoot.resolve(".vgl");
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(vglPath)) {
            cfg.store(out, "auto-created by VGL");
        }
        Git g = Git.open(repoRoot.toFile());
        Properties loadedCfg = VglRepo.loadConfig(repoRoot);
        return new VglRepo(g, loadedCfg);
    }

    /**
     * Optionally prompt the user to create a .vgl config if missing (stub for CLI integration).
     * Returns true if created, false otherwise.
     */
    public boolean maybePromptAndCreateVgl(Path repoRoot, boolean interactive) throws IOException {
        if (!interactive) return false;
        // In real CLI, prompt user; here, always create for test/demo
        createVglConfig(repoRoot);
        return true;
    }
}
