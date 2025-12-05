@echo off
REM Pre-push hook: run fast smoke tests before allowing push.
REM Install by copying this file to .git\hooks\pre-push
call .\gradlew.bat smokeTest --console=plain
if %ERRORLEVEL% NEQ 0 (
  echo Smoke tests failed. Push aborted.
  exit /b 1
)
exit /b 0
