param(
    [string] $Serial,
    [string] $Jdk = 'D:\soft\JAVA\jdk-17',
    [string] $Sdk = 'D:\Android\Sdk',
    [string] $Results
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Results) { $Results = Join-Path $root '..\device-results' }
$Results = Join-Path $Results (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force -Path $Results | Out-Null
$Results = (Resolve-Path -LiteralPath $Results).Path
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$package = 'com.example.meinstundenplan.validation'
$testPackage = "$package.test"
foreach ($required in @($adb, (Join-Path $Jdk 'bin\java.exe'), (Join-Path $Sdk 'platforms\android-36.1\android.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing dependency: $required" }
}
$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $Sdk
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$script:deviceArgs = @()
function Invoke-Recorded([string] $Exe, [string[]] $Arguments, [string] $Log) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Exe @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $Results $Log)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($code -ne 0) { throw "$Exe failed with exit code $code; see $Log" }
}
function Invoke-Device([string[]] $Arguments, [string] $Log) {
    Invoke-Recorded $adb (@($script:deviceArgs) + $Arguments) $Log
}

$transcriptStarted = $false
try {
    Start-Transcript -Path (Join-Path $Results 'session.txt') -Force | Out-Null
    $transcriptStarted = $true
    Write-Host 'Unlock the phone and accept its USB debugging authorization if prompted.'
    Invoke-Recorded $adb @('start-server') 'adb-start.txt'
    $listing = @(& $adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB cannot enumerate devices.' }
    $listing | Set-Content -LiteralPath (Join-Path $Results 'devices.txt') -Encoding UTF8
    $listing | ForEach-Object { Write-Host $_ }
    if ($listing -match '\sunauthorized(?:\s|$)') {
        Read-Host 'Accept the USB debugging prompt on the phone, then press Enter' | Out-Null
        $listing = @(& $adb devices -l)
        if ($LASTEXITCODE -ne 0) { throw 'ADB cannot enumerate devices after authorization.' }
        $listing | Set-Content -LiteralPath (Join-Path $Results 'devices.txt') -Encoding UTF8
    }
    $ready = @($listing | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($Serial) {
        if ($ready -notcontains $Serial) { throw 'Selected device is absent, offline or unauthorized.' }
    } elseif ($ready.Count -eq 1) { $Serial = $ready[0] }
    elseif ($ready.Count -eq 0) { throw 'No authorized device. Check USB data mode/cable, RSA authorization and OEM ADB driver.' }
    else { throw 'Multiple devices: rerun with -Serial <device-id>.' }
    $script:deviceArgs = @('-s', $Serial)
    Invoke-Device @('shell', 'getprop', 'ro.product.model') 'model.txt'
    Invoke-Device @('shell', 'getprop', 'ro.build.version.sdk') 'sdk.txt'
    $api = [int](Get-Content -LiteralPath (Join-Path $Results 'sdk.txt') -Raw).Trim()
    if ($api -lt 23) { throw 'App requires Android API 23 or later.' }
    Push-Location $root
    try {
        Invoke-Recorded (Join-Path $root 'gradlew.bat') @('--no-daemon', '--console=plain', '--init-script',
            (Join-Path $PSScriptRoot 'device-validation.init.gradle'), ':app:checkDeviceValidationConfig', ':app:assembleDebug', ':app:assembleDebugAndroidTest') 'build.txt'
    } finally { Pop-Location }
    $appApk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $testApk = Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    $analyzer = Join-Path $Sdk 'build-tools\36.1.0\aapt.exe'
    $badging = & $analyzer dump badging $appApk
    if ($LASTEXITCODE -ne 0 -or -not ($badging -match "^package: name='$package' ")) {
        throw 'Refusing installation: APK does not have the isolated validation package ID.'
    }
    Invoke-Device @('install', '-r', '-t', $appApk) 'install-app.txt'
    Invoke-Device @('install', '-r', '-t', $testApk) 'install-tests.txt'
    if ($api -ge 33) {
        $permissions = & $adb @script:deviceArgs shell dumpsys package $package
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect test-app notification permission.' }
        if (-not ($permissions -match 'android.permission.POST_NOTIFICATIONS: granted=true')) {
            Write-Host 'Enable notifications for the validation app on the phone.'
            Invoke-Device @('shell', 'am', 'start', '-a', 'android.settings.APP_NOTIFICATION_SETTINGS',
                '--es', 'android.provider.extra.APP_PACKAGE', $package) 'notification-settings.txt'
            Read-Host 'After enabling notifications, press Enter to continue' | Out-Null
            $permissions = & $adb @script:deviceArgs shell dumpsys package $package
            if ($LASTEXITCODE -ne 0 -or -not ($permissions -match 'android.permission.POST_NOTIFICATIONS: granted=true')) {
                throw 'Notification permission is still absent; no notification test was run.'
            }
        }
        'Notification permission granted (verified from PackageManager).' |
            Set-Content -LiteralPath (Join-Path $Results 'notification-permission.txt') -Encoding UTF8
    }
    Invoke-Device @('shell', 'am', 'instrument', '-w', '-r', "$testPackage/com.example.meinstundenplan.DeviceValidationRunner") 'instrumentation.txt'
    $instrumentation = Get-Content -LiteralPath (Join-Path $Results 'instrumentation.txt') -Raw
    Invoke-Device @('shell', 'am', 'start', '-W', '-n', "$package/com.example.meinstundenplan.MainActivity") 'launch.txt'
    Invoke-Device @('shell', 'screencap', '-p', '/data/local/tmp/meinstundenplan-validation.png') 'screenshot-capture.txt'
    Invoke-Device @('pull', '/data/local/tmp/meinstundenplan-validation.png', (Join-Path $Results 'screen.png')) 'screenshot-pull.txt'
    Invoke-Device @('shell', 'rm', '/data/local/tmp/meinstundenplan-validation.png') 'screenshot-cleanup.txt'
    if ($instrumentation -notmatch 'DEVICE_VALIDATION passed=\d+ failed=0 skipped=\d+' -or
        $instrumentation -notmatch 'INSTRUMENTATION_CODE: -1' -or
        $instrumentation -match 'INSTRUMENTATION_FAILED|FATAL|FAIL ') {
        throw 'Device validation failed or did not complete. See instrumentation.txt.'
    }
    Write-Host "Device tests finished. Results: $Results" -ForegroundColor Green
    Write-Host 'The separate validation app remains installed. Its temporary alarms and notifications have been cancelled.'
} catch {
    $_ | Out-String | Set-Content -LiteralPath (Join-Path $Results 'failure.txt') -Encoding UTF8
    Write-Host $_ -ForegroundColor Red
    exit 1
} finally {
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
}
