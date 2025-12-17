package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.utils.RepoResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;

import java.util.List;

public class PullCommand implements Command {
    @Override public String name(){ return "pull"; }

    @Override public int run(List<String> args) throws Exception {
        boolean force = Args.hasFlag(args, "-f");
        boolean dr = args.contains("-noop");
        if (dr) {
            System.out.println("(dry run) would pull from remote");
            return 0;
        }
        com.vgl.cli.services.RepoResolution repoRes = RepoResolver.resolveForCommand();
        if (repoRes.getGit() == null) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() ||
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() ||
                                !status.getMissing().isEmpty();
            if (hasChanges) {
                System.err.println("Warning: You have uncommitted changes.");
                System.err.println("Pulling may cause conflicts or data loss:");
                status.getModified().forEach(f -> System.err.println("  M " + f));
                status.getChanged().forEach(f -> System.err.println("  M " + f));
                status.getAdded().forEach(f -> System.err.println("  A " + f));
                status.getRemoved().forEach(f -> System.err.println("  D " + f));
                status.getMissing().forEach(f -> System.err.println("  D " + f));
                System.err.println("Warning: You have uncommitted changes.");
                if (!force) {
                    System.err.print("Continue? (y/N): ");
                    String response;
                    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                        response = scanner.nextLine().trim().toLowerCase();
                    }
                    if (!response.equals("y") && !response.equals("yes")) {
                        System.err.println("Pull cancelled.");
                        return 0;
                    }
                }
            }
            PullResult r = git.pull().call();
            if (r.isSuccessful()) System.out.println("Pulled and merged.");
            else System.out.println("Pull had conflicts or failed.");
        }
        return 0;
    }
}
