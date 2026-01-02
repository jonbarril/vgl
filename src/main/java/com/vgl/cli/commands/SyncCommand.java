package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.utils.Messages;
import java.util.List;

public class SyncCommand implements Command {
    @Override
    public String name() {
        return "sync";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.syncUsage());
            return 0;
        }

        if (ArgsHelper.hasFlag(args, "-noop")) {
            System.out.println(Messages.syncDryRun());
            return 0;
        }

        int rc = new PullCommand().run(args);
        if (rc != 0) {
            return rc;
        }
        return new PushCommand().run(args);
    }
}
