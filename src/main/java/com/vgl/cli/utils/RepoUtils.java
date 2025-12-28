package com.vgl.cli.utils;

import com.vgl.cli.services.VglRepo;
import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class RepoUtils {
    private RepoUtils() {}

    /**
     * Checks for ancestor repo and warns user if nested. Returns false if user cancels.
     * Used by all commands that create a repo (create, checkout, etc).
     * @param dir Target directory for repo creation
     * @param force If true, skip prompt
     * @return true if allowed to proceed, false if user cancels
     */
    public static boolean checkAndWarnIfNestedRepo(Path dir, boolean force) {
        Path ancestorRepo = com.vgl.cli.utils.NestedRepoDetector.findAncestorRepo(dir);
        if (ancestorRepo != null) {
            boolean nestedOk = warnNestedRepo(dir, ancestorRepo, force);
            if (!nestedOk) {
                System.out.println("Create cancelled. No repository created.");
                return false;
            }
        }
        return true;
    }

    // ...existing static methods...
    public static java.nio.file.Path resolveEffectiveRepoRoot(com.vgl.cli.VglCli vgl, java.nio.file.Path cwd) {
        String localDir = null;
        try {
            localDir = vgl.getLocalDir();
        } catch (Exception ignored) {}
        if (localDir != null && !localDir.isBlank()) {
            return java.nio.file.Paths.get(localDir).toAbsolutePath().normalize();
        }
        return cwd.toAbsolutePath().normalize();
    }

    /**
     * Get the git repository root directory.
     *
     * @param startPath Directory to start searching from
     * @return Path to the repository root, or null if no repository found
     */
    public static Path getGitRepoRoot(Path startPath) throws IOException {
        return getGitRepoRoot(startPath, null);
    }

    /**
     * Get the git repository root directory with optional ceiling.
     *
     * @param startPath Directory to start searching from
     * @param ceilingDir Directory to stop searching at (null = no limit)
     * @return Path to the repository root, or null if no repository found
     */
    public static Path getGitRepoRoot(Path startPath, Path ceilingDir) throws IOException {
        if (ceilingDir == null) {
            try (Git git = findGitRepo(startPath, null)) {
                if (git == null) return null;
                return git.getRepository().getWorkTree().toPath();
            }
        }
        try (Git git = findGitRepo(startPath, ceilingDir)) {
            if (git == null) return null;
            return git.getRepository().getWorkTree().toPath();
        }
    }

    /**
     * Check if a directory is inside a nested git repository.
     *
     * @param startPath Directory to check
     * @return true if nested repo detected, false otherwise
     */
    public static boolean isNestedRepo(Path startPath) {
        return com.vgl.cli.utils.NestedRepoDetector.isNestedRepo(startPath);
    }

    /**
     * Warn about nested repository and prompt user for confirmation.
     * Used by commands that create repositories (create, checkout).
     *
     * @param targetDir The directory where the new repository will be created
     * @param parentRepo The parent repository path
     * @return true if user confirms to continue, false otherwise
     */
    public static boolean warnNestedRepo(Path targetDir, Path parentRepo, boolean force) {
        System.out.println(com.vgl.cli.utils.MessageConstants.MSG_NESTED_REPO_WARNING + parentRepo);
        System.out.flush();
        if (force) {
            return true;
        }
        if (!isInteractive()) {
            System.out.println("Non-interactive environment detected; cancelling create by default.");
            return false;
        }
        System.out.print("Continue? (y/N): ");
        String response = "";
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            if (scanner.hasNextLine()) {
                response = scanner.nextLine().trim().toLowerCase();
            }
        } catch (Exception e) {
            return false;
        }
        return response.equals("y") || response.equals("yes");
    }

    /**
     * Print consistent switch state showing LOCAL and REMOTE, current and jump.
     * Uses compact format with truncated paths (non-verbose).
     */
    public static void printSwitchState(com.vgl.cli.VglCli vgl) {
        printSwitchState(vgl, false);
    }

    public static void printSwitchState(com.vgl.cli.VglCli vgl, boolean verbose) {
        printSwitchState(vgl, verbose, false);
    }

    public static void printSwitchState(com.vgl.cli.VglCli vgl, boolean verbose, boolean veryVerbose) {
        String localDir = vgl.getLocalDir();
        String localBranch = vgl.getLocalBranch();
        String remoteUrl = vgl.getRemoteUrl();
        String remoteBranch = vgl.getRemoteBranch();
        try {
            com.vgl.cli.services.VglStateStore.VglState s = com.vgl.cli.services.VglStateStore.read();
            if (s != null) {
                if (s.localDir != null && !s.localDir.isBlank()) localDir = s.localDir;
                if (s.localBranch != null && !s.localBranch.isBlank()) localBranch = s.localBranch;
                if (s.remoteUrl != null && !s.remoteUrl.isBlank()) remoteUrl = s.remoteUrl;
                if (s.remoteBranch != null && !s.remoteBranch.isBlank()) remoteBranch = s.remoteBranch;
            }
        } catch (Exception ignored) {}
        String separator = " :: ";
        int maxPathLen = 35;
        java.util.function.BiFunction<String, Integer, String> truncatePath = (path, maxLen) -> {
            if (verbose || path.length() <= maxLen) return path;
            int leftLen = (maxLen - 3) / 2;
            int rightLen = maxLen - 3 - leftLen;
            return path.substring(0, leftLen) + "..." + path.substring(path.length() - rightLen);
        };
        String displayLocalDir = truncatePath.apply(localDir, maxPathLen);
        boolean hasRemote = (remoteUrl != null && !remoteUrl.isEmpty());
        String displayRemoteUrl = hasRemote ? truncatePath.apply(remoteUrl, maxPathLen) : "(none)";
        int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());
        System.out.println("LOCAL  " + padRight(displayLocalDir, maxLen) + separator + localBranch);
        if (veryVerbose && localDir != null && !localDir.isEmpty()) {
            System.out.println("  -- Branches:");
            boolean printedAny = false;
            try (Git git = Git.open(java.nio.file.Paths.get(localDir).toFile())) {
                java.util.List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                for (org.eclipse.jgit.lib.Ref ref : branches) {
                    String branchName = ref.getName().replaceFirst("refs/heads/", "");
                    if (branchName.equals(localBranch)) {
                        System.out.println("  * " + branchName);
                    } else {
                        System.out.println("    " + branchName);
                    }
                    printedAny = true;
                }
            } catch (Exception ignored) {}
            if (!printedAny) {
                String showBranch = (localBranch == null || localBranch.isEmpty()) ? "(none)" : localBranch;
                System.out.println("  * " + showBranch);
            }
        }
        String currentRemoteBranch = hasRemote ? remoteBranch : "(none)";
        System.out.println("REMOTE " + padRight(displayRemoteUrl, maxLen) + separator + currentRemoteBranch);
        if (veryVerbose && localDir != null && !localDir.isEmpty()) {
            System.out.println("  -- Branches:");
            boolean printedAnyRemote = false;
            if (hasRemote) {
                try (Git git = Git.open(java.nio.file.Paths.get(localDir).toFile())) {
                    java.util.List<org.eclipse.jgit.lib.Ref> remoteBranches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                    for (org.eclipse.jgit.lib.Ref ref : remoteBranches) {
                        String full = ref.getName();
                        String display = full.replaceFirst("refs/remotes/", "");
                        if (display.endsWith("/" + currentRemoteBranch)) {
                            System.out.println("  * " + display);
                        } else {
                            System.out.println("    " + display);
                        }
                        printedAnyRemote = true;
                    }
                } catch (Exception ignored) {}
            }
            if (!printedAnyRemote) {
                String showRemote = hasRemote && currentRemoteBranch != null ? currentRemoteBranch : "(none)";
                if (hasRemote) {
                    System.out.println("  * " + showRemote);
                } else {
                    System.out.println("  (no remote)");
                }
            }
        }
    }

    private static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    // isInteractive helper for warnNestedRepo
    public static boolean isInteractive() {
        if (Boolean.getBoolean("vgl.noninteractive")) {
            return false;
        }
        if (Boolean.getBoolean("vgl.force.interactive")) {
            return true;
        }
        try {
            String testBase = System.getProperty("vgl.test.base");
            if (testBase != null && !testBase.isEmpty()) return false;
        } catch (Exception ignored) {}
        boolean consolePresent = System.console() != null;
        return consolePresent;
    }

    /**
     * Returns true if the repository has at least one commit (HEAD is not unborn).
     */
    public static boolean hasCommits(org.eclipse.jgit.lib.Repository repo) {
        if (repo == null) return false;
        try {
            org.eclipse.jgit.lib.ObjectId head = repo.resolve("HEAD");
            return head != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Write a .vgl config file in the given repo root.
     * Overwrites any existing .vgl file.
     */
    public static void writeVglConfig(Path repoRoot, String branch, String remoteUrl, String remoteBranch) throws java.io.IOException {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("local.dir", repoRoot.toAbsolutePath().normalize().toString());
        if (branch != null && !branch.isBlank()) props.setProperty("local.branch", branch);
        if (remoteUrl != null && !remoteUrl.isBlank()) props.setProperty("remote.url", remoteUrl);
        if (remoteBranch != null && !remoteBranch.isBlank()) props.setProperty("remote.branch", remoteBranch);
        java.nio.file.Path savePath = repoRoot.resolve(".vgl");
        // Debug output removed for clean test output
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(savePath)) {
            props.store(out, "VGL configuration created by command");
        }
    }

    /**
     * Find git repository starting from a specific directory.
     * Searches upward from the start path to find .git directory.
     * Returns null silently if not found - caller decides how to handle.
     */
    public static Git findGitRepo(Path startPath) throws IOException {
        // Honor test ceiling when present so tests cannot search above the provided base.
        try {
            String testBase = System.getProperty("vgl.test.base");
            if (testBase != null && !testBase.isEmpty()) {
                Path ceiling = Paths.get(testBase).toAbsolutePath().normalize();
                Path cur = (startPath == null) ? Paths.get("").toAbsolutePath() : startPath.toAbsolutePath().normalize();
                while (cur != null) {
                    if (java.nio.file.Files.exists(cur.resolve(".git"))) {
                        try {
                            return Git.open(cur.toFile());
                        } catch (IOException e) {
                            return null;
                        }
                    }
                    if (cur.equals(ceiling)) break;
                    cur = cur.getParent();
                }
                return null;
            }
        } catch (Exception ignored) {}
        return com.vgl.cli.utils.GitUtils.findGitRepo(startPath);
    }

    public static Git findGitRepo(Path startPath, Path ceilingDir) throws IOException {
        return com.vgl.cli.utils.GitUtils.findGitRepo(startPath, ceilingDir);
    }

    public static Git findGitRepo() throws IOException {
        return findGitRepo(Paths.get("").toAbsolutePath(), null);
    }

    public static VglRepo findVglRepo(Path startPath) throws IOException {
        return findVglRepo(startPath, null);
    }

    public static VglRepo findVglRepo(Path startPath, Path ceilingDir) throws IOException {
        Git git = findGitRepo(startPath, ceilingDir);
        if (git == null) {
            return null;
        }
        Path repoRoot = git.getRepository().getWorkTree().toPath();
        Properties config = VglRepo.loadConfig(repoRoot);
        return new VglRepo(git, config);
    }

    public static VglRepo findVglRepo() throws IOException {
        return findVglRepo(Paths.get("").toAbsolutePath());
    }

    public static String normalizeToRepoPath(Path repoRoot, Path file) {
        Path rel = repoRoot.relativize(file);
        String s = rel.toString().replace('\\', '/');
        return s;
    }

    public static boolean isGitIgnored(Path path, org.eclipse.jgit.lib.Repository repository) {
        // Lightweight placeholder: callers should use JGit ignore checks when needed.
        return false;
    }

    public static boolean isInsideNestedRepo(Path repoRoot, Path path) {
        if (repoRoot == null || path == null) return false;
        try {
            Path cur = path.toAbsolutePath().normalize();
            Path root = repoRoot.toAbsolutePath().normalize();
            while (cur != null && !cur.equals(root)) {
                if (java.nio.file.Files.exists(cur.resolve(".git"))) return true;
                cur = cur.getParent();
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public static java.util.Set<String> listNestedRepos(Path repoRoot) {
        return com.vgl.cli.utils.GitUtils.listNestedRepos(repoRoot);
    }

    public static java.util.List<String> expandGlobsToFiles(java.util.List<String> globs, Path repoRoot, org.eclipse.jgit.lib.Repository repo) throws IOException {
        return com.vgl.cli.utils.IgnoreUtils.expandGlobsToFiles(globs, repoRoot, repo);
    }
}

