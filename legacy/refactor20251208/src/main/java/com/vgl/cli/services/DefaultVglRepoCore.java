package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.lib.Repository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal default implementation of VglRepoCore for the refactor scaffold.
 * computeUndecidedFiles currently returns an empty set (placeholder).
 * saveUndecidedFiles writes a simple .vgl file listing undecided entries if invoked.
 */
public class DefaultVglRepoCore implements VglRepoCore {
    @Override
    public Set<String> computeUndecidedFiles(Git git, Status status) throws Exception {
        // Compute undecided files without persisting.
        // Strategy:
        // - If a .vgl file exists at repo root, read non-comment non-empty lines as undecided entries.
        // - Otherwise return an empty set (status is passive and should not create .vgl).
        try {
            Repository repo = git.getRepository();
            Path repoRoot = repo.getWorkTree().toPath();
            Path vglPath = repoRoot.resolve(".vgl");
            if (!Files.exists(vglPath)) return Collections.emptySet();
            Set<String> result = new HashSet<>();
            for (String line : Files.readAllLines(vglPath, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                // normalize separators to '/'
                t = t.replace('\\', '/');
                result.add(t);
            }
            return result;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    @Override
    public void saveUndecidedFiles(Path repoRoot, Set<String> undecided) throws Exception {
        if (repoRoot == null) throw new IllegalArgumentException("repoRoot is null");
        Path vglPath = repoRoot.resolve(".vgl");
        StringBuilder sb = new StringBuilder();
        sb.append("# vgl config - undecided files\n");
        for (String u : undecided) {
            sb.append(u).append('\n');
        }
        try {
            Files.write(vglPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Unable to write .vgl: " + e.getMessage(), e);
        }
    }
}
