package com.vgl.cli.commands;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;

import com.vgl.cli.Utils;

public class CommitCommand implements Command {
    @Override public String name(){ return "commit"; }

    @Override public int run(List<String> args) throws Exception {
        String msg;
        List<String> rest = new ArrayList<>(args);
        if (!rest.isEmpty() && "-m".equals(rest.get(0)) && rest.size() >= 2) {
            msg = rest.get(1);
        } else if (!rest.isEmpty()) {
            msg = rest.get(0);
        } else {
            System.out.println("Usage: vgl commit \"msg\" | -m \"new msg\"");
            return 1;
        }

        Git maybe = Utils.openGit();
        if (maybe == null) maybe = Git.open(new java.io.File("."));
        if (maybe == null) {
            System.out.println("Warning: No Git repository found in: " + 
                Paths.get(".").toAbsolutePath().normalize());
            return 1;
        }
        try (Git git = maybe) {

            // First get status to see what needs to be added
            Status preStatus = git.status().call();
            
            // Stage untracked files (respects .gitignore automatically)
            if (!preStatus.getUntracked().isEmpty()) {
                for (String untracked : preStatus.getUntracked()) {
                    git.add().addFilepattern(untracked).call();
                }
            }

            // Stage everything: additions and modifications
            git.add().addFilepattern(".").call();
            // Stage deletions
            git.add().setUpdate(true).addFilepattern(".").call();

            Status s = git.status().call();
            boolean nothingToCommit = (
                s.getAdded().isEmpty() &&
                s.getChanged().isEmpty() &&
                s.getRemoved().isEmpty() &&
                s.getModified().isEmpty() &&
                s.getMissing().isEmpty() &&
                s.getUntracked().isEmpty()
            );
            if (nothingToCommit) {
                System.out.println("Nothing to commit.");
                System.out.flush();
                return 1;
            }

            RevCommit rc = git.commit()
                    .setMessage(msg)
                    .call();

            // First line: exactly 7-char short SHA
            String short7 = rc.getId().abbreviate(7).name();
            System.out.println(short7);

            return 0;
        }
    }
}
