Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "local-env.ps1")
$javaHome = Get-MeinJavaHome
$androidHome = Get-MeinAndroidHome
$gradleUserHome = Get-MeinGradleUserHome

$wrapper = Join-Path $root "gradlew.bat"
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Gradle wrapper not found: $wrapper"
}

foreach ($required in @(
    (Join-Path $javaHome "bin\java.exe"),
    (Join-Path $androidHome "platforms\android-36.1\android.jar")
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing build dependency: $required"
    }
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = $gradleUserHome

Push-Location $root
try {
    & $wrapper --no-daemon clean lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$outputDir = Join-Path $root "app\build\outputs\apk\release"
$apk = Get-ChildItem -LiteralPath $outputDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) {
    throw "Gradle finished but no release APK was found under $outputDir"
}

if ($apk.Name -like "*unsigned*") {
    Write-Host "Unsigned release APK built: $($apk.FullName)" -ForegroundColor Yellow
    Write-Host "Add signing credentials (see README) to produce a signed APK." -ForegroundColor Yellow
} else {
    Write-Host "Signed release APK built: $($apk.FullName)" -ForegroundColor Green
}