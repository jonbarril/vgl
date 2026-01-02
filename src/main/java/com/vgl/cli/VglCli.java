package com.vgl.cli;

import com.vgl.cli.commands.CreateCommand;
import com.vgl.cli.commands.CommitCommand;
import com.vgl.cli.commands.AbortCommand;
import com.vgl.cli.commands.CheckinCommand;
import com.vgl.cli.commands.CheckoutCommand;
import com.vgl.cli.commands.DeleteCommand;
import com.vgl.cli.commands.DiffCommand;
import com.vgl.cli.commands.HelpCommand;
import com.vgl.cli.commands.LogCommand;
import com.vgl.cli.commands.MergeCommand;
import com.vgl.cli.commands.PullCommand;
import com.vgl.cli.commands.PushCommand;
import com.vgl.cli.commands.RestoreCommand;
import com.vgl.cli.commands.SplitCommand;
import com.vgl.cli.commands.SwitchCommand;
import com.vgl.cli.commands.StatusCommand;
import com.vgl.cli.commands.SyncCommand;
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
            Abort.class,
            Checkin.class,
            Checkout.class,
            Commit.class,
            Create.class,
            Delete.class,
            Diff.class,
            Log.class,
            Merge.class,
            Pull.class,
            Push.class,
            Restore.class,
            Split.class,
            Switch.class,
            Track.class,
            Untrack.class,
            Status.class,
            Sync.class,
            Help.class
        }
    )
    static class Root {
        // Root command intentionally does not implement business logic.
    }

    @Command(name = "abort")
    static class Abort implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            return new AbortCommand().run(List.of());
        }
    }

    @Command(name = "pull")
    static class Pull implements Callable<Integer> {

        @Option(names = "-f")
        boolean force;

        @Option(names = "-noop")
        boolean noop;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (force) {
                forwarded.add("-f");
            }
            if (noop) {
                forwarded.add("-noop");
            }
            if (localRepoDir != null) {
                forwarded.add("-lr");
                forwarded.add(localRepoDir.toString());
            }
            return new PullCommand().run(forwarded);
        }
    }

    @Command(name = "push")
    static class Push implements Callable<Integer> {

        @Option(names = "-noop")
        boolean noop;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (noop) {
                forwarded.add("-noop");
            }
            if (localRepoDir != null) {
                forwarded.add("-lr");
                forwarded.add(localRepoDir.toString());
            }
            return new PushCommand().run(forwarded);
        }
    }

    @Command(name = "sync")
    static class Sync implements Callable<Integer> {

        @Option(names = "-noop")
        boolean noop;

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (noop) {
                forwarded.add("-noop");
            }
            if (force) {
                forwarded.add("-f");
            }
            if (localRepoDir != null) {
                forwarded.add("-lr");
                forwarded.add(localRepoDir.toString());
            }
            return new SyncCommand().run(forwarded);
        }
    }

    @Command(name = "restore")
    static class Restore implements Callable<Integer> {

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-rr", paramLabel = "URL")
        String remoteUrl;

        @Option(names = "-rb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String remoteBranch;

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "GLOB")
        List<String> globs;

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
            if (localBranch != null) {
                forwarded.add("-lb");
                forwarded.add(localBranch);
            }
            if (remoteUrl != null) {
                forwarded.add("-rr");
                forwarded.add(remoteUrl);
            }
            if (remoteBranch != null) {
                forwarded.add("-rb");
                forwarded.add(remoteBranch);
            }
            if (globs != null) {
                forwarded.addAll(globs);
            }
            return new RestoreCommand().run(forwarded);
        }
    }

    @Command(name = "diff")
    static class Diff implements Callable<Integer> {

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

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "GLOB|*")
        List<String> globs;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
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
            if (globs != null) {
                forwarded.addAll(globs);
            }

            return new DiffCommand().run(forwarded);
        }
    }

    @Command(name = "log")
    static class Log implements Callable<Integer> {

        @Option(names = "-v")
        boolean verbose;

        @Option(names = "-vv")
        boolean veryVerbose;

        @Option(names = "-graph")
        boolean graph;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (veryVerbose) {
                forwarded.add("-vv");
            } else if (verbose) {
                forwarded.add("-v");
            }
            if (graph) {
                forwarded.add("-graph");
            }
            return new LogCommand().run(forwarded);
        }
    }

    @Command(name = "merge")
    static class Merge implements Callable<Integer> {

        @Option(names = "-into")
        boolean into;

        @Option(names = "-from")
        boolean from;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-bb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String bothBranch;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (into) {
                forwarded.add("-into");
            }
            if (from) {
                forwarded.add("-from");
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

            return new MergeCommand().run(forwarded);
        }
    }

    @Command(name = "split")
    static class Split implements Callable<Integer> {

        @Option(names = "-into")
        boolean into;

        @Option(names = "-from")
        boolean from;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-bb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String bothBranch;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (into) {
                forwarded.add("-into");
            }
            if (from) {
                forwarded.add("-from");
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

            return new SplitCommand().run(forwarded);
        }
    }

    @Command(name = "checkout")
    static class Checkout implements Callable<Integer> {

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

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
            if (remoteUrl != null) {
                forwarded.add("-rr");
                forwarded.add(remoteUrl);
            }
            if (remoteBranch != null) {
                forwarded.add("-rb");
                forwarded.add(remoteBranch);
            }
            return new CheckoutCommand().run(forwarded);
        }
    }

    @Command(name = "checkin")
    static class Checkin implements Callable<Integer> {

        @Option(names = "-draft")
        boolean draft;

        @Option(names = "-final")
        boolean fin;

        @Option(names = "-m", paramLabel = "MESSAGE")
        String message;

        @picocli.CommandLine.Parameters(arity = "0..*", paramLabel = "GLOB|*")
        List<String> globs;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
            if (draft) {
                forwarded.add("-draft");
            }
            if (fin) {
                forwarded.add("-final");
            }
            if (message != null) {
                forwarded.add("-m");
                forwarded.add(message);
            }
            if (globs != null) {
                forwarded.addAll(globs);
            }
            return new CheckinCommand().run(forwarded);
        }
    }

    @Command(name = "commit")
    static class Commit implements Callable<Integer> {

        @Option(names = "-f")
        boolean force;

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-new", paramLabel = "MESSAGE")
        String newMessage;

        @Option(names = "-add", paramLabel = "MESSAGE")
        String addMessage;

        @picocli.CommandLine.Parameters(arity = "0..1", paramLabel = "MESSAGE")
        String message;

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

            if (newMessage != null) {
                forwarded.add("-new");
                forwarded.add(newMessage);
            }
            if (addMessage != null) {
                forwarded.add("-add");
                forwarded.add(addMessage);
            }

            if (message != null) {
                forwarded.add(message);
            }
            return new CommitCommand().run(forwarded);
        }
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

        @picocli.CommandLine.Parameters(arity = "0..1", paramLabel = "COMMAND")
        String command;

        @Override
        public Integer call() {
            List<String> forwarded = new ArrayList<>();
            if (veryVerbose) {
                forwarded.add("-vv");
            } else if (verbose) {
                forwarded.add("-v");
            }
            if (command != null && !command.isBlank()) {
                forwarded.add(command);
            }
            return new HelpCommand().run(forwarded);
        }
    }

    @Command(name = "switch")
    static class Switch implements Callable<Integer> {

        @Option(names = "-lr", paramLabel = "DIR")
        Path localRepoDir;

        @Option(names = "-lb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String localBranch;

        @Option(names = "-bb", paramLabel = "BRANCH", arity = "0..1", fallbackValue = "main")
        String bothBranch;

        @Override
        public Integer call() throws Exception {
            List<String> forwarded = new ArrayList<>();
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

            return new SwitchCommand().run(forwarded);
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

        @Option(names = {"-changes", "-commits"})
        boolean changes;

        @Option(names = "-history")
        boolean history;

        @Option(names = "-files")
        boolean files;

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
            if (changes) {
                forwarded.add("-changes");
            }
            if (history) {
                forwarded.add("-history");
            }
            if (files) {
                forwarded.add("-files");
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
            } else if ("commit".equals(first)) {
                err.println(Usage.commit());
            } else if ("abort".equals(first)) {
                err.println(Usage.abort());
            } else if ("pull".equals(first)) {
                err.println(Usage.pull());
            } else if ("push".equals(first)) {
                err.println(Usage.push());
            } else if ("sync".equals(first)) {
                err.println(Usage.sync());
            } else if ("restore".equals(first)) {
                err.println(Usage.restore());
            } else if ("diff".equals(first)) {
                err.println(Usage.diff());
            } else if ("log".equals(first)) {
                err.println(Usage.log());
            } else if ("merge".equals(first)) {
                err.println(Usage.merge());
            } else if ("split".equals(first)) {
                err.println(Usage.split());
            } else if ("checkout".equals(first)) {
                err.println(Usage.checkout());
            } else if ("checkin".equals(first)) {
                err.println(Usage.checkin());
            } else if ("track".equals(first)) {
                err.println(Usage.track());
            } else if ("untrack".equals(first)) {
                err.println(Usage.untrack());
            } else if ("status".equals(first)) {
                err.println(Usage.status());
            } else if ("switch".equals(first)) {
                err.println(Usage.switchCmd());
            } else {
                err.println(Usage.root());
            }

            err.flush();
            return 1;
        }
    }
}
