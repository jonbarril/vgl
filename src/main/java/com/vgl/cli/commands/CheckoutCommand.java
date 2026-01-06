package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;

public class CheckoutCommand implements Command {
    @Override
    public String name() {
        return "checkout";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.checkoutUsage());
            return 0;
        }

        // checkout is remote-only; it copies a remote repo into the current working directory
        if (args.contains("-lr") || args.contains("-lb") || args.contains("-bb")) {
            System.err.println(Messages.checkoutUsage());
            return 1;
        }

        boolean force = ArgsHelper.hasFlag(args, "-f");

        // Checkout is remote-only; the target directory is always the current working directory.
        Path targetDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        String remoteUrl = ArgsHelper.valueAfterFlag(args, "-rr");
        if (remoteUrl == null || remoteUrl.isBlank()) {
            System.err.println(Messages.checkoutUsage());
            return 1;
        }

        String remoteBranch = ArgsHelper.valueAfterFlag(args, "-rb");
        if (args.contains("-rb") && (remoteBranch == null || remoteBranch.isBlank())) {
            remoteBranch = "main";
        }
        if (remoteBranch == null || remoteBranch.isBlank()) {
            remoteBranch = "main";
        }

        if (RepoUtils.isNestedUnderExistingRepo(targetDir) && !force) {
            if (!Utils.confirm(Messages.nestedRepoPrompt(RepoUtils.findNearestRepoRoot(targetDir)))) {
                System.out.println("Checkout cancelled.");
                return 0;
            }
        }

        if (Files.exists(targetDir) && Files.isDirectory(targetDir)) {
            try (var stream = Files.list(targetDir)) {
                if (stream.findAny().isPresent()) {
                    System.err.println("Error: Target directory is not empty: " + targetDir);
                    return 1;
                }
            }
        }

        Files.createDirectories(targetDir);

        try (Git ignored = Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(targetDir.toFile())
            .setBranch("refs/heads/" + remoteBranch)
            .call()) {
            // cloned
        }

        final String branchToUse = remoteBranch;
        final String remoteUrlToUse = remoteUrl;

        VglConfig.ensureGitignoreHasVgl(targetDir);
        VglConfig.writeProps(targetDir, props -> {
            props.setProperty(VglConfig.KEY_LOCAL_BRANCH, branchToUse);
            props.setProperty(VglConfig.KEY_REMOTE_URL, remoteUrlToUse);
            props.setProperty(VglConfig.KEY_REMOTE_BRANCH, branchToUse);
            var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
            branches.add(branchToUse);
            VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
        });

        System.out.println(Messages.checkoutCompleted(targetDir, remoteBranch));
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(targetDir, false);
        return 0;
    }
}
