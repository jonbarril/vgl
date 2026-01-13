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
            "Voodoo Gitless (" + Utils.versionFromRuntime() + ") — Git for mortals",
            "",
            "vgl hides Git’s complexity so you can focus on your work.",
            "",
            "",
            "Key concepts:",
            "",
            "  • A repo is a folder tree tracked by vgl.",
            "  • Your workspace is the files in that folder tree.",
            "  • A local repo lives on your computer.",
            "  • A remote repo lives elsewhere and is optional.",
            "  • Changes persist only when you commit.",
            "  • Collaboration is explicit.",
            "",
            "",
            "Common commands:",
            "",
            "Workspace & context:",
            "  create      Create a repo or branch",
            "  switch      Change branch or remote context",
            "  status      Show workspace and sync state",
            "",
            "Local work:",
            "  track       Add files to version control",
            "  commit      Save workspace changes to history",
            "  split       Branch and start independent work",
            "  merge       Combine local branches",
            "",
            "Remote collaboration:",
            "  checkout    Get a remote repo to work on",
            "  pull        Review and merge remote changes",
            "  push        Send local commits to a remote",
            "  checkin     Share work for remote review",
            "  sync        Pull then push",
            "",
            "Review & recovery:",
            "  diff        Compare changes",
            "  log         Show commit history",
            "  restore     Undo workspace changes",
            "",
            "",
            "Get help:",
            "  vgl help -v          Command syntax and options",
            "  vgl help -vv         Concepts and workflows",
            "  vgl help <command>   Help for a specific command"
        );
    }

    private static String generalHelpVerbose() {
        return String.join("\n",
            header(),
            "",
            "Command Reference",
            "",
            "Context commands:",
            "  create [-f] [-lr DIR] [-lb BRANCH | -bb BRANCH]",
            "  delete [-f] [-lr DIR] [-lb BRANCH | -bb BRANCH]",
            "  switch [-lb BRANCH | -bb BRANCH] [-rr URL] [-rb BRANCH]",
            "  status [-v|-vv] [-context] [-local] [-remote [URL]] [-changes] [-history] [-files]",
            "",
            "Local work:",
            "  track GLOB... | -all",
            "  untrack GLOB... | -all",
            "  commit [-f] MESSAGE | -new MESSAGE | -add MESSAGE",
            "  split [-f] [-noop] [-from [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
            "                     [-into [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
            "  merge [-f] [-noop] [-from [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
            "                     [-into [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
            "  restore [-f] [GLOB] {-lr DIR [-lb BRANCH] | -rr URL [-rb BRANCH]}",
            "",
            "Remote collaboration:",
            "  checkout [-f] -rr URL [-rb BRANCH]",
            "  copy {-from {-lr DIR | -rr URL [-rb BRANCH]} | -into -lr DIR} [-f]",
            "  checkin {-draft | -final}",
            "  pull [-f] [-noop]",
            "  push [-noop]",
            "  sync [-f] [-noop]",
            "  abort",
            "",
            "Review:",
            "  diff [-noop] [GLOB] [-lr DIR]... [-lb BRANCH]... [-rr URL]... [-rb BRANCH]...",
            "  diff [-noop] COMMIT1 COMMIT2 [GLOB]",
            "  log [-v|-vv] [-graph] [COMMIT]",
            "",
            "Flag reference:",
            "  -f            Force; bypass confirmation prompts",
            "  -noop         Dry run; preview changes without applying",
            "  -v, -vv       Verbose output (increasing detail)",
            "  -lr DIR       Local repository directory (default: CWD repo)",
            "  -lb BRANCH    Local branch (default: 'main' or switch state)",
            "  -rr URL       Remote repository URL (default: switch state)",
            "  -rb BRANCH    Remote branch (default: 'main' or switch state)",
            "  -bb BRANCH    Same branch name local+remote (exclusive with -lb/-rb)",
            "",
            "Notes:",
            "  - Commands use switch state for default repo/branch when omitted",
            "  - CWD determines local repo; use 'cd' to change context",
            "  - Flag -bb is mutually exclusive with -lb and -rb",
            "  - Branch flags accept BRANCH or COMMIT id",
            "  - Glob patterns: *.ext, file?.txt, **/*.py, *.{a,b}",
            "",
            "Get more help:",
            "  vgl help -vv         Show concepts and workflows",
            "  vgl help <command>   Show detailed help for one command"
        );
    }

    private static String generalHelpVeryVerbose() {
        return String.join("\n",
            header(),
            "",
            "Concepts and Workflows",
            "",
            "Understanding vgl:",
            "",
            "  Workspace",
            "    - Files under the repo root (determined by your CWD).",
            "    - Changes live in the workspace until you 'commit' them.",
            "",
            "  Local repo",
            "    - The Git repo on your machine, inferred from CWD.",
            "",
            "  Context (switch state)",
            "    - Current local repo directory and branch name.",
            "    - Current remote repo URL and branch name (optional).",
            "    - Use 'switch' to show or set context.",
            "",
            "  Undecided files",
            "    - Files not yet explicitly tracked, untracked, or ignored.",
            "    - Applies to new and existing files.",
            "    - Once decided, cannot revert to undecided.",
            "",
            "  Command guarantees",
            "    - status: observe only; never changes workspace files",
            "    - switch: sets context; -lb switches files; -rr/-rb never switch files",
            "    - commit: saves workspace changes to local history only",
            "    - checkout: creates a working copy of a remote repo",
            "    - pull: reviews remote changes before applying; never auto-commits",
            "    - push/checkin: send local commits to a remote",
            "    - split/merge: local history operations only",
            "",
            "Typical workflows:",
            "",
            "  Working locally:",
            "    - Track files you want under version control.",
            "    - Commit to create history.",
            "    - Use split/merge to experiment on branches.",
            "",
            "  Collaborating with a remote:",
            "    - Use checkout to materialize a remote repo.",
            "    - Use switch -rr/-rb to set remote context.",
            "    - Pull to merge remote changes, then commit, then push.",
            "",
            "Examples for non-obvious commands:",
            "",
            "  switch -> changes context, may change workspace",
            "    vgl switch -lb feature          # Switch branch (switches files)",
            "    vgl switch -rr https://... -rb dev  # Set remote (no file switch)",
            "    vgl switch -bb experiment       # Set local+remote branch",
            "",
            "  checkout -> materializes remote repo",
            "    vgl checkout -rr https://github.com/user/repo  # Copy repo",
            "    vgl checkout -rr https://... -rb dev  # Copy branch only",
            "",
            "  split -> branch + switch",
            "    vgl split -into -lb experiment  # Create from current branch",
            "    vgl split -from -lb main        # Create from 'main' branch",
            "",
            "  merge -> combine + switch",
            "    vgl merge -from -lb feature  # Merge into current branch",
            "    vgl merge -into -lb main     # Merge into 'main' branch",
            "",
            "Get specific help:",
            "  vgl help <command>   Show detailed syntax and notes for one command"
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
                "  vgl status [-v|-vv]",
                "  vgl status [-v|-vv] [-context] [-local] [-remote [URL]] [-changes] [-history] [-files]",
                "",
                "Options:",
                "  -v, -vv         Verbose output (more detail)",
                "  -context        Show LOCAL+REMOTE section only",
                "  -local          Show LOCAL section only",
                "  -remote         Show REMOTE section from switch state",
                "  -remote URL     List remote branches at URL (or shorthand)",
                "  -changes        Show CHANGES section only",
                "  -history        Show HISTORY section only",
                "  -files          Show FILES section only",
                "",
                "Remote discovery:",
                "  vgl status -remote https://github.com/org/repo  # List branches",
                "  vgl status -remote otherRepo                   # Uses current remote base",
                "  Then: vgl switch -rr URL -rb BRANCH            # Connect to remote",
                "",
                "Notes:",
                "  - status never changes files; it only reports",
                "  - Undecided files exist in the workspace, but have not yet been explicitly",
                "    tracked, untracked, or ignored.",
                "  - If no section flags specified, all sections shown",
                "  - -remote URL is best-effort; if ambiguous, provide full URL"
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
                "  -bb BRANCH         Local branch (default: 'main')"
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
                "  -bb BRANCH         Local branch (default: 'main')"
            );
            case "switch" -> String.join("\n",
                header(),
                "",
                "switch -- Set branch/remote context (local repository from CWD)",
                "",
                "Usage:",
                "  vgl switch",
                "  vgl switch [-lb BRANCH|-bb BRANCH] [-rr URL] [-rb BRANCH]",
                "",
                "Options:",
                "  -lb BRANCH         Local branch (default: 'main')",
                "  -bb BRANCH         Same name for local+remote (default: 'main')",
                "  -rr URL            Remote repository URL",
                "  -rb BRANCH         Remote branch (default: 'main')",
                "",
                "Notes:",
                "  - With no args, switch prints the current context",
                "  - switch changes context; it does not copy files",
                "  - Changing -lb may change workspace files",
                "  - Changing -rr/-rb does not change workspace files"
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
                "commit -- Commit workspace changes to local repo",
                "",
                "Usage:",
                "  vgl commit [-f] MESSAGE",
                "  vgl commit [-f] -new MESSAGE",
                "  vgl commit [-f] -add MESSAGE",
                "",
                "Options:",
                "  MESSAGE            Commit message (required, must be last argument)",
                "  -new MESSAGE       Amend last commit with new message",
                "  -add MESSAGE       Amend last commit with additional changes",
                "  -f                 Bypass confirmation prompts",
                "",
                "Notes:",
                "  - Commits workspace to current repo/branch from switch state",
                "  - No -lr/-lb/-rr/-rb flags; uses switch state only",
                "  - MESSAGE must be the last argument",
                "  - Undecided files: prompts Abort/Continue/Track-all"
            );
            case "restore" -> String.join("\n",
                header(),
                "",
                "restore -- Replace working files with prior versions",
                "",
                "Usage:",
                "  vgl restore [-f] [GLOB]",
                "  vgl restore [-f] [GLOB] -lr DIR [-lb BRANCH]",
                "  vgl restore [-f] [GLOB] -rr URL [-rb BRANCH]",
                "",
                "Options:",
                "  GLOB               File pattern (default: restore all tracked files)",
                "  -lr DIR            Local repo source (default: CWD repo)",
                "  -lb BRANCH         Local branch (default: 'main' or single branch)",
                "  -rr URL            Remote repo source",
                "  -rb BRANCH         Remote branch (default: 'main' or single branch)",
                "  -f                 Force restore without confirmation",
                "",
                "Notes:",
                "  - Local and remote sources are mutually exclusive (use only one)",
                "  - Default source is HEAD of current branch",
                "  - Use glob patterns: *.java, src/**/*.py"
            );
            case "diff" -> String.join("\n",
                header(),
                "",
                "diff -- Compare files between any two sources",
                "",
                "Usage:",
                "  vgl diff [-noop] [GLOB]",
                "  vgl diff [-noop] [GLOB] [-lr DIR]... [-lb BRANCH]... [-rr URL]... [-rb BRANCH]...",
                "  vgl diff [-noop] COMMIT1 COMMIT2 [GLOB]",
                "",
                "Options:",
                "  GLOB               File pattern (default: all files)",
                "  -lr DIR            Local repo for comparison (can repeat)",
                "  -lb BRANCH         Local branch (can repeat)",
                "  -rr URL            Remote repo for comparison (can repeat)",
                "  -rb BRANCH         Remote branch (can repeat)",
                "  -noop              Show summary counts only",
                "  COMMIT1/2          Compare two specific commits",
                "",
                "Examples:",
                "  vgl diff                            Workspace vs HEAD",
                "  vgl diff -lb main -lb feature       Local branch comparison",
                "  vgl diff -rr URL -rb dev            Workspace vs remote",
                "  vgl diff -rr URL1 -rr URL2          Remote-to-remote",
                "",
                "Notes:",
                "  - Default compares workspace with one other source",
                "  - Use -noop to see change counts without detailed diff"
            );
            case "log" -> String.join("\n",
                header(),
                "",
                "log -- Show commit history",
                "",
                "Usage:",
                "  vgl log [-v|-vv] [-graph] [COMMIT]",
                "",
                "Options:",
                "  -v              Show more commits (still one-line summaries)",
                "  -vv             Show a full patch for the most recent commit (or COMMIT)",
                "  -graph          Show ASCII graph of history",
                "",
                "Notes:",
                "  - Default output is a short, human-readable list",
                "  - Tip: Run 'vgl log <commit> -vv' for a specific commit patch"
            );
            case "pull" -> String.join("\n",
                header(),
                "",
                "pull -- Merge remote changes into local branch",
                "",
                "Usage:",
                "  vgl pull [-f] [-noop]",
                "",
                "Options:",
                "  -f          Bypass confirmation prompts",
                "  -noop       Dry run (no changes)"
            );
            case "push" -> String.join("\n",
                header(),
                "",
                "push -- Replace remote branch from local changes",
                "",
                "Usage:",
                "  vgl push [-noop]",
                "",
                "Options:",
                "  -noop       Dry run (no changes)"
            );
            case "sync" -> String.join("\n",
                header(),
                "",
                "sync -- Pull then push",
                "",
                "Usage:",
                "  vgl sync [-f] [-noop]",
                "",
                "Options:",
                "  -f          Bypass confirmation prompts",
                "  -noop       Dry run (no changes)"
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
                "merge -- Merge branches, then switch to destination",
                "",
                "Usage:",
                "  vgl merge [-f] [-noop] [-from [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
                "                         [-into [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
                "",
                "Options:",
                "  -from              Source branch (default: current branch)",
                "  -into              Destination branch (default: current branch)",
                "  -lr DIR            Local repo (default: CWD repo)",
                "  -lb BRANCH         Local branch (default: 'main' or single branch)",
                "  -bb BRANCH         Same branch name (exclusive with -lb and -rb)",
                "  -noop              Preview merge without applying changes",
                "  -f                 Bypass confirmation prompts",
                "",
                "Notes:",
                "  - Specify -from, -into, or both to define source/destination",
                "  - If only -from: destination is current branch",
                "  - If only -into: source is current branch",
                "  - If both: explicit source and destination",
                "  - Each -from/-into can have its own -lr/-lb/-bb flags",
                "  - -bb sets both local and remote branch to same name",
                "  - Shows merge preview with conflict counts before applying",
                "  - Switches to destination branch after merge"
            );
            case "split" -> String.join("\n",
                header(),
                "",
                "split -- Copy branch, then switch to destination",
                "",
                "Usage:",
                "  vgl split [-f] [-noop] [-from [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
                "                         [-into [-lr DIR] [-lb BRANCH | -bb BRANCH]]",
                "",
                "Options:",
                "  -from              Source branch (default: current branch)",
                "  -into              Destination branch (default: current branch)",
                "  -lr DIR            Local repo (default: CWD repo)",
                "  -lb BRANCH         Local branch (default: 'main' or single branch)",
                "  -bb BRANCH         Same branch name (exclusive with -lb and -rb)",
                "  -noop              Preview what would be split",
                "  -f                 Bypass confirmation prompts",
                "",
                "Notes:",
                "  - Specify -from, -into, or both to define source/destination",
                "  - If only -from: destination is current branch",
                "  - If only -into: source is current branch",
                "  - If both: explicit source and destination",
                "  - Each -from/-into can have its own -lr/-lb/-bb flags",
                "  - -bb sets both local and remote branch to same name",
                "  - Switches to destination branch after split"
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
                "Options:",
                "  -draft               Creates a draft pull request (PR)",
                "  -final               Creates a ready-to-review pull request (PR)"
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
