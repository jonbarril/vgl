package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Rich result object describing how a repository/state was resolved.
 */
public final class RepoResolution {
    public enum ResolutionKind { NONE, FOUND_GIT_ONLY, FOUND_VGL_ONLY, FOUND_BOTH, CREATED_VGL, EXPLICIT }

    private final Git git;
    private final VglRepo vglRepo;
    private final Path repoRoot;
    private final ResolutionKind kind;
    private final boolean createdVgl;
    private final boolean prompted;
    private final boolean interactive;
    private final String message;
    private final Map<String,String> meta;

    public RepoResolution(Git git, VglRepo vglRepo, Path repoRoot, ResolutionKind kind,
                          boolean createdVgl, boolean prompted, boolean interactive,
                          String message, Map<String,String> meta) {
        this.git = git;
        this.vglRepo = vglRepo;
        this.repoRoot = repoRoot;
        this.kind = kind;
        this.createdVgl = createdVgl;
        this.prompted = prompted;
        this.interactive = interactive;
        this.message = message;
        this.meta = (meta == null) ? Collections.emptyMap() : Collections.unmodifiableMap(meta);
    }

    public Git getGit() { return git; }
    public VglRepo getVglRepo() { return vglRepo; }
    public Path getRepoRoot() { return repoRoot; }
    public ResolutionKind getKind() { return kind; }
    public boolean isCreatedVgl() { return createdVgl; }
    public boolean isPrompted() { return prompted; }
    public boolean isInteractive() { return interactive; }
    public String getMessage() { return message; }
    public Map<String,String> getMeta() { return meta; }
}
