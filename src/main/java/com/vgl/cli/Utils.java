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
     * Uses compact format: section header on same line as first value.
     */
    public static void printSwitchState(com.vgl.cli.VglCli vgl) {
        String localDir = vgl.getLocalDir();
        String localBranch = vgl.getLocalBranch();
        String remoteUrl = vgl.getRemoteUrl();
        String remoteBranch = vgl.getRemoteBranch();
        String jumpLocalDir = vgl.getJumpLocalDir();
        String jumpLocalBranch = vgl.getJumpLocalBranch();
        
        String separator = " :: ";
        
        // LOCAL current
        System.out.println("LOCAL  " + localDir + separator + localBranch);
        
        // LOCAL jump
        if (jumpLocalDir != null && !jumpLocalDir.isEmpty()) {
            if (jumpLocalDir.equals(localDir)) {
                System.out.println("       (same)" + separator + jumpLocalBranch);
            } else {
                System.out.println("       " + jumpLocalDir + separator + jumpLocalBranch);
            }
        } else {
            System.out.println("       (none)" + separator + "(none)");
        }
        
        // REMOTE current
        if (remoteUrl != null && !remoteUrl.isEmpty()) {
            System.out.println("REMOTE " + remoteUrl + separator + remoteBranch);
        } else {
            System.out.println("REMOTE (none)" + separator + "(none)");
        }
        
        // REMOTE jump
        if (jumpLocalDir != null && !jumpLocalDir.isEmpty()) {
            if (jumpLocalDir.equals(localDir)) {
                System.out.println("       (same)" + separator + remoteBranch);
            } else {
                // Different directory - show same remote for now
                if (remoteUrl != null && !remoteUrl.isEmpty()) {
                    System.out.println("       " + remoteUrl + separator + remoteBranch);
                } else {
                    System.out.println("       (none)" + separator + "(none)");
                }
            }
        } else {
            System.out.println("       (none)" + separator + "(none)");
        }
    }
}
