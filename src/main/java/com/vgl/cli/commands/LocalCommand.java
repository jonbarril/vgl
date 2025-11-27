package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalCommand implements Command {
    @Override
    public String name() {
        return "local";
    }

    @Override
    public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        String path = vgl.getLocalDir(); // Default to .vgl state
        String branch = vgl.getLocalBranch(); // Default to .vgl state

        // Parse arguments
        int bIndex = args.indexOf("-b");
        if (bIndex != -1 && bIndex + 1 < args.size()) {
            branch = args.get(bIndex + 1);
        }
        
        // Get path from first non-flag argument
        for (String arg : args) {
            if (!arg.equals("-b") && !arg.equals(branch)) {
                path = arg;
                break;
            }
        }

        // Fallback to current working directory and "main" branch if not set
        if (path == null || path.isBlank()) path = ".";
        if (branch == null || branch.isBlank()) branch = "main";
        
        final String finalBranch = branch;

        Path dir = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(dir.resolve(".git"))) {
            System.out.println("Warning: No local repository found in: " + dir);
            return 1;
        }

        @SuppressWarnings("resource")
        Git git = Git.open(dir.toFile());
        
        // Verify the branch exists (if any branches exist at all)
        List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
        if (!branches.isEmpty()) {
            boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + finalBranch));
            
            if (!branchExists) {
                git.close();
                System.out.println("Warning: Branch '" + finalBranch + "' does not exist in local repository: " + dir);
                System.out.println("Create the branch with: vgl create -b " + finalBranch);
                return 1;
            }
        }
        // If no branches exist (fresh repo), allow any branch name
        
        // Actually checkout the branch in Git
        if (!branches.isEmpty()) {
            git.checkout().setName(finalBranch).call();
        }
        
        git.close();
        
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(finalBranch);
        System.out.println("Switched to local repository: " + dir + " on branch '" + branch + "'.");
        return 0;
    }
}
