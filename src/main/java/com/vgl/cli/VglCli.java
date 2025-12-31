package com.vgl.cli;

import com.vgl.cli.commands.CreateCommand;
import com.vgl.cli.commands.DeleteCommand;
import com.vgl.cli.commands.HelpCommand;
import com.vgl.cli.commands.StatusCommand;
import com.vgl.cli.commands.TrackCommand;
import com.vgl.cli.commands.UntrackCommand;
import com.vgl.cli.commands.helpers.Usage;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * CLI wiring/binding layer.
 *
 * <p>Business logic lives in {@code com.vgl.cli.commands.*}. This class binds that logic to the
 * CLI framework.
 */
public final class VglCli {

    private VglCli() {}

    static CommandLine newCommandLine() {
        CommandLine cmd = new CommandLine(new Root());
        cmd.setParameterExceptionHandler(new UsageOnlyParameterExceptionHandler());
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println("ERROR: " + ex.getMessage());
            return 1;
        });
        return cmd;
    }

    @Command(
        name = "vgl",
        subcommands = {
            Create.class,
            Delete.class,
            Track.class,
            Untrack.class,
            Status.class,
            Help.class
        }
    )
    static class Root {
        // Root command intentionally does not implement business logic.
    }

    @Command(name = "create")
    static class Create implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-bb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String bothBranch;

        @Option(names = "-rr", paramLabel = "URL")
        String remoteUrl;

        @Option(names = "-rb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String remoteBranch;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (force) {
                forwarded.add("-f");
            }
            if (localRepoDir != null) {
                forwarded.add("-lr");
                forwarded.add(localRepoDir.toString());
            }

            String branch = (bothBranch != null) ? bothBranch : localBranch;
            if (branch != null) {
                if (bothBranch != null) {
                    forwarded.add("-bb");
                    forwarded.add(branch);
                } else {
                    forwarded.add("-lb");
                    forwarded.add(branch);
                }
            }

            if (remoteUrl != null) {
                forwarded.add("-rr");
                forwarded.add(remoteUrl);
            }
            if (remoteBranch != null) {
                forwarded.add("-rb");
                forwarded.add(remoteBranch);
            }

            return new CreateCommand().run(forwarded);
        }
    }

    @Command(name = "delete")
    static class Delete implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-bb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String bothBranch;

        @Option(names = "-rr", paramLabel = "URL")
        String remoteUrl;

        @Option(names = "-rb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String remoteBranch;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (force) {
                forwarded.add("-f");
            }
            if (localRepoDir != null) {
                forwarded.add("-lr");
                forwarded.add(localRepoDir.toString());
            }

            String branch = (bothBranch != null) ? bothBranch : localBranch;
            if (branch != null) {
                if (bothBranch != null) {
                    forwarded.add("-bb");
                    forwarded.add(branch);
                } else {
                    forwarded.add("-lb");
                    forwarded.add(branch);
                }
            }

            if (remoteUrl != null) {
                forwarded.add("-rr");
                forwarded.add(remoteUrl);
            }
            if (remoteBranch != null) {
                forwarded.add("-rb");
                forwarded.add(remoteBranch);
            }

            return new DeleteCommand().run(forwarded);
        }
    }

    @Command(name = "help")
    static class Help implements Callable<Integer> {

        @Option(names = "-v")
        boolean verbose;

        @Option(names = "-vv")
        boolean veryVerbose;

        @Override
        public Integer call() {
            if (veryVerbose) {
                return new HelpCommand().run(List.of("-vv"));
            }
            if (verbose) {
                return new HelpCommand().run(List.of("-v"));
            }
            return new HelpCommand().run(List.of());
        }
    }

    @Command(name = "status")
    static class Status implements Callable<Integer> {

        @Option(names = "-v")
        boolean verbose;

        @Option(names = "-vv")
        boolean veryVerbose;

        @Option(names = "-local")
        boolean local;

        @Option(names = "-remote")
        boolean remote;

        @Option(names = "-commits")
        boolean commits;

        @Option(names = "-files")
        boolean files;

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "COMMIT|GLOB|*")
        List<String> filters;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (veryVerbose) {
                forwarded.add("-vv");
            } else if (verbose) {
                forwarded.add("-v");
            }
            if (local) {
                forwarded.add("-local");
            }
            if (remote) {
                forwarded.add("-remote");
            }
            if (commits) {
                forwarded.add("-commits");
            }
            if (files) {
                forwarded.add("-files");
            }
            if (filters != null) {
                forwarded.addAll(filters);
            }
            return new StatusCommand().run(forwarded);
        }
    }

    @Command(name = "track")
    static class Track implements Callable<Integer> {

        @Option(names = "-all")
        boolean all;

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "GLOB")
        List<String> globs;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (all) {
                forwarded.add("-all");
            }
            if (globs != null) {
                forwarded.addAll(globs);
            }
            return new TrackCommand().run(forwarded);
        }
    }

    @Command(name = "untrack")
    static class Untrack implements Callable<Integer> {

        @Option(names = "-all")
        boolean all;

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "GLOB")
        List<String> globs;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (all) {
                forwarded.add("-all");
            }
            if (globs != null) {
                forwarded.addAll(globs);
            }
            return new UntrackCommand().run(forwarded);
        }
    }

    static class UsageOnlyParameterExceptionHandler implements IParameterExceptionHandler {
        @Override
        public int handleParseException(ParameterException ex, String[] args) {
            CommandLine cmd = ex.getCommandLine();
            PrintWriter err = cmd.getErr();

            String first = (args != null && args.length > 0) ? args[0] : null;
            if ("create".equals(first)) {
                err.println(Usage.create());
            } else if ("delete".equals(first)) {
                err.println(Usage.delete());
            } else if ("track".equals(first)) {
                err.println(Usage.track());
            } else if ("untrack".equals(first)) {
                err.println(Usage.untrack());
            } else if ("status".equals(first)) {
                err.println(Usage.status());
            } else {
                err.println(Usage.root());
            }

            err.flush();
            return 1;
        }
    }
}
