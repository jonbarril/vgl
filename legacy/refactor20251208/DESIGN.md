VGL Refactor Design
===================

Goal
----
Rebuild VGL as a lightweight, clearly scoped wrapper around Git/JGit that preserves VGL terminology and user-facing concepts, while preferring Git-native behavior where it simplifies implementation or improves correctness. The implementation should be modular, well-tested, and optimized for clarity for both novices and experienced users.

Core constraint (thin wrapper)
-----------------------------
- VGL must act primarily as a thin presentation and safety layer on top of Git/JGit. Where Git semantics are clear and sufficient, VGL should delegate to Git/JGit rather than reimplementing behavior.
- Any deviations from Git must be minimal, deliberate, and motivated by improving usability for VGL's audience (for example: a simplified `status` output, clearly defined `Undecided` semantics, or safety checks around destructive commands).
- Command behaviors must conform to the user-facing descriptions in `HelpCommand` — use that text as the authoritative source for VGL's CLI semantics.

High-level principles
---------------------
- Centralize repository discovery using JGit (`FileRepositoryBuilder`) so all commands have a consistent view of repo root, submodules, and nested repos.
- Make passive commands (e.g., `status`) read-only by default: compute undecided or derived state in-memory and never create or persist `.vgl` unless the user explicitly runs an interactive command that intends to change state.
- Provide a small, well-tested core API: `RepoFinder`, `VglRepoCore` (compute-only public APIs + explicit persistence API), and `StatusService`.
- Prefer JGit primitives for low-level operations (index status, diff, rename detection via `DiffFormatter#setDetectRenames(true)`, commit traversal via `RevWalk`), normalizing paths to repo-root-relative `/` form for output and comparison.
- Keep CLI wiring thin: map CLI flags and commands to core services and format results consistently. Preserve VGL's established output conventions (FILES two-line summary, `-v`/`-vv` semantics, `Undecided` section behavior) unless the help text specifies otherwise.
- Make explicit, documented choices for any Git-to-VGL mapping that differs from raw Git behavior (document these in code and tests).
- Consolidate and simplify tests: unit-test core services with deterministic inputs and add a small set of focused integration tests to assert CLI output formatting.

Build / Encoding
----------------
- Keep the existing Gradle build; place new sources under `src/main/java` and tests under `src/test/java`.
- Ensure all new and modified source files are encoded as UTF-8 without BOM (CI should enforce this when possible).

Phases
------
1. Create an in-repo scaffold and `DESIGN.md` (this file).
2. Implement `RepoFinder` and JGit helper utilities (repo root discovery, nested-repo detection, normalized path utilities).
3. Implement `VglRepoCore` with two distinct APIs: compute-only methods (non-persistent) and explicit persistence methods called only by interactive commands.
4. Implement `StatusService` that returns a structured model (counts, lists, rename unions, etc.) and a lightweight formatter for CLI output that enforces VGL's printing rules.
5. Replace or adapt existing `StatusCommand` and related helpers to use the new services.
6. Consolidate tests: add unit tests for core services and replace brittle output-focused tests with a small suite of stable integration tests that exercise formatting.
7. Iterate, run the full test suite, and remove deprecated/unused code after verification.
