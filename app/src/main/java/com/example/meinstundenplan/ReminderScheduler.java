package com.example.meinstundenplan;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


final class ReminderScheduler {
    static final String EXTRA_REQUEST_CODE = "reminder_request_code";
    static final String EXTRA_TRIGGER_AT = "reminder_trigger_at";
    static final String EXTRA_GENERATION = "reminder_schedule_generation";

    private static final String PREFS_NAME = "timetable";
    private static final String STORE_KEY = "class_reminder_schedule_encrypted_v1";
    private static final String LAST_SUBMISSION_OK_KEY = "class_reminder_last_submission_ok";
    private static final String LAST_SUBMISSION_AT_KEY = "class_reminder_last_submission_at";
    private static final String KEY_ALIAS = "mein_stundenplan_reminder_schedule_key";
    private static final SecureStorage STORAGE = new SecureStorage(KEY_ALIAS);

    private ReminderScheduler() {
    }

    static void replaceSchedule(Context context, List<Entry> entries) {
        ScheduleData previous = loadSchedule(context);
        long generation = nextGeneration(previous.generation);
        ScheduleData replacement = new ScheduleData(generation, entries);
        if (!saveSchedule(context, replacement)) {
            recordSubmissionResult(context, false);
            return;
        }
        cancelEntries(context, previous.entries);
        boolean submitted = true;
        for (Entry entry : entries) {
            submitted &= scheduleFirstFuture(context, entry, System.currentTimeMillis(), generation);
        }
        recordSubmissionResult(context, submitted);
    }

    static void rescheduleStored(Context context) {
        ScheduleData schedule = loadSchedule(context);
        long now = System.currentTimeMillis();
        boolean submitted = true;
        for (Entry entry : schedule.entries) {
            submitted &= scheduleFirstFuture(context, entry, now, schedule.generation);
        }
        recordSubmissionResult(context, submitted);
    }

