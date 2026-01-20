package com.vgl.cli.utils;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;

/**
 * Shared, host-agnostic remote operations.
 *
 * <p>Policy:
 * <ul>
 *   <li>If env credentials are set, use JGit with CredentialsProvider (automation-friendly).</li>
 *   <li>Otherwise, prefer native git (uses existing credential helpers like Git Credential Manager).</li>
 *   <li>Otherwise, attempt JGit without creds and translate the common "no CredentialsProvider" error.</li>
 * </ul>
 */
public final class GitRemoteOps {
    private GitRemoteOps() {
        // static only
    }

    public enum FetchOutcome {
        OK_JGIT,
        OK_NATIVE,
        AUTH_FAILED
    }

    public static String ensureOriginConfigured(Repository repo, String vglRemoteUrl) {
        if (repo == null) {
            return null;
        }
        try {
            StoredConfig cfg = repo.getConfig();
            String originUrl = cfg.getString("remote", "origin", "url");
            if ((originUrl == null || originUrl.isBlank()) && vglRemoteUrl != null && !vglRemoteUrl.isBlank()) {
                cfg.setString("remote", "origin", "url", vglRemoteUrl);
                cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                cfg.save();
                originUrl = vglRemoteUrl;
            }
            return (originUrl != null && !originUrl.isBlank()) ? originUrl : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static FetchOutcome fetchOrigin(Path repoRoot, Git git, String originUrlForHint, boolean required, PrintStream err)
        throws Exception {
        if (repoRoot == null || git == null) {
            return FetchOutcome.AUTH_FAILED;
        }

        boolean hasEnvCreds = GitAuth.credentialsProviderFromEnvOrNull() != null;

        if (hasEnvCreds) {
            try {
                GitAuth.applyCredentialsIfPresent(git.fetch().setRemote("origin")).call();
                return FetchOutcome.OK_JGIT;
            } catch (Exception e) {
                if (GitAuth.handleMissingCredentialsProvider(e, originUrlForHint, err)) {
                    return FetchOutcome.AUTH_FAILED;
                }
                throw e;
            }
        }

        if (GitNative.isGitAvailable()) {
            try {
                GitNative.fetch(repoRoot, "origin");
                return FetchOutcome.OK_NATIVE;
            } catch (Exception e) {
                boolean auth = GitAuth.handleNativeAuthFailure(e, originUrlForHint, err);
                if (auth) {
                    return FetchOutcome.AUTH_FAILED;
                }
                if (!required) {
                    // best-effort: ignore non-auth failures
                    return FetchOutcome.OK_NATIVE;
                }
                throw e;
            }
        }

        // Fallback: JGit without creds.
        try {
            git.fetch().setRemote("origin").call();
            return FetchOutcome.OK_JGIT;
        } catch (Exception e) {
            if (GitAuth.handleMissingCredentialsProvider(e, originUrlForHint, err)) {
                return FetchOutcome.AUTH_FAILED;
            }
            if (!required) {
                return FetchOutcome.OK_JGIT;
            }
            throw e;
        }
    }

    /**
     * Clone into an existing empty directory.
     *
     * @return true if cloned successfully, false if auth is required (and was already reported).
     */
    public static boolean cloneInto(Path targetDir, String remoteUrl, String branch, PrintStream err) throws Exception {
        boolean hasEnvCreds = GitAuth.credentialsProviderFromEnvOrNull() != null;

        if (!hasEnvCreds && GitNative.isGitAvailable()) {
            try {
                GitNative.cloneIntoCurrentDir(targetDir, remoteUrl, branch);
                return true;
            } catch (Exception e) {
                if (GitAuth.handleNativeAuthFailure(e, remoteUrl, err)) {
                    return false;
                }
                throw e;
            }
        }

        try (Git ignored = GitAuth.applyCredentialsIfPresent(Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(targetDir.toFile())
            .setBranch("refs/heads/" + ((branch == null || branch.isBlank()) ? "main" : branch)))
            .call()) {
            // cloned
            return true;
        } catch (Exception e) {
            if (GitAuth.handleMissingCredentialsProvider(e, remoteUrl, err)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Push localBranch -> remoteBranch.
     *
     * @return true if pushed, false if auth is required (and was already reported).
     */
    public static boolean push(Path repoRoot, Git git, String originUrlForHint, String localBranch, String remoteBranch, PrintStream err)
        throws Exception {
        boolean hasEnvCreds = GitAuth.credentialsProviderFromEnvOrNull() != null;

        if (!hasEnvCreds && GitNative.isGitAvailable()) {
            try {
                GitNative.push(repoRoot, "origin", localBranch, remoteBranch);
                return true;
            } catch (Exception e) {
                if (GitAuth.handleNativeAuthFailure(e, originUrlForHint, err)) {
                    return false;
                }
                throw e;
            }
        }

        try {
            GitAuth.applyCredentialsIfPresent(git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec(localBranch + ":" + remoteBranch)))
                .call();
            return true;
        } catch (Exception e) {
            if (GitAuth.handleMissingCredentialsProvider(e, originUrlForHint, err)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * List remote heads (branch names). Uses native git if available.
     *
     * @return branches, or null if auth is required (and was already reported).
     */
    public static java.util.List<String> listRemoteHeads(String remoteUrl, PrintStream err) throws Exception {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return java.util.List.of();
        }

        if (GitNative.isGitAvailable()) {
            try {
                return GitNative.listRemoteHeads(remoteUrl);
            } catch (Exception e) {
                if (GitAuth.handleNativeAuthFailure(e, remoteUrl, err)) {
                    return null;
                }
                throw e;
            }
        }

        try {
            Collection<org.eclipse.jgit.lib.Ref> refs = GitAuth.applyCredentialsIfPresent(Git.lsRemoteRepository()
                .setRemote(remoteUrl)
                .setHeads(true)
                .setTags(false))
                .call();

            java.util.List<String> branches = new java.util.ArrayList<>();
            if (refs != null) {
                for (org.eclipse.jgit.lib.Ref ref : refs) {
                    if (ref == null || ref.getName() == null) {
                        continue;
                    }
                    String name = ref.getName();
                    if (name.startsWith(org.eclipse.jgit.lib.Constants.R_HEADS)) {
                        branches.add(name.substring(org.eclipse.jgit.lib.Constants.R_HEADS.length()));
                    }
                }
            }
            java.util.Collections.sort(branches);
            return branches;
        } catch (Exception e) {
            if (GitAuth.handleMissingCredentialsProvider(e, remoteUrl, err)) {
                return null;
            }
            throw e;
        }
    }
}
