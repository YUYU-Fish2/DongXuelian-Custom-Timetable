Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "local-env.ps1")
$javaHome = Get-MeinJavaHome
$androidHome = Get-MeinAndroidHome
$javac = Join-Path $javaHome "bin\javac.exe"
$java = Join-Path $javaHome "bin\java.exe"
$androidJar = Join-Path $androidHome "platforms\android-36.1\android.jar"

foreach ($required in @($javac, $java, $androidJar)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required verification dependency is missing: $required"
    }
}

$logicOut = Join-Path $root "build\logic-test-classes"
$androidOut = Join-Path $root "build\android-java-check"
New-Item -ItemType Directory -Force -Path $logicOut, $androidOut | Out-Null

& $javac -encoding UTF-8 -d $logicOut `
    (Join-Path $root "app\src\main\java\com\example\meinstundenplan\TimetableRules.java") `
    (Join-Path $root "app\src\main\java\com\example\meinstundenplan\ImportException.java") `
    (Join-Path $root "app\src\main\java\com\example\meinstundenplan\PdfCourseParser.java") `
    (Join-Path $root "tools\TimetableRulesTest.java") `
    (Join-Path $root "tools\PdfCourseParserTest.java")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java -cp $logicOut com.example.meinstundenplan.TimetableRulesTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java -cp $logicOut com.example.meinstundenplan.PdfCourseParserTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$sources = Get-ChildItem -LiteralPath (Join-Path $root "app\src\main\java") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName
$stubs = Get-ChildItem -LiteralPath (Join-Path $root "tools\compile-stubs") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName

& $javac -encoding UTF-8 --release 17 -cp $androidJar -d $androidOut @sources @stubs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Android Java compile check passed." -ForegroundColor Green
