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
    private interface Checked { void run() throws Exception; }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        try {
            check(getTargetContext().getPackageName().endsWith(".validation"), "Refusing production package");
            isolatedTarget = true;
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
