package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

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

        try (Git git = (Utils.openGit() != null ? Utils.openGit() : Git.open(new java.io.File(".")))) {
            if (git == null) return 1;

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
