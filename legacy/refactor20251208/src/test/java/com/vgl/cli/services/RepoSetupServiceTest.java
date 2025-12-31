package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class RepoSetupServiceTest {
    @Test
    public void createsVglConfig(@TempDir Path tmp) throws Exception {
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            git.getRepository();
        }
        RepoSetupService svc = new RepoSetupService();
        VglRepo vglRepo = svc.createVglConfig(tmp);
        assertNotNull(vglRepo, ".vgl config should be created and loaded");
        Properties cfg = VglRepo.loadConfig(tmp);
        assertEquals(tmp.toAbsolutePath().toString(), cfg.getProperty("local.dir"));
    }

    @Test
    public void maybePromptAndCreateVgl_interactive(@TempDir Path tmp) throws Exception {
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            git.getRepository();
        }
        RepoSetupService svc = new RepoSetupService();
        boolean created = svc.maybePromptAndCreateVgl(tmp, true);
        assertTrue(created, "Should create .vgl in interactive mode");
        Properties cfg = VglRepo.loadConfig(tmp);
        assertEquals(tmp.toAbsolutePath().toString(), cfg.getProperty("local.dir"));
    }

    @Test
    public void maybePromptAndCreateVgl_noninteractive(@TempDir Path tmp) throws Exception {
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            git.getRepository();
        }
        RepoSetupService svc = new RepoSetupService();
        boolean created = svc.maybePromptAndCreateVgl(tmp, false);
        assertFalse(created, "Should not create .vgl in non-interactive mode");
        assertFalse(java.nio.file.Files.exists(tmp.resolve(".vgl")), ".vgl should not exist");
    }
}
