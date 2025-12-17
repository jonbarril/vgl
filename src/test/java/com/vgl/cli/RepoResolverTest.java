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
    public void gitOnly_detected(@TempDir Path tmp) throws Exception {
        System.setProperty("vgl.noninteractive", "true");
        try (Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            git.getRepository();
        }
        RepoResolution r = RepoResolver.resolveForCommand(tmp);
        assertNotNull(r.getGit(), "Git should be found");
        assertNull(r.getVglRepo(), "No .vgl should exist");
        assertEquals(RepoResolution.ResolutionKind.FOUND_GIT_ONLY, r.getKind());
    }

    @Test
    public void vglOnly_detected(@TempDir Path tmp) throws Exception {
        java.nio.file.Path vgl = tmp.resolve(".vgl");
        java.util.Properties p = new java.util.Properties();
        p.setProperty("local.dir", tmp.toAbsolutePath().toString());
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(vgl)) {
            p.store(out, "test");
        }
        RepoResolution r = RepoResolver.resolveForCommand(tmp);
        assertNull(r.getGit(), "No Git repo should be found");
        assertNull(r.getVglRepo(), "No VglRepo should be loaded without Git");
        assertEquals(RepoResolution.ResolutionKind.FOUND_VGL_ONLY, r.getKind());
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
