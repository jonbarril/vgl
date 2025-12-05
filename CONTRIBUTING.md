Quick test workflow

- Fast local pre-push smoke test (optional):
  - Install the git hook (Windows):

    copy hooks\pre-push.bat .git\hooks\pre-push

  - This runs a small `smoke` test before push. It is optional but recommended.

- Full integration tests are intentionally separate and run with:

  .\gradlew.bat integrationTest

- CI recommendation: run `integrationTest` on merge or nightly.
