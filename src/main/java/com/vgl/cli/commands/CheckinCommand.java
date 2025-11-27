package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;

import java.nio.file.Paths;
import java.util.List;

public class CheckinCommand implements Command {
    @Override public String name(){ return "checkin"; }

    @Override public int run(List<String> args) throws Exception {
        boolean draft = args.contains("-draft");
        boolean fin = args.contains("-final");
        if (!draft && !fin) { System.out.println("Usage: vgl checkin -draft|-final"); return 1; }
        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("Warning: No Git repository found in: " + 
                    Paths.get(".").toAbsolutePath().normalize());
                return 1;
            }
            String branch = git.getRepository().getBranch();
            String url = git.getRepository().getConfig().getString("remote","origin","url");
            if (url != null && url.contains("github.com")) {
                String name = url.replaceFirst(".*github.com[:/]", "").replaceAll("\\.git$", "");
                System.out.println("Open your PR: https://github.com/" + name + "/compare/main..." + branch + "?expand=1" + (draft ? " (draft)" : ""));
            } else {
                System.out.println("Remote is not GitHub; open a PR in your provider.");
            }
        }
        return 0;
    }
}
