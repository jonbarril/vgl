package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.lib.Repository;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RestoreCommand implements Command {
    @Override public String name(){ return "restore"; }

    @Override public int run(List<String> args) throws Exception {
        boolean lb = args.contains("-lb");
        boolean rb = args.contains("-rb");
        List<String> pats = new ArrayList<>();
        for (String s : args) if (!s.equals("-lb") && !s.equals("-rb")) pats.add(s);

        try (Git git = Utils.openGit()) {
            if (git == null) {
                System.out.println("No Git repository found.");
                return 1;
            }
            if (!lb && !rb) lb = true;
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
            if (rb && remoteUrl == null) {
                System.out.println("No remote connected.");
                return 1;
            }
            if (pats.isEmpty()) {
                System.out.println("Usage: vgl restore -lb|-rb <commit|glob|*>");
                return 1;
            }

            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            String treeish = (lb ? "HEAD" : "origin/"+branch);

            for (String p : pats) {
                String spec = treeish + ":" + p.replace("\\", "/");
                ObjectId blobId = repo.resolve(spec);
                if (blobId == null) {
                    System.out.println("No matching file found for: " + p);
                    continue;
                }

                try (TreeWalk tw = TreeWalk.forPath(repo, p.replace("\\","/"), repo.resolve(treeish + "^{tree}"))) {
                    if (tw == null) {
                        System.out.println("No matching file found for: " + p);
                        continue;
                    }
                    byte[] data = repo.open(tw.getObjectId(0)).getBytes();
                    Path target = repo.getWorkTree().toPath().resolve(p);
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                        fos.write(data);
                    }
                    System.out.println("RESTORE " + p);
                }
            }
        }
        return 0;
    }
}
