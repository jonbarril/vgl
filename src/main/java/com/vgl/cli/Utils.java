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
     * Print consistent switch state feedback message.
     * Format: "@ DIR :: BRANCH"
     */
    public static void printSwitchState(String dir, String branch) {
        System.out.println("@ " + dir + " :: " + branch);
    }

    /**
     * Print consistent jump state feedback message.
     * Format: "← DIR :: BRANCH"
     */
    public static void printJumpState(String dir, String branch) {
        System.out.println("← " + dir + " :: " + branch);
    }
}
