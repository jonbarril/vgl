package com.vgl.cli;

import com.vgl.cli.commands.*;
import java.util.*;

public class Vgl {
    private final Map<String, Command> cmds = new LinkedHashMap<>();

    public Vgl() {
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
        Command command = cmds.getOrDefault(args, cmds.get("help"));
        try {
            return command.run(Collections.unmodifiableList(args));
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(); // Added for better debugging
            return 1;
        }
    }
}
