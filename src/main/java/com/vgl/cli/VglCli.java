package com.vgl.cli;
import com.vgl.cli.commands.StatusCommand;

import com.vgl.cli.commands.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;

public class VglCli {
    private static final String CONFIG_FILE = ".vgl";
    private final Map<String, Command> cmds = new LinkedHashMap<>();
    private final Properties config = new Properties();

    public VglCli() {
        loadConfig();
        register(new HelpCommand());
        register(new CreateCommand());
        register(new DeleteCommand());
        register(new CheckoutCommand());
        register(new SwitchCommand());
        register(new JumpCommand());
        register(new SplitCommand());
        register(new MergeCommand());
        register(new TrackCommand());
        register(new UntrackCommand());
        register(new CommitCommand());
        register(new RestoreCommand());
        register(new PullCommand());
        register(new PushCommand());
        register(new CheckinCommand());
        register(new SyncCommand());
        register(new AbortCommand());
        register(new StatusCommand());
        register(new DiffCommand());
        register(new LogCommand());
    }

    private void register(Command c) {
        cmds.put(c.name(), c);
    }

    public int run(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        // Insert "help" as the default command if no command is provided
        if (args.isEmpty() || cmds.get(args.get(0)) == null) {
            args.add(0, "help");
        }

        String commandName = args.remove(0); // Extract the command name
        Command command = cmds.get(commandName);

        try {
            int result = command.run(Collections.unmodifiableList(args));
            // Note: Commands that modify configuration are responsible for calling save()
            return result;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    public boolean isConfigurable() {
        String localDir = getLocalDir();
        return Files.exists(Paths.get(localDir).resolve(".git"));
    }

    /**
     * Search upward from current directory for .vgl file.
     * 
     * @param repoRoot The root of the git repository, or null if no repo is known.
     *                 If provided, search stops at this boundary.
     *                 If null, only checks the current working directory.
     */
    private Path findConfigFile(Path repoRoot) {
        // Use user.dir property (respects test environment)
        String userDir = System.getProperty("user.dir");
        Path current = Paths.get(userDir).toAbsolutePath().normalize();
        
        // If no repo root provided, only check current directory
        if (repoRoot == null) {
            Path configPath = current.resolve(CONFIG_FILE);
            return Files.exists(configPath) ? configPath : null;
        }
        
        // Normalize repo root for comparison
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        
        // Search from current dir up to repo root for .vgl
        while (current != null) {
            Path configPath = current.resolve(CONFIG_FILE);
            if (Files.exists(configPath)) {
                return configPath;
            }
            
            // Stop at repo root
            if (current.equals(normalizedRoot)) {
                break;
            }
            
            current = current.getParent();
        }
        
        return null; // Not found
    }

    private void loadConfig() {
        // Find git repo root first, then search for .vgl within that boundary
        Path repoRoot = null;
        try {
            Git git = Utils.findGitRepo();
            if (git != null) {
                repoRoot = git.getRepository().getWorkTree().toPath();
                git.close();
            }
        } catch (IOException e) {
            // No repo found - will only check current directory
        }
        
        // Search upward for .vgl file (bounded by repo root)
        Path configPath = findConfigFile(repoRoot);
        if (configPath != null && Files.exists(configPath)) {
            // Check if .git exists alongside .vgl
            Path vglDir = configPath.getParent();
            if (!Files.exists(vglDir.resolve(".git"))) {
                // Orphaned .vgl file - .git was deleted or moved
                System.err.println("Warning: Found .vgl but no .git directory.");
                System.err.println("The .git repository may have been deleted or moved.");
                System.err.println();
                System.err.println("Options:");
                System.err.println("  - Delete .vgl and start fresh: vgl create <path>");
                System.err.println("  - Clone from remote: vgl checkout <url>");
                System.err.println("  - Keep .vgl if you plan to restore .git");
                System.err.println();
                
                // Only prompt if we are in an interactive environment
                if (com.vgl.cli.Utils.isInteractive()) {
                    // Avoid blocking reads from System.in in test environments where
                    // System.in may be open but offer no data. Use BufferedReader.ready()
                    // to check for available input before attempting to read.
                    System.err.print("Delete orphaned .vgl file? (y/N): ");
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                        if (br.ready()) {
                            String response = br.readLine();
                            if (response != null) response = response.trim().toLowerCase(); else response = "";
                            if (response.equals("y") || response.equals("yes")) {
                                Files.delete(configPath);
                                System.err.println("Deleted .vgl file.");
                                return;
                            } else {
                                System.err.println("Kept .vgl file. Remember: .vgl only works with .git");
                            }
                        } else {
                            // No input ready - treat as non-interactive
                            try { Files.delete(configPath); } catch (IOException ignore) {}
                            System.err.println("Deleted orphaned .vgl file (non-interactive mode).");
                            return;
                        }
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to delete .vgl file.");
                    }
                } else {
                    // Non-interactive mode - just delete it
                    try {
                        Files.delete(configPath);
                        System.err.println("Deleted orphaned .vgl file (non-interactive mode).");
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to delete orphaned .vgl file.");
                    }
                }
                // Don't load the orphaned config
                return;
            }
            
            // Valid .vgl with .git - load it
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
            } catch (IOException e) {
                System.err.println("Warning: Failed to load configuration file.");
            }
        } else {
            //// System.out.println("Info: No configuration file found. Defaults will
            /// be used.");
        }
    }

