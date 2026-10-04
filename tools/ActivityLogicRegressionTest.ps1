# Compile the actual, unchanged method bodies in a small host-JVM harness.
# This tests private Activity logic without invoking Android's stub constructors.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$source = Get-Content -LiteralPath (Join-Path $root 'app/src/main/java/com/example/meinstundenplan/MainActivity.java') -Raw -Encoding UTF8
$scheduler = Get-Content -LiteralPath (Join-Path $root 'app/src/main/java/com/example/meinstundenplan/ReminderScheduler.java') -Raw -Encoding UTF8
$receiver = Get-Content -LiteralPath (Join-Path $root 'app/src/main/java/com/example/meinstundenplan/ClassReminderReceiver.java') -Raw -Encoding UTF8

function Get-MethodBody([string] $text, [string] $signature) {
    $start = $text.IndexOf($signature)
    if ($start -lt 0) { throw "Method not found: $signature" }
    $open = $text.IndexOf('{', $start)
    $depth = 1
    $end = $open + 1
    while ($depth -gt 0 -and $end -lt $text.Length) {
        if ($text[$end] -eq '{') { $depth++ }
        if ($text[$end] -eq '}') { $depth-- }
        $end++
    }
    return $text.Substring($start, $end - $start)
}

$schedulerSignature = if ($scheduler.Contains('static boolean scheduleNextFromBroadcast(')) {
    'static boolean scheduleNextFromBroadcast('
} else { 'static void scheduleNextFromBroadcast(' }
$methods = @(
    (Get-MethodBody $source 'private Long parseDateMillis('),
    (Get-MethodBody $source 'private long startOfDayMillis('),
    (Get-MethodBody $source 'private int currentTeachingWeek('),
    (Get-MethodBody $source 'private int calendarTotalWeeks('),
    (Get-MethodBody $source 'private String weekRangeLabel('),
    (Get-MethodBody $source 'private long courseDateMillisForWeek('),
    (Get-MethodBody $source 'private long displayedDateMillis('),
    (Get-MethodBody $source 'private String displayedWeekRangeLabel('),
    (Get-MethodBody $source 'private boolean hasCourseConflict('),
    (Get-MethodBody $source 'private boolean periodsOverlap('),
    (Get-MethodBody $source 'private boolean weeksOverlap('),
    (Get-MethodBody $source 'private boolean hasImportedCourseDuplicate('),
    (Get-MethodBody $receiver 'public void onReceive('),
    (Get-MethodBody $scheduler $schedulerSignature)
) -join "`n"
$importLoop = Get-MethodBody $source 'for (Course parsed : parsedCourses)'
$methods += @"

private int importCourses(List<Course> parsedCourses) {
    int imported = 0, skipped = 0;
    long baseId = System.currentTimeMillis();
    StringBuilder skipLog = new StringBuilder();
    $importLoop
    return imported;
}
"@

$template = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ActivityLogicRegressionTest.java.template') -Raw -Encoding UTF8
$schedulerBridge = if ($schedulerSignature.StartsWith('static boolean')) {
    'return ActivityLogicRegressionTest.scheduleNextFromBroadcast(c, i);'
} else { 'ActivityLogicRegressionTest.scheduleNextFromBroadcast(c, i); return true;' }
$generated = $template.Replace('/* ACTUAL_METHODS */', $methods).Replace('/* SCHEDULER_BRIDGE */', $schedulerBridge)
$out = Join-Path $root 'build/activity-logic-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$file = Join-Path $out 'ActivityLogicRegressionTest.java'
[IO.File]::WriteAllText($file, $generated, [Text.UTF8Encoding]::new($false))
$jdk = Get-MeinJavaHome
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 -d $out $file (Join-Path $root 'app/src/main/java/com/example/meinstundenplan/TimetableRules.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $jdk 'bin/java.exe') -cp $out com.example.meinstundenplan.ActivityLogicRegressionTest
exit $LASTEXITCODE
