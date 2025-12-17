package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

import com.vgl.cli.services.RepoResolution;
import com.vgl.cli.utils.RepoResolver;
public class RepoResolverTest {

    @Test
    public void gitOnly_noninteractive(@TempDir Path tmp) throws Exception {
        System.setProperty("vgl.noninteractive", "true");
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            // no-op; ensure repo created then close
            // Touch the repository object to satisfy static analyzers that the resource is used
            git.getRepository();
        }

        RepoResolution r = RepoResolver.resolveForCommand(tmp);
        assertNotNull(r.getGit(), "Git should be found");
        assertNull(r.getVglRepo(), "No .vgl should exist");
        assertEquals(RepoResolution.ResolutionKind.FOUND_GIT_ONLY, r.getKind());
        assertFalse(r.isCreatedVgl());
    }

    @Test
    public void createdVgl_interactive(@TempDir Path tmp) throws Exception {
        // Ensure interactive mode and simulate user input 'y' to accept conversion
        System.setProperty("vgl.noninteractive", "false");
        java.io.InputStream oldIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream("y\n".getBytes()));
            try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
                // no-op; ensure repo created then close
                // Touch repository to mark usage
                git.getRepository();
            }

            RepoResolution r = RepoResolver.resolveForCommand(tmp);
            assertNotNull(r.getGit(), "Git should be found");
            assertNotNull(r.getVglRepo(), "A .vgl should have been created and loaded");
            assertEquals(RepoResolution.ResolutionKind.CREATED_VGL, r.getKind());
            assertTrue(r.isCreatedVgl());
        } finally {
            System.setIn(oldIn);
            System.setProperty("vgl.noninteractive", "true");
        }
    }

    @Test
    public void foundBoth(@TempDir Path tmp) throws Exception {
        System.setProperty("vgl.noninteractive", "true");
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            // Touch repository to mark usage for static analysis
            git.getRepository();
            java.nio.file.Path vgl = tmp.resolve(".vgl");
            java.util.Properties p = new java.util.Properties();
            p.setProperty("local.dir", tmp.toAbsolutePath().toString());
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(vgl)) {
                p.store(out, "test");
            }
        }

        RepoResolution r = RepoResolver.resolveForCommand(tmp);
        assertNotNull(r.getGit());
        assertNotNull(r.getVglRepo());
        assertEquals(RepoResolution.ResolutionKind.FOUND_BOTH, r.getKind());
        assertFalse(r.isCreatedVgl());
    }
}
