package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jgit.api.Git;

public class TrackCommand implements Command {
    @Override
    public String name() {
        return "track";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args == null || args.isEmpty()) {
            System.err.println(Messages.trackUsage());
            return 1;
        }

        boolean all = args.size() == 1 && "-all".equals(args.get(0));

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        List<String> candidates;
        if (all) {
            candidates = computeUndecided(repoRoot);
            if (candidates.isEmpty()) {
                System.out.println(Messages.trackNothingToDo());
                return 0;
            }
        } else {
            candidates = GlobUtils.resolveGlobs(args, repoRoot, System.out);
            if (candidates.isEmpty()) {
                System.err.println(Messages.trackNoMatches());
                return 1;
            }
        }

        Set<String> nested = GitUtils.listNestedRepos(repoRoot);
        List<String> nestedRequested = new ArrayList<>();
        List<String> filtered = new ArrayList<>();
        for (String p : candidates) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String norm = p.replace('\\', '/');
            if (".vgl".equals(norm) || ".git".equals(norm)) {
                continue;
            }
            if (isInsideAnyNestedRepo(norm, nested)) {
                nestedRequested.add(norm);
                continue;
            }
            filtered.add(norm);
        }

        if (!nestedRequested.isEmpty()) {
            System.err.println(Messages.trackIgnoringNested(nestedRequested));
        }

        if (filtered.isEmpty()) {
            if (!nestedRequested.isEmpty()) {
                return 1;
            }
            System.err.println(Messages.trackNoMatches());
            return 1;
        }

        Properties props = VglConfig.readProps(repoRoot);
        Set<String> tracked = VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES);
        Set<String> untracked = VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES);

        List<String> actuallyTracked = new ArrayList<>();
        List<String> alreadyTracked = new ArrayList<>();

        for (String p : filtered) {
            if (tracked.contains(p)) {
                alreadyTracked.add(p);
                continue;
            }
            // Tracking overrides untracked.
            untracked.remove(p);
            tracked.add(p);
            actuallyTracked.add(p);
        }

        // Stage newly tracked files in Git so they become eligible for commit.
        if (!actuallyTracked.isEmpty()) {
            try (Git git = GitUtils.openGit(repoRoot)) {
                for (String p : actuallyTracked) {
                    try {
                        git.add().addFilepattern(p).call();
                    } catch (Exception e) {
                        // best-effort; still record config update
                        System.err.println(Messages.trackStageFailed(p, e.getMessage()));
                    }
                }
            }
        }

        VglConfig.setPathSet(props, VglConfig.KEY_TRACKED_FILES, tracked);
        VglConfig.setPathSet(props, VglConfig.KEY_UNTRACKED_FILES, untracked);
        VglConfig.writeProps(repoRoot, p -> {
            // overwrite with the updated snapshot
            p.clear();
            p.putAll(props);
        });

        if (!actuallyTracked.isEmpty()) {
            System.out.println(Messages.trackSuccess(actuallyTracked));
        }
        for (String p : alreadyTracked) {
            System.err.println(Messages.trackAlreadyTracked(p));
        }

        return alreadyTracked.isEmpty() ? 0 : 1;
    }

    private static boolean isInsideAnyNestedRepo(String relPath, Set<String> nestedRepos) {
        if (relPath == null || nestedRepos == null || nestedRepos.isEmpty()) {
            return false;
        }
        for (String n : nestedRepos) {
            if (n == null || n.isBlank()) {
                continue;
            }
            if (relPath.equals(n) || relPath.startsWith(n + "/")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> computeUndecided(Path repoRoot) throws Exception {
        // Spec: undecided is the set of non-ignored, non-nested files that are not in tracked/untracked.
        // Current implementation uses Git status untracked as the source of undecided.
        try (Git git = GitUtils.openGit(repoRoot)) {
            var status = git.status().call();
            Set<String> untracked = new LinkedHashSet<>(status.getUntracked());
            untracked.remove(".vgl");

            Set<String> nested = GitUtils.listNestedRepos(repoRoot);
            Properties props = VglConfig.readProps(repoRoot);
            Set<String> decidedTracked = VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES);
            Set<String> decidedUntracked = VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES);

            List<String> out = new ArrayList<>();
            for (String p : untracked) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                String norm = p.replace('\\', '/');
                if (decidedTracked.contains(norm) || decidedUntracked.contains(norm)) {
                    continue;
                }
                if (isInsideAnyNestedRepo(norm, nested)) {
                    continue;
                }
                out.add(norm);
            }
            out.sort(String::compareTo);
            return out;
        }
    }
}
