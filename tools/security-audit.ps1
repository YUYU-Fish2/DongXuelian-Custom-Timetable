Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $root "app/src/main/AndroidManifest.xml"
$activityPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/MainActivity.java"
$secureStoragePath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/SecureStorage.java"
$reminderReceiverPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ClassReminderReceiver.java"
$reminderSchedulerPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ReminderScheduler.java"
$bootReceiverPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ReminderBootReceiver.java"
$rulesPath = Join-Path $root "app/src/main/res/xml/data_extraction_rules.xml"
$appBuildPath = Join-Path $root "app/build.gradle"

$failures = New-Object System.Collections.Generic.List[string]

function Assert-Contains {
    param(
        [string] $Name,
        [string] $Text,
        [string] $Pattern
    )
    if ($Text -notmatch $Pattern) {
        $failures.Add("$Name missing required pattern: $Pattern")
    }
}

function Assert-NotContains {
    param(
        [string] $Name,
        [string] $Text,
        [string] $Pattern
    )
    if ($Text -match $Pattern) {
        $failures.Add("$Name contains forbidden pattern: $Pattern")
    }
}

foreach ($path in @($manifestPath, $activityPath, $secureStoragePath, $reminderReceiverPath, $reminderSchedulerPath, $bootReceiverPath, $rulesPath, $appBuildPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Required file is missing: $path")
    }
}

if ($failures.Count -eq 0) {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
    $activity = Get-Content -LiteralPath $activityPath -Raw -Encoding UTF8
    $secureStorage = Get-Content -LiteralPath $secureStoragePath -Raw -Encoding UTF8
    $reminderReceiver = Get-Content -LiteralPath $reminderReceiverPath -Raw -Encoding UTF8
    $reminderScheduler = Get-Content -LiteralPath $reminderSchedulerPath -Raw -Encoding UTF8
    $rules = Get-Content -LiteralPath $rulesPath -Raw -Encoding UTF8
    $appBuild = Get-Content -LiteralPath $appBuildPath -Raw -Encoding UTF8
    $scanExtensions = @(".java", ".kt", ".xml", ".gradle", ".properties")
    $appSourceFiles = Get-ChildItem -LiteralPath (Join-Path $root "app") -Recurse -File |
        Where-Object { $scanExtensions.Contains($_.Extension) -and $_.FullName -notmatch '[\\/](build|\.gradle)[\\/]' }
    $rootConfigFiles = @("build.gradle", "settings.gradle", "gradle.properties") |
        ForEach-Object { Join-Path $root $_ } |
        Where-Object { Test-Path -LiteralPath $_ }
    $scanFiles = @($appSourceFiles.FullName) + @($rootConfigFiles)
    $allProjectText = ($scanFiles | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    $javaKotlinText = ($appSourceFiles | Where-Object { $_.Extension -in @(".java", ".kt") } |
        ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"

    Assert-Contains "AndroidManifest.xml" $manifest 'android:allowBackup="false"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:fullBackupContent="false"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:dataExtractionRules="@xml/data_extraction_rules"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:usesCleartextTraffic="false"'
    Assert-NotContains "AndroidManifest.xml" $manifest '<uses-permission\s+android:name="android\.permission\.INTERNET"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.RECEIVE_BOOT_COMPLETED'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.SCHEDULE_EXACT_ALARM'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:name="\.ReminderBootReceiver"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.intent\.action\.BOOT_COMPLETED'

    Assert-Contains "data_extraction_rules.xml" $rules '<exclude\s+domain="sharedpref"\s+path="\."\s*/>'

    # 加密实现已抽到 SecureStorage.java；断言随之迁移
    Assert-Contains "SecureStorage.java" $secureStorage 'AES/GCM/NoPadding'
    Assert-Contains "SecureStorage.java" $secureStorage 'AndroidKeyStore'
    Assert-Contains "SecureStorage.java" $secureStorage 'KeyGenParameterSpec'
    Assert-Contains "SecureStorage.java" $secureStorage 'setRandomizedEncryptionRequired\(true\)'
    Assert-Contains "MainActivity.java" $activity 'new SecureStorage\('
    Assert-Contains "MainActivity.java" $activity 'courses_encrypted_v2'
    Assert-Contains "MainActivity.java" $activity '\.commit\(\)'
    Assert-Contains "MainActivity.java" $activity 'storageLocked'
    Assert-Contains "MainActivity.java" $activity 'InputFilter\.LengthFilter'
    Assert-Contains "MainActivity.java" $activity 'MAX_COURSES'
    Assert-Contains "MainActivity.java" $activity 'ACTION_OPEN_DOCUMENT'
    Assert-Contains "MainActivity.java" $activity 'MAX_PDF_BYTES'
    # 在线校历导入已移除：不允许再出现任何联网代码
    Assert-NotContains "MainActivity.java" $activity 'HttpURLConnection|java\.net\.URL|importAcademicCalendarOnline|fetchAcademicCalendar'
    Assert-Contains "MainActivity.java" $activity 'PdfCourseParser\.parseCourses'
    Assert-Contains "MainActivity.java" $activity 'readWithLimit'
    Assert-NotContains "MainActivity.java" $activity 'putString\(COURSES_KEY[^;]*?\.apply\(\)'
    Assert-NotContains "MainActivity.java" $activity 'putString\(COURSES_KEY,\s*array\.toString\(\)\)'
    Assert-Contains "app/build.gradle" $appBuild 'minifyEnabled\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'shrinkResources\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'debuggable\s+false'
    Assert-Contains "app/build.gradle" $appBuild 'proguard-android-optimize\.txt'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.scheduleNextFromBroadcast'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.EXTRA_REQUEST_CODE'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'class_reminder_schedule_encrypted_v1'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'new SecureStorage\('
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'STORAGE\.encrypt'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'STORAGE\.decrypt'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setExactAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'canScheduleExactAlarms'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'reminder_schedule_generation'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler '\.commit\(\)'
    Assert-NotContains "ReminderScheduler.java" $reminderScheduler '\.apply\(\)'
    if ($reminderReceiver.IndexOf('ReminderScheduler.scheduleNextFromBroadcast') -gt
            $reminderReceiver.IndexOf('POST_NOTIFICATIONS')) {
        $failures.Add("ClassReminderReceiver.java must continue the reminder chain before notification permission checks")
    }

    Assert-NotContains "project" $allProjectText 'android\.permission\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|QUERY_ALL_PACKAGES|REQUEST_INSTALL_PACKAGES|SYSTEM_ALERT_WINDOW|READ_SMS|SEND_SMS|RECORD_AUDIO|CAMERA|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)'
    Assert-NotContains "project" $allProjectText 'WebView|addJavascriptInterface|setJavaScriptEnabled\(true\)'
    Assert-NotContains "project" $allProjectText 'DexClassLoader|PathClassLoader|loadClass\('
    Assert-NotContains "project" $allProjectText 'Runtime\.getRuntime\(\)\.exec|ProcessBuilder'
    Assert-NotContains "project" $allProjectText 'ObjectInputStream|Serializable'
    Assert-NotContains "project" $allProjectText 'TrustManager|HostnameVerifier|setHostnameVerifier'
    Assert-NotContains "project" $allProjectText 'MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE'
    Assert-NotContains "Java/Kotlin source" $javaKotlinText 'http://'
    Assert-NotContains "Java/Kotlin source" $javaKotlinText 'https://'
}

if ($failures.Count -gt 0) {
    Write-Host "Security audit failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Security audit passed." -ForegroundColor Green
