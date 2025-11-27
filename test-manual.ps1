# Manual test to debug commit issue
$tmpDir = New-TemporaryFile | ForEach-Object { Remove-Item $_; New-Item -ItemType Directory -Path $_ }
Write-Host "Using temp dir: $tmpDir"

Push-Location $tmpDir
try {
    # Simulate the test
    java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain create $tmpDir
    "content" | Out-File -FilePath "test.txt" -Encoding UTF8
    java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain local $tmpDir
    java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain remote origin
    java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain commit "test commit message"
    java -cp "build\install\vgl\lib\*" com.vgl.cli.VglMain status -v
} finally {
    Pop-Location
    Remove-Item -Recurse -Force $tmpDir
}
