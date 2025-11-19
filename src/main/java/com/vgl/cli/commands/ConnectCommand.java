package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.util.List;

public class ConnectCommand implements Command {
    @Override public String name(){ return "connect"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) { System.out.println("Usage: vgl connect <url>[@branch] | -b <branch>"); return 1; }
        String spec = args.get(0);
        String branch = "main";
        int at = spec.indexOf('@');
        String url = spec;
        if (at >= 0) { url = spec.substring(0, at); branch = spec.substring(at+1); }
        if ("-b".equals(spec) && args.size() >= 2) { branch = args.get(1); url = null; }

        try (Git git = Utils.openGit()) {
            if (git == null) { System.out.println("No focused repo. Use `vgl focus <path>` or run inside a Git repo."); return 1; }
            StoredConfig cfg = git.getRepository().getConfig();
            if (url != null) cfg.setString("remote","origin","url",url);
            cfg.setString("branch",branch,"remote","origin");
            cfg.setString("branch",branch,"merge","refs/heads/"+branch);
            cfg.save();
        }
        return 0;
    }
}
