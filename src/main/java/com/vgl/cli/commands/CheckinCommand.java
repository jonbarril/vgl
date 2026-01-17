package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.GlobUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;

public class CheckinCommand implements Command {
    @Override
    public String name() {
        return "checkin";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.checkinUsage());
            return 0;
        }

        boolean draft = args.contains("-draft");
        boolean fin = args.contains("-final");
        if (draft == fin) {
            System.err.println(Messages.checkinUsage());
            return 1;
        }

        String message = ArgsHelper.valueAfterFlag(args, "-m");
        if (message == null || message.isBlank()) {
            message = draft ? "checkin (draft)" : "checkin (final)";
        }

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        List<String> globs = collectPositionals(args);

        try (Git git = GitUtils.openGit(repoRoot)) {
            // Stage everything (VGL has no staging), or limit to specified globs when provided.
            if (globs.isEmpty()) {
                git.add().addFilepattern(".").call();
                git.add().addFilepattern(".").setUpdate(true).call();
            } else {
                    List<String> files = GlobUtils.resolveGlobs(globs, repoRoot, System.out);
                for (String f : files) {
                    git.add().addFilepattern(f).call();
                }
                git.add().addFilepattern(".").setUpdate(true).call();
            }

            Status status = git.status().call();
            boolean hasStagedChanges = !status.getAdded().isEmpty()
                || !status.getChanged().isEmpty()
                || !status.getRemoved().isEmpty();

            if (!hasStagedChanges) {
                System.out.println("No changes to commit.");
                return 0;
            }

            PersonIdent ident = defaultIdent();
            git.commit().setMessage(message).setAuthor(ident).setCommitter(ident).call();
        }

        // Push to remote and (eventually) create a PR.
        int rc = new PushCommand().run(List.of());
        if (rc != 0) {
            return rc;
        }

        System.out.println(Messages.checkinCompleted(draft));
        return 0;
    }

    private static PersonIdent defaultIdent() {
        String name = System.getProperty("user.name");
        if (name == null || name.isBlank()) {
            name = "vgl";
        }
        String email = System.getProperty("user.email");
        if (email == null || email.isBlank()) {
            email = name + "@localhost";
        }
        return new PersonIdent(name, email);
    }

    private static List<String> collectPositionals(List<String> args) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }

        List<String> flagsWithValue = List.of("-m");
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null) {
                continue;
            }
            if (token.startsWith("-")) {
                if (flagsWithValue.contains(token)) {
                    i++;
                }
                continue;
            }
            out.add(token);
        }
        return out;
    }
}
