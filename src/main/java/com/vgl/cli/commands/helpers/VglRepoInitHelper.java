package com.vgl.cli.commands.helpers;

import com.vgl.cli.services.RepoResolution;
import com.vgl.cli.services.RepoSetupService;
import com.vgl.cli.utils.RepoResolver;
import java.nio.file.Path;

/**
 * Helper to ensure .vgl config exists, prompting/creating if needed.
 */
public class VglRepoInitHelper {
    /**
     * Ensure .vgl exists for a Git repo, optionally prompting/creating.
     * Returns updated RepoResolution after any creation.
     */
    public static RepoResolution ensureVglConfig(Path repoRoot, boolean interactive) throws Exception {
        RepoResolution res = RepoResolver.resolveForCommand(repoRoot);
        if (res.getKind() == RepoResolution.ResolutionKind.FOUND_GIT_ONLY) {
            RepoSetupService svc = new RepoSetupService();
            svc.maybePromptAndCreateVgl(repoRoot, interactive);
            // Re-resolve after creation
            res = RepoResolver.resolveForCommand(repoRoot);
        }
        return res;
    }
}
