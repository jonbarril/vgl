package com.vgl.cli.commands;

import java.util.List;

public class SyncCommand implements Command {
    @Override public String name(){ return "sync"; }

    @Override public int run(List<String> args) throws Exception {
        int rc1 = new PullCommand().run(args);
        if (rc1 != 0) return rc1;
        return new PushCommand().run(args);
    }
}
