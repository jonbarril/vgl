<!-- GitHub Copilot / AI agent instructions for the `vgl` repo -->

# Quick Context

VGL is a small Gradle-based Java CLI application that wraps Git operations (uses JGit).
Key entry point: `com.vgl.cli.VglMain`. Main CLI commands live in `src/main/java/com/vgl/cli/commands`.
The project produces an installable distribution under `build/install/vgl` and example `bin/` artifacts.

# How to build, run and test (Windows / PowerShell)

- Build the project: `.
  gradlew.bat build`
- Run unit tests: `.
  gradlew.bat test`
- Run smoke tests: `.
  gradlew.bat smokeTest`
- Run integration tests (these depend on the installed distribution):
  `.
  gradlew.bat integrationTest` (this runs tests tagged `integration` and depends on `installDist`).
- Create the distribution and run the CLI manually:
  1. `.
     gradlew.bat installDist`
  2. Run the installed wrapper: `build\install\vgl\bin\vgl.bat` or run the main class directly:
     `java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain <command>`

# Important project conventions and patterns

- Commands: implement the `Command` interface and add a `name()` and `run(List<String> args)` method.
  Example: `src/main/java/com/vgl/cli/commands/StatusCommand.java` — follow its structure and output formatting.
- CLI helpers and state access live under `com.vgl.cli` (e.g. `VglCli`, `Utils`, `StatusSyncState`). Use these
  rather than duplicating config parsing or repo-access logic.
- Logging: the project uses `slf4j-simple` at runtime (declared in `build.gradle.kts`). Prefer using existing
  logging patterns (or stdout/stderr printing where the current code does so) to keep CLI output consistent.
- Tests:
  - JUnit 5 is used; tests may be tagged `smoke` or `integration` (integration tests rely on `installDist`).
  - Test output is configured to show `standardOut` in the Gradle test task so test diagnostics often print to stdout.

# Architecture / Big picture (brief)

- Frontend: small CLI front-end `com.vgl.cli.VglMain` dispatches to individual `Command` implementations.
- Domain: command implementations use `VglCli` and JGit to access and manipulate repos; status/summary helpers
  are split into dedicated classes (see `StatusSyncState`, `StatusFileSummary`, `StatusVerboseOutput`).
- Packaging: Gradle `application` plugin builds a runnable distribution in `build/install/vgl` and a JAR.

# Integration & external dependencies

- Uses JGit (`org.eclipse.jgit`) for Git operations; be careful with JGit semantics (unborn repos, missing heads).
- Runtime logging via `org.slf4j:slf4j-simple` — test/dev runs expect console logging.
- No external network services are required by core code, but remote Git URLs are used by the CLI and tests may
  expect deterministic local repository scaffolding.

# Code and style notes for AI edits

- Keep the public CLI output format stable. Many tests rely on exact printed lines and section headings
  (for example `StatusCommand` prints `LOCAL`, `REMOTE`, `STATE`, `FILES` and subsection headings like `-- Commits:`).
- Follow existing error handling patterns: commands often print messages to `System.out`/`System.err` and return
  integer exit codes from `run(...)` — mirror that style.
- Avoid upgrading Java/tooling unless requested: the project explicitly sets Java toolchain to Java 23 in
  `build.gradle.kts` — changing that is a breaking change unless tests/CI indicate otherwise.

# Files & locations to inspect for context

- CLI and commands: `src/main/java/com/vgl/cli/` and `src/main/java/com/vgl/cli/commands/`
- Build: `build.gradle.kts`, `settings.gradle.kts`, `gradlew.bat` (use these for build/test commands)
- Manual/run scripts and integration examples: `test-manual.ps1`, `bin/`, `install/`
- Test artifacts: `build/test-results/test/` and `build/reports/tests/test/`
- Hooks: `hooks/pre-push.bat`

# Quick examples (copy-paste)

- Build + install distribution (PowerShell):
```
.\gradlew.bat clean installDist
build\install\vgl\bin\vgl.bat status -v
```
- Run a single test class (Gradle):
```
.\gradlew.bat --tests "com.vgl.cli.StatusCommandTest" test
```

# If you change behavior

- If you change any CLI output, update or add tests under `src/test/java/` and re-run the test suite.
- If you modify packaging or the `mainClass`, ensure `installDist` still produces a working `build/install/vgl`.

---
If anything here is unclear or you'd like examples expanded (e.g., common refactor patterns, how commands are registered), tell me which area to expand and I will iterate.
