package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Git;

public class StatusLocalSection {
    public enum VerbosityLevel { NORMAL, VERBOSE, VERY_VERBOSE }

    /**
     * Prints the LOCAL section for status output.
     * @param git The JGit instance (may be null)
     * @param localDir The local repo directory (absolute path)
     * @param localBranch The current branch name
     * @param verbose true if -v
     * @param veryVerbose true if -vv
     * @param labelPad The padded label (e.g. "LOCAL:   ")
     * @param separator The separator string (e.g. " :: ")
     * @param maxLen The max path length for truncation
     */
    public static void printLocalSection(Git git, String localDir, String localBranch, boolean verbose, boolean veryVerbose, String labelPad, String separator, int maxLen) {
        java.util.function.BiFunction<String, Integer, String> truncatePath = (path, maxL) -> {
            if (verbose || veryVerbose || path.length() <= maxL) return path;
            int leftLen = (maxL - 3) / 2;
            int rightLen = maxL - 3 - leftLen;
            return path.substring(0, leftLen) + "..." + path.substring(path.length() - rightLen);
        };
        String displayLocalDir = truncatePath.apply(localDir, maxLen);
        if (veryVerbose || verbose) {
            System.out.println(labelPad + localDir + separator + (localBranch != null ? localBranch : "(none)"));
            // Branches subsection for LOCAL
            try {
                if (git != null) {
                    java.util.List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
                    System.out.println("-- Branches:"); // No indentation for subsection header
                    String currentBranch = git.getRepository().getBranch();
                    for (org.eclipse.jgit.lib.Ref branch : branches) {
                        String name = branch.getName();
                        String shortName = name.replaceFirst("refs/heads/", "");
                        if (shortName.equals(currentBranch)) {
                            System.out.println("  * " + shortName + " (current)"); // Indented content
                        } else {
                            System.out.println("    " + shortName); // Indented content
                        }
                    }
                }
            } catch (Exception ignore) {}
        } else {
            System.out.println(labelPad + com.vgl.cli.utils.FormatUtils.padRight(displayLocalDir, maxLen) + separator + (localBranch != null ? localBranch : "(none)"));
        }
    }
}
