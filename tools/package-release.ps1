# Archive the committed project, including its documentation and screenshots.
# Ignored local files and build outputs are never read into the source archive.
# Usage: .\tools\package-release.ps1 [-Version 1.3.0] [-OutDir dist]
[CmdletBinding()]
param(
    [string] $Version,
    [string] $OutDir = "dist"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gitPath = (Get-Command git -CommandType Application -ErrorAction Stop |
    Select-Object -First 1).Source

function Invoke-ProjectGit {
    param([string[]] $GitArguments)
    $output = @(& $gitPath -c core.quotepath=false -C $root @GitArguments)
    if ($LASTEXITCODE -ne 0) {
        throw "Git failed while preparing the source archive (exit $LASTEXITCODE)."
    }
    return $output
}

$status = @(Invoke-ProjectGit @("status", "--porcelain=v1", "--untracked-files=all", "--ignore-submodules=none"))
if (($status -join "`n").Length -gt 0) {
    throw "Refusing to package a changed working tree. Commit all source changes and resolve untracked files first. Ignored local files do not enter the archive."
}

# Pin the archive to the commit checked above rather than copying live files.
$commit = (Invoke-ProjectGit @("rev-parse", "--verify", "HEAD^{commit}")) -join ""
if (-not $Version) {
    $buildFile = (Invoke-ProjectGit @("show", "${commit}:app/build.gradle")) -join "`n"
    $versionMatch = [regex]::Match($buildFile, 'versionName\s+"([^"]+)"')
    if (-not $versionMatch.Success) {
        throw "Could not read versionName from the committed app/build.gradle."
    }
    $Version = $versionMatch.Groups[1].Value
}
if ($Version -notmatch '\A[0-9A-Za-z][0-9A-Za-z._+-]{0,63}\z') {
    throw "Version must be 1-64 letters, digits, dots, underscores, plus signs or hyphens, starting with a letter or digit. Paths and whitespace are not allowed."
}
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    throw "OutDir must name an output directory."
}

# NUL-separated names retain spaces and unusual characters during the audit.
$treeOutput = @(Invoke-ProjectGit @("ls-tree", "-r", "-z", "--name-only", $commit))
$files = @(($treeOutput -join "`n") -split "`0" | Where-Object { $_.Length -gt 0 })
$blocked = @($files | Where-Object {
    $_ -match '(?i)(\A|/)(local\.properties|release-signing\.properties|[^/]+\.(jks|keystore|p12|pfx|jks\.properties))\z' -or
    ($_ -match '(?i)(\A|/)secrets/' -and $_ -notmatch '(?i)\.template\z') -or
    $_ -match '(?i)\.(pdf|apk|aab|apks|idsig)\z' -or
    $_ -match '(?i)(\A|/)(build|\.gradle|\.idea|\.android-tools|\.gradle-home|artifacts|dist|outputs|node_modules)(/|\z)'
})
if ($blocked.Count -gt 0) {
    throw ("Refusing to archive tracked credentials, private PDFs or build output: " + ($blocked -join ", "))
}

if ([System.IO.Path]::IsPathRooted($OutDir)) {
    $distDir = [System.IO.Path]::GetFullPath($OutDir)
} else {
    $distDir = [System.IO.Path]::GetFullPath((Join-Path $root $OutDir))
}
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$archiveName = "dongxuelian-timetable-v$Version"
$zipPath = Join-Path $distDir "$archiveName-source.zip"
if (Test-Path -LiteralPath $zipPath -PathType Container) {
    throw "The ZIP output path points to a directory: $zipPath"
}

# git archive writes or replaces only this ZIP, preserving every source directory.
Invoke-ProjectGit @("archive", "--format=zip", "--prefix=$archiveName/", "--output=$zipPath", $commit) | Out-Null
$zip = Get-Item -LiteralPath $zipPath
Write-Host "Commit: $commit"
Write-Host "Version: v$Version"
Write-Host ("Source archive: {0} ({1:N2} MiB)" -f $zip.FullName, ($zip.Length / 1MB))
