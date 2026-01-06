package com.vgl.cli.commands;

import com.vgl.cli.utils.Utils;
import java.util.List;
import java.util.Locale;

/**
 * HelpCommand displays usage information.
 * 
 * Note: Help text was refactored for compactness on 2025-12-04.
 * Original version with multi-line descriptions is preserved in git history (commit before this change).
 */
public class HelpCommand implements Command {
    @Override public String name() { return "help"; }

    @Override public int run(List<String> args) {
        ParsedArgs parsed = ParsedArgs.parse(args);
        String commandName = parsed.commandName;
        if (commandName != null) {
            System.out.println(commandHelp(commandName));
            return 0;
        }

        if (parsed.veryVerbose) {
            System.out.println(generalHelpVeryVerbose());
            return 0;
        }
        if (parsed.verbose) {
            System.out.println(generalHelpVerbose());
            return 0;
        }

        System.out.println(generalHelpDefault());
        return 0;
    }

    private static final class ParsedArgs {
        private final boolean verbose;
        private final boolean veryVerbose;
        private final String commandName;

        private ParsedArgs(boolean verbose, boolean veryVerbose, String commandName) {
            this.verbose = verbose;
            this.veryVerbose = veryVerbose;
            this.commandName = commandName;
        }

        static ParsedArgs parse(List<String> args) {
            boolean verbose = false;
            boolean veryVerbose = false;
            String commandName = null;

            for (String a : args) {
                if ("-vv".equals(a)) {
                    veryVerbose = true;
                } else if ("-v".equals(a)) {
                    verbose = true;
                } else if (a != null && !a.isBlank() && !a.startsWith("-")) {
                    // First non-flag token is treated as a command name.
                    if (commandName == null) {
                        commandName = a;
                    }
                }
            }

            return new ParsedArgs(verbose || veryVerbose, veryVerbose, commandName);
        }
    }

    private static String header() {
        return "Voodoo Gitless (" + Utils.versionFromRuntime() + ") -- Git for mortals";
    }

    private static String generalHelpDefault() {
        return String.join("\n",
            header(),
            "",
            "Usage:",
            "  vgl <command> [args]",
            "",
            "Commands:",
            "  create     Create repo/branch, then switch (prints LOCAL/REMOTE)",
            "  delete     Delete repo/branch",
            "  switch     Switch to repo/branch",
            "  split      Copy and switch to new branch",
            "  merge      Merge branches, then switch",
            "  checkout   Copy remote into CWD, then switch",
            "  copy       Copy a repo for local use",
            "  checkin    Push and create pull request",
            "",
            "  track      Add files to version control",
            "  untrack    Remove files from version control",
            "  commit     Commit workspace changes",
            "  restore    Replace working files with prior versions",
            "  pull       Merge remote changes into local branch",
            "  push       Replace remote branch from local changes",
            "  sync       Pull then push",
            "  abort      Cancel ongoing merge or pull",
            "",
            "  status     Show workspace/repo/file status",
            "  diff       Compare files between any two",
            "  log        Show commit history",
            "  help       Show help",
            "",
            "More help:",
            "  vgl help -v         Show command flags and defaults",
            "  vgl help -vv        Show a quick workflow guide",
            "  vgl help <command>  Show help for one command"
        );
    }

