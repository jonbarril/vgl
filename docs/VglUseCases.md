```markdown
# VGL ΓÇö Use Cases and Expected Behavior

This document captures concrete user-facing use cases, edge cases, and the expected VGL behavior. Keep concise, grouped by topic for easy reference for design and test.

**Overview:**
- **Purpose:** Describe how VGL should work with emphasis on a user's perspective. Describes both main use cases as well as edge cases as they are discovered and addressed. User messaging and actions are kept general as the wording and specifics may change with refinement of design.
- **Scope:** This document is intended to be consistent with the text in the help command, which describes general command behavior and usage. For commands and use cases that require further elaboration they will be detailed here.
- **Audience:** CLI maintainers, tests, and contributors implementing command a  and system logic.


**VGL State and Arguments:**
- **Vgl Concepts:** VGL concepts are loosely based on Gitless. Unlike Git, in Vgl there is no concept of staging. File changes in the working space are immediately eligible for commit to the local repo. As such Vgl files exist in three conceptual locations: workspace, local repo, and remote repo, with remote repo being optional. Users are allowed to operate using Git directly if desired, moving between Vgl and Git as needed. Thus Vgl must tolerate underlying changes to Git state. However, as with Gitless, the intent of Vgl is to divorce the user from the complexities and pitfalls of Git concepts and operation.
- **VGL state:** VGL application state is maintained in the current repo `.vgl` file. The current VGL repo is always the one resolved from the user's current working directory (unless a command arg specifies one). 
- **Repo context:** Specifies the working local and remote repo state. Also called the "switch state" as it is set and changed using the switch command.
- Consists of the local repo path and branch name, and the remote repo URL and branch name.
- By definition, the local repo is that resolved from the user current working directory.
- **Repo Workspace**
- The workspace of a repo (local or remote) consists of all files in that repo's root directory (i.e. directory containing a .git file) subtree.
- Files inside nested repositories (directories that contain their own `.git`) are treated as Ignored for parent-level commands (e.g., status, glob expansion).
- By changing local working directory the user may move up or down nested repo trees, with the current local repo being the closest ancestor repo resolved from the current working directory.
- **Default args:** The default branch (i.e. no branch name specified) for local and remote repo creation is "main". Otherwise, commands that reference a local and/or remote repo/branch default to the current repo context in the VGL state.
- **File args:** Glob expansion and file listings must be bounded to the repository (do not walk the entire filesystem).
- **.vgl treatment:** By default, `.vgl` is included in `.gitignore` when a VGL repo is created. As such, it should be treated as Ignored.
- **Internal state:** The `.vgl` file may also store internal state such as undecided files, but this is not typically surfaced to the user.

**Repository Resolution:**
- **Overview:** When VGL requires the local VGL repo, VGL searches upward from the target directory, which may be the current working directory, and stops at the first directory that contains either a `.git` or a `.vgl`.
- **Behavior summary (examples):**
  - Both `.git` and `.vgl`: If both are valid and consistent, then the directory is considered a valid VGL repo and the search suceeds. If invalid, warn the user, note the location and problem, suggest the user resolve the problem outside of VGL, and the search fails.
  - `.git` only: If valid, warn the user and offer to convert the Git repo into a VGL repo (create `.vgl`). If not converted, `.git` is not deleted, and it still counts as a repo for nesting warnings. In non-interactive contexts, print a short hint and fail the search.
  - `.vgl` only: If valid, warn the user and offer to initialize a Git repository from the `.vgl` state (interactive). In non-interactive contexts, print a short hint and fail the search.
  - Neither found up to filesystem root: Warn and fail the search.
  **Validation:** An invalid .git/.vgl state is one where either references local or remote repos or branches that do not exist or are inaccessible. If both .git and .vgl are present, then their states must be consistent, with local and remote repo and branch matching.
- **Test Ceiling:** In test or CI, discovery honors `-Dvgl.test.base` to avoid searching above a configured test base directory. This is for safety. Otherwise commands will not be restricted during normal use.

**Repository creation**
- Creation occurs relative to a target directory, which can be the current working directory. It starts with repo resolution relative to the target directory.
- If after resolution the target directory is nested under a git or vgl repo the user is warned and offered the option to continue creation.
- If a VGL repo does not exist, one will be created in the target directory, consisting of a git repository (.git and .gitignore) and a .vgl file to maintain VGL specific state.
- If a VGL repo already exists in the target directory the user is warned and creation exits.
- Upon repo creation .gitignore shall include specs for typically ignored files and directories in a repo (build products, etc.).
- Repo admin files (.git, .vgl) are always ignored and cannot be tracked, which is consistent with Gitless and Git.
- If creation or branch creation/switch results in changing the repo context resolved from the CWD, VGL prints the new switch state as the non-verbose LOCAL and REMOTE lines (same format as `status` default mode).
- If a command targets a different local repo that does not resolve from the user’s CWD, the local switch state does not change and no switch-state output is printed.
- In that case, VGL prints a concise warning to `stderr` indicating switch state is unchanged and suggesting the user `cd` to the target repo.

**Repository deletion**
- Deletion occurs relative to a target directory, which can be the current working directory. It starts with repo resolution relative to the target directory.
- If a Vgl or Git repo exist at the target the user will be asked to confirm its deletion (otherwise the user is warned that none exists there).
- If uncommitted changes and/or unpushed commits exist the user will be warned and asked to proceed or not.
- If repo deletion is confirmed the user will be asked if the content should also be deleted. If so then the target directory should be deleted using system commands.

**Context switching**
- The switch command allows the local and/or remote repo and/or branch to be specified, which is called the "switch state".
- However, since having a command change the CWD is not recommended switch does not support local repo specification. Instead it is always resolved from the CWD.
- Local branch switching may change the workspace contents, but remote repo or branch switching will not.
- In general a remote repo is speciified by repo URL. However, if a remote URL context has been established (by .git or by a previous URL spec) then the remote repo need only be named (e.g. instead of "github.com/myname/myrepo" I could simply say "myrepo" if the URL "github/myname" is already established).

**Workspace commit**
- Only 'tracked' files can be committed to the local repo.
- When trying to commit changes the user will first be warned if any files in the repo are undecided, with a hint/choice to use "track -all" to track all undecided files.

**Workspace merge**
- When files are merged in the workspace the user is always in control of the process and the actions are observable and consistent. 
- A merge (e.g. as the result of a pull) occurs in two phases:
  - The system analyzes and reports potential workspace changes from the merge, including the number of source commits, the number of files affected, and the number of files with conflicts (i.e. cannot be auto-merged), the number of files without conflicts.
  - The user is prompted to approve the merge, reject the merge, or to see details. If details then the output will be expanded with each summary count as a section followed by the affected files with change letters.
- Once a merge is complete it is the user's responsibility to commit it to the local repo (i.e. no auto-commit).
- Commands that involve merging (or any potential to modify workspace files) will have a -noop flag, which performs a "dry run". If -v is included then the output will be the same as the detailed ouput above.

**Status command:**
  - **Overview:** Provides the overall status of workspace, and local and remote repo files. More detail is progressively revealed with the use of verbose flags (-v, -vv). Output also can be filtered using section name flags (e.g. -local). Files in a VGL repo are classified as follows:
    - All files in the workspace of a new repo, or added to an existing one, default to 'undecided' unless...
      - Files are 'ignored' by default if they resolve from the glob specs in the repo .gitignore file.
      - Nested repos and their files are also ignored by default (i.e. never in undecided/tracked/untrack lists). Besides being indicated as a directory in the ignored list a repo will be decorated (@ <REPO_ROOT_PATH>/).
    - Files can be 'tracked' or 'untracked' by the user with the corresponding commands.
    - Tracking overrides a file's ignored status (but its status reverts to ignored if it is untracked).
    - Once an undecided file is tracked, untracked or ignored it is no longer undecided (there is no way to make a decided file undecided again).
    - File categories (Added/Modified/Deleted/Renamed/Undecided/Tracked/Untracked/Ignored) and counts are as defined by VGL, not by Git, although there may be overlap.
    - Directories that appear in file lists include a trailing "/" indicating it is a directory and not just a file.
  - **Default:** Default behavior is when neither -v or -vv flags are present. This prints the minimal status. Each section includes a one or two line summary of an aspect of repo status. As needed paths and branch names will be shortened using elipses so that the format remains consistent and column aligned.
  - LOCAL shows the current local repo and branch (or '(none)' for each).
  - REMOTE shows the current remote repo and branch (or '(none)' for each).
  - CHANGES shows summary counts for: Files to commit, commits to push, commits to pull.
  - HISTORY shows summary counts for Local commits, Remote commits.
  - FILES shows summary file counts for: Undecided, Tracked, Untracked, Ignored.
  - **Verbose:** This is when -v is present but -vv is not. Same as Default mode but paths and file names are indicated in full regardless of column formatting.
  - LOCAL and REMOTE include branch list subsections, with the current branch (corresponding to that in the summary) decorated (* <BRANCH>).
  - CHANGES adds subsections for Files to Commit (with file change letters), with count matching that for files to commit.
  - HISTORY adds subsection for Local-only and Remote-only commits, shown as hash and truncated message on a single line, with counts matching summary counts.
  - FILES adds subsection for 'Undecided Files', with count matching summary count.
  - **Very Verbose:** This is when -vv is present.
  - All commit entries show hash, date, author, and full message.
  - CHANGES adds subsections for 'Commits to Push' and 'Commits to Pull', with counts matching summary counts.
  - FILES adds subsections for 'Tracked Files', 'Untracked Files' and 'Ignored Files', with counts matching summary counts.
  - **Section Flags** The output can be filtered to show only requested sections by including one or more section flags (in addition to -v and -vv): -local, -remote, -changes, -history, -files.
    - The -remote flag can accept an optional URL argument, in which case all remote repos and their branches discoverable at the URL are reported.

**Help command**
-Goal
  - Rewrite the vgl help output to be user-centric, predictable, and low-cognitive-load, while accurately reflecting vgl’s actual behavior.
  - The help file is a primary source of truth and must explain intent and invariants, not implementation details.

-Core Principles (must include)
  - Progressive disclosure
    - Help default: invariants and intent only
    - Help -v: command reference and syntax
    - Help -vv: concepts, workflows, and examples
    - Avoid duplication across levels
  - User intent over Git internals
    - Describe what users do and why
    - Avoid Git terminology unless unavoidable
    - Hide Git’s remote/tracking complexity
  - No surprises
    - Never imply automatic file changes unless they actually occur
    - Clearly separate:
      - observe (status)
      - select context (switch)
      - materialize files (checkout)
      - persist history (commit)

- Mental Model to Convey
  - Workspace
    - Files under the repo root determined by CWD
  - Local repo
    - Always inferred from CWD (never switchable logically)
  - Context
    -0 Current local repo (resolved from CWD)
    - Current local branch
    - Current remote repo
    - Current remote branch
  - Commands
    - status → report state and discover information
    - switch → change context only
    - checkout → create/copy files
    - commit → create history
    - pull → analyze then optionally merge (no auto-commit)

- Command Semantics (must be reflected in help)
  - status
    - Reports:
      - local file state
      - current local/remote context
    - Remote discovery lives here
      - status -remote → show current remote context and branches
      - status -remote <url> → list repos and branches at that remote scope
    - No mutation, no prompts
  - switch
    - Changes context only
    - Local repo is always from CWD
    - Effects:
      - Flag -lb may change workspace files
      - Flags -rr, -rb never change files
    - No discovery, no copying
  - checkout
    - Always creates a working copy
    - Used to materialize a remote repo/branch
    - May set initial context

- What Default Help Must Contain
  - One-sentence description of vgl
  - Key invariants:
    - workspace definition
    - commit creates history
    - collaboration is explicit
  - List of commands grouped by intent
  - Pointer to help <command>, -v, -vv
  - No flags, no examples.

- What -v Must Contain
  - Command syntax
  - Brief purpose per command
  - Mention flags exist but defaults are common
  - No workflows

- What -vv Must Contain
  - Explanation of:
    - workspace
    - context
    - local vs remote
  - Typical workflows:
    - local-only
    - collaboration

  - Examples for non-obvious commands only:
    - switch
    - checkout
    - split
    - merge

- Explicit Non-Goals (do NOT include)
  - Git refspecs, tracking branches, upstreams
  - Automatic discovery without explicit scope
  - Implicit directory changes
  - Multiple competing mental models

- Success Criteria
  - The help file should allow:
    - A novice to complete basic workflows without learning Git
    - An expert to understand guarantees and boundaries
    - Zero ambiguity about when files change and when they don’t

**Interactive vs automated modes:**
- **Interactive detection:** Use `Utils.isInteractive()`; honor `-Dvgl.noninteractive=true` for tests and CI to suppress prompts.
- **Test harness behavior:** Tests may temporarily override `vgl.noninteractive` when injecting stdin to validate prompt flows.

**UX invariants & minimal output:**
- Keep prompts and warnings single-line where practical to reduce noise.
- Print warnings to `stderr` so normal command output remains parseable.
- **Copy/paste friendly paths:** When VGL prints local filesystem paths in user-facing output (warnings, hints, status, info), it should format them for the user's active shell execution environment so they can be copied directly (e.g., Git Bash/MSYS users see `/c/Users/...` on Windows; PowerShell/CMD users see `C:\Users\...`).
- Preserve backwards compatibility of `status` textual layout (section headings, two-line FILES summary) so tests that assert exact output remain stable.
- Commands that change state (e.g. create/delete/switch/commit/track/untrack) should print a concise state-change report: what changed, and the resulting state (e.g. LOCAL/REMOTE switch state, affected files, etc.).

**Testing guidance & focused tests:**
- Add focused unit tests for these scenarios:
  - No `.vgl`, no Git repo ΓåÆ `status` prints a single-line warning/hint (`No VGL repository found... Run 'vgl create <path>' to make one.`) and returns exit code `1`.
  - Git ancestor exists, non-interactive ΓåÆ `status` prints short hint and does not block.
  - Git ancestor exists, interactive `y` ΓåÆ `.vgl` is created with expected properties.
  - Git ancestor exists, interactive `n` ΓåÆ no `.vgl` created; hint printed.
  - Expand globs only returns files within repo and excludes nested-repo files.
- Use `-Dvgl.test.base` to limit upward searches in tests.
- Use `VglTestHarness.runCommandWithInput` for simulating interactive input in tests.

**Refactor Policy For Living Use Cases Document**
- **Review on refactor:** Every time code is refactored (behavioral, architectural, or API changes), the author must review `docs/VglUseCases.md` and update it as necessary.
- **Conflict detection:** If a planned refactor introduces behavior that conflicts with existing use-cases, the conflict must be documented in the file as a noted "Refactor Conflict" entry and discussed with maintainers before merging.
- **Pull request checklist:** Include a short note in the PR description confirming the use-case document was reviewed and updated (or explain why no changes were needed).
- **Living doc:** Treat `docs/VglUseCases.md` as the authoritative living specification for CLI behaviors; tests and implementation should aim to satisfy the documented use cases.
- **Automation (optional):** Consider adding a CI check in the future that requires a changelog entry or a tag in the use-case file when core behavior changes.

```
