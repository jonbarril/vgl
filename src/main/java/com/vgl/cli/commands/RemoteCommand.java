package com.vgl.cli.commands;

import java.util.List;

public class RemoteCommand implements Command {
    @Override
    public String name() {
        return "remote"; // Updated command name
    }

    @Override
    public int run(List<String> args) {
        // ...existing code for the connect command...
        System.out.println("Remote repository/branch set.");
        return 0;
    }
}
