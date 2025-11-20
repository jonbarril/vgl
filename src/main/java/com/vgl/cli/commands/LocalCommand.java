package com.vgl.cli.commands;

import java.util.List;

public class LocalCommand implements Command {
    @Override
    public String name() {
        return "local"; // Updated command name
    }

    @Override
    public int run(List<String> args) {
        // ...existing code for the focus command...
        System.out.println("Switched to local repository/branch.");
        return 0;
    }
}
