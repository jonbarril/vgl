Quick test workflow

- Focused tests first (recommended):
  - Run focused command/unit tests for the commands you're changing before running the full suite.
  - Gradle helper: `./gradlew focusedTest`.
  - The main `test` task runs `focusedTest` first by default.

- Fast local pre-push smoke test (optional):
  - Install the git hook (Windows):

    copy hooks\pre-push.bat .git\hooks\pre-push

  - This runs a small `smoke` test before push. It is optional but recommended.

- Full integration tests are intentionally separate and run with:

  .\gradlew.bat integrationTest

- CI recommendation: run `focusedTest` first, then the full `test` suite, and run `integrationTest` on merge or nightly.
