```markdown
# VGL — Use Cases and Expected Behavior

This document captures concrete user-facing use cases, edge cases, and the expected VGL behavior. Keep concise, grouped by topic for easy reference for design and test.

**Overview:**
- **Purpose:** Describe how VGL should work with emphasis on a user's perspective. Describes both main use cases as well as edge cases as they are discovered and addressed. User messaging and actions are kept general as the wording and specifics may change with refinement of design.
- **Scope:** This document is intended to be consistent with the text in the help command, which describes general command behavior and usage. For commands and use cases that require further elaboration they will be detailed here.
- **Audience:** CLI maintainers, tests, and contributors implementing command a  and system logic.


**VGL State and Arguments:**
- **VGL state:** VGL application state is maintained in the current repo’s `.vgl` file. The current VGL repo is always the one resolved from the user’s current working directory (unless a command arg specifies one). 
- **Repo context:** Specifies the working local and remote repo state. Consists of the local repo path and branch name, and the remote repo URL and branch name. By definition, the local repo is that resolved from the user’s current working directory.
- **Default args:** The default branch (i.e. no branch name specified) for local and remote repo creation is "main". Otherwise, commands that reference a local and/or remote repo/branch default to the current repo context in the VGL state.
- **File args:** Glob expansion and file listings must be bounded to the repository (do not walk the entire filesystem); use JGit working-tree iterators / index-aware listing.
- **Nested repos:** Files inside nested repositories (directories that contain their own `.git`) are treated as Ignored for parent-level commands (e.g., status, glob expansion).
- **.vgl treatment:** By default, `.vgl` is included in `.gitignore` when a VGL repo is created. As such, it should be treated as Ignored.
- **Internal state:** The `.vgl` file may also store internal state such as undecided files, but this is not typically surfaced to the user.

**Repository Resolution:**
- **Overview:** When VGL requires the local repo, VGL searches upward from the current working directory and stops at the first directory that contains either a `.git` or a `.vgl`.
- **Behavior summary (examples):**
  - Both `.git` and `.vgl`: If both are valid and consistent, then the directory is considered a valid VGL repo and the command proceeds. If invalid, warn the user, note the location and problem, suggest the user resolve the problem outside of VGL, and fail.
  - `.git` only: If valid, warn the user and offer to convert the Git repo into a VGL repo (interactive). In non-interactive contexts, print a short hint and fail.
  - `.vgl` only: If valid, warn the user and offer to initialize a Git repository from the `.vgl` state (interactive). In non-interactive contexts, print a short hint and fail.
  - Neither found up to filesystem root: Warn and fail the command.
  **Validation:** An invalid .git/.vgl state is one where either references local or remote repos or branches that do not exist or are inaccessible. If both .git and .vgl are present, then their states must be consistent, with local and remote repo and branch matching.
- **Test Ceiling:** In test or CI, discovery honors `-Dvgl.test.base` to avoid searching above a configured test base directory. This is for safety.

**Status command:**
  - **Overview:** The current valid VGL repo is indicated in LOCAL. Although REMOTE may be '(none)' the local repo can never be '(none)' as that would indicate an invalid VGL repo in which case status and any other command needing a VGL repo should fail with a warning. In all cases, info in summary counts shall be consistent with the number of branches, commits and files in subsection lists.
  - **Default:** Default behavior is when neither -v or -vv flags are present. This prints the minimal status consisting of sections LOCAL/REMOTE/COMMITS/FILES. Each section includes a one or two lline summary of the corresponding aspect of repo status. As needed paths and branch names will be shortened using elipses so that the format remains consistent and column aligned.
  - **Verbose:** This is when -v is present but -vv is not. Same as Default mode but paths and file names are indicated in full regardless of column formatting. COMMITS adds a subsection listing commit codes and truncated message to maintain a single line format. FILES adds subsections with 'Files to Commit', 'Files to Merge' and 'Undecided Files', which are truncated to a single line.
  - **Very Verbose:** This is when -vv is present. Same as Verbose mode but LOCAL and REMOTE include branch list subsections, with the current branch (as indicated in the summary line) starred. COMMIT commit list messages are not truncated. FILES file names are not truncated and subsections for 'TRACKED Files', 'Unttracked Files' and 'Ignored Files' are added.
  - **General:**
  -- File counts and categories (Added/Modified/Deleted/Undecided/Tracked/Untracked/Ignored) are derived from JGit `Status` plus repo-index/working-tree listing.
  -- Nested repo paths appear in the Ignored set and are excluded from Undecided/Tracked/Untracked lists.
  -- Directories that appear in file lists include a trailing "/" indicating it is a directory and not just a file. If a dir is also a nested repo it will have an additional indicator (e.g. '(repo)').

**Interactive vs automated modes:**
- **Interactive detection:** Use `Utils.isInteractive()`; honor `-Dvgl.noninteractive=true` for tests and CI to suppress prompts.
- **Test harness behavior:** Tests may temporarily override `vgl.noninteractive` when injecting stdin to validate prompt flows.

**UX invariants & minimal output:**
- Keep prompts and warnings single-line where practical to reduce noise.
- Print warnings to `stderr` so normal command output remains parseable.
- Preserve backwards compatibility of `status` textual layout (section headings, two-line FILES summary) so tests that assert exact output remain stable.

**Testing guidance & focused tests:**
- Add focused unit tests for these scenarios:
  - No `.vgl`, no Git repo → `status` prints a single-line warning/hint (`No VGL repository found... Run 'vgl create <path>' to make one.`) and returns exit code `1`.
  - Git ancestor exists, non-interactive → `status` prints short hint and does not block.
  - Git ancestor exists, interactive `y` → `.vgl` is created with expected properties.
  - Git ancestor exists, interactive `n` → no `.vgl` created; hint printed.
  - Expand globs only returns files within repo and excludes nested-repo files.
- Use `-Dvgl.test.base` to limit upward searches in tests.
- Use `VglTestHarness.runCommandWithInput` for simulating interactive input in tests.

**Refactor Policy — Living Use Cases Document**
- **Review on refactor:** Every time code is refactored (behavioral, architectural, or API changes), the author must review `docs/VglUseCases.md` and update it as necessary.
- **Conflict detection:** If a planned refactor introduces behavior that conflicts with existing use-cases, the conflict must be documented in the file as a noted "Refactor Conflict" entry and discussed with maintainers before merging.
- **Pull request checklist:** Include a short note in the PR description confirming the use-case document was reviewed and updated (or explain why no changes were needed).
- **Living doc:** Treat `docs/VglUseCases.md` as the authoritative living specification for CLI behaviors; tests and implementation should aim to satisfy the documented use cases.
- **Automation (optional):** Consider adding a CI check in the future that requires a changelog entry or a tag in the use-case file when core behavior changes.

```
