package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.Properties;
import org.eclipse.jgit.api.Git;

/**
 * Prints the current switch state in the same compact format as `status` CONTEXT section.
 */
public final class SwitchStateOutput {
    private SwitchStateOutput() {}

    public static java.util.List<String> formatLines(
        String localDir,
        String localBranch,
        String remoteUrl,
        String remoteBranch,
        int labelWidth,
        boolean truncatePaths
    ) {
        int maxPathLen = 35;
        String separator = " :: ";

        String localDirSafe = (localDir != null) ? localDir : "(unknown)";
        String remoteUrlSafe = (remoteUrl != null) ? com.vgl.cli.utils.FormatUtils.normalizeRemoteUrlForDisplay(remoteUrl) : "";

        String displayLocalDir = truncatePaths ? FormatUtils.truncateMiddle(localDirSafe, maxPathLen) : localDirSafe;

        boolean hasRemote = remoteUrlSafe != null && !remoteUrlSafe.isBlank();
        String displayRemoteUrl = hasRemote
            ? (truncatePaths ? FormatUtils.truncateMiddle(remoteUrlSafe, maxPathLen) : remoteUrlSafe)
            : "(none)";

        int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());

        int contextLabelWidth = Math.max("Local:".length(), "Remote:".length());
        String localLabelPad = FormatUtils.padRight("Local:", contextLabelWidth + 1);
        String remoteLabelPad = FormatUtils.padRight("Remote:", contextLabelWidth + 1);

        String localBranchSafe = (localBranch != null && !localBranch.isBlank()) ? localBranch : "(none)";
        String remoteBranchSafe = hasRemote
            ? ((remoteBranch != null && !remoteBranch.isBlank()) ? remoteBranch : "(none)")
            : "(none)";

        String localLine = "  " + localLabelPad + FormatUtils.padRight(displayLocalDir, maxLen) + separator + localBranchSafe;
        String remoteLine = "  " + remoteLabelPad + FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + remoteBranchSafe;

        return java.util.List.of("CONTEXT:", localLine, remoteLine);
    }

    public static void print(Path repoRoot) throws Exception {
        if (repoRoot == null) {
            return;
        }

        Properties vglProps = VglConfig.readProps(repoRoot);
        String vglLocalBranch = vglProps.getProperty("local.branch", "main");
        String remoteUrl = vglProps.getProperty("remote.url", "");
        String remoteBranch = vglProps.getProperty("remote.branch", "main");

        String localBranch;
        try (Git git = GitUtils.openGit(repoRoot)) {
            String gitBranch;
            try {
                gitBranch = git.getRepository().getBranch();
            } catch (Exception e) {
                gitBranch = null;
            }
            localBranch = (gitBranch != null && !gitBranch.isBlank()) ? gitBranch : vglLocalBranch;
        }

        int labelWidth = Math.max("Local:".length(), "Remote:".length());
        for (String line : formatLines(Utils.formatPath(repoRoot), localBranch, remoteUrl, remoteBranch, labelWidth, true)) {
            System.out.println(line);
        }
    }

}
