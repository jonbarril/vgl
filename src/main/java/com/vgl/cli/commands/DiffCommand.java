package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DiffCommand implements Command {
    @Override public String name(){ return "diff"; }

    @Override public int run(List<String> args) throws Exception {
        boolean lb = args.contains("-lb");
        boolean rb = args.contains("-rb");
        List<String> paths = new ArrayList<>();
        for (String s : args) if (!s.equals("-lb") && !s.equals("-rb")) paths.add(s);

        try (Git git = Utils.openGit()) {
            if (git == null) return 0;
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");

            if (!lb && !rb) lb = true; // default local when not specified
            if (rb && remoteUrl == null) {
                System.out.println("No remote connected.");
                return 1;
            }

            // Local branch: show working tree changes
            if (lb && !rb) {
                Status st = git.status().call();
                Set<String> out = new LinkedHashSet<>();
                st.getChanged().forEach(p -> out.add("MODIFY " + p));
                st.getModified().forEach(p -> out.add("MODIFY " + p));
                st.getAdded().forEach(p -> out.add("ADD " + p));
                st.getRemoved().forEach(p -> out.add("DELETE " + p));
                st.getMissing().forEach(p -> out.add("DELETE " + p + " (missing)"));

                if (!paths.isEmpty()) {
                    out.removeIf(line -> {
                        String file = line.replaceFirst("^[A-Z]+\s+","");
                        boolean any = false;
                        for (String p : paths) if (file.equals(p)) { any = true; break; }
                        return !any;
                    });
                }
                out.forEach(System.out::println);
                return 0;
            }

            // Remote branch path (basic placeholder)
            if (rb) {
                System.out.println("(remote diff) compare with origin/" + git.getRepository().getBranch());
                return 0;
            }
        }
        return 0;
    }
}
