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

    public static Repository openNearestGitRepo(File start) throws IOException {
        FileRepositoryBuilder fb = new FileRepositoryBuilder();
        fb.findGitDir(start);
        return (fb.getGitDir()!=null) ? fb.build() : null;
    }

    public static Git openGit() throws IOException {
        Repository r = openNearestGitRepo(new File("."));
        if (r == null || r.isBare()) return null;
        return new Git(r);
    }

    public static Git openGit(File directory) throws IOException {
        FileRepositoryBuilder fb = new FileRepositoryBuilder();
        fb.findGitDir(directory);
        Repository repo = (fb.getGitDir() != null) ? fb.build() : null;
        if (repo == null || repo.isBare()) return null;
        return new Git(repo);
    }

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
        Path base = Paths.get(".").toAbsolutePath().normalize();
        Set<String> out = new LinkedHashSet<>();
        for (String g : globs) {
            if ("*".equals(g)) {
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
