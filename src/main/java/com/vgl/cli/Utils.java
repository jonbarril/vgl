package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
//import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Utils {
    private Utils(){}

    /**
     * Returns true if the given file is ignored by git (.gitignore, etc).
     * Uses JGit's StatusCommand.
     */
    public static boolean isGitIgnored(Path file, org.eclipse.jgit.lib.Repository repo) {
        if (repo == null || file == null) return false;
        // Always treat the VGL configuration file as ignored regardless of .gitignore
        try {
            if (file.getFileName() != null && ".vgl".equals(file.getFileName().toString())) return true;
        } catch (Exception e) {
            // ignore and continue to JGit check
        }
        try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repo)) {
            Path repoRoot = repo.getWorkTree().toPath();
            String relPath = repoRoot.relativize(file.toAbsolutePath()).toString().replace('\\', '/');
            return git.status().call().getIgnoredNotInIndex().contains(relPath);
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================================
    // Standard Messages (for testing and consistency)
    // ============================================================================
    
    public static final String MSG_NO_REPO_PREFIX = "Error: No git repository found in: ";
    public static final String MSG_NO_REPO_HELP = "Initialize a repository with: vgl create";
    public static final String MSG_NO_REPO_WARNING_PREFIX = "Warning: No local repository found in: ";
    
    /**
     * Get the standard "no repository" error message for a given path.
     * Useful for tests that need to verify error messages.
     */
    public static String getNoRepoMessage(Path searchPath) {
        return MSG_NO_REPO_PREFIX + searchPath.toAbsolutePath().normalize() + "\n" + MSG_NO_REPO_HELP;
    }
    
    /**
     * Get the standard "no repository" warning message for a given path.
     * Used by commands that can proceed without a repo (like track).
     */
    public static String getNoRepoWarning(Path searchPath) {
        return MSG_NO_REPO_WARNING_PREFIX + searchPath.toAbsolutePath().normalize();
    }

    // ============================================================================
    // Core Repository Finding
    // ============================================================================
    
    /**
     * Find git repository starting from a specific directory.
     * Searches upward from the start path to find .git directory.
     * Returns null silently if not found - caller decides how to handle.
     * 
     * @param startPath Directory to start searching from
     * @return Git instance, or null if no repository found
     */
    public static Git findGitRepo(Path startPath) throws IOException {
        return findGitRepo(startPath, null);
    }
    
    /**
     * Find git repository with optional ceiling directory.
     * Package-private for testing - allows tests to prevent upward search beyond test directories.
     * 
     * @param startPath Directory to start searching from
     * @param ceilingDir Directory to stop searching at (null = no limit)
     * @return Git instance, or null if no repository found
     */
    static Git findGitRepo(Path startPath, Path ceilingDir) throws IOException {
        if (startPath == null) {
            return null;
        }
        File ceilingFile = (ceilingDir != null) ? ceilingDir.toFile() : null;
        Repository r = openNearestGitRepo(startPath.toFile(), ceilingFile);
        if (r == null || r.isBare()) return null;
        return new Git(r);
    }

    /**
     * Find git repository starting from current working directory.
     * Returns null silently if not found - caller decides how to handle.
     * 
     * @return Git instance, or null if no repository found
     */
    public static Git findGitRepo() throws IOException {
        return findGitRepo(currentDir());
    }

    /**
     * Find VGL repository (git + config) starting from a specific directory.
     * Returns null silently if not found - caller decides how to handle.
     * 
     * @param startPath Directory to start searching from
     * @return VglRepo instance, or null if no git repository found
     */
    public static VglRepo findVglRepo(Path startPath) throws IOException {
        return findVglRepo(startPath, null);
    }
    
    /**
     * Find VGL repository with optional ceiling directory.
     * Package-private for testing - allows tests to prevent upward search beyond test directories.
     * 
     * @param startPath Directory to start searching from
     * @param ceilingDir Directory to stop searching at (null = no limit)
     * @return VglRepo instance, or null if no git repository found
     */
    static VglRepo findVglRepo(Path startPath, Path ceilingDir) throws IOException {
        Git git = findGitRepo(startPath, ceilingDir);
        if (git == null) {
            return null;
        }
        
        Path repoRoot = git.getRepository().getWorkTree().toPath();
        Properties config = VglRepo.loadConfig(repoRoot);
        return new VglRepo(git, config);
    }

    /**
     * Find VGL repository (git + config) from current working directory.
     * Returns null silently if not found - caller decides how to handle.
     * 
     * @return VglRepo instance, or null if no git repository found
     */
    public static VglRepo findVglRepo() throws IOException {
        return findVglRepo(currentDir());
    }

    // ============================================================================
    // Repository Finding with Standard Error Messages
    // ============================================================================

    /**
     * Find git repository, printing standard error message if not found.
     * Use this in commands that require a repository.
     * 
     * @param startPath Directory to start searching from
     * @return Git instance, or null if not found (after printing error)
     */
    public static Git findGitRepoOrWarn(Path startPath) throws IOException {
        Git git = findGitRepo(startPath);
        if (git == null) {
            warnNoRepo(startPath);
        }
        return git;
    }

    /**
     * Find git repository from current directory, printing error if not found.
     * Use this in commands that require a repository.
     * 
     * @return Git instance, or null if not found (after printing error)
     */
    public static Git findGitRepoOrWarn() throws IOException {
        return findGitRepoOrWarn(currentDir());
    }

    /**
     * Find VGL repository (git + config), printing error if not found.
     * Use this in commands that require a repository.
     * 
     * @param startPath Directory to start searching from
     * @return VglRepo instance, or null if not found (after printing error)
     */
    public static VglRepo findVglRepoOrWarn(Path startPath) throws IOException {
        VglRepo repo = findVglRepo(startPath);
        if (repo == null) {
            warnNoRepo(startPath);
        }
        return repo;
    }

    /**
     * Find VGL repository from current directory, printing error if not found.
     * Use this in commands that require a repository.
     * 
     * @return VglRepo instance, or null if not found (after printing error)
     */
    public static VglRepo findVglRepoOrWarn() throws IOException {
        return findVglRepoOrWarn(currentDir());
    }

    // ============================================================================
    // Repository Information Utilities
    // ============================================================================

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
     * Package-private for testing.
     * 
     * @param startPath Directory to start searching from
     * @param ceilingDir Directory to stop searching at (null = no limit)
     * @return Path to the repository root, or null if no repository found
     */
    static Path getGitRepoRoot(Path startPath, Path ceilingDir) throws IOException {
        try (Git git = findGitRepo(startPath, ceilingDir)) {
            if (git == null) {
                return null;
            }
            return git.getRepository().getWorkTree().toPath();
        }
    }

    /**
     * Get the git repository root directory from current working directory.
     * 
     * @return Path to the repository root, or null if no repository found
     */
    public static Path getGitRepoRoot() throws IOException {
        return getGitRepoRoot(currentDir());
    }

    /**
     * Check if a directory is inside a nested git repository.
     * This detects when startPath is in a repo that's nested inside another repo.
     * 
     * @param startPath Directory to check
     * @return true if nested repo detected, false otherwise
     */
    public static boolean isNestedRepo(Path startPath) throws IOException {
        return isNestedRepo(startPath, null);
    }
    
    /**
     * Check if a directory is inside a nested git repository with optional ceiling.
     * Package-private for testing.
     * 
     * @param startPath Directory to check
     * @param ceilingDir Directory to stop searching at (null = no limit)
     * @return true if nested repo detected, false otherwise
     */
    static boolean isNestedRepo(Path startPath, Path ceilingDir) throws IOException {
        if (startPath == null) {
            return false;
        }
        
        Path firstRepoRoot = getGitRepoRoot(startPath, ceilingDir);
        if (firstRepoRoot == null) {
            return false;
        }
        
        Path parentSearch = firstRepoRoot.getParent();
        if (parentSearch == null) {
            return false;
        }
        
        Path outerRepoRoot = getGitRepoRoot(parentSearch, ceilingDir);
        return outerRepoRoot != null;
    }

    /**
     * Return true if the current environment should be treated as interactive.
     * Tests can force non-interactive mode by setting system property
     * `vgl.noninteractive=true`.
     */
    public static boolean isInteractive() {
        // If explicitly forced non-interactive, honor that first
        if (Boolean.getBoolean("vgl.noninteractive")) {
            return false;
        }
        boolean consolePresent = System.console() != null;
        if (Boolean.getBoolean("vgl.debug")) {
            System.err.println("[vgl.debug] isInteractive: vgl.noninteractive=" + Boolean.getBoolean("vgl.noninteractive") + ", consolePresent=" + consolePresent);
        }
        return consolePresent;
    }

    /**
     * Prints a warning message about creating a nested repository.
     * @param targetDir The directory where the new repository will be created
     * @param parentRepo The parent repository root
     */
    /**
     * Warn about nested repository and prompt user for confirmation.
     * Used by commands that create repositories (create, checkout).
     * 
     * @param targetDir The directory where the new repository will be created
     * @param parentRepo The parent repository path
     * @return true if user confirms to continue, false otherwise
     */
    public static boolean warnNestedRepo(Path targetDir, Path parentRepo) {
        System.out.println("Warning: Creating a repository inside another Git repository.");
        System.out.println("This will create a nested repository and may cause confusion.");
        System.out.println("Parent repository: " + parentRepo);
        System.out.println("New repository: " + targetDir);
        System.out.println();
        System.out.print("Continue? (y/N): ");
        
        String response;
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            response = scanner.nextLine().trim().toLowerCase();
        }
        
        return response.equals("y") || response.equals("yes");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private static Path currentDir() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static void warnNoRepo(Path searchPath) {
        System.out.println(MSG_NO_REPO_PREFIX + searchPath.toAbsolutePath().normalize());
        System.out.println(MSG_NO_REPO_HELP);
    }

    static Repository openNearestGitRepo(File start) throws IOException {
        return openNearestGitRepo(start, null);
    }
    
    static Repository openNearestGitRepo(File start, File ceiling) throws IOException {
        FileRepositoryBuilder fb = new FileRepositoryBuilder();
        if (ceiling != null) {
            fb.addCeilingDirectory(ceiling);
        }
        fb.findGitDir(start);
        return (fb.getGitDir()!=null) ? fb.build() : null;
    }

    // ============================================================================
    // Other Utilities
    // ============================================================================

    public static String versionFromRuntime() {
        Package p = Utils.class.getPackage();
        String v = (p != null) ? p.getImplementationVersion() : null;
        if (v != null && !v.isBlank()) return v;
        String sys = System.getProperty("vgl.version");
        if (sys != null && !sys.isBlank()) return sys;
        return "MVP";
    }

    public static List<String> expandGlobs(List<String> globs) throws IOException {
        // Prefer repo-root-relative expansion when possible; fall back to CWD-based expansion
        try {
            Path repoRoot = getGitRepoRoot(currentDir());
            if (repoRoot != null) return expandGlobs(globs, repoRoot);
        } catch (Exception ignored) {}
        return expandGlobs(globs, null);
    }

    /**
     * Expand globs relative to an explicit repo root (if provided). Returns paths
     * relative to the repo root (or base) using '/' separators.
     */
    public static List<String> expandGlobs(List<String> globs, Path repoRoot) throws IOException {
        if (globs == null || globs.isEmpty()) return Collections.emptyList();

        // If we have a repo root and can open the repository, delegate to the
        // repo-aware implementation that filters nested repos and ignored files.
        if (repoRoot != null) {
            try {
                Git git = findGitRepo(repoRoot);
                if (git != null) {
                    return expandGlobsToFiles(globs, repoRoot.toAbsolutePath().normalize(), git.getRepository());
                }
            } catch (Exception ignored) {}
        }

        Path base = (repoRoot != null) ? repoRoot.toAbsolutePath().normalize() : currentDir();
        Set<String> out = new LinkedHashSet<>();
        for (String g : globs) {
            if ("*".equals(g) || ".".equals(g)) {
                try (Stream<Path> s = Files.walk(base)) {
                    s.filter(p -> Files.isRegularFile(p)).forEach(p -> out.add(base.relativize(p).toString().replace('\\','/')));
                }
                continue;
            }
            final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
            try (Stream<Path> s = Files.walk(base)) {
                s.map(p -> base.relativize(p))
                 .filter(p -> m.matches(p))
                 .forEach(p -> out.add(p.toString().replace('\\','/')));
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Resolve an effective repo root for callers. Preference order:
     * 1) explicit `VglCli` configuration (if provided and configurable)
     * 2) persisted VGL state file (user-level)
     * 3) git repo root discovered from `cwd`
     * 4) fallback to `cwd`
     */
    public static Path resolveEffectiveRepoRoot(VglCli vgl, Path cwd) {
        try {
            if (vgl != null && vgl.isConfigurable()) {
                String ld = vgl.getLocalDir();
                if (ld != null && !ld.isBlank()) return Paths.get(ld).toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {}

        try {
            VglStateStore.VglState s = VglStateStore.read();
            if (s != null && s.localDir != null && !s.localDir.isBlank()) {
                return Paths.get(s.localDir).toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {}

        try {
            Path start = (cwd != null) ? cwd : currentDir();
            Path repoRoot = getGitRepoRoot(start);
            if (repoRoot != null) return repoRoot;
        } catch (Exception ignored) {}

        return (cwd != null) ? cwd.toAbsolutePath().normalize() : currentDir();
    }

    /**
     * Return the set of non-ignored regular files under the repository working tree.
     * Paths are returned relative to the repo root and use '/' separators.
     */
    public static java.util.Set<String> listNonIgnoredFiles(Path repoRoot, org.eclipse.jgit.lib.Repository repo) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (repo == null || repoRoot == null) return out;
        try {
            org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(repo);
            org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repo);
            treeWalk.addTree(workingTreeIt);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                org.eclipse.jgit.treewalk.WorkingTreeIterator wti = (org.eclipse.jgit.treewalk.WorkingTreeIterator) treeWalk.getTree(0, org.eclipse.jgit.treewalk.WorkingTreeIterator.class);
                String path = treeWalk.getPathString();
                if (wti == null) continue;
                try {
                    if (!wti.isEntryIgnored()) {
                        out.add(path.replace('\\','/'));
                    }
                } catch (Exception e) {
                    // ignore and continue
                }
            }
            treeWalk.close();
        } catch (Exception e) {
            // fallback: nothing
        }
        return out;
    }

    /**
     * Return true if the repository has at least one commit (i.e., HEAD is resolvable).
     * This makes it explicit that some repos may be "unborn" (no commits yet).
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
     * Return true if the given path is inside a nested repository (a directory that contains its own .git).
     * The search stops at repoRoot; paths equal to repoRoot are not considered nested.
     */
    public static boolean isInsideNestedRepo(Path repoRoot, Path path) {
        if (repoRoot == null || path == null) return false;
        try {
            Path cur = path.toAbsolutePath().normalize();
            Path root = repoRoot.toAbsolutePath().normalize();
            while (cur != null && !cur.equals(root)) {
                if (Files.exists(cur.resolve(".git"))) return true;
                cur = cur.getParent();
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * Return a set of repository-relative paths (no trailing slash) for directories
     * under repoRoot that contain their own .git directory (nested repositories).
     */
    public static java.util.Set<String> listNestedRepos(Path repoRoot) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (repoRoot == null) return out;
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(repoRoot)) {
            s.filter(java.nio.file.Files::isDirectory).forEach(d -> {
                try {
                    if (java.nio.file.Files.exists(d.resolve(".git"))) {
                        Path rel = repoRoot.toAbsolutePath().normalize().relativize(d.toAbsolutePath().normalize());
                        String r = rel.toString().replace('\\','/');
                        if (!r.isEmpty()) out.add(r);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            // ignore
        }
        return out;
    }

    /**
     * Expand globs into a list of regular files relative to repoRoot.
     * - Expands patterns (glob syntax) relative to repoRoot
     * - Expands directories into their contained regular files
     * - Excludes files inside nested repositories
     * - Excludes git-ignored files
     * Returns repo-relative paths using '/' separators.
     */
    public static java.util.List<String> expandGlobsToFiles(java.util.List<String> globs, Path repoRoot, org.eclipse.jgit.lib.Repository repo) throws IOException {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (globs == null || globs.isEmpty()) return new java.util.ArrayList<>();
        Path base = repoRoot.toAbsolutePath().normalize();

        for (String g : globs) {
            if ("*".equals(g) || ".".equals(g)) {
                try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(base)) {
                    s.filter(java.nio.file.Files::isRegularFile).forEach(p -> {
                        try {
                            if (isInsideNestedRepo(base, p)) return;
                            if (isGitIgnored(p, repo)) return;
                            Path rel = base.relativize(p);
                            // Exclude internal git directory contents
                            String rels = rel.toString().replace('\\','/');
                            if (rels.startsWith(".git/") || ".git".equals(rels)) return;
                            out.add(rel.toString().replace('\\','/'));
                        } catch (Exception ignored) {}
                    });
                }
                continue;
            }

            final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
            try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(base)) {
                s.forEach(p -> {
                    try {
                        Path rel = base.relativize(p);
                        if (!m.matches(rel)) return;
                        if (java.nio.file.Files.isDirectory(p)) {
                            // expand directory
                            try (java.util.stream.Stream<Path> sub = java.nio.file.Files.walk(p)) {
                                sub.filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                                    try {
                                        if (isInsideNestedRepo(base, f)) return;
                                        if (isGitIgnored(f, repo)) return;
                                        Path r2 = base.relativize(f);
                                        out.add(r2.toString().replace('\\','/'));
                                    } catch (Exception ignored) {}
                                });
                            }
                        } else if (java.nio.file.Files.isRegularFile(p)) {
                            if (isInsideNestedRepo(base, p)) return;
                            if (isGitIgnored(p, repo)) return;
                            String rels = rel.toString().replace('\\','/');
                            // Exclude files under .git
                            if (rels.startsWith(".git/") || ".git".equals(rels)) return;
                            out.add(rels);
                        }
                    } catch (Exception ignored) {}
                });
            }
        }

        return new java.util.ArrayList<>(out);
    }

    /**
     * Print consistent switch state showing LOCAL and REMOTE, current and jump.
     * Uses compact format with truncated paths (non-verbose).
     */
    public static void printSwitchState(com.vgl.cli.VglCli vgl) {
        printSwitchState(vgl, false);
    }

    /**
     * Print consistent switch state showing LOCAL and REMOTE, current and jump.
     * Uses compact format: section header on same line as first value.
     * @param vgl The VglCli instance
     * @param verbose If true, show full paths; if false, truncate with ellipsis
     */
    public static void printSwitchState(com.vgl.cli.VglCli vgl, boolean verbose) {
        String localDir = vgl.getLocalDir();
        String localBranch = vgl.getLocalBranch();
        String remoteUrl = vgl.getRemoteUrl();
        String remoteBranch = vgl.getRemoteBranch();
        String jumpLocalDir = vgl.getJumpLocalDir();
        String jumpLocalBranch = vgl.getJumpLocalBranch();
        String jumpRemoteUrl = vgl.getJumpRemoteUrl();
        String jumpRemoteBranch = vgl.getJumpRemoteBranch();
        
        String separator = " :: ";
        int maxPathLen = 35;
        
        // Truncation helper
        java.util.function.BiFunction<String, Integer, String> truncatePath = (path, maxLen) -> {
            if (verbose || path.length() <= maxLen) return path;
            int leftLen = (maxLen - 3) / 2;
            int rightLen = maxLen - 3 - leftLen;
            return path.substring(0, leftLen) + "..." + path.substring(path.length() - rightLen);
        };
        
        // Calculate display strings for LOCAL
        String displayLocalDir = truncatePath.apply(localDir, maxPathLen);
        String displayJumpDir = "(none)";
        if (jumpLocalDir != null && !jumpLocalDir.isEmpty()) {
            if (jumpLocalDir.equals(localDir)) {
                displayJumpDir = "(same)";
            } else {
                displayJumpDir = truncatePath.apply(jumpLocalDir, maxPathLen);
            }
        }
        
        // Calculate display strings for REMOTE
        boolean hasRemote = (remoteUrl != null && !remoteUrl.isEmpty());
        boolean hasJumpRemote = (jumpRemoteUrl != null && !jumpRemoteUrl.isEmpty());
        
        String displayRemoteUrl = hasRemote ? truncatePath.apply(remoteUrl, maxPathLen) : "(none)";
        
        String displayJumpRemote = "(none)";
        if (hasJumpRemote) {
            // Only show "(same)" if current also has a remote and they match
            if (hasRemote && jumpRemoteUrl.equals(remoteUrl)) {
                displayJumpRemote = "(same)";
            } else {
                displayJumpRemote = truncatePath.apply(jumpRemoteUrl, maxPathLen);
            }
        }
        // else: no jump remote, display stays "(none)"
        
        // Find longest path/URL for alignment
        int maxLen = Math.max(
            Math.max(displayLocalDir.length(), displayJumpDir.length()),
            Math.max(displayRemoteUrl.length(), displayJumpRemote.length())
        );
        
        // Print with padding to align separators
        System.out.println("LOCAL  " + padRight(displayLocalDir, maxLen) + separator + localBranch);
        
        String jumpBranch = (jumpLocalBranch != null && !jumpLocalBranch.isEmpty()) ? jumpLocalBranch : "(none)";
        System.out.println("       " + padRight(displayJumpDir, maxLen) + separator + jumpBranch);
        // If very verbose, list local branches immediately under LOCAL for clarity
        if (verbose && vgl.isConfigurable()) {
            try (Git git = Git.open(Paths.get(vgl.getLocalDir()).toFile())) {
                List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                if (!branches.isEmpty()) {
                    for (org.eclipse.jgit.lib.Ref ref : branches) {
                        String branchName = ref.getName().replaceFirst("refs/heads/", "");
                        if (branchName.equals(localBranch)) {
                            System.out.println("  * " + branchName);
                        } else {
                            System.out.println("    " + branchName);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // REMOTE: if no remote URL, branch must be (none)
        String currentRemoteBranch = hasRemote ? remoteBranch : "(none)";
        System.out.println("REMOTE " + padRight(displayRemoteUrl, maxLen) + separator + currentRemoteBranch);

        // REMOTE jump: if no jump remote URL, branch must be (none)
        String jumpRemoteBranchDisplay = hasJumpRemote ? jumpRemoteBranch : "(none)";
        System.out.println("       " + padRight(displayJumpRemote, maxLen) + separator + jumpRemoteBranchDisplay);
    }
    
    /**
     * Pad string with spaces on the right to reach target length.
     */
    private static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

}
