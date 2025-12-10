```markdown
# VGL — Use Cases and Expected Behavior

This document captures concrete user-facing use cases, edge cases, and the expected VGL behavior. Keep concise, grouped by topic for easy reference for design and test.

**Overview:**
- **Purpose:** Describe how VGL discovers repos, how `status` behaves, and what prompting/creation flows should do.
- **Audience:** CLI maintainers, tests, and contributors implementing status/creation logic.

**Repository discovery (resolution):**
- **Overview:** When a VGL command requires a repository, VGL searches upward from the current working directory and stops at the first directory that contains either a `.git` or a `.vgl`.
- **Behavior summary (examples):**
  - Both `.git` and `.vgl`: treat the directory as a valid Vgl repo and proceed; validate and warn if states are inconsistent.
  - `.git` only: warn the user and offer to convert the Git repo into a Vgl repo (interactive). In non-interactive contexts, print a short hint and fail.
  - `.vgl` only: warn the user and offer to initialize a Git repository from the `.vgl` state (interactive). In non-interactive contexts, print a short hint and fail.
  - Neither found up to filesystem root: warn and fail the command.
- **Test Ceiling:** In test or CI, discovery honors `-Dvgl.test.base` to avoid searching above a configured test base directory.

**Status command behavior:**
  - **No VGL config, no Git repo:** Warn the user that no local repository was found and print a short hint showing how to create one: `Run 'vgl create <path>' to make one.` Do not attempt to open a repository; the command should exit non-zero.
- **Configured VGL repo:** Only treat `local.dir` as active when the directory contains a valid Git repository (there is a `.git` or an accessible gitdir).
- **Git repo ancestor + no `.vgl`:** Offer a short, single-line prompt (interactive) or a short hint (non-interactive):
  - Prompt: `Git repo '<path>' found. Use it as a Vgl repo? (y/N): `
  - Non-interactive hint: `Run 'vgl create <path>' to make one.`

**Prompt & creation flow:**
- **Interactive accept:** Create a minimal `.vgl` at the repo root with the following properties:
  - `local.dir` = absolute repository root
  - `local.branch` = current branch (if resolvable)
  - `remote.url` and `remote.branch` if configured/tracking
  After creation, reload VGL state and continue the original command.
- **Interactive decline:** Print the short hint (see above) and proceed as if no VGL repo exists.
- **Non-interactive:** Never prompt. Print the short hint and return null so commands remain non-blocking in CI.

**Relationship to `vgl create` command:**
- `vgl create` remains the canonical higher-level command for creating repositories and VGL config.
- The prompt-driven `.vgl` creation is intentionally minimal (write `.vgl`) to avoid altering Git state; it provides a convenient, non-invasive path to make the repository a VGL repo.

**Status counts & file summaries:**
- **Source of truth:** File counts and categories (Added/Modified/Deleted/Undecided/Tracked/Untracked/Ignored) are derived from JGit `Status` plus repo-index/working-tree listing.
- **Repo-bound listing:** Glob expansion and file listings must be bounded to the repository (do not walk entire filesystem); use JGit working-tree iterators / index-aware listing.
- **Nested repos:** Files inside nested repositories (directories that contain their own `.git`) are treated as Ignored for parent-level status; nested repo paths appear in the Ignored set and are excluded from Tracked/Untracked/Undecided lists.
- **.vgl treatment:** `.vgl` itself must be treated as Ignored and never shown in tracked or undecided lists.

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

**Edge cases & cautions:**
- **User home `.vgl`:** Avoid loading or writing `.vgl` in the real `user.home` during tests; tests must set `-Duser.home` or code must guard against writing into user home.
- **Unborn repos:** Handle repos with no commits gracefully (COMMITS shows `(no commits yet)`).
- **Fail-fast diagnostics:** For long-running filesystem operations, prefer bounding the traversal rather than adding timeouts.

**Change log / future work:**
- Consider adding a `--create-only` mode to `vgl create` so prompt flow can call a single canonical code path that creates `.vgl` without modifying Git.
- Audit any remaining uses of unbounded `Files.walk` and replace with JGit-backed listings.

---
Generated by the development session to record expected behavior and use cases for `vgl` CLI discovery, `status`, and prompt/create flows.

**Refactor Policy — Living Use Cases Document**
- **Review on refactor:** Every time code is refactored (behavioral, architectural, or API changes), the author must review `docs/VglUseCases.md` and update it as necessary.
- **Conflict detection:** If a planned refactor introduces behavior that conflicts with existing use-cases, the conflict must be documented in the file as a noted "Refactor Conflict" entry and discussed with maintainers before merging.
- **Pull request checklist:** Include a short note in the PR description confirming the use-case document was reviewed and updated (or explain why no changes were needed).
- **Living doc:** Treat `docs/VglUseCases.md` as the authoritative living specification for CLI behaviors; tests and implementation should aim to satisfy the documented use cases.
- **Automation (optional):** Consider adding a CI check in the future that requires a changelog entry or a tag in the use-case file when core behavior changes.

```
