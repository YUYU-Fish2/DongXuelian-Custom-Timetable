# Device validation of the timetable fixes

The repaired debug app and its instrumentation APK built successfully with real
AndroidX/Material dependencies. `lintDebug`, the host regression suite and the
static security audit passed on 2026-10-04.

Instrumentation ran on an RMX3700 with Android 16 (API 36). Both test installations
used the separate application ID `com.example.meinstundenplan.validation` and
test ID `com.example.meinstundenplan.validation.test`. The existing production app
was left installed and its preferences were not touched.

Final device result: **15 passed, 0 failed, 0 skipped**.

```text
PASS activity-launch
PASS pdf-ascii
PASS pdf-hex
PASS pdf-period
PASS pdf-compressed
PASS pdf-encoding
PASS android-keystore
PASS strict-dates
PASS daylight-saving
PASS course-edit-persist-reload
PASS import-conflict-dialog
PASS import-different-weeks-dialog
PASS import-period-recheck-dialog
PASS receiver-current-and-stale
PASS alarm-system-delivery
INSTRUMENTATION_CODE: -1
```

The first run skipped system alarm delivery because exact-alarm access was absent.
After the user granted it to the validation app, the rerun received the notification
from the actual AlarmManager broadcast. The receiver test also verified that a
previously valid broadcast does not post after the schedule is cleared. Temporary
scheduled reminders and notifications were cancelled at the end of the runner.

Course edit/save/reload tests invoke the real Activity methods. Import tests click
the real confirmation dialog. PDF fixtures execute the actual parser on Android.
These checks do not cover every edit-form input, the system file picker, prolonged
idle, reboot recovery, old-installation migration or other devices/OS versions.
Release packaging and Release lint were not rerun.

## Reproduce

With JDK 17+, Android SDK 36.1/build-tools 36.1.0 and an authorized USB device:

```powershell
.\tools\verify-device.ps1 -Jdk '<JDK path>' -Sdk '<SDK path>' -Serial '<device ID>'
```

Accept phone installation/notification prompts. Grant exact-alarm access to the
validation app to exercise the final alarm-delivery test; otherwise it reports
SKIP rather than claiming delivery succeeded. The init script adds the validation
application ID suffix and custom instrumentation runner only for this invocation.
The installer verifies the APK ID and the runner checks its target before writing.

## Remaining findings

- Ordinary courses with explicit past weeks are automatically deleted rather than
  retained for historical week browsing.
- Failed encryption/preferences commits do not roll back the already edited
  in-memory course list; callers can still show success.
- User signing properties overwrite project signing properties, contrary to the
  documented precedence.
- The PDF parser still assumes a particular school layout and has no complete font
  CMap handling. Unmarked UTF-16BE bytes that are also valid UTF-8/ASCII remain
  ambiguous (for example `65 70 5B 66` can be `ep[f` or the Chinese name 数学).
