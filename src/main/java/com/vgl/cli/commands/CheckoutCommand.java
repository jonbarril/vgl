package com.vgl.cli.commands;

import java.nio.file.*;
import java.util.List;
import org.eclipse.jgit.api.Git;

public class CheckoutCommand implements Command {
    @Override public String name(){ return "checkout"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl checkout <url>[@branch] | -b <branch>"); return 1; }
        String spec = args.get(0);
        String branch = "main";
        int at = spec.indexOf('@');
        String url = spec;
        if (at >= 0) { url = spec.substring(0, at); branch = spec.substring(at+1); }
        String repoName = url.replaceAll(".*/","").replaceAll("\\.git$","");
        Path dir = Paths.get(repoName).toAbsolutePath().normalize();
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            System.out.println("Directory already exists and is not empty: " + dir);
            return 1;
        }
        try (Git git = Git.cloneRepository().setURI(url).setDirectory(dir.toFile()).setBranch("refs/heads/"+branch).setCloneAllBranches(true).call()) {
            System.out.println("Focused: " + dir + "@" + branch + " (connected to " + url + ")");
        }
        return 0;
    }
}
