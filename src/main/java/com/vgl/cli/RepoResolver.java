package com.vgl.cli;

import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized repo/state resolution helper used by CLI commands.
 * Returns a rich {@link RepoResolution} describing the outcome. Backwards-compatible
 * convenience methods are provided for callers that only need a raw Git or VglRepo.
 */
public final class RepoResolver {
    private RepoResolver() {}

    /**
     * Resolve repository state starting from the current working directory.
     */
    public static RepoResolution resolveForCommand() throws IOException {
        Path cwd = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return resolveForCommand(cwd);
    }

    /**
     * Resolve repository state starting from the supplied path. This may prompt (interactive)
     * and may create a `.vgl` when appropriate (matching existing behavior in `Utils`).
     */
    public static RepoResolution resolveForCommand(Path start) throws IOException {
        if (start == null) start = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

        Path repoRoot = null;

        // Fast path: if the user-level VGL state points at a repository, validate
        // it first. This lets us fail fast when persisted state is corrupted
        // (refs a non-existent path) instead of scanning large trees.
        try {
            VglStateStore.VglState s = VglStateStore.read();
            if (s != null && s.localDir != null && !s.localDir.isBlank()) {
                java.nio.file.Path saved = java.nio.file.Paths.get(s.localDir).toAbsolutePath().normalize();
                // If the saved path exists and contains a .git, use it directly.
                try {
                    if (java.nio.file.Files.exists(saved) && java.nio.file.Files.exists(saved.resolve(".git"))) {
                        try {
                            org.eclipse.jgit.api.Git g = org.eclipse.jgit.api.Git.open(saved.toFile());
                            java.util.Properties cfg = VglRepo.loadConfig(saved);
                            VglRepo vglRepo = new VglRepo(g, cfg);
                            repoRoot = saved;
                            Map<String,String> meta = new HashMap<>();
                            try { String lb = cfg.getProperty("local.branch", null); if (lb != null) meta.put("local.branch", lb); } catch (Exception ignore) {}
                            return new RepoResolution(g, vglRepo, repoRoot, RepoResolution.ResolutionKind.FOUND_BOTH, false, false, Utils.isInteractive(), null, meta);
                        } catch (Exception ignore) {
                            // Fall through to normal resolution if opening fails
                        }
                    } else {
                        // Persisted state points at a path that either does not exist or has no .git
                        String statePath = VglStateStore.getDefaultStatePath().toString();
                        String msg = "VGL persisted state '" + statePath + "' references '" + saved + "' but no Git repository was found there.";
                        Map<String,String> meta = new HashMap<>();
                        meta.put("vgl.state.path", statePath);
                        meta.put("vgl.state.referenced", saved.toString());
                        return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg + " Delete or fix the state file and run 'vgl create <path>' to recreate.", meta);
                    }
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}

        // Check whether a .vgl existed before resolution (search upward until root)
        boolean vglExisted = false;
        Path cur = start.toAbsolutePath().normalize();
        while (cur != null) {
            if (java.nio.file.Files.exists(cur.resolve(".vgl"))) { vglExisted = true; break; }
            // stop if .git exists at this level per discovery rules
            if (java.nio.file.Files.exists(cur.resolve(".git"))) break;
            cur = cur.getParent();
        }

        // Fast-check: look for a .vgl file in any ancestor and validate it without
        // opening large work trees. This lets us detect corrupted .vgl state quickly
        // and return a helpful message rather than scanning directories.
        VglRepo vglRepo = null;
        try {
            Path curCheck = start.toAbsolutePath().normalize();
            Path foundVgl = null;
            while (curCheck != null) {
                Path cand = curCheck.resolve(".vgl");
                if (java.nio.file.Files.exists(cand)) { foundVgl = cand; break; }
                // stop at filesystem root
                curCheck = curCheck.getParent();
            }
            if (foundVgl != null) {
                // Read the .vgl properties quickly and validate the referenced local.dir
                java.util.Properties p = new java.util.Properties();
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(foundVgl)) {
                    p.load(in);
                } catch (Exception ignore) {}

                String localDirRef = p.getProperty("local.dir", p.getProperty("localDir", null));
                if (localDirRef != null && !localDirRef.isBlank()) {
                    // Some persisted state formats include a display suffix like " :: branch".
                    // Strip such suffixes if present to recover the raw path quickly.
                    String cleaned = localDirRef;
                    int sep = cleaned.indexOf(" :: ");
                    if (sep >= 0) cleaned = cleaned.substring(0, sep).trim();
                    java.nio.file.Path referenced = null;
                    try {
                        referenced = java.nio.file.Paths.get(cleaned).toAbsolutePath().normalize();
                    } catch (Exception parseEx) {
                        String statePath = foundVgl.toAbsolutePath().toString();
                        String msg = "VGL configuration '" + statePath + "' contains an invalid local.dir value: '" + localDirRef + "'.";
                        Map<String,String> fastMeta = new HashMap<>();
                        fastMeta.put("vgl.file", statePath);
                        fastMeta.put("vgl.referenced.raw", localDirRef);
                        return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg + " Delete or fix the .vgl file and run 'vgl create <path>' to recreate.", fastMeta);
                    }
                    if (referenced != null) {
                        boolean exists = java.nio.file.Files.exists(referenced);
                        boolean hasGit = exists && java.nio.file.Files.exists(referenced.resolve(".git"));
                        if (!exists || !hasGit) {
                            String statePath = foundVgl.toAbsolutePath().toString();
                            String msg = "VGL configuration '" + statePath + "' references '" + referenced + "' but the target is missing or lacks a .git directory.";
                            Map<String,String> fastMeta = new HashMap<>();
                            fastMeta.put("vgl.file", statePath);
                            fastMeta.put("vgl.referenced.localDir", referenced.toString());
                            fastMeta.put("vgl.referenced.exists", String.valueOf(exists));
                            fastMeta.put("vgl.referenced.hasGit", String.valueOf(hasGit));
                            return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg + " Delete or fix the .vgl file and run 'vgl create <path>' to recreate.", fastMeta);
                        }
                        try {
                            org.eclipse.jgit.api.Git g = org.eclipse.jgit.api.Git.open(referenced.toFile());
                            vglRepo = new VglRepo(g, p);
                            repoRoot = referenced;
                        } catch (Exception ignore) {
                            // fall through to normal resolution
                        }
                    }
                }
            }
        } catch (Exception ignore) {}

        // Try to find a Git repo (non-warning) for meta information
        Git git = Utils.findGitRepo(start);
        if (git != null) {
            try { repoRoot = git.getRepository().getWorkTree().toPath(); } catch (Exception ignore) {}
        } else if (vglRepo != null) {
            try { repoRoot = vglRepo.getRepoRoot(); } catch (Exception ignore) {}
        }

        // If the on-disk .vgl state points at a missing or invalid path, treat it
        // as corrupted: record a message and neutralize the VglRepo so callers
        // won't accidentally walk a huge or incorrect tree.
        boolean corruptedVgl = false;
        String corruptedPath = null;
        String corruptedMessage = null;
        try {
            if (vglRepo != null) {
                Path vglRoot = null;
                try { vglRoot = vglRepo.getRepoRoot(); } catch (Exception ignore) {}
                if (vglRoot == null || !java.nio.file.Files.exists(vglRoot)) {
                    corruptedVgl = true;
                }
            }
        } catch (Exception ignore) {}

        if (corruptedVgl) {
            try {
                corruptedPath = "(unknown)";
                try { corruptedPath = String.valueOf(vglRepo.getRepoRoot()); } catch (Exception ignore) {}
                corruptedMessage = "VGL state refers to '" + corruptedPath + "' but the path is missing or invalid.";
                if (vglRepo != null) vglRepo = null;
            } catch (Exception ignore) {}
        }

        boolean createdVgl = (!vglExisted && vglRepo != null);
        boolean interactive = Utils.isInteractive();
        boolean prompted = (!vglExisted && interactive && createdVgl);

        RepoResolution.ResolutionKind kind = RepoResolution.ResolutionKind.NONE;
        if (vglRepo != null && git != null) kind = RepoResolution.ResolutionKind.FOUND_BOTH;
        else if (vglRepo == null && git != null) kind = RepoResolution.ResolutionKind.FOUND_GIT_ONLY;
        else if (vglRepo != null && git == null) kind = RepoResolution.ResolutionKind.FOUND_VGL_ONLY;
        if (createdVgl) kind = RepoResolution.ResolutionKind.CREATED_VGL;

        String message = null;
        Map<String,String> meta = new HashMap<>();
        if (corruptedMessage != null) {
            message = corruptedMessage;
            try { if (corruptedPath != null) meta.put("vgl.corrupted.path", corruptedPath); } catch (Exception ignore) {}
        }
        try {
            if (vglRepo != null) {
                try {
                    java.util.Properties p = vglRepo.getConfig();
                    String lb = p.getProperty("local.branch", null);
                    String ru = p.getProperty("remote.url", null);
                    String rb = p.getProperty("remote.branch", null);
                    if (lb != null) meta.put("local.branch", lb);
                    if (ru != null) meta.put("remote.url", ru);
                    if (rb != null) meta.put("remote.branch", rb);
                } catch (Exception ignore) {}
            } else if (git != null) {
                try {
                    String branch = git.getRepository().getBranch();
                    if (branch != null) meta.put("local.branch", branch);
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}

        if (kind == RepoResolution.ResolutionKind.FOUND_GIT_ONLY && !interactive) {
            message = "Found Git repository but no .vgl (non-interactive)";
        }

        return new RepoResolution(git, vglRepo, repoRoot, kind, createdVgl, prompted, interactive, message, meta);
    }

    // Backwards-compatible helpers
    public static VglRepo resolveVglRepoForCommand() throws IOException {
        RepoResolution r = resolveForCommand();
        return r.getVglRepo();
    }

    public static VglRepo resolveVglRepoForCommand(java.nio.file.Path start) throws IOException {
        RepoResolution r = resolveForCommand(start);
        return r.getVglRepo();
    }

    public static Git resolveGitRepoForCommand() throws IOException {
        RepoResolution r = resolveForCommand();
        return r.getGit();
    }

    public static Git resolveGitRepoForCommand(java.nio.file.Path start) throws IOException {
        RepoResolution r = resolveForCommand(start);
        return r.getGit();
    }
}
