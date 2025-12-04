package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.lib.Repository;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class RestoreCommand implements Command {
    @Override public String name(){ return "restore"; }

    @Override public int run(List<String> args) throws Exception {
        boolean lb = args.contains("-lb");
        boolean rb = args.contains("-rb");
        List<String> filters = new ArrayList<>();
        for (String s : args) if (!s.equals("-lb") && !s.equals("-rb")) filters.add(s);

        try (Git git = Utils.findGitRepoOrWarn()) {
            if (git == null) {
                System.out.println("Warning: No local repository found in: " + 
                    Paths.get(".").toAbsolutePath().normalize());
                return 1;
            }
            if (!lb && !rb) lb = true;
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");
            if (rb && remoteUrl == null) {
                System.out.println("No remote connected.");
                return 1;
            }

            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            String treeish = (lb ? "HEAD" : "origin/"+branch);
            
            // If no filters, default to "*" (everything)
            if (filters.isEmpty()) {
                filters.add("*");
            }

            // First pass: collect all files that will be restored
            Set<String> filesToRestore = new LinkedHashSet<>();
            ObjectId treeId = repo.resolve(treeish + "^{tree}");
            if (treeId == null) {
                System.out.println("Cannot resolve " + treeish);
                return 1;
            }
            
            try (TreeWalk tw = new TreeWalk(repo)) {
                tw.addTree(treeId);
                tw.setRecursive(true);
                
                while (tw.next()) {
                    String path = tw.getPathString();
                    
                    // Apply filters
                    if (matchesAnyFilter(path, filters)) {
                        filesToRestore.add(path);
                    }
                }
            }
            
            if (filesToRestore.isEmpty()) {
                System.out.println("No files match the specified filters.");
                return 0;
            }
            
            // Show what will be restored
            System.out.println("The following files will be restored from " + treeish + ":");
            filesToRestore.forEach(f -> System.out.println("  " + f));
            System.out.println();
            System.out.print("Continue? (y/N): ");
            
            String response;
            try (Scanner scanner = new Scanner(System.in)) {
                response = scanner.nextLine().trim().toLowerCase();
            }
            
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("Restore cancelled.");
                return 0;
            }
            
            // Second pass: actually restore the files
            int restoredCount = 0;
            for (String filePath : filesToRestore) {
                try (TreeWalk tw = TreeWalk.forPath(repo, filePath, treeId)) {
                    if (tw == null) {
                        System.out.println("Warning: Could not restore " + filePath);
                        continue;
                    }
                    byte[] data = repo.open(tw.getObjectId(0)).getBytes();
                    Path target = repo.getWorkTree().toPath().resolve(filePath);
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                        fos.write(data);
                    }
                    restoredCount++;
                }
            }
            
            System.out.println("Restored " + restoredCount + " file(s).");
        }
        return 0;
    }
    
    private boolean matchesAnyFilter(String path, List<String> filters) {
        for (String filter : filters) {
            // Special case: * matches everything
            if (filter.equals("*")) {
                return true;
            }
            
            // Support glob patterns
            if (filter.contains("*") || filter.contains("?")) {
                String regex = filter.replace(".", "\\.")
                                    .replace("*", ".*")
                                    .replace("?", ".");
                if (path.matches(regex)) {
                    return true;
                }
            } else {
                // Exact match or prefix match
                if (path.equals(filter) || path.startsWith(filter + "/") || path.contains("/" + filter)) {
                    return true;
                }
            }
        }
        return false;
    }
}
