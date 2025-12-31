package com.vgl.cli.commands;

import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class UntrackCommand implements Command {
    @Override public String name(){ return "untrack"; }

    @Override public int run(List<String> args) throws Exception {
        if (args.isEmpty()) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_UNTRACK_USAGE);
            return 1;
        }

        VglCli vgl = new VglCli();
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();

        if (!vgl.isConfigurable()) {
            System.err.println(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_WARNING_PREFIX + dir);
            return 1;
        }

        boolean useAll = args.size() == 1 && args.get(0).equals("-all");
        List<String> filesToUntrack;
        com.vgl.cli.services.VglRepo vglRepo = com.vgl.cli.utils.RepoResolver.resolveVglRepoForCommand(dir);
        if (vglRepo == null) {
            System.out.println("No .vgl repo found for untrack operation.");
            return 1;
        }
        if (useAll) {
            filesToUntrack = new java.util.ArrayList<>(vglRepo.getTrackedFiles());
            if (filesToUntrack.isEmpty()) {
                System.out.println("No tracked files to untrack.");
                return 0;
            }
        } else {
            try (Git git = Git.open(dir.toFile())) {
                filesToUntrack = RepoUtils.expandGlobsToFiles(args, dir, git.getRepository());
            }
        }

        // Remove .vgl and nested repo paths from filesToUntrack
        List<String> filteredFiles = new java.util.ArrayList<>();
        List<String> nestedRequests = new java.util.ArrayList<>();
        for (String p : filesToUntrack) {
            if (".vgl".equals(p)) continue;
            Path fp = dir.resolve(p).toAbsolutePath().normalize();
            if (RepoUtils.isInsideNestedRepo(dir, fp)) {
                nestedRequests.add(p);
                continue;
            }
            filteredFiles.add(p);
        }
        if (!nestedRequests.isEmpty()) {
            System.out.println("Error: Cannot untrack nested repository paths: " + String.join(" ", nestedRequests));
            System.out.println("Remove or convert nested repositories to submodules before untracking their files from the parent.");
            if (filteredFiles.isEmpty()) return 1;
        }
        if (filteredFiles.isEmpty()) {
            for (String file : args) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_FILE_NOT_TRACKED + file);
            }
            return 1;
        }

        // Only .vgl config determines tracked state; remove from tracked.files
        List<String> tracked = new java.util.ArrayList<>(vglRepo.getTrackedFiles());
        List<String> undecided = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
        List<String> actuallyUntracked = new java.util.ArrayList<>();
        List<String> failed = new java.util.ArrayList<>();
        for (String p : filteredFiles) {
            if (tracked.contains(p)) {
                tracked.remove(p);
                actuallyUntracked.add(p);
                // Optionally, add to undecided if not ignored (VGL model: once decided, cannot become undecided again)
            } else {
                failed.add(p);
            }
        }
        vglRepo.setTrackedFiles(tracked);
        vglRepo.setUndecidedFiles(undecided);
        vglRepo.saveConfig();

        if (!actuallyUntracked.isEmpty()) {
            System.out.println(com.vgl.cli.utils.MessageConstants.MSG_UNTRACK_SUCCESS_PREFIX + String.join(" ", actuallyUntracked));
        }
        if (!failed.isEmpty()) {
            for (String file : failed) {
                System.err.println(com.vgl.cli.utils.MessageConstants.MSG_ERR_FILE_NOT_TRACKED + file);
            }
        }
        return 0;
    }
}
