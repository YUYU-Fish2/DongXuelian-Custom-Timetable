# Build a clean, publish-ready source distribution for GitHub.
#
# Copies only the publishable source (explicit allowlist), refuses to run if any
# credential or build junk slipped in, then writes a spec-compliant zip whose
# entry names use forward slashes so Linux/macOS unzip restores the tree.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package-release.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package-release.ps1 -Version 1.3.0
[CmdletBinding()]
param(
    [string] $Version,
    [string] $OutDir = "dist"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

if (-not $Version) {
    $versionLine = Select-String -LiteralPath (Join-Path $root "app\build.gradle") `
        -Pattern 'versionName\s+"([^"]+)"' | Select-Object -First 1
    if (-not $versionLine) { throw "Could not read versionName from app\build.gradle" }
    $Version = $versionLine.Matches[0].Groups[1].Value
}

$stagingName = "mein-stundenplan-v$Version"
$distDir = Join-Path $root $OutDir
$staging = Join-Path $distDir $stagingName

# Republishing from the same folder is the normal workflow, so an existing nested git
# repository (the one you push to GitHub) survives a rebuild.
$gitBackup = Join-Path $distDir ".$stagingName.git-backup"
$stagingGit = Join-Path $staging ".git"
if (Test-Path -LiteralPath $gitBackup) { Remove-Item -LiteralPath $gitBackup -Recurse -Force }
if ((Test-Path -LiteralPath $staging) -and (Test-Path -LiteralPath $stagingGit)) {
    Move-Item -LiteralPath $stagingGit -Destination $gitBackup
}
if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
if (Test-Path -LiteralPath $gitBackup) {
    Move-Item -LiteralPath $gitBackup -Destination $stagingGit
}

# Single files, addressed relative to the project root.
$files = @(
    ".gitignore",
    ".gitattributes",
    "README.md",
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "app\build.gradle",
    "app\proguard-rules.pro",
    "docs\CHANGELOG.md",
    "secrets\release-signing.properties.template",
    "tools\build-verify.ps1",
    "tools\verify-logic.ps1",
    "tools\security-audit.ps1",
    "tools\local-env.ps1",
    "tools\package-release.ps1",
    "tools\TimetableRulesTest.java",
    "tools\PdfCourseParserTest.java"
)

# Directories copied recursively (source tree, wrapper, offline compile stubs).
$treeDirs = @(
    "app\src",
    "gradle\wrapper",
    "tools\compile-stubs"
)

foreach ($file in $files) {
    $source = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $source)) { throw "Expected release file not found: $file" }
    $target = Join-Path $staging $file
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
}

foreach ($tree in $treeDirs) {
    $sourceDir = Join-Path $root $tree
    if (-not (Test-Path -LiteralPath $sourceDir)) { throw "Expected release directory not found: $tree" }
    foreach ($item in Get-ChildItem -LiteralPath $sourceDir -Recurse -Force -File) {
        $relative = $item.FullName.Substring($sourceDir.Length).TrimStart([char]92)
        $target = Join-Path (Join-Path $staging $tree) $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $target -Force
    }
}

# Guard rails: no keystores, no passwords, no machine-local config, no real timetable
# exports (PDFs carry teacher names, class codes and print timestamps), no build output.
$blockedNames = @("*.jks", "*.keystore", "*.p12", "*.pfx", "release-signing.properties", "local.properties", "*.pdf", "*.jks.properties")
$leaks = Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
    Where-Object { $blockedNames -contains $_.Name }
if ($leaks) {
    throw ("Refusing to package credentials or local config: " + (($leaks.Name | Sort-Object -Unique) -join ", "))
}

foreach ($junk in @("build", ".gradle", ".idea", "outputs")) {
    $found = Get-ChildItem -LiteralPath $staging -Recurse -Force -Directory -Filter $junk
    if ($found) { throw "Build output leaked into the distribution: $junk" }
}

$secretHits = Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
    # The packager itself declares these patterns, so it always self-matches.
    Where-Object { $_.Name -ne "package-release.ps1" } |
    Where-Object { $_.Extension -in @(".java", ".xml", ".gradle", ".ps1", ".md", ".properties", ".pro") } |
    Select-String -Pattern 'storePassword=\S|keyPassword=\S|C:\\Users\\|D:\\|\.edu(\.cn)?\b'
if ($secretHits) {
    foreach ($hit in $secretHits) { Write-Warning "$($hit.Filename):$($hit.LineNumber): $($hit.Line.Trim())" }
    throw "Possible hardcoded credentials in the distribution."
}

foreach ($required in @("gradlew", "gradle\wrapper\gradle-wrapper.jar", "app\src\main\AndroidManifest.xml")) {
    if (-not (Test-Path -LiteralPath (Join-Path $staging $required))) { throw "Distribution is missing: $required" }
}

$zipPath = Join-Path $distDir "$stagingName-source.zip"
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
Add-Type -AssemblyName System.IO.Compression | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

$archive = [System.IO.Compression.ZipFile]::Open($zipPath, "Create")
try {
    foreach ($item in Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
            Where-Object { $_.FullName -notmatch ([regex]::Escape("$stagingGit" + [char]92)) }) {
        $relative = $item.FullName.Substring($staging.Length).TrimStart([char]92).Replace([char]92, "/")
        $entry = $archive.CreateEntry("$stagingName/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
        $target = $entry.Open()
        $source = $item.OpenRead()
        try { $source.CopyTo($target) } finally { $source.Dispose(); $target.Dispose() }
    }
} finally {
    $archive.Dispose()
}

$summary = Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
    Where-Object { $_.FullName -notmatch ([regex]::Escape("$stagingGit" + [char]92)) } |
    Measure-Object -Property Length -Sum
Write-Host "Version:              v$Version"
Write-Host "Distribution folder:  $staging"
Write-Host ("Distribution zip:     {0} ({1:N0} files, {2:N2} MB)" -f $zipPath, $summary.Count, ($summary.Sum / 1MB))