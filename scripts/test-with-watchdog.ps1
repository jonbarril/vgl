param(
    [string]$Task = 'test',
    [int]$TimeoutSec = 1200
)

Write-Host "Watchdog: running Gradle task '$Task' with timeout ${TimeoutSec}s"

$gradlew = Join-Path $PSScriptRoot '..\gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Error "gradlew not found at $gradlew"
    exit 2
}

$proc = Start-Process -FilePath $gradlew -ArgumentList $Task -PassThru

$start = Get-Date

# Sleep in small intervals to be responsive to early exit
$elapsed = 0
$interval = 1
while ($elapsed -lt $TimeoutSec) {
    Start-Sleep -Seconds $interval
    $elapsed = (Get-Date -UFormat %s) - (Get-Date $start -UFormat %s)
    if ($proc.HasExited) {
        Write-Host "Watchdog: Gradle process exited with code $($proc.ExitCode) after ${elapsed}s"
        exit $proc.ExitCode
    }
}

Write-Host "Watchdog: timeout reached (${TimeoutSec}s). Capturing diagnostics..."

$reports = Join-Path $PSScriptRoot '..\build\reports\hangs'
if (-not (Test-Path $reports)) { New-Item -ItemType Directory -Path $reports | Out-Null }

# Capture thread dumps for all java processes (best-effort)
$javaProcs = Get-Process -Name java -ErrorAction SilentlyContinue
if ($javaProcs) {
    foreach ($j in $javaProcs) {
        $outFile = Join-Path $reports "jstack-$($j.Id)-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
        try {
            # Try jcmd first
            & jcmd $($j.Id) Thread.print > $outFile 2>&1
            Write-Host "Watchdog: captured thread dump via jcmd for PID $($j.Id) -> $outFile"
        } catch {
            try {
                & jstack -l $($j.Id) > $outFile 2>&1
                Write-Host "Watchdog: captured thread dump via jstack for PID $($j.Id) -> $outFile"
            } catch {
                Write-Warning "Watchdog: failed to capture thread dump for PID $($j.Id): $_"
            }
        }
    }
} else {
    Write-Warning "Watchdog: no java processes found to dump"
}

# Try to capture Gradle worker logs if any (best-effort)
$gradleLogs = Join-Path $PSScriptRoot '..\build\reports\tests'
if (Test-Path $gradleLogs) {
    Write-Host "Watchdog: test reports available at $gradleLogs"
}

# Kill the Gradle process
try {
    Stop-Process -Id $proc.Id -Force -ErrorAction Stop
    Write-Host "Watchdog: killed Gradle process (PID $($proc.Id))"
} catch {
    Write-Warning "Watchdog: failed to kill Gradle process: $_"
}

exit 124
