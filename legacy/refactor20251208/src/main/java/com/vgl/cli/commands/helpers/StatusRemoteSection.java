package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Git;
import java.util.List;

public class StatusRemoteSection {
    /**
     * Prints the REMOTE section for status output.
     * @param git The JGit instance (may be null)
     * @param remoteUrl The remote URL (may be null)
     * @param remoteBranch The remote branch name (may be null)
     * @param hasRemote True if a remote is configured
     * @param verbose true if -v
     * @param veryVerbose true if -vv
     * @param labelPad The padded label (e.g. "REMOTE:   ")
     * @param separator The separator string (e.g. " :: ")
     * @param maxLen The max path length for truncation
     */
    public static void printRemoteSection(Git git, String remoteUrl, String remoteBranch, boolean hasRemote, boolean verbose, boolean veryVerbose, String labelPad, String separator, int maxLen) {
            // Debug output removed
        java.util.function.BiFunction<String, Integer, String> truncatePath = (path, maxL) -> {
            if (verbose || veryVerbose || path == null || path.length() <= maxL) return path;
            int leftLen = (maxL - 3) / 2;
            int rightLen = maxL - 3 - leftLen;
            return path.substring(0, leftLen) + "..." + path.substring(path.length() - rightLen);
        };
        String displayRemoteUrl = (remoteUrl != null && !remoteUrl.isBlank()) ? truncatePath.apply(remoteUrl, maxLen) : "(none)";
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            if (veryVerbose) {
                System.out.println(labelPad + remoteUrl + separator + (remoteBranch != null ? remoteBranch : "(none)"));
                // Branches subsection for REMOTE (only for -vv)
                try {
                    if (git != null && hasRemote) {
                        List<org.eclipse.jgit.lib.Ref> remoteBranches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call();
                        System.out.println("-- Remote Branches:");
                        String currentRemoteBranch = remoteBranch != null ? remoteBranch : "main";
                        for (org.eclipse.jgit.lib.Ref branch : remoteBranches) {
                            String name = branch.getName();
                            String shortName = name.replaceFirst("refs/remotes/origin/", "");
                            if (shortName.equals(currentRemoteBranch)) {
                                System.out.println("  * " + shortName + " (current)");
                            } else {
                                System.out.println("    " + shortName);
                            }
                        }
                    }
                } catch (Exception ignore) {}
            } else {
                System.out.println(labelPad + com.vgl.cli.utils.FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + (remoteBranch != null ? remoteBranch : "(none)"));
            }
        } else {
            // Always print REMOTE in the format (none) :: (none), aligned
            if (veryVerbose || verbose) {
                System.out.println(labelPad + "(none)" + separator + "(none)");
            } else {
                System.out.println(labelPad + com.vgl.cli.utils.FormatUtils.padRight("(none)", maxLen) + separator + "(none)");
            }
        }
    }
}
