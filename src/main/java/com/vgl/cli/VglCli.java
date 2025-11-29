package com.vgl.cli;

import com.vgl.cli.commands.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VglCli {
    private static final String CONFIG_FILE = ".vgl";
    private final Map<String, Command> cmds = new LinkedHashMap<>();
    private final Properties config = new Properties();

    public VglCli() {
        loadConfig();
        register(new HelpCommand());
        register(new CreateCommand());
        register(new CheckoutCommand());
        register(new LocalCommand());
        register(new RemoteCommand());
        register(new JumpCommand());
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

    private void loadConfig() {
        // Check for .vgl in current directory
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            // Check if .git exists alongside .vgl
            if (!Files.exists(Paths.get(".git"))) {
                // Orphaned .vgl file - .git was deleted or moved
                System.err.println("Warning: Found .vgl but no .git directory.");
                System.err.println("The .git repository may have been deleted or moved.");
                System.err.println();
                System.err.println("Options:");
                System.err.println("  - Delete .vgl and start fresh: vgl create <path>");
                System.err.println("  - Clone from remote: vgl checkout <url>");
                System.err.println("  - Keep .vgl if you plan to restore .git");
                System.err.println();
                
                // Only prompt if we have an interactive console
                if (System.console() != null) {
                    System.err.print("Delete orphaned .vgl file? (y/N): ");
                    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                        String response = scanner.nextLine().trim().toLowerCase();
                        if (response.equals("y") || response.equals("yes")) {
                            Files.delete(configPath);
                            System.err.println("Deleted .vgl file.");
                            return;
                        } else {
                            System.err.println("Kept .vgl file. Remember: .vgl only works with .git");
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
        String localDir = getLocalDir();
        if (Files.exists(Paths.get(localDir).resolve(".git"))) {
            Path configPath = Paths.get(localDir).resolve(CONFIG_FILE);
            try (OutputStream out = Files.newOutputStream(configPath)) {
                config.store(out, "VGL Configuration");
            } catch (IOException e) {
                System.err.println("Warning: Failed to save configuration file.");
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
        return config.getProperty("local.dir", ".");
    }

    public void setLocalDir(String dir) {
        config.setProperty("local.dir", dir);
    }

    public String getLocalBranch() {
        return config.getProperty("local.branch", "main");
    }

    public void setLocalBranch(String branch) {
        config.setProperty("local.branch", branch);
    }

    public String getRemoteUrl() {
        return config.getProperty("remote.url", null);
    }

    public void setRemoteUrl(String url) {
        config.setProperty("remote.url", url);
    }

    public String getRemoteBranch() {
        return config.getProperty("remote.branch", "main");
    }

    public void setRemoteBranch(String branch) {
        config.setProperty("remote.branch", branch);
    }

    // Jump state management - stores previous context for toggle
    public String getJumpLocalDir() {
        return config.getProperty("jump.local.dir", null);
    }

    public void setJumpLocalDir(String dir) {
        if (dir != null) {
            config.setProperty("jump.local.dir", dir);
        } else {
            config.remove("jump.local.dir");
        }
    }

    public String getJumpLocalBranch() {
        return config.getProperty("jump.local.branch", null);
    }

    public void setJumpLocalBranch(String branch) {
        if (branch != null) {
            config.setProperty("jump.local.branch", branch);
        } else {
            config.remove("jump.local.branch");
        }
    }

    public String getJumpRemoteUrl() {
        return config.getProperty("jump.remote.url", null);
    }

    public void setJumpRemoteUrl(String url) {
        if (url != null) {
            config.setProperty("jump.remote.url", url);
        } else {
            config.remove("jump.remote.url");
        }
    }

    public String getJumpRemoteBranch() {
        return config.getProperty("jump.remote.branch", null);
    }

    public void setJumpRemoteBranch(String branch) {
        if (branch != null) {
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
