package com.vgl.cli.commands;

import java.util.List;

public class RemoteCommand implements Command {
    @Override
    public String name() {
        return "remote";
    }

    @Override
    public int run(List<String> args) {
        // ...existing code...
        System.out.println("Remote repository/branch set.");
        return 0;
    }
}