    static void scheduleNextFromBroadcast(Context context, Intent deliveredIntent) {
        if (deliveredIntent == null) {
            return;
        }
        int requestCode = deliveredIntent.getIntExtra(EXTRA_REQUEST_CODE, 0);
        long generation = deliveredIntent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE);
        ScheduleData current = loadSchedule(context);
        if (generation != current.generation) {
            return;
        }
        Entry entry = current.entryForRequestCode(requestCode);
        if (entry == null) {
            return;
        }
        long deliveredAt = deliveredIntent.getLongExtra(EXTRA_TRIGGER_AT, 0L);
        boolean submitted = scheduleFirstFuture(
                context,
                entry,
                Math.max(System.currentTimeMillis(), deliveredAt),
                current.generation
        );
        recordSubmissionResult(context, submitted);
    }

    @SuppressLint("ApplySharedPref") // 保存 tombstone 失败时兜底清空，必须同步落盘，避免下次启动恢复旧提醒
    static void clear(Context context) {
        ScheduleData previous = loadSchedule(context);
        ScheduleData tombstone = new ScheduleData(
                nextGeneration(previous.generation),
                new ArrayList<>()
        );
        boolean saved = saveSchedule(context, tombstone);
        if (!saved) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(STORE_KEY, "")
                    .commit();
        }
        cancelEntries(context, previous.entries);
        recordSubmissionResult(context, saved);
    }

    static Status status(Context context) {
        List<Entry> entries = loadSchedule(context).entries;
        long now = System.currentTimeMillis();
        long nextTriggerAt = -1L;
        int pendingCourses = 0;
        for (Entry entry : entries) {
            long candidate = TimetableRules.nextTriggerAfter(entry.triggerTimes, now);
            if (candidate <= 0L) {
                continue;
            }
            pendingCourses++;
            if (nextTriggerAt <= 0L || candidate < nextTriggerAt) {
                nextTriggerAt = candidate;
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean submissionOk = prefs.getBoolean(LAST_SUBMISSION_OK_KEY, entries.isEmpty());
        long submittedAt = prefs.getLong(LAST_SUBMISSION_AT_KEY, 0L);
        return new Status(
                pendingCourses,
                nextTriggerAt,
                submissionOk,
                submittedAt,
                canScheduleExactAlarms(context)
        );
    }

    private static long nextGeneration(long previousGeneration) {
        long candidate = System.currentTimeMillis() * 1024L
                + (android.os.SystemClock.elapsedRealtimeNanos() & 1023L);
        return Math.max(candidate, previousGeneration + 1L);
    }

    private static void cancelEntries(Context context, List<Entry> entries) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            for (Entry entry : entries) {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        entry.requestCode,
                        new Intent(context, ClassReminderReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        }
    }

    private static boolean scheduleFirstFuture(
            Context context,
            Entry entry,
            long afterMillis,
            long generation
    ) {
        long triggerAt = TimetableRules.nextTriggerAfter(entry.triggerTimes, afterMillis);
        if (triggerAt <= 0L) {
            return true;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return false;
        }
        Intent intent = entry.toIntent(context, generation);
        intent.putExtra(EXTRA_TRIGGER_AT, triggerAt);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                entry.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    @SuppressLint("ApplySharedPref") // BroadcastReceiver 路径下进程可能被回收，必须同步落盘
    private static void recordSubmissionResult(Context context, boolean success) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LAST_SUBMISSION_OK_KEY, success)
                .putLong(LAST_SUBMISSION_AT_KEY, System.currentTimeMillis())
                .commit();
    }

    private static boolean saveSchedule(Context context, ScheduleData schedule) {
        try {
            JSONArray array = new JSONArray();
            for (Entry entry : schedule.entries) {
                array.put(entry.toJson());
            }
            JSONObject payload = new JSONObject();
            payload.put("generation", schedule.generation);
            payload.put("entries", array);
            String encrypted = STORAGE.encrypt(payload.toString());
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(STORE_KEY, encrypted)
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ScheduleData loadSchedule(Context context) {
        List<Entry> entries = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encrypted = prefs.getString(STORE_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) {
            return new ScheduleData(0L, entries);
        }
        try {
            String decoded = STORAGE.decrypt(encrypted);
            long generation = 0L;
            JSONArray array;
            if (decoded.trim().startsWith("[")) {
                array = new JSONArray(decoded);
            } else {
                JSONObject payload = new JSONObject(decoded);
                generation = payload.optLong("generation", 0L);
                array = payload.optJSONArray("entries");
                if (array == null) {
                    return new ScheduleData(generation, entries);
                }
            }
            for (int i = 0; i < array.length(); i++) {
                Entry entry = Entry.fromJson(array.optJSONObject(i));
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return new ScheduleData(generation, entries);
        } catch (Exception ignored) {
            // Keep the encrypted payload intact. Opening the app can rebuild it from course data.
        }
        return new ScheduleData(Long.MIN_VALUE, entries);
    }


    static final class Entry {
        final int requestCode;
        final String name;
        final String time;
        final String room;
        final int leadMinutes;
        final long[] triggerTimes;

        Entry(int requestCode, String name, String time, String room, int leadMinutes, long[] triggerTimes) {
            this.requestCode = requestCode;
            this.name = name == null ? "" : name;
            this.time = time == null ? "" : time;
            this.room = room == null ? "" : room;
            this.leadMinutes = leadMinutes;
            this.triggerTimes = triggerTimes == null ? new long[0] : triggerTimes.clone();
            Arrays.sort(this.triggerTimes);
        }

        Intent toIntent(Context context, long generation) {
            Intent intent = new Intent(context, ClassReminderReceiver.class);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, name);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_TIME, time);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_ROOM, room);
            intent.putExtra(ClassReminderReceiver.EXTRA_REMINDER_MINUTES, leadMinutes);
            intent.putExtra(EXTRA_REQUEST_CODE, requestCode);
            intent.putExtra(EXTRA_GENERATION, generation);
            return intent;
        }

        JSONObject toJson() throws Exception {
            JSONObject object = new JSONObject();
            object.put("requestCode", requestCode);
            object.put("name", name);
            object.put("time", time);
            object.put("room", room);
            object.put("leadMinutes", leadMinutes);
            JSONArray times = new JSONArray();
            for (long triggerTime : triggerTimes) {
                times.put(triggerTime);
            }
            object.put("triggerTimes", times);
            return object;
        }

        static Entry fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            JSONArray times = object.optJSONArray("triggerTimes");
            if (times == null) {
                return null;
            }
            long[] triggerTimes = new long[times.length()];
            for (int i = 0; i < times.length(); i++) {
                triggerTimes[i] = times.optLong(i, -1L);
            }
            return new Entry(
                    object.optInt("requestCode", 0),
                    object.optString("name", ""),
                    object.optString("time", ""),
                    object.optString("room", ""),
                    object.optInt("leadMinutes", 30),
                    triggerTimes
            );
        }

    }

    static final class Status {
        final int pendingCourses;
        final long nextTriggerAt;
        final boolean submissionOk;
        final long submittedAt;
        final boolean exactAlarmAllowed;

        Status(
                int pendingCourses,
                long nextTriggerAt,
                boolean submissionOk,
                long submittedAt,
                boolean exactAlarmAllowed
        ) {
            this.pendingCourses = pendingCourses;
            this.nextTriggerAt = nextTriggerAt;
            this.submissionOk = submissionOk;
            this.submittedAt = submittedAt;
            this.exactAlarmAllowed = exactAlarmAllowed;
        }
    }

    private static final class ScheduleData {
        final long generation;
        final List<Entry> entries;

        ScheduleData(long generation, List<Entry> entries) {
            this.generation = generation;
            this.entries = new ArrayList<>(entries);
        }

        Entry entryForRequestCode(int requestCode) {
            for (Entry entry : entries) {
                if (entry.requestCode == requestCode) {
                    return entry;
                }
            }
            return null;
        }
    }
}
