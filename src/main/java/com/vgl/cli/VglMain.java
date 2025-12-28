package com.vgl.cli;

public class VglMain {
    public static void main(String[] args) {
        // Determine command name before instantiating VglCli
        String commandName = (args.length > 0) ? args[0] : "help";
        int exitCode = 1;
        try {
            if ("checkout".equals(commandName)) {
                // Run CheckoutCommand directly to avoid premature config loading
                exitCode = new com.vgl.cli.commands.CheckoutCommand().run(java.util.Arrays.asList(args).subList(1, args.length));
            } else if ("create".equals(commandName)) {
                // Run CreateCommand directly to avoid premature config loading
                exitCode = new com.vgl.cli.commands.CreateCommand().run(java.util.Arrays.asList(args).subList(1, args.length));
            } else {
                exitCode = new VglCli().run(args);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
