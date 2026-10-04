package com.example.meinstundenplan;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/** No emulator, mocks, JUnit or external test-runner dependencies are used. */
public final class DeviceValidationRunner extends Instrumentation {
    private Activity activity;
    private boolean isolatedTarget;
    private int passed, failed, skipped;
    private final StringBuilder report = new StringBuilder();
    private boolean visualBaseline;
    private boolean statusMatrix;
    private interface Checked { void run() throws Exception; }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        visualBaseline = arguments != null && "true".equals(arguments.getString("visualBaseline"));
        statusMatrix = arguments != null && "true".equals(arguments.getString("statusMatrix"));
        start();
    }

    @Override public void onStart() {
        try {
            check(getTargetContext().getPackageName().endsWith(".validation"), "Refusing production package");
            isolatedTarget = true;
            if (visualBaseline) {
                captureVisualBaseline();
                return;
            }
            SharedPreferences prefs = prefs();
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
            long start = today.getTimeInMillis();
            today.add(Calendar.DAY_OF_YEAR, 150);
            check(prefs.edit().clear().putBoolean("calendar_initialized", true)
                    .putBoolean("notifications_enabled", false).putLong("calendar_start_millis", start)
                    .putLong("calendar_end_millis", today.getTimeInMillis()).commit(), "Cannot seed test preferences");
            activity = startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
                    "com.example.meinstundenplan.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            test("activity-launch", () -> check(!activity.isFinishing(), "Activity closed"));
            for (String fixture : new String[]{"ascii", "hex", "period", "compressed", "encoding"}) {
                test("pdf-" + fixture, () -> BugRegressionTest.main(new String[]{fixture}));
            }
            test("android-keystore", () -> {
                SecureStorage storage = new SecureStorage("device_validation_test_key");
                String encrypted = storage.encrypt("真实设备：Math");
                check(!encrypted.contains("Math"), "Plaintext leaked");
                check("真实设备：Math".equals(storage.decrypt(encrypted)), "Keystore round trip failed");
            });
            test("strict-dates", () -> onMain(() -> {
                check(call("parseDateMillis", "2026-03-02junk") == null, "Suffix accepted");
                check(call("parseDateMillis", "2026-3-02") == null, "Short month accepted");
                check(call("parseDateMillis", "2026-02-30") == null, "Invalid day accepted");
                check(call("parseDateMillis", "2026-03-02") != null, "Valid date rejected");
            }));
            test("daylight-saving", () -> onMain(() -> {
                TimeZone previous = TimeZone.getDefault();
                long oldStart = prefs.getLong("calendar_start_millis", 0);
                long oldEnd = prefs.getLong("calendar_end_millis", 0);
                try {
                    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
                    long spring = (Long) call("parseDateMillis", "2026-03-02");
                    long end = (Long) call("parseDateMillis", "2026-03-15");
                    check(TimetableRules.daysBetweenDates(spring, end) == 13, "Civil day count drifted");
                    check(prefs.edit().putLong("calendar_start_millis", spring).putLong("calendar_end_millis", end).commit(), "Preferences failed");
                    check(((Integer) call("calendarTotalWeeks")) == 2, "DST week count drifted");
                    check(((String) call("weekRangeLabel", 2)).endsWith("3.9–3.15"), "DST label drifted");
                } finally {
                    TimeZone.setDefault(previous);
                    prefs.edit().putLong("calendar_start_millis", oldStart).putLong("calendar_end_millis", oldEnd).commit();
                }
            }));
            test("course-edit-persist-reload", () -> onMain(() -> {
                courses().clear(); Object course = course(1, "原课程", "1-20周", 1, 2);
                call("upsertCourse", course); call("saveCourses");
                String saved = prefs.getString("courses_encrypted_v2", null);
                check(saved != null && !saved.contains("原课程"), "Encrypted course save failed");
                set(course, "name", "修改后课程"); call("upsertCourse", course); call("saveCourses");
                courses().clear(); call("loadCourses");
                check(courses().size() == 1, "Reload count wrong");
                check("修改后课程".equals(field(courses().get(0), "name")), "Edit not persisted");
            }));
            test("import-conflict-dialog", () -> {
                onMain(() -> { courses().clear(); courses().add(course(1, "已有课程", "1-20周", 1, 2)); });
                importViaDialog("冲突课程", "1-20周", 2, 3);
                onMain(() -> check(courses().size() == 1, "Conflict was imported"));
            });
            test("import-different-weeks-dialog", () -> {
                onMain(() -> { courses().clear(); courses().add(course(1, "已有课程", "1-10周", 1, 2)); });
                importViaDialog("不同周课程", "11-20周", 1, 2);
                onMain(() -> check(courses().size() == 2, "Non-overlapping weeks rejected"));
            });
            test("import-period-recheck-dialog", () -> {
                onMain(() -> courses().clear());
                // A preview from an older period setting must be rejected at confirmation.
                importViaDialog("旧节次课程", "1-20周", 19, 20);
                onMain(() -> check(courses().isEmpty(), "Out-of-range period imported"));
            });
            test("receiver-current-and-stale", () -> {
                Context context = getTargetContext();
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                check(manager != null && (Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled()), "Notification permission/channel disabled");
                manager.cancelAll();
                ReminderScheduler.Entry entry = new ReminderScheduler.Entry(9181, "真机提醒验证", "08:00", "测试教室", 10, new long[0]);
                ReminderScheduler.replaceSchedule(context, Collections.singletonList(entry));
                String payload = new SecureStorage("mein_stundenplan_reminder_schedule_key").decrypt(
                        prefs.getString("class_reminder_schedule_encrypted_v1", ""));
                long generation = new org.json.JSONObject(payload).getLong("generation");
                Intent current = entry.toIntent(context, generation);
                context.sendBroadcast(current);
                waitForNotification(manager, 9181);
                manager.cancelAll();
                ReminderScheduler.clear(context);
                context.sendBroadcast(current);
                SystemClock.sleep(700);
                check(manager.getActiveNotifications().length == 0, "Stale reminder posted after clear");
                new ClassReminderReceiver().onReceive(context, null);
            });
            test("alarm-system-delivery", () -> {
                Context context = getTargetContext();
                ReminderScheduler.Status status = ReminderScheduler.status(context);
                if (!status.exactAlarmAllowed) { skipped++; report.append("SKIP alarm-system-delivery: exact alarm permission absent\n"); return; }
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                check(manager != null && (Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled()), "Notifications disabled");
                manager.cancelAll();
                ReminderScheduler.Entry entry = new ReminderScheduler.Entry(9182, "定时提醒真机验证", "测试", "测试教室", 10,
                        new long[]{System.currentTimeMillis() + 5000});
                ReminderScheduler.replaceSchedule(context, Collections.singletonList(entry));
                check(ReminderScheduler.status(context).submissionOk, "Alarm submission failed");
                waitForNotification(manager, 9182);
            });
            for (int state = 0; state < 8; state++) {
                final int scenario = state;
                test("status-card-" + (char) ('A' + state), () -> onMain(() -> checkStatusCard(scenario)));
            }
            test("status-card-ended-without-future", () -> onMain(() -> {
                checkStatusCard(3);
                set(courses().get(0), "weeks", "1周");
                call("render");
                check(((android.widget.TextView) field(activity, "statusChipLabel")).getText().toString()
                        .equals("今日课程已结束"), "Ended status lost");
                check(((android.widget.TextView) field(activity, "statusChipName")).getText().length() == 0,
                        "Old future course retained");
            }));
            test("status-card-preview-return", () -> onMain(() -> {
                checkStatusCard(5);
                check(((android.view.View) field(activity, "statusChip")).performClick(), "Preview not clickable");
                check((int) field(activity, "viewingWeek") == 0, "Preview click did not return to current week");
            }));
        } catch (Throwable error) { failed++; report.append("FATAL ").append(error).append('\n'); }
        finally {
            try { if (isolatedTarget) {
                ReminderScheduler.clear(getTargetContext());
                getTargetContext().getSystemService(NotificationManager.class).cancelAll();
                if (activity != null) onMain(() -> activity.finish());
            } } catch (Throwable error) { failed++; report.append("CLEANUP FAILED ").append(error).append('\n'); }
            Bundle result = new Bundle();
            result.putString("stream", "\nDEVICE_VALIDATION passed=" + passed + " failed=" + failed + " skipped=" + skipped + "\n" + report);
            result.putInt("failed", failed);
            finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }

    private void test(String name, Checked body) {
        int before = skipped;
        try { body.run(); if (skipped == before) { passed++; report.append("PASS ").append(name).append('\n'); } }
        catch (Throwable error) { failed++; report.append("FAIL ").append(name).append(": ").append(error).append('\n'); }
    }
    private void checkStatusCard(int state) throws Exception {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        int day = (int) call("currentSchoolDay");
        Calendar start = (Calendar) today.clone(); start.add(Calendar.DAY_OF_YEAR, -day);
        Calendar end = (Calendar) start.clone(); end.add(Calendar.WEEK_OF_YEAR, 17);
        if (state == 6) { start.add(Calendar.WEEK_OF_YEAR, 1); end.add(Calendar.WEEK_OF_YEAR, 1); }
        if (state == 7) { start.add(Calendar.WEEK_OF_YEAR, -20); end.add(Calendar.WEEK_OF_YEAR, -20); }
        prefs().edit().putLong("calendar_start_millis", start.getTimeInMillis())
                .putLong("calendar_end_millis", end.getTimeInMillis()).commit();
        courses().clear(); set(activity, "viewingWeek", state == 5 ? 8 : 0);
        int now = (int) call("currentMinutes");
        Object item = course(301, "状态测试课程", "1-17周", 3, 4);
        set(item, "day", day); set(item, "room", "锡科503"); set(item, "teacher", "罗志坚");
        set(item, "customStartMinutes", Math.max(0, now - 30));
        set(item, "customEndMinutes", Math.min(1439, now + 38));
        if (state == 1 || state == 2 || state == 6) {
            int nextMinutes = state == 1 ? Math.min(1439, now + 31) : 600;
            set(item, "customStartMinutes", nextMinutes);
            set(item, "customEndMinutes", Math.min(1439, nextMinutes + 100));
        }
        if (state == 3) {
            set(item, "customStartMinutes", Math.max(0, now - 100));
            set(item, "customEndMinutes", Math.max(0, now - 1));
            courses().add(item);
        }
        // Exercise the complete render -> existing course lookup -> presentation chain.
        if (state == 2) set(item, "weeks", "2周");
        if (state != 3 && state != 4) courses().add(item);
        set(activity, "selectedDay", day);
        call("render");
        String[] labels = {"正在上课", "下一节", "下一节 · ", "今日课程已结束", "暂无后续课程", "预览第 8 周", "未开学", "本学期已结束"};
        android.widget.TextView label = (android.widget.TextView) field(activity, "statusChipLabel");
        check(label.getText().toString().startsWith(labels[state]), "Wrong status: " + label.getText());
        android.widget.TextView countdown = (android.widget.TextView) field(activity, "statusChipCountdown");
        check((countdown.getVisibility() == android.view.View.VISIBLE) == (state <= 1), "Countdown visibility wrong");
        if (state == 0) check(countdown.getText().toString().contains("剩余"), "Remaining label absent");
        if (state == 1) check(countdown.getText().toString().endsWith("min 后"), "Next countdown absent");
        android.widget.TextView name = (android.widget.TextView) field(activity, "statusChipName");
        boolean detail = state == 0 || state == 1 || state == 2 || state == 3 || state == 6;
        check((name.getVisibility() == android.view.View.VISIBLE) == detail, "Stale course detail");
        if (!detail) check(name.getText().length() == 0, "Hidden stale course name");
        android.widget.TextView date = (android.widget.TextView) field(activity, "statusChipDate");
        check((date.getVisibility() == android.view.View.VISIBLE) == (state == 2 || state == 3 || state == 6), "Future date visibility wrong");
    }
    /** Screenshot fixtures are confined to the isolated instrumentation target. */
    private void captureVisualBaseline() throws Exception {
        Calendar start = Calendar.getInstance();
        start.set(2026, Calendar.SEPTEMBER, 28, 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.WEEK_OF_YEAR, 17);
        check(prefs().edit().clear().putBoolean("calendar_initialized", true)
                .putBoolean("notifications_enabled", false).putInt("accent_preset", 0)
                .putLong("calendar_start_millis", start.getTimeInMillis())
                .putLong("calendar_end_millis", end.getTimeInMillis()).commit(), "Cannot seed visual fixture");
        activity = startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
                "com.example.meinstundenplan.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        onMain(() -> {
            courses().clear();
            int[] begins = {480, 600, 870};
            int[] ends = {580, 700, 970};
            for (int i = 0; i < 3; i++) {
                Object item = course(100 + i, i == 0 ? "离散数学" : "数据结构", "1-17周", i * 2 + 1, i * 2 + 2);
                set(item, "day", 6);
                set(item, "room", i == 2 ? "锡科301" : "锡科503");
                set(item, "teacher", "罗志坚");
                set(item, "customStartMinutes", begins[i]);
                set(item, "customEndMinutes", ends[i]);
                courses().add(item);
            }
            call("saveCourses");
            set(activity, "selectedDay", 6);
            set(activity, "viewingWeek", 0);
            call("render");
        });
        screenshot("baseline_home.png");
        if (statusMatrix) {
            for (int state = 0; state < 8; state++) {
                final int scenario = state;
                test("status-card-" + (char) ('A' + state), () -> onMain(() -> checkStatusCard(scenario)));
                screenshot("status-" + (char) ('A' + state) + ".png");
            }
            return;
        }
        onMain(() -> call("showSettingsDialogV2"));
        screenshot("baseline_settings.png");
        onMain(() -> ((android.view.View) field(activity, "settingsOverlay")).performClick());
        onMain(() -> call("showCourseDialog", courses().get(1)));
        screenshot("baseline_course_edit.png");
        passed++;
        report.append("PASS visual-baseline: 3 screenshots; fixed Sunday courses, preset=0\n");
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(900);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Screenshot unavailable: " + name);
        java.io.File directory = new java.io.File(getTargetContext().getExternalFilesDir(null), "ui-baseline");
        check(directory.isDirectory() || directory.mkdirs(), "Cannot create screenshot directory");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name))) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Screenshot encode failed");
        } finally { bitmap.recycle(); }
        report.append("CAPTURE ").append(name).append(" at ").append(new java.util.Date()).append('\n');
    }
    private SharedPreferences prefs() { return getTargetContext().getSharedPreferences("timetable", Context.MODE_PRIVATE); }
    private void onMain(Checked action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new Exception(error.get());
    }
    @SuppressWarnings("unchecked") private List<Object> courses() throws Exception { return (List<Object>) field(activity, "courses"); }
    private Object course(long id, String name, String weeks, int start, int end) throws Exception {
        Constructor<?> constructor = Class.forName("com.example.meinstundenplan.MainActivity$Course").getDeclaredConstructor();
        constructor.setAccessible(true); Object value = constructor.newInstance();
        set(value, "id", id); set(value, "day", 0); set(value, "period", start); set(value, "endPeriod", end);
        set(value, "name", name); set(value, "weeks", weeks); return value;
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private Object call(String name, Object... args) throws Exception {
        for (Method method : activity.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == args.length) {
                method.setAccessible(true); return method.invoke(activity, args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private void importViaDialog(String name, String weeks, int start, int end) throws Exception {
        PdfCourseParser.ParsedCourse item = new PdfCourseParser.ParsedCourse();
        item.name = name; item.weeks = weeks; item.day = 0; item.period = start; item.endPeriod = end;
        item.room = "测试教室"; item.teacher = "测试教师";
        onMain(() -> call("parsedAndShowCourses", Collections.singletonList(item)));
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("确认导入")) {
                    if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        SystemClock.sleep(350); waitForIdleSync(); return;
                    }
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Import confirmation button not found");
    }
    private static void waitForNotification(NotificationManager manager, int id) throws Exception {
        check(Build.VERSION.SDK_INT >= 23, "API too old");
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            for (android.service.notification.StatusBarNotification notification : manager.getActiveNotifications()) {
                if (notification.getId() == id) return;
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("Notification " + id + " did not arrive within 20 seconds");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
