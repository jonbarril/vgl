package com.vgl.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;

import com.vgl.cli.Utils;
import com.vgl.cli.VglRepo;

public class CommitCommand implements Command {
    @Override public String name(){ return "commit"; }

    @Override public int run(List<String> args) throws Exception {
        String msg;
        boolean amend = false;
        List<String> rest = new ArrayList<>(args);
        
        if (!rest.isEmpty() && ("-new".equals(rest.get(0)) || "-add".equals(rest.get(0))) && rest.size() >= 2) {
            amend = true;
            msg = rest.get(1);
        } else if (!rest.isEmpty()) {
            msg = rest.get(0);
        } else {
            System.out.println("Usage: vgl commit \"msg\" | [-new|-add] \"msg\"");
            return 1;
        }

        try (VglRepo vglRepo = Utils.findVglRepoOrWarn()) {
            if (vglRepo == null) return 1;
            
            Git git = vglRepo.getGit();

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

            // Check for unresolved conflict markers
            List<String> filesWithConflicts = new ArrayList<>();
            Path repoRoot = git.getRepository().getWorkTree().toPath();
            
            // Check all files that will be committed
            for (String file : s.getAdded()) {
                if (hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            for (String file : s.getChanged()) {
                if (hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            for (String file : s.getModified()) {
                if (hasConflictMarkers(repoRoot.resolve(file))) {
                    filesWithConflicts.add(file);
                }
            }
            
            if (!filesWithConflicts.isEmpty()) {
                System.out.println("Error: The following files contain unresolved conflict markers:");
                filesWithConflicts.forEach(f -> System.out.println("  " + f));
                System.out.println("\nConflict markers look like:");
                System.out.println("  " + "<".repeat(7) + " HEAD");
                System.out.println("  " + "=".repeat(7));
                System.out.println("  " + ">".repeat(7) + " branch-name");
                System.out.println("\nEdit these files to resolve conflicts before committing.");
                return 1;
            }

            RevCommit rc = git.commit()
                    .setMessage(msg)
                    .setAmend(amend)
                    .call();

            // First line: exactly 7-char short SHA
            String short7 = rc.getId().abbreviate(7).name();
            System.out.println(short7);

            return 0;
        }
    }

    private boolean hasConflictMarkers(Path file) {
        try {
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return false;
            }
            String content = Files.readString(file);
            return content.contains("<<<<<<<") || content.contains(">>>>>>>") || 
                   (content.contains("=======") && (content.contains("<<<<<<<") || content.contains(">>>>>>>")));
        } catch (IOException e) {
            // If we can't read the file, don't block the commit
            return false;
        }
    }
}
