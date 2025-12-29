package com.vgl.cli;

import com.vgl.cli.commands.HelpCommand;
import java.util.Arrays;
import java.util.List;

public class VglMain {
    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        List<String> argList = Arrays.asList(args);
        try {
            if (argList.isEmpty()) {
                return new HelpCommand().run(List.of());
            }

            String command = argList.get(0);
            List<String> tailArgs = argList.subList(1, argList.size());

            if (command.equals("help") || command.equals("-h") || command.equals("--help")) {
                return new HelpCommand().run(tailArgs);
            }

            if (command.equals("-v") || command.equals("-vv")) {
                return new HelpCommand().run(argList);
            }

            System.err.println("Unknown command: " + command);
            new HelpCommand().run(List.of());
            return 1;
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            return 1;
        }
    }
}
