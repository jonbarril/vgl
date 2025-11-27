package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DiffCommand implements Command {
    @Override public String name(){ return "diff"; }

    @Override public int run(List<String> args) throws Exception {
        boolean lb = args.contains("-lb");
        boolean rb = args.contains("-rb");
        List<String> filters = new ArrayList<>();
        for (String s : args) if (!s.equals("-lb") && !s.equals("-rb")) filters.add(s);

        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("Warning: No local repository found in: " + 
                    Paths.get(".").toAbsolutePath().normalize());
                return 1;
            }
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

                if (!filters.isEmpty()) {
                    out.removeIf(line -> {
                        String file = line.replaceFirst("^[A-Z]+\\s+","");
                        // Check if any filter matches this file
                        boolean matches = false;
                        for (String filter : filters) {
                            // Check if it's a commit ID - if so, ignore for diff (commits show changes)
                            if (filter.matches("[0-9a-f]{7,40}")) {
                                // For commit IDs in diff, we'd need to show diff between commits
                                // For now, skip this filter
                                continue;
                            }
                            // Support glob patterns
                            if (filter.contains("*") || filter.contains("?")) {
                                String regex = filter.replace(".", "\\\\.")
                                                    .replace("*", ".*")
                                                    .replace("?", ".");
                                if (file.matches(regex)) {
                                    matches = true;
                                    break;
                                }
                            } else {
                                // Exact match or path contains
                                if (file.equals(filter) || file.startsWith(filter + "/") || file.contains("/" + filter)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                        return !matches;
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
