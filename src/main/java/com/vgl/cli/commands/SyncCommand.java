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

        // For noop, run pull -noop (which now properly analyzes merge)
        // For non-noop, run pull then push
        int rc = new PullCommand().run(args);
        if (rc != 0) {
            return rc;
        }

        // If noop, don't run push
        if (ArgsHelper.hasFlag(args, "-noop")) {
            return 0;
        }

        return new PushCommand().run(args);
    }
}
