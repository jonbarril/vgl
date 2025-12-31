package com.vgl.cli;

import com.vgl.cli.commands.HelpCommand;
import java.util.List;
import picocli.CommandLine;

public class VglMain {
    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        try {
            if (args == null || args.length == 0) {
                return new HelpCommand().run(List.of());
            }

            String first = args[0];
            if (first.equals("-h") || first.equals("--help")) {
                return new HelpCommand().run(List.of());
            }

            if (first.equals("-v") || first.equals("-vv")) {
                return new HelpCommand().run(List.of(first));
            }

            CommandLine cmd = VglCli.newCommandLine();
            return cmd.execute(args);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            return 1;
        }
    }
}
