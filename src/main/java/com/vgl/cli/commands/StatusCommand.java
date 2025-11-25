package com.vgl.cli.commands;

import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.revwalk.RevCommit;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class StatusCommand implements Command {
    @Override public String name() { return "status"; }

    @Override public int run(List<String> args) throws Exception {
        VglCli vgl = new VglCli();
        boolean hasLocalRepo = vgl.isConfigurable();
        String localDir = hasLocalRepo ? Paths.get(vgl.getLocalDir()).toAbsolutePath().normalize().toString() : "(none)";
        String localBranch = hasLocalRepo ? vgl.getLocalBranch() : "main";
        String remoteUrl = hasLocalRepo ? (vgl.getRemoteUrl() != null ? vgl.getRemoteUrl() : "none") : "none";
        String remoteBranch = hasLocalRepo ? vgl.getRemoteBranch() : "main";

        boolean verbose = args.contains("-v");
        boolean veryVerbose = args.contains("-vv");

        // Report LOCAL
        System.out.println("LOCAL   " + (hasLocalRepo ? localDir + ":" + localBranch : "(none)"));

        // Report REMOTE
        System.out.println("REMOTE  " + (!remoteUrl.equals("none") ? remoteUrl + ":" + remoteBranch : "(none)"));

        // Report STATE and FILES
        if (hasLocalRepo) {
            try (Git git = Git.open(Paths.get(localDir).toFile())) {
                BranchTrackingStatus bts = BranchTrackingStatus.of(git.getRepository(), localBranch);
                if (bts == null) {
                    System.out.println("STATE   (no tracking)");
                } else if (bts.getAheadCount() == 0 && bts.getBehindCount() == 0) {
                    System.out.println("STATE   clean");
                } else {
                    System.out.printf("STATE   ahead %d, behind %d%n", bts.getAheadCount(), bts.getBehindCount());
                }

                Status status = git.status().call();
                int modified = status.getChanged().size() + status.getModified().size() + status.getAdded().size() +
                               status.getRemoved().size() + status.getMissing().size();
                int untracked = status.getUntracked().size();
                System.out.printf("FILES   %d modified(tracked), %d untracked%n", modified, untracked);

                if (verbose) {
                    System.out.println("-- Recent Commits:");
                    Iterable<RevCommit> commits = git.log().setMaxCount(5).call();
                    for (RevCommit commit : commits) {
                        System.out.printf("  %s %s%n", commit.getId().abbreviate(7).name(), commit.getShortMessage());
                    }
                }

                if (veryVerbose) {
                    System.out.println("-- Tracked Files:");
                    status.getChanged().forEach(p -> System.out.println("  M " + p));
                    status.getModified().forEach(p -> System.out.println("  M " + p));
                    status.getAdded().forEach(p -> System.out.println("  A " + p));
                    status.getRemoved().forEach(p -> System.out.println("  D " + p));
                    status.getMissing().forEach(p -> System.out.println("  D " + p + " (missing)"));
                    System.out.println("-- Untracked Files:");
                    status.getUntracked().forEach(p -> System.out.println("  ? " + p));
                }
            }
        } else {
            System.out.println("STATE   (no local repository)");
            System.out.println("FILES   (no local repository)");
        }

        return 0;
    }
}
