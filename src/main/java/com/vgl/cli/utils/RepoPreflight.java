package com.vgl.cli.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.Git;

/**
 * Lightweight, best-effort repository metadata preflight.
 *
 * <p>This is intentionally conservative: it warns and continues by default, and only refuses to
 * proceed when the user explicitly aborts (interactive) or when Git cannot be opened.
 */
public final class RepoPreflight {
    private RepoPreflight() {}

    /**
     * Runs lightweight checks against {@code repoRoot}. Returns false only when the command should
     * stop.
     */
    public static boolean preflight(Path repoRoot) {
        if (repoRoot == null) {
            return false;
        }

        boolean prompt = Boolean.parseBoolean(System.getProperty("vgl.preflight.prompt", "false"));

        // 1) Ensure Git can be opened.
        String gitBranch = null;
        try (Git git = GitUtils.openGit(repoRoot)) {
            try {
                gitBranch = git.getRepository().getBranch();
            } catch (Exception ignored) {
                gitBranch = null;
            }
        } catch (Exception e) {
            System.err.println(Messages.warnHintGitInvalid(repoRoot));
            return false;
        }

        // 2) If .vgl exists, ensure it's readable enough to load properties.
        Properties props = new Properties();
        Path vgl = repoRoot.resolve(VglConfig.FILENAME);
        if (Files.isRegularFile(vgl)) {
            try (InputStream in = Files.newInputStream(vgl)) {
                props.load(in);
            } catch (Exception e) {
                if (!prompt) {
                    System.err.println(Messages.warnHintVglUnreadable(repoRoot));
                } else {
                    char choice = Utils.warnHintAndMaybePromptChoice(
                        Messages.warnHintVglUnreadable(repoRoot),
                        false,
                        "Action? [A]bort / [C]ontinue / [R]ecreate-.vgl then continue: ",
                        'c',
                        'a',
                        'c',
                        'r'
                    );
                    if (choice == 'a') {
                        return false;
                    }
                    if (choice == 'r') {
                        final String branchToWrite = (gitBranch == null || gitBranch.isBlank()) ? "main" : gitBranch;
                        try {
                            VglConfig.writeProps(repoRoot, p -> p.setProperty(VglConfig.KEY_LOCAL_BRANCH, branchToWrite));
                        } catch (Exception ignored) {
                            // keep going
                        }
                    }
                }
                // Continue with empty props.
                props = new Properties();
            }
        }

        // 3) Branch mismatch is a good candidate for warn/hint/prompt-to-fix.
        String vglBranch = props.getProperty(VglConfig.KEY_LOCAL_BRANCH);
        if (vglBranch != null && !vglBranch.isBlank() && gitBranch != null && !gitBranch.isBlank()) {
            if (!vglBranch.equals(gitBranch)) {
                if (!prompt) {
                    try {
                        final String branchToWrite = gitBranch;
                        VglConfig.writeProps(repoRoot, p -> p.setProperty(VglConfig.KEY_LOCAL_BRANCH, branchToWrite));
                    } catch (Exception e) {
                        System.err.println(Messages.warnHintBranchMismatch(vglBranch, gitBranch));
                    }
                } else {
                    char choice = Utils.warnHintAndMaybePromptChoice(
                        Messages.warnHintBranchMismatch(vglBranch, gitBranch),
                        false,
                        "Action? [A]bort / [C]ontinue / [U]pdate-.vgl then continue: ",
                        'c',
                        'a',
                        'c',
                        'u'
                    );
                    if (choice == 'a') {
                        return false;
                    }
                    if (choice == 'u') {
                        final String branchToWrite = gitBranch;
                        try {
                            VglConfig.writeProps(repoRoot, p -> p.setProperty(VglConfig.KEY_LOCAL_BRANCH, branchToWrite));
                        } catch (Exception e) {
                            System.err.println(Messages.warnHintVglUnreadable(repoRoot));
                        }
                    }
                }
            }
        }

        // 4) Stale tracked/untracked path entries in .vgl are another good candidate.
        Set<String> missing = new LinkedHashSet<>();
        missing.addAll(findMissingPaths(repoRoot, VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES)));
        missing.addAll(findMissingPaths(repoRoot, VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES)));
        if (!missing.isEmpty()) {
            if (!prompt) {
                // Non-disruptive default: silently prune missing paths.
                final Set<String> toRemove = new LinkedHashSet<>(missing);
                try {
                    VglConfig.writeProps(repoRoot, p -> {
                        Set<String> tracked = VglConfig.getPathSet(p, VglConfig.KEY_TRACKED_FILES);
                        tracked.removeAll(toRemove);
                        VglConfig.setPathSet(p, VglConfig.KEY_TRACKED_FILES, tracked);

                        Set<String> untracked = VglConfig.getPathSet(p, VglConfig.KEY_UNTRACKED_FILES);
                        untracked.removeAll(toRemove);
                        VglConfig.setPathSet(p, VglConfig.KEY_UNTRACKED_FILES, untracked);
                    });
                } catch (Exception e) {
                    System.err.println(Messages.warnHintVglHasMissingPaths(missing.size()));
                }
            } else {
                char choice = Utils.warnHintAndMaybePromptChoice(
                    Messages.warnHintVglHasMissingPaths(missing.size()),
                    false,
                    "Action? [A]bort / [C]ontinue / [P]rune-missing then continue: ",
                    'c',
                    'a',
                    'c',
                    'p'
                );
                if (choice == 'a') {
                    return false;
                }
                if (choice == 'p') {
                    final Set<String> toRemove = new LinkedHashSet<>(missing);
                    try {
                        VglConfig.writeProps(repoRoot, p -> {
                            Set<String> tracked = VglConfig.getPathSet(p, VglConfig.KEY_TRACKED_FILES);
                            tracked.removeAll(toRemove);
                            VglConfig.setPathSet(p, VglConfig.KEY_TRACKED_FILES, tracked);

                            Set<String> untracked = VglConfig.getPathSet(p, VglConfig.KEY_UNTRACKED_FILES);
                            untracked.removeAll(toRemove);
                            VglConfig.setPathSet(p, VglConfig.KEY_UNTRACKED_FILES, untracked);
                        });
                    } catch (Exception e) {
                        System.err.println(Messages.warnHintVglUnreadable(repoRoot));
                    }
                }
            }
        }

        return true;
    }

    private static Set<String> findMissingPaths(Path repoRoot, Set<String> repoRelativePaths) {
        Set<String> missing = new LinkedHashSet<>();
        if (repoRoot == null || repoRelativePaths == null || repoRelativePaths.isEmpty()) {
            return missing;
        }
        for (String rel : repoRelativePaths) {
            if (rel == null || rel.isBlank()) {
                continue;
            }
            Path p = repoRoot.resolve(rel).normalize();
            if (!p.startsWith(repoRoot)) {
                // Defensive: ignore suspicious paths.
                continue;
            }
            if (!Files.exists(p)) {
                missing.add(rel);
            }
        }
        return missing;
    }
}
