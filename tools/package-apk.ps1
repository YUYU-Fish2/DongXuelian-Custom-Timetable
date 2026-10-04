# Package a signed preview APK and a ZIP without modifying its signature.
[CmdletBinding()]
param([string] $Apk, [string] $OutDir)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-env.ps1')
$sdk = Get-MeinAndroidHome
$env:JAVA_HOME = Get-MeinJavaHome
$versionLine = Select-String -LiteralPath (Join-Path $root 'app\build.gradle') -Pattern 'versionName\s+"([^"]+)"' | Select-Object -First 1
if (-not $versionLine) { throw 'Cannot read the app version.' }
$version = $versionLine.Matches[0].Groups[1].Value
if (-not $Apk) { $Apk = Join-Path $root 'app\build\outputs\apk\preview\app-preview.apk' }
$Apk = (Resolve-Path -LiteralPath $Apk).Path
if (-not $OutDir) { $OutDir = Join-Path $root "dist\apk-v$version" }
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$OutDir = (Resolve-Path -LiteralPath $OutDir).Path

$aapt = Join-Path $sdk 'build-tools\36.1.0\aapt.exe'
$signer = Join-Path $sdk 'build-tools\36.1.0\apksigner.bat'
$badging = & $aapt dump badging $Apk
if ($LASTEXITCODE -ne 0 -or -not ($badging -match "^package: name='com.example.meinstundenplan' ") -or
        -not ($badging -match "versionName='$version-preview'") -or ($badging -match '^application-debuggable')) {
    throw 'Expected a non-debuggable preview APK with the original application ID and current version.'
}
$signature = & $signer verify --print-certs $Apk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

$baseName = "dongxuelian-timetable-v$version-preview"
$publishedApk = Join-Path $OutDir "$baseName.apk"
if ($publishedApk -ne $Apk) { Copy-Item -LiteralPath $Apk -Destination $publishedApk -Force }
$instructions = Join-Path $OutDir 'INSTALL.txt'
@"
东雪莲定制课表 $version-preview

直接安装同目录中的 $baseName.apk；ZIP 需要先解压。
本包使用代码与资源裁剪，保留原应用包名；签名已通过完整性检查。
仅相同签名的旧安装可直接覆盖升级。正式签名版本需使用对应签名的安装包。
首次启动请设置校历，日期格式为 YYYY-MM-DD，例如 2026-08-31。
源码与使用说明：https://github.com/YUYU-Fish2/DongXuelian-Custom-Timetable
"@ | Set-Content -LiteralPath $instructions -Encoding UTF8

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path $OutDir "$baseName.zip"
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
$archive = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    foreach ($file in @($publishedApk, $instructions)) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file,
            [System.IO.Path]::GetFileName($file), [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }

$checksums = foreach ($file in @($publishedApk, $zipPath)) {
    "{0}  {1}" -f (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant(), [System.IO.Path]::GetFileName($file)
}
$checksums | Set-Content -LiteralPath (Join-Path $OutDir 'SHA256SUMS.txt') -Encoding ASCII
$signature | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' } | Write-Host
Get-Item -LiteralPath $publishedApk, $zipPath | Select-Object Name, Length
Write-Host "APK, ZIP and SHA-256 checksums saved to $OutDir"
