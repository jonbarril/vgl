package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.BranchTrackingStatus;

import java.util.List;

public class StatusCommand implements Command {
    @Override public String name(){ return "status"; }

    @Override public int run(List<String> args) throws Exception {
        String commitish = null; boolean v=false, vv=false;
        for (String s : args) { if ("-v".equals(s)) v=true; else if ("-vv".equals(s)) vv=true; else commitish = (commitish==null? s: commitish); }
        try (Git git = Utils.openGit()) {
            if (git == null) { System.out.println("No repo."); return 0; }
            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            String remoteUrl = repo.getConfig().getString("remote","origin","url");
            System.out.println("LOCAL   " + repo.getWorkTree() + "@" + branch);
            System.out.println("REMOTE  " + (remoteUrl==null? "(none)": remoteUrl + "@" + branch));
            BranchTrackingStatus bts=null; try{ bts=BranchTrackingStatus.of(repo, branch);}catch(Exception ignore){}
            if (bts == null) System.out.println("STATE   (no tracking)");
            else if (bts.getAheadCount()==0 && bts.getBehindCount()==0) System.out.println("STATE   clean");
            else System.out.printf("STATE   ahead %d, behind %d%n", bts.getAheadCount(), bts.getBehindCount());
            Status s = git.status().call();
            int modified = s.getChanged().size()+s.getModified().size()+s.getAdded().size()+s.getRemoved().size()+s.getMissing().size();
            int untracked = s.getUntracked().size();
            System.out.printf("FILES   %d modified(tracked), %d untracked%n", modified, untracked);
            if (v||vv) {
                s.getChanged().forEach(p->System.out.println("  M "+p));
                s.getModified().forEach(p->System.out.println("  M "+p));
                s.getAdded().forEach(p->System.out.println("  A "+p));
                s.getRemoved().forEach(p->System.out.println("  D "+p));
                s.getMissing().forEach(p->System.out.println("  D "+p+" (missing)"));
                System.out.println("-- Untracked:");
                s.getUntracked().forEach(p->System.out.println("  ? "+p));
            }
            if (vv) {
                String cur=""; try{ cur="* "; }catch(Exception ignore){}
                for (Ref ref: git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()) {
                    String name = ref.getName();
                    if (name.startsWith("refs/heads/")) name = name.substring("refs/heads/".length());
                    if (name.startsWith("refs/remotes/")) name = "rem:"+name.substring("refs/remotes/".length());
                    System.out.println(cur+name);
                }
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return name();
    }
}
