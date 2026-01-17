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

public class UntrackCommand implements Command {
    @Override
    public String name() {
        return "untrack";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args == null || args.isEmpty()) {
            System.err.println(Messages.untrackUsage());
            return 1;
        }

        boolean all = args.size() == 1 && "-all".equals(args.get(0));

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        Properties props = VglConfig.readProps(repoRoot);
        Set<String> tracked = VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES);
        Set<String> decidedUntracked = VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES);

        List<String> requested;
        if (all) {
            requested = new ArrayList<>(tracked);
            requested.sort(String::compareTo);
            if (requested.isEmpty()) {
                System.out.println(Messages.untrackNothingToDo());
                return 0;
            }
        } else {
            requested = GlobUtils.resolveGlobs(args, repoRoot, System.out);
            if (requested.isEmpty()) {
                // Try to give per-arg errors for literal requests.
                for (String a : args) {
                    if (a == null || a.isBlank() || a.startsWith("-")) {
                        continue;
                    }
                    System.err.println(Messages.untrackNotTracked(a));
                }
                return 1;
            }
        }

        Set<String> nested = GitUtils.listNestedRepos(repoRoot);
        List<String> nestedRequested = new ArrayList<>();
        List<String> filtered = new ArrayList<>();
        for (String p : requested) {
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
            System.err.println(Messages.untrackNestedError(nestedRequested));
            if (filtered.isEmpty()) {
                return 1;
            }
        }

        List<String> actuallyUntracked = new ArrayList<>();
        List<String> notTracked = new ArrayList<>();

        for (String p : filtered) {
            if (!tracked.contains(p)) {
                notTracked.add(p);
                continue;
            }
            tracked.remove(p);
            decidedUntracked.add(p);
            actuallyUntracked.add(p);
        }

        // Remove from Git index without deleting the working file.
        if (!actuallyUntracked.isEmpty()) {
            try (Git git = GitUtils.openGit(repoRoot)) {
                for (String p : actuallyUntracked) {
                    try {
                        git.rm().addFilepattern(p).setCached(true).call();
                    } catch (Exception e) {
                        // best-effort; still record config update
                        System.err.println(Messages.untrackIndexFailed(p, e.getMessage()));
                    }
                }
            }
        }

        VglConfig.setPathSet(props, VglConfig.KEY_TRACKED_FILES, tracked);
        VglConfig.setPathSet(props, VglConfig.KEY_UNTRACKED_FILES, decidedUntracked);
        VglConfig.writeProps(repoRoot, p -> {
            p.clear();
            p.putAll(props);
        });

        if (!actuallyUntracked.isEmpty()) {
            System.out.println(Messages.untrackSuccess(actuallyUntracked));
        }
        for (String p : notTracked) {
            System.err.println(Messages.untrackNotTracked(p));
        }

        return notTracked.isEmpty() ? 0 : 1;
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
}
