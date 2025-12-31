# VGL Guidelines & Lessons Learned (Living)

This file is the running, shared reference for development and testing conventions in this repo. Update it whenever we agree on a new guideline or learn a lesson the hard way.

## Sources of Truth (Spec-First)

- **Authoritative contracts**
  - [docs/VglUseCases.md](docs/VglUseCases.md)
  - [docs/VglChatContext.txt](docs/VglChatContext.txt)
  - `HelpCommand.java` output/behavior
- **Behavior changes are spec changes**: If runtime behavior must change, update the source-of-truth doc/code first, then update tests.

## Help Output as a Frozen Contract

- Treat help output as a contract and lock it with **golden-file tests**.
- Golden tests should:
  - Normalize CRLF/LF so Windows/Linux behave the same.
  - Be robust against UTF-8 BOM.
  - Stabilize volatile fields (e.g., set `-Dvgl.version=TEST_VERSION`).

## Testing Strategy (Best Practice)

- **Unit tests per command**: One test class per command for command behavior.
- **Minimal integration tests**: A small smoke test that exercises CLI dispatch/wiring (e.g., `VglMain.run(...)`) without duplicating unit coverage.
- Prefer test helpers to avoid repeated boilerplate (e.g., stdout/stderr capture).

## Shared Helpers + Shared Messages

- **Centralize user-facing strings** in a single place so:
  - Commands print consistent messages.
  - Tests compare against the same source (no duplicated literals).
- **Deduplicate common parsing/logic** via helpers (e.g., flag parsing like `-lr`, `-lb`, `-bb`, `-f`).

### Standard Warnings (Non-Brittle Tests)

- If multiple commands need the same warning/wording, implement it once in `Messages` (and optionally a small helper in `commands/helpers/` for printing).
- Tests should assert via `Messages.*()` (or `Usage.*()`) rather than hard-coded strings.
  - This keeps tests stable when wording is tweaked in one place.
  - Example: the "target repo is not current; switch state unchanged" warning.

## Check Legacy Code Before Refactors

- Before introducing a new command structure, CLI framework wiring pattern, or shared support layer (utilities/helpers/constants), **skim the legacy code** (e.g., the previous `main` implementation) for existing patterns.
  - The legacy approach may not be “correct,” but it often encodes practical best practices and can prevent churn (e.g., adopting a framework and then immediately reorganizing around a different binder pattern like `VglCli`).
  - If you choose to deviate from legacy patterns, do it intentionally and document why.

## Borrowing Legacy Code (Best Practice)

- Prefer **porting patterns, not copying files**: treat `legacy/` as reference and re-implement in the current architecture (`commands/` business logic + `VglCli` binder).
- Keep changes **spec-first**: verify against [docs/VglUseCases.md](docs/VglUseCases.md) and the current `HelpCommand.java` contract before importing legacy behavior.
- Avoid dragging over legacy debt:
  - Don’t copy debug output, test-only hacks, or unused helpers.
  - Don’t introduce new dependencies unless required and justified.
- Follow Java/Gradle/CLI best practices:
  - Keep commands deterministic and easy to unit test (return exit codes; write to stdout/stderr consistently).
  - Centralize user-facing strings in `Messages` and keep tests asserting those helpers.
  - Prefer small, focused helpers over large “god” classes; keep public APIs minimal.
  - After any port/refactor, run `./gradlew test` and add/adjust tests that lock the intended UX.

## CLI Args & Usage

- If args are malformed (e.g., missing required value for `-lr`, mixing `-lr DIR` with a positional DIR, unexpected extra args), print a short **Usage** block and exit non-zero.

## Non-Interactive Behavior

- Support explicit non-interactive mode via `-Dvgl.noninteractive=true`.
- When non-interactive:
  - Don’t prompt.
  - Default to safe behavior (refuse destructive actions unless forced).

## Repo Discovery in Tests

- Repo-root search helpers should honor a test ceiling like `-Dvgl.test.base` so tests/CI don’t accidentally traverse outside temporary directories.

## Gitless Principle

- VGL’s user model should not introduce a Git “staging area” concept.
- If implementation uses Git under the hood (e.g., via JGit), keep the UX aligned with the Gitless model.

## Windows/PowerShell Practicalities

- Be careful with how JVM system properties are passed when running Gradle/tests from PowerShell.
- Normalize line endings in tests (CRLF vs LF) to keep outputs stable.

## JGit Practicalities

- A newly `init`'d Git repo can have **no commits**; in that state, **`HEAD` may be unresolved**.
  - Some JGit operations (e.g., creating a branch from `HEAD`, branch tracking queries) can fail until an initial commit exists.
  - In tests, if you need to create branches, seed an initial empty commit first.

## Process Notes

- Keep `main` clean and spec-driven.
- Avoid accumulating debug artifacts in the working tree; prefer test fixtures and temporary directories.

## Backward Compatibility

- Unless explicitly requested, **do not preserve old/legacy behavior for compatibility**.
  - Prefer simplifying refactors and aligning to current sources of truth over maintaining historical quirks.
  - If compatibility is required for a specific command/flag/behavior, call it out explicitly in the spec/docs and add tests.
