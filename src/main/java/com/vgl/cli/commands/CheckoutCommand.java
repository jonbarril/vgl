package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CheckoutCommand implements Command {
    @Override public String name(){ return "checkout"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println("Usage: vgl checkout <url> -b <branch>");
            return 1;
        }

        // Parse arguments
        String branch = "main";
        int bIndex = args.indexOf("-b");
        if (bIndex != -1 && bIndex + 1 < args.size()) {
            branch = args.get(bIndex + 1);
        }
        
        // Get URL from first non-flag argument
        String url = null;
        for (String arg : args) {
            if (!arg.equals("-b") && !arg.equals(branch)) {
                url = arg;
                break;
            }
        }
        
        if (url == null) {
            System.out.println("Usage: vgl checkout <url> -b <branch>");
            return 1;
        }

        String repoName = url.replaceAll(".*/", "").replaceAll("\\.git$", "");
        Path dir = Paths.get(repoName).toAbsolutePath().normalize();
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            System.out.println("Directory already exists and is not empty: " + dir);
            return 1;
        }

        Git git = Git.cloneRepository().setURI(url).setDirectory(dir.toFile()).setBranch("refs/heads/" + branch).call();
        git.close();
        VglCli vgl = new VglCli();
        vgl.setLocalDir(dir.toString());
        vgl.setLocalBranch(branch);
        vgl.setRemoteUrl(url);
        vgl.setRemoteBranch(branch);
        System.out.println("Cloned remote repository: " + url + " to local directory: " + dir + " on branch '" + branch + "'.");
        return 0;
    }
}
