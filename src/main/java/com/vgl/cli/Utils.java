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
        if (globs == null || globs.isEmpty()) return Collections.emptyList();
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Set<String> out = new LinkedHashSet<>();
        for (String g : globs) {
            if ("*".equals(g) || ".".equals(g)) {
                try (Stream<Path> s = Files.walk(base)) {
                    s.filter(Files::isRegularFile).forEach(p -> out.add(base.relativize(p).toString().replace('\\','/')));
                }
                continue;
            }
            final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
            try (Stream<Path> s = Files.walk(base)) {
                s.filter(Files::isRegularFile)
                 .map(p -> base.relativize(p))
                 .filter(p -> m.matches(p))
                 .forEach(p -> out.add(p.toString().replace('\\','/')));
            }
        }
        return new ArrayList<>(out);
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
