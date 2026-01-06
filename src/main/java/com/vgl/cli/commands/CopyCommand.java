package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StateChangeOutput;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;

/**
 * Copy a repository for local use.
 *
 * <p>Conceptually similar to {@code split} but operates on repos instead of branches.
 * This command never rewrites history; it performs a git clone from a local directory or remote URL.
 */
public class CopyCommand implements Command {
    @Override
    public String name() {
        return "copy";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.copyUsage());
            return 0;
        }

        boolean into = args.contains("-into");
        boolean from = args.contains("-from");
        if (into == from) {
            System.err.println(Messages.copyUsage());
            return 1;
        }

        boolean force = ArgsHelper.hasFlag(args, "-f");

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoDirArg = ArgsHelper.pathAfterFlag(args, "-lr");
        String remoteUrl = ArgsHelper.valueAfterFlag(args, "-rr");
        String remoteBranch = ArgsHelper.valueAfterFlag(args, "-rb");
        if (args.contains("-rb") && (remoteBranch == null || remoteBranch.isBlank())) {
            remoteBranch = "main";
        }
        if (remoteBranch == null || remoteBranch.isBlank()) {
            remoteBranch = "main";
        }

        final Path sourceDir;
        final String sourceUri;
        final Path destDir;

        if (into) {
            // Copy current repo into another directory.
            if (repoDirArg == null) {
                System.err.println(Messages.copyUsage());
                return 1;
            }
            sourceDir = cwd;
            destDir = repoDirArg.toAbsolutePath().normalize();
            sourceUri = sourceDir.toUri().toString();
        } else {
            // Copy from another repo (local or remote) into current directory.
            destDir = cwd;

            if (remoteUrl != null && !remoteUrl.isBlank()) {
                sourceDir = null;
                sourceUri = remoteUrl;
            } else if (repoDirArg != null) {
                sourceDir = repoDirArg.toAbsolutePath().normalize();
                sourceUri = sourceDir.toUri().toString();
            } else {
                System.err.println(Messages.copyUsage());
                return 1;
            }
        }

        if (RepoUtils.isNestedUnderExistingRepo(destDir) && !force) {
            if (!Utils.confirm(Messages.nestedRepoPrompt(RepoUtils.findNearestRepoRoot(destDir)))) {
                System.out.println("Copy cancelled.");
                return 0;
            }
        }

        if (Files.exists(destDir) && Files.isDirectory(destDir)) {
            try (var stream = Files.list(destDir)) {
                if (stream.findAny().isPresent()) {
                    System.err.println("Error: Target directory is not empty: " + Utils.formatPath(destDir));
                    return 1;
                }
            }
        }

        Files.createDirectories(destDir);

        try (Git ignored = Git.cloneRepository()
            .setURI(sourceUri)
            .setDirectory(destDir.toFile())
            .setBranch("refs/heads/" + remoteBranch)
            .call()) {
            // cloned
        }

        // For local use, do not configure a VGL remote.
        VglConfig.ensureGitignoreHasVgl(destDir);
        final String branchToUse = remoteBranch;
        VglConfig.writeProps(destDir, props -> {
            props.setProperty(VglConfig.KEY_LOCAL_BRANCH, branchToUse);
            props.setProperty(VglConfig.KEY_REMOTE_URL, "");
            props.setProperty(VglConfig.KEY_REMOTE_BRANCH, "");
            var branches = VglConfig.getStringSet(props, VglConfig.KEY_LOCAL_BRANCHES);
            branches.add(branchToUse);
            VglConfig.setStringSet(props, VglConfig.KEY_LOCAL_BRANCHES, branches);
        });

        System.out.println("Copied repository.");
        StateChangeOutput.printSwitchStateAndWarnIfNotCurrent(destDir, true);
        return 0;
    }
}