    private static String generalHelpVerbose() {
        StringBuilder helpText = new StringBuilder();
        helpText.append(String.join("\n",
            header(),
            "",
            "Note: See Flags (help -v/-vv) for flag combinations and defaults.",
            "",
            "Commands:",
            "  create [-f]                        Create repo/branch, then switch",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (TBD: use hosting tools)",
            "  delete [-f]                        Delete repo/branch",
            "    -lr [DIR]  -lb [BRANCH]            (local repo/branch)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (TBD: use hosting tools)",
            "  switch [-f]                        Switch to repo/branch",
            "    -lr [DIR]  -lb [BRANCH]            (local repo/branch)",
            "    -bb [BRANCH]                       (same name for local+remote)",
            "    -rr [URL]  -rb [BRANCH]            (remote repo/branch)",
            "",
            "  split -from|-into                  Copy branch, then switch to dest",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for from+into)",
            "  merge -into|-from                  Merge branches, then switch to dest",
            "    -lr [DIR]  -lb [BRANCH]            (no switch if DIR != CWD)",
            "    -bb [BRANCH]                       (same name for from+into)",
            "",
            "  copy -from|-into [-f]              Copy a repo for local use",
            "    -from: -rr [URL] or -lr [DIR]      (source repo)",
            "    -into: -lr [DIR]                   (destination directory)",
            "  checkout [-f]                      Copy remote branch into CWD",
            "    -rr [URL]  -rb [BRANCH]            (remote repo/branch)",
            "  checkin -draft|-final              Push and create pull request",
            "",
            "  track GLOB...                      Add files to version control",
            "    -all                               Track all undecided files",
            "  untrack GLOB...                    Remove files from version control",
            "  commit \"MESSAGE\"                   Commit workspace changes",
            "    -new|-add \"MESSAGE\"                Amend last commit message",
            "  restore [-f] [GLOB|*]              Replace working files with:",
            "    -lr [DIR] -lb [BRANCH] [COMMIT]    local branch/commit",
            "    -rr [URL] -rb [BRANCH] [COMMIT]    remote branch/commit",
            "  pull [-f] [-noop]                  Merge remote changes into local branch",
            "  push [-noop]                       Replace remote branch from local changes",
            "  sync [-noop]                       Pull then push",
            "  abort                              Cancel ongoing merge or pull",
            "",
            "  status [-v|-vv]                    Show workspace/repo/file status",
            "    [-local][-remote][-changes][-history][-files] Show only some sections",
            "  diff [GLOB|*]                      Compare files between any two:",
            "    -lr [DIR] -lb [BRANCH] [COMMIT]    local branch/commit",
            "    -bb BRANCH                         (same name for local+remote)",
            "    -rr [URL] -rb [BRANCH] [COMMIT]    remote branch/commit",
            "  log [-v|-vv] [-graph]              Show commit history",
            "  help [-v|-vv] [COMMAND]            Show help (or help for one command)"
        ));

        helpText.append("\n\nFlags:\n");
        helpText.append(String.join("\n",
            "  Note: If no repo/branch (-lr, -lb, -rr, -rb) is specified then the corresponding",
            "        switch state is used. If a switch state is not set or resolved DIR defaults",
            "        to the current local repo (resolved from the CWD), local and remote BRANCH",
            "        default to 'main', and URL has no default. Branch flags can accept no name,",
            "        a BRANCH and/or a COMMIT id in the branch.",
            "",
            "  CWD           Current workspace directory",
            "  -lr DIR       Local repository directory",
            "  -lb BRANCH    Local branch (default: 'main')",
            "  -rr URL       Remote repository URL",
            "  -rb BRANCH    Remote branch (default: 'main')",
            "  -bb BRANCH    Both local and remote branch (default: 'main')",
            "  -f            Bypass confirmation prompts",
            "  -noop         Dry run (no changes)",
            "  -v, -vv       Verbose output (more detail)"
        ));

        helpText.append("\n\nGlob Patterns:\n");
        helpText.append(String.join("\n",
            "  *.log              All .log files         -> app.log, build.log",
            "  file?.txt          Single-char wildcard   -> file1.txt, fileA.txt",
            "  **/*.py            Recursive search       -> main.py, lib/util.py",
            "  *.{png,jpg}        Multiple extensions    -> cat.png, dog.jpg"
        ));

