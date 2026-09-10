# Resolve the JDK, Android SDK and Gradle user home for the helper scripts.
#
# Resolution order (no machine-specific defaults are hardcoded, so the scripts
# work on any developer machine and on CI):
#   JDK        $env:JAVA_HOME
#   Android    $env:ANDROID_HOME -> local.properties "sdk.dir" -> %LOCALAPPDATA%\Android\Sdk
#   Gradle     $env:GRADLE_USER_HOME -> ~/.gradle
#
# local.properties is machine-local and git-ignored; it is never published.

function Get-MeinJavaHome {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }
    throw "JAVA_HOME is not set. Point it at a JDK 17+ (for example the JBR bundled with Android Studio)."
}

function Get-MeinAndroidHome {
    if ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }
    $localProperties = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        foreach ($line in Get-Content -LiteralPath $localProperties) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                $sdkDir = $matches[1].Trim() -replace '\\:', ':'
                if ($sdkDir -and (Test-Path -LiteralPath $sdkDir)) {
                    return $sdkDir
                }
            }
        }
    }
    $fallback = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path -LiteralPath $fallback) {
        return $fallback
    }
    throw "Android SDK not found. Set ANDROID_HOME or declare sdk.dir in local.properties."
}

function Get-MeinGradleUserHome {
    if ($env:GRADLE_USER_HOME -and (Test-Path -LiteralPath $env:GRADLE_USER_HOME)) {
        return $env:GRADLE_USER_HOME
    }
    return (Join-Path $env:USERPROFILE ".gradle")
}