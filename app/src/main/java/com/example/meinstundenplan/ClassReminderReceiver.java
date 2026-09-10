package com.example.meinstundenplan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

public class ClassReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "class_reminders_high_v2";
    static final String EXTRA_COURSE_NAME = "course_name";
    static final String EXTRA_COURSE_TIME = "course_time";
    static final String EXTRA_COURSE_ROOM = "course_room";
    static final String EXTRA_REMINDER_MINUTES = "reminder_minutes";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Continue the chain before attempting to post. A denied notification permission must not
        // make every later reminder for the course unreachable.
        ReminderScheduler.scheduleNextFromBroadcast(context, intent);

        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        ensureChannel(manager);

        String name = intent.getStringExtra(EXTRA_COURSE_NAME);
        String time = intent.getStringExtra(EXTRA_COURSE_TIME);
        String room = intent.getStringExtra(EXTRA_COURSE_ROOM);
        int minutes = intent.getIntExtra(EXTRA_REMINDER_MINUTES, 30);
        int reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REQUEST_CODE, 1);
        if (name == null || name.trim().isEmpty()) {
            name = "\u4e0b\u4e00\u8282\u8bfe";
        }
        String content = minutes + " \u5206\u949f\u540e\u4e0a\u8bfe";
        if (time != null && !time.trim().isEmpty()) {
            content += " \u00b7 " + time;
        }
        if (room != null && !room.trim().isEmpty()) {
            content += " \u00b7 " + room;
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(MainActivity.EXTRA_SHOW_REMINDER_POPUP, true);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_NAME, name);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_TIME, time);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_ROOM, room);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_LEAD_MINUTES, minutes);
        openIntent.setData(Uri.parse("meinstundenplan://reminder/" + reminderId));
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                reminderId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(name)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setColor(MainActivity.accentColorFor(context))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        manager.notify(reminderId, notification);
    }

    static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel != null) {
            return;
        }
        channel = new NotificationChannel(CHANNEL_ID, "\u8bfe\u7a0b\u63d0\u9192", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("\u4e0a\u8bfe\u524d\u63d0\u9192");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }
}
