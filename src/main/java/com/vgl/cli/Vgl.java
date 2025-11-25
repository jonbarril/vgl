package com.vgl.cli;

import com.vgl.cli.commands.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Vgl {
    private static final String CONFIG_FILE = ".vgl";
    private final Map<String, Command> cmds = new LinkedHashMap<>();
    private final Properties config = new Properties();

    public Vgl() {
        loadConfig();
        register(new HelpCommand());
        register(new CreateCommand());
        register(new CheckoutCommand());
        register(new LocalCommand());
        register(new RemoteCommand());
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
            saveConfig(); // Save the configuration after running the command
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
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
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
        if (isConfigurable()) {
            Path configPath = Paths.get(CONFIG_FILE);
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
}