        return helpText.toString();
    }

    private static String generalHelpVeryVerbose() {
        return generalHelpVerbose()
            + "\n\nUsage:\n"
            + String.join("\n",
                "  Working Locally:",
                "    - Use 'create -lr DIR' to make a new repo (defaults to branch 'main').",
                "    - Or 'switch -lr DIR -lb BRANCH' to work in an existing repo and branch.",
                "    - Your workspace holds your files, where changes stay until you 'commit' them.",
                "    - Use 'split' for experiments, 'merge' to combine, 'delete' to clean up.",
                "",
                "  Remote Collaboration (Optional):",
                "    - Use 'switch -rr URL -rb BRANCH' to connect to a remote repo.",
                "    - 'push' sends local commits; 'pull' fetches and merges remote changes.",
                "    - 'sync' does both. 'checkin' creates a pull request (PR) for review.",
                "",
                "  Inspecting Your Work:",
                "    - 'status' shows switch state, uncommitted changes, sync state, and files.",
                "    - 'diff' reviews changes or compares branches.",
                "    - 'log' shows commit history.",
                "    - 'restore' undoes changes, returning files to last commit."
            );
    }

    private static String commandHelp(String rawCommand) {
        String command = rawCommand.toLowerCase(Locale.ROOT);
        return switch (command) {
            case "help" -> String.join("\n",
                header(),
                "",
                "help -- Show help for all commands, or one command",
                "",
                "Usage:",
                "  vgl help [-v|-vv] [COMMAND]",
                "",
                "Notes:",
                "  - 'vgl help' is short and beginner-friendly.",
                "  - 'vgl help -v' shows flags and defaults.",
                "  - 'vgl help <command>' shows focused help for one command."
            );
            case "status" -> String.join("\n",
                header(),
                "",
                "status -- Show workspace/repo/file status",
                "",
                "Usage:",
                "  vgl status [-v|-vv] [-local] [-remote] [-changes] [-history] [-files]",
                "",
                "Options:",
                "  -v, -vv     Verbose output (more detail)",
                "  -local      Show LOCAL section",
                "  -remote     Show REMOTE section",
                "  -changes    Show CHANGES section",
                "  -history    Show HISTORY section",
                "  -files      Show FILES section",
                "",
                "Notes:",
                "  - If no section flags are specified, all sections are shown."
            );
            case "create" -> String.join("\n",
                header(),
                "",
                "create -- Create repo/branch, then switch (prints LOCAL/REMOTE)",
                "",
                "Usage:",
                "  vgl create [-f] [-lr DIR] [-lb BRANCH|-bb BRANCH]",
                "",
                "Options:",
                "  -f                 Bypass confirmation prompts",
                "  -lr DIR            Local repository directory (no switch if DIR != CWD)",
                "  -lb BRANCH         Local branch (default: 'main')",
                "  -bb BRANCH         Same name for local+remote (default: 'main')",
                "  -rr URL            Remote repository URL (TBD: use hosting tools)",
                "  -rb BRANCH         Remote branch (default: 'main')"
            );
            case "delete" -> String.join("\n",
                header(),
                "",
                "delete -- Delete repo/branch",
                "",
                "Usage:",
                "  vgl delete [-f] [-lr DIR] [-lb BRANCH|-bb BRANCH]",
                "",
                "Options:",
                "  -f                 Bypass confirmation prompts",
                "  -lr DIR            Local repository directory",
                "  -lb BRANCH         Local branch (default: 'main')",
                "  -bb BRANCH         Same name for local+remote (default: 'main')",
                "  -rr URL            Remote repository URL (TBD: use hosting tools)",
                "  -rb BRANCH         Remote branch (default: 'main')"
            );
            case "switch" -> String.join("\n",
                header(),
                "",
                "switch -- Switch to repo/branch",
                "",
                "Usage:",
                "  vgl switch [-f] [-lr DIR] [-lb BRANCH|-bb BRANCH] [-rr URL] [-rb BRANCH]",
                "",
                "Options:",
                "  -lr DIR            Local repository directory",
                "  -lb BRANCH         Local branch (default: 'main')",
                "  -bb BRANCH         Same name for local+remote (default: 'main')",
                "  -rr URL            Remote repository URL",
                "  -rb BRANCH         Remote branch (default: 'main')",
                "  -f                 Bypass confirmation prompts"
            );
            case "track" -> String.join("\n",
                header(),
                "",
                "track -- Add files to version control",
                "",
                "Usage:",
                "  vgl track <glob...>",
                "  vgl track -all",
                "",
                "Options:",
                "  -all               Track all undecided file"
            );
            case "untrack" -> String.join("\n",
                header(),
                "",
                "untrack -- Remove files from version control",
                "",
                "Usage:",
                "  vgl untrack <glob...>",
                "  vgl untrack -all",
                "",
                "Options:",
                "  -all               Untrack all files"
            );
            case "commit" -> String.join("\n",
                header(),
                "",
                "commit -- Commit workspace changes",
                "",
                "Usage:",
                "  vgl commit [-f] [-lr DIR] MESSAGE",
                "  vgl commit [-f] [-lr DIR] -new MESSAGE",
                "  vgl commit [-f] [-lr DIR] -add MESSAGE",
                "",
                "Options:",
                "  -f                 Bypass confirmation prompts",
                "  -lr DIR            Local repository directory",
                "  -new MESSAGE       Amend last commit message",
                "  -add MESSAGE       Amend last commit message",
                "",
                "Notes:",
                "  - If undecided files exist, commit can prompt:",
                "    Abort / Continue / Track-all then continue."
            );
            case "restore" -> String.join("\n",
                header(),
                "",
                "restore -- Replace working files with prior versions",
                "",
                "Usage:",
                "  vgl restore [-f] [GLOB|*] [-lr DIR] [-lb BRANCH] [-rr URL] [-rb BRANCH]",
                "",
                "Notes:",
                "  - Use -f to bypass confirmation prompts.",
                "  - Use branch flags to select local/remote source." 
            );
            case "diff" -> String.join("\n",
                header(),
                "",
                "diff -- Compare files between any two sources",
                "",
                "Usage:",
                "  vgl diff [GLOB|*] [-lr DIR] [-lb BRANCH|-bb BRANCH] [-rr URL] [-rb BRANCH]",
                "",
                "Notes:",
                "  - Use -bb to compare the same branch name local vs remote."
            );
            case "log" -> String.join("\n",
                header(),
                "",
                "log -- Show commit history",
                "",
                "Usage:",
                "  vgl log [-v|-vv] [-graph]",
                "",
                "Options:",
                "  -v, -vv     Verbose output (more detail)",
                "  -graph      Show ASCII graph of history"
            );
            case "pull" -> String.join("\n",
                header(),
                "",
                "pull -- Merge remote changes into local branch",
                "",
                "Usage:",
                "  vgl pull [-f] [-noop] [-lr DIR]",
                "",
                "Options:",
                "  -f          Bypass confirmation prompts",
                "  -noop       Dry run (no changes)",
                "  -lr DIR     Local repository directory"
            );
            case "push" -> String.join("\n",
                header(),
                "",
                "push -- Replace remote branch from local changes",
                "",
                "Usage:",
                "  vgl push [-noop] [-lr DIR]",
                "",
                "Options:",
                "  -noop       Dry run (no changes)",
                "  -lr DIR     Local repository directory"
            );
            case "sync" -> String.join("\n",
                header(),
                "",
                "sync -- Pull then push",
                "",
                "Usage:",
                "  vgl sync [-f] [-noop] [-lr DIR]",
                "",
                "Options:",
                "  -f          Bypass confirmation prompts",
                "  -noop       Dry run (no changes)",
                "  -lr DIR     Local repository directory"
            );
            case "abort" -> String.join("\n",
                header(),
                "",
                "abort -- Cancel ongoing merge or pull",
                "",
                "Usage:",
                "  vgl abort"
            );
            case "merge" -> String.join("\n",
                header(),
                "",
                "merge -- Merge branches, then switch",
                "",
                "Usage:",
                "  vgl merge -from|-into [-lr DIR] [-lb BRANCH|-bb BRANCH]",
                "",
                "Notes:",
                "  - Use -from when you are on the destination branch.",
                "  - Use -into when you are on the source branch."
            );
            case "split" -> String.join("\n",
                header(),
                "",
                   "split -- Copy branch, then switch",
                "",
                "Usage:",
                    "  vgl split -from|-into [-lr DIR] [-lb BRANCH|-bb BRANCH]",
                "",
                "Notes:",
                "  - Use -into when you are on the source branch.",
                "  - Use -from when you are on the destination branch."
            );
            case "checkout" -> String.join("\n",
                header(),
                "",
                   "checkout -- Copy remote into CWD, then switch",
                "",
                "Usage:",
                "  vgl checkout [-f] -rr URL [-rb BRANCH]",
                "",
                "Options:",
                "  -f          Bypass confirmation prompts",
                "  -rr URL     Remote repository URL",
                "  -rb BRANCH  Remote branch (default: 'main')"
            );
            case "copy" -> String.join("\n",
                header(),
                "",
                "copy -- Copy a repo for local use",
                "",
                "Usage:",
                "  vgl copy -into [-f] -lr DIR",
                "  vgl copy -from [-f] (-lr DIR | -rr URL) [-rb BRANCH]",
                "",
                "Notes:",
                "  - copy preserves commit history; it does not rewrite history.",
                "  - The copied repo is configured as local-only in .vgl (no remote)."
            );
            case "checkin" -> String.join("\n",
                header(),
                "",
                "checkin -- Push and create pull request",
                "",
                "Usage:",
                "  vgl checkin -draft|-final [-m MESSAGE] [GLOB|*]",
                "",
                "Notes:",
                "  - -draft creates a draft PR; -final creates a ready-for-review PR."
            );
            default -> String.join("\n",
                header(),
                "",
                "Unknown command: " + rawCommand,
                "",
                "Try:",
                "  vgl help",
                "  vgl help -v"
            );
        };
    }
}
