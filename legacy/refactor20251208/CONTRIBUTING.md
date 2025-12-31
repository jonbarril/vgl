Quick test workflow

- Manual help/status checks (on request):
  - Use the console-printing test or JavaExec helpers when you want to inspect CLI output.
  - Gradle helpers: `./gradlew manualOutputTest`, `./gradlew vglStatus`, `./gradlew vglHelp`.

- Fast local pre-push smoke test (optional):
  - Install the git hook (Windows):

    copy hooks\pre-push.bat .git\hooks\pre-push

  - This runs a small `smoke` test before push. It is optional but recommended.

- Full integration tests are intentionally separate and run with:

  .\gradlew.bat integrationTest

-- CI recommendation: run the full `test` suite and run `integrationTest` on merge or nightly. Use `manualOutputTest` or the JavaExec helpers locally when you need to inspect help/status output.
