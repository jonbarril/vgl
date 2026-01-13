<!-- GitHub Copilot / AI agent instructions for the `vgl` repo -->

# Quick context

VGL is a Gradle-based Java CLI that wraps Git operations (JGit) to provide a simpler, Gitless-style user model.
Primary entry point: `com.vgl.cli.VglMain`.
Command implementations: `src/main/java/com/vgl/cli/commands`.
CLI wiring: `src/main/java/com/vgl/cli/VglCli.java`.

# Sources of truth

- `docs/VglUseCases.md`
- `docs/VglChatContext.txt`
- `src/main/java/com/vgl/cli/commands/HelpCommand.java` (help output is a contract)
- Golden help tests under `src/test/resources/com/vgl/cli/commands/help.*.txt`

If behavior must change, update the source of truth and tests together.

# Build and test (Windows / PowerShell)

- Unit tests: `./gradlew.bat test`
- Smoke tests: `./gradlew.bat smokeTest`
- Integration tests: `./gradlew.bat integrationTest`
- Distribution: `./gradlew.bat installDist` then run `build\install\vgl\bin\vgl.bat`

# Output stability

- Keep user-facing CLI output stable and predictable.
- If you change any output, update tests and regenerate help goldens using `regenerate-help-golden.ps1`.

# Best-practice guardrail (ask before proceeding)

If a request appears to conflict with VGL guidelines or general engineering best practices, pause and ask for clarification + explicit confirmation before making changes.

Confirmation required for:
- Breaking CLI changes (removing flags, changing defaults, changing exit codes)
- Help/output contract changes (goldens, headings, wording locked by tests)
- Adding/changing dependencies
- Adding network behavior (HTTP calls, GitHub APIs, auth requirements)
- Security-sensitive changes (credentials, filesystem writes outside repo root)
- Large refactors not strictly required by the task

When asking, include:
- The guideline/best practice concern
- A safer alternative
- Expected blast radius (files/tests/goldens)
