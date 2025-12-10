package com.vgl.cli.commands;

import com.vgl.cli.Args;
import com.vgl.cli.RepoResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;

import java.nio.file.Paths;
import java.util.List;

public class PullCommand implements Command {
    @Override public String name(){ return "pull"; }

    @Override public int run(List<String> args) throws Exception {
        boolean force = Args.hasFlag(args, "-f");
        boolean dr = args.contains("-noop");
        if (dr) { System.out.println("(dry run) would pull from remote"); return 0; }
        try (Git git = RepoResolver.resolveGitRepoForCommand()) {
            if (git == null) {
                System.out.println("Warning: No local repository found in: " + 
                    Paths.get(".").toAbsolutePath().normalize());
                return 1;
            }
            
            // Check for uncommitted changes
            org.eclipse.jgit.api.Status status = git.status().call();
            boolean hasChanges = !status.getModified().isEmpty() || !status.getChanged().isEmpty() || 
                                !status.getAdded().isEmpty() || !status.getRemoved().isEmpty() || 
                                !status.getMissing().isEmpty();
            
            if (hasChanges) {
                System.out.println("Warning: You have uncommitted changes.");
                System.out.println("Pulling may cause conflicts or data loss:");
                status.getModified().forEach(f -> System.out.println("  M " + f));
                status.getChanged().forEach(f -> System.out.println("  M " + f));
                status.getAdded().forEach(f -> System.out.println("  A " + f));
                status.getRemoved().forEach(f -> System.out.println("  D " + f));
                status.getMissing().forEach(f -> System.out.println("  D " + f));
                System.out.println("Warning: You have uncommitted changes.");
                
                if (!force) {
                    System.out.print("Continue? (y/N): ");
                    
                    String response;
                    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                        response = scanner.nextLine().trim().toLowerCase();
                    }
                    
                    if (!response.equals("y") && !response.equals("yes")) {
                        System.out.println("Pull cancelled.");
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
