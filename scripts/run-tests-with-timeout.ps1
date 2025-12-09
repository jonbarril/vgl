Param(
    [int]$TimeoutSeconds = 180
)

$gradle = Join-Path $PSScriptROOT '..\gradlew.bat'
if (!(Test-Path $gradle)) { $gradle = '.\gradlew.bat' }

Write-Output "Running Gradle tests with timeout ${TimeoutSeconds}s..."

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $gradle
$psi.Arguments = 'clean test --no-daemon --console=plain --info'
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$psi.WorkingDirectory = (Get-Location).Path

$p = New-Object System.Diagnostics.Process
$p.StartInfo = $psi
$p.Start() | Out-Null

$stdOut = $p.StandardOutput
$stdErr = $p.StandardError
$start = Get-Date

while (-not $p.HasExited) {
    while (-not $stdOut.EndOfStream) { $line = $stdOut.ReadLine(); Write-Output $line }
    while (-not $stdErr.EndOfStream) { $line = $stdErr.ReadLine(); Write-Output $line }
    Start-Sleep -Milliseconds 200
    if ((Get-Date) - $start -gt [TimeSpan]::FromSeconds($TimeoutSeconds)) {
        Write-Output "TIMEOUT_REACHED - killing process"
        try { $p.Kill() } catch {}
        break
    }
}

while (-not $stdOut.EndOfStream) { Write-Output $stdOut.ReadLine() }
while (-not $stdErr.EndOfStream) { Write-Output $stdErr.ReadLine() }

if ($p.HasExited) { exit $p.ExitCode } else { exit 124 }
