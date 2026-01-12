# Regenerate help golden test files with correct encoding and version
# PSScriptAnalyzer -SuppressWarnings PSAvoidAssignmentToAutomaticVariable
$ErrorActionPreference = 'Stop'

# Build the project
Write-Host "Building project..."
.\gradlew.bat installDist classes -q

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
    exit 1
}

# UTF-8 encoding without BOM
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Helper function to run vgl with java directly (from compiled classes) and save output
function Save-HelpOutput(
    [string]$outputFile,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$vglTokens
) {
    
    # Run Java directly with TEST_VERSION system property.
    # IMPORTANT: do NOT put the built application jar on the classpath.
    # If we include it, Utils.versionFromRuntime() will pick up the jar's
    # Implementation-Version instead of the TEST_VERSION system property.
    $javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

    $cpEntries = New-Object System.Collections.Generic.List[string]
    $null = $cpEntries.Add((Join-Path $PWD "build\classes\java\main"))
    $null = $cpEntries.Add((Join-Path $PWD "build\resources\main"))

    $depJars = Get-ChildItem "build\install\vgl\lib\*.jar" -File |
        Where-Object { $_.Name -notlike "vgl-*.jar" } |
        ForEach-Object { $_.FullName }

    $classpath = ($cpEntries.ToArray() + $depJars) -join ";"
    
    $javaCmdLine = New-Object System.Collections.Generic.List[string]
    $null = $javaCmdLine.Add("-Dvgl.version=TEST_VERSION")
    $null = $javaCmdLine.Add("-cp")
    $null = $javaCmdLine.Add($classpath)
    $null = $javaCmdLine.Add("com.vgl.cli.VglMain")

    if ($null -ne $vglTokens) {
        foreach ($token in $vglTokens) {
            $null = $javaCmdLine.Add($token)
        }
    }

    $output = & $javaExe $javaCmdLine.ToArray() 2>&1 | Out-String
    $output = $output.TrimEnd()
    
    $fullPath = Join-Path $PWD $outputFile
    [System.IO.File]::WriteAllText($fullPath, $output, $utf8NoBom)
    
    Write-Host "Generated: $outputFile"
}

# Generate all golden files
# Note: for default/-v/-vv, VglMain routes directly to HelpCommand without picocli.
Save-HelpOutput "src\test\resources\com\vgl\cli\commands\help.default.txt"
Save-HelpOutput "src\test\resources\com\vgl\cli\commands\help.v.txt" "-v"
Save-HelpOutput "src\test\resources\com\vgl\cli\commands\help.vv.txt" "-vv"
Save-HelpOutput "src\test\resources\com\vgl\cli\commands\help.status.txt" "help" "status"
Save-HelpOutput "src\test\resources\com\vgl\cli\commands\help.create.txt" "help" "create"

Write-Host "Done! All golden files regenerated."
