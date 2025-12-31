package com.vgl.cli.commands.helpers;

import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Path;
import java.util.Properties;
import org.eclipse.jgit.api.Git;

/**
 * Prints the current switch state in the same compact format as `status` default mode
 * (non-verbose LOCAL and REMOTE sections).
 */
public final class SwitchStateOutput {
    private SwitchStateOutput() {}

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

        int maxPathLen = 35;
        String separator = " :: ";

        String displayLocalDir = FormatUtils.truncateMiddle(repoRoot.toString(), maxPathLen);
        String displayRemoteUrl = (remoteUrl != null && !remoteUrl.isBlank())
            ? FormatUtils.truncateMiddle(remoteUrl, maxPathLen)
            : "(none)";

        int maxLen = Math.max(displayLocalDir.length(), displayRemoteUrl.length());

        String localLabel = "LOCAL:";
        String remoteLabel = "REMOTE:";
        int labelWidth = Math.max(localLabel.length(), remoteLabel.length());

        String localLabelPad = FormatUtils.padRight(localLabel, labelWidth + 1);
        String remoteLabelPad = FormatUtils.padRight(remoteLabel, labelWidth + 1);

        System.out.println(localLabelPad + FormatUtils.padRight(displayLocalDir, maxLen) + separator + (localBranch != null ? localBranch : "(none)"));

        boolean hasRemote = remoteUrl != null && !remoteUrl.isBlank();
        if (!hasRemote) {
            System.out.println(remoteLabelPad + FormatUtils.padRight("(none)", maxLen) + separator + "(none)");
            return;
        }

        System.out.println(remoteLabelPad + FormatUtils.padRight(displayRemoteUrl, maxLen) + separator + (remoteBranch != null ? remoteBranch : "(none)"));
    }

}