    private void saveConfig() {
        // Find the git root for local.dir and save .vgl there
        String localDir = getLocalDir();
        Path localPath = Paths.get(localDir).toAbsolutePath().normalize();
        
        // Search upward from local.dir to find .git
        Path gitRoot = localPath;
        while (gitRoot != null && !Files.exists(gitRoot.resolve(".git"))) {
            gitRoot = gitRoot.getParent();
        }
        
        if (gitRoot != null) {
            // Save .vgl alongside .git
            Path savePath = gitRoot.resolve(CONFIG_FILE);
            try (OutputStream out = Files.newOutputStream(savePath)) {
                config.store(out, "VGL Configuration");
            } catch (IOException e) {
                System.err.println("Warning: Failed to save configuration file.");
                e.printStackTrace();
            }
        } else {
            //// System.out.println("Info: No local repository found. Configuration file
            /// will not be created.");
        }
    }

    public void save() {
        saveConfig();
    }

    public String getLocalDir() {
        String dir = config.getProperty("local.dir", null);
        if (dir == null || dir.isEmpty()) {
            // Default to current directory as absolute path
            // Use user.dir property (respects test environment)
            String userDir = System.getProperty("user.dir");
            return Paths.get(userDir).toAbsolutePath().normalize().toString();
        }
        return dir;
    }

    public void setLocalDir(String dir) {
        // Always store absolute paths
        String absolutePath = Paths.get(dir).toAbsolutePath().normalize().toString();
        config.setProperty("local.dir", absolutePath);
    }

    public String getLocalBranch() {
        return config.getProperty("local.branch", "main");
    }

    public void setLocalBranch(String branch) {
        config.setProperty("local.branch", branch);
    }

    public String getRemoteUrl() {
        String url = config.getProperty("remote.url", null);
        return (url == null || url.isEmpty()) ? null : url;
    }

    public void setRemoteUrl(String url) {
        if (url != null && !url.isEmpty()) {
            config.setProperty("remote.url", url);
        } else {
            config.remove("remote.url");
        }
    }

    public String getRemoteBranch() {
        String branch = config.getProperty("remote.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setRemoteBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("remote.branch", branch);
        } else {
            config.remove("remote.branch");
        }
    }

    // Jump state management - stores previous context for toggle
    public String getJumpLocalDir() {
        String dir = config.getProperty("jump.local.dir", null);
        return (dir == null || dir.isEmpty()) ? null : dir;
    }

    public void setJumpLocalDir(String dir) {
        if (dir != null && !dir.isEmpty()) {
            // Always store absolute paths
            String absolutePath = Paths.get(dir).toAbsolutePath().normalize().toString();
            config.setProperty("jump.local.dir", absolutePath);
        } else {
            config.remove("jump.local.dir");
        }
    }

    public String getJumpLocalBranch() {
        String branch = config.getProperty("jump.local.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setJumpLocalBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("jump.local.branch", branch);
        } else {
            config.remove("jump.local.branch");
        }
    }

    public String getJumpRemoteUrl() {
        String url = config.getProperty("jump.remote.url", null);
        return (url == null || url.isEmpty()) ? null : url;
    }

    public void setJumpRemoteUrl(String url) {
        if (url != null && !url.isEmpty()) {
            config.setProperty("jump.remote.url", url);
        } else {
            config.remove("jump.remote.url");
        }
    }

    public String getJumpRemoteBranch() {
        String branch = config.getProperty("jump.remote.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setJumpRemoteBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("jump.remote.branch", branch);
        } else {
            config.remove("jump.remote.branch");
        }
    }

    /**
     * Check if jump state exists (at least one jump property is set)
     */
    public boolean hasJumpState() {
        return getJumpLocalDir() != null || getJumpLocalBranch() != null;
    }
}
