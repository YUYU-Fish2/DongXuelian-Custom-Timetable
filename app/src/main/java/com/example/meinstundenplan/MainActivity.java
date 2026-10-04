package com.example.meinstundenplan;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "timetable";
    private static final String LEGACY_COURSES_KEY = "courses";
    private static final String COURSES_KEY = "courses_encrypted_v2";
    private static final String THEME_KEY = "theme_mode";
    private static final String ACCENT_KEY = "accent_preset";
    private static final int DEFAULT_ACCENT_PRESET = 0;
    private static final int[][] ACCENT_PRESETS = {
            {0xFF4C63D9, 0xFF7895E8}, // blue (default) — brief 指定主品牌色 Primary Blue
            {0xFF0E9F8A, 0xFF4FD1BC}, // teal
            {0xFF2E9E44, 0xFF6FD08A}, // green
            {0xFF7A4FE0, 0xFFB59AFF}, // purple
            {0xFFE8722E, 0xFFFFA35C}, // orange
            {0xFFD64550, 0xFFFF8A94}, // red
            {0xFF4050D8, 0xFF8A97FF}  // indigo
    };
    private static final String[] ACCENT_PRESET_NAMES = {
            "\u84dd\u8272", "\u9752\u7eff", "\u7eff\u8272", "\u7d2b\u8272", "\u6a59\u8272", "\u7ea2\u8272", "\u975b\u9752"
    };
    private static final String REMINDER_CODES_KEY = "class_reminder_codes";
    private static final String NOTIFICATIONS_ENABLED_KEY = "notifications_enabled";
    private static final String PERIOD_TIMES_KEY = "period_times_v1";
    private static final String CALENDAR_START_KEY = "calendar_start_millis";
    private static final String CALENDAR_END_KEY = "calendar_end_millis";
    private static final String CALENDAR_WEEKS_KEY = "calendar_total_weeks";
    private static final String CALENDAR_NAME_KEY = "calendar_name";
    // 首次安装引导：从未保存过校历（且也没有旧版遗留的校历数据）时，强制要求设置开学日/散学日
    private static final String CALENDAR_INITIALIZED_KEY = "calendar_initialized";
    private static final int MAX_FIELD_LENGTH = 60;
    private static final int MAX_COURSES = 200;
    private static final int REQUEST_IMPORT_PDF = 40;
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private static final int MAX_STREAM_BYTES = 2 * 1024 * 1024;
    private static final long PROGRESS_REFRESH_MS = 5000L;
    private static final long COUNTDOWN_REFRESH_MS = 1000L;
    private static final int COUNTDOWN_SECONDS_THRESHOLD = 5 * 60;
    private static final int COUNTDOWN_ANIMATION_MS = 260;
    private static final int CONTENT_SIDE_MARGIN_DP = 28;
    // Hero 区角色插画。置 false 即回到"无人物版"：去掉人物后版式依然完整可编译。
    private static final boolean SHOW_HERO_CHARACTER = true;
    // Hero 高度硬上限：不超过屏高的 22%，避免压缩课表可视空间。
    private static final int HERO_CHARACTER_MAX_HEIGHT_DP = 150;
    private static final float HERO_TARGET_HEIGHT_RATIO = 0.19f;
    // Initial visual scale; validate cropping within the approved 1.18–1.26 range.
    private static final float HERO_SCENE_SCALE = 1.22f;
    // 课程卡片右下角小插画的透明度。
    // 按反馈"要像 Hero 一样凸显"，已提到接近满不透明；素材端也把饱和/对比拉了一档。
    // 插画绘制在文字下层，且位于右下角，长文本省略时也不会盖住可读内容。
    private static final float COURSE_ART_ALPHA = 0.95f;

    // ─────────────────────────────────────────────────────────────────────
    // 设计 token（brief §1 / §10）。统一收在这里，不要再往 buildLayout 里散写常量。
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 背景三段竖直渐变。
     *
     * 按效果图实测取值：参考图整页是**连续的淡蓝**，越往下越蓝
     * （y=40 #F2F7FE -> y=1000 #F6F9FE -> y=1400 #E3EBFA -> y=1480 #D3E0F7）。
     * 早先底部取了 #F8FAFF（几乎是白的），结果 Hero 的蓝往下过渡到中间
     * 突然变成一片白，断层很突兀。现在中段和底部都跟着蓝下去，整页才连续。
     */
    private static final int PAGE_TOP_LIGHT = 0xFFEDF4FE;
    private static final int PAGE_MID_LIGHT = 0xFFF1F6FE;
    private static final int PAGE_BOTTOM_LIGHT = 0xFFE3ECFB;

    /** 圆角（brief §10）：主要卡片 16dp、按钮 16~20dp、胶囊全圆、小标签 10~12dp。 */
    private static final int RADIUS_CARD = 16;
    private static final int RADIUS_BUTTON = 20;
    private static final int RADIUS_TAG = 12;

    /** 间距基数（brief §1：4 / 8 / 16 / 24）。 */
    private static final int SPACE_XS = 4;
    private static final int SPACE_S = 8;
    private static final int SPACE_M = 16;
    private static final int SPACE_L = 24;

    /**
     * 柔和阴影的高程。
     *
     * brief §10 要求 {@code 0 4px 20px rgba(80,100,150,0.06)} —— 一个带蓝灰色调的极淡投影。
     * Android 的 elevation 阴影由系统按光源生成、**颜色不可指定**（永远是黑色系），
     * 因此无法精确还原这个"染色柔光"。这里用 2dp 高程 + 1dp 细描边近似，
     * 视觉上同为"浅色纸上浮起的卡片"，且没有滚动性能代价。
     * 如需严格还原色值，只能改用软件层 + setShadowLayer，会在列表滚动时掉帧。
     */
    private static final int ELEVATION_SOFT = 2;

    /** 装饰透明度区间（brief §8）：页面装饰 5%~20%，卡片插画 8%~15%。 */
    private static final float DECOR_ALPHA_MIN = 0.05f;
    private static final float DECOR_ALPHA_MAX = 0.20f;

    /** 顶部动作圆钮直径。brief 未给数值，按示意图取 42dp；做正圆时半径取其一半。 */
    private static final int ACTION_BUTTON_SIZE_DP = 42;

    /**
     * 时间轴竖线距 gutter 右边缘的距离。
     * 时间和圆点排成一行右对齐、圆点压在竖线上，都靠这个值对齐。
     */
    private static final int TIMELINE_LINE_INSET_DP = 11;

    /**
     * 时间轴 gutter 中圆点相对行顶的纵向位置。
     * 首行竖线要从这里起、末行竖线要在这里收，否则线会多出或缺少一截。
     * （时间和圆点同一行，所以只差半个行高。）
     */
    private static final int TIMELINE_DOT_TOP_DP = 8;
    private static final int REQUEST_POST_NOTIFICATIONS = 61;
    private static final int LONG_CLASS_REMINDER_MINUTES = 30;
    private static final int SHORT_CLASS_REMINDER_MINUTES = 10;
    private static final int FLOATING_START_MINUTES = 20;
    private static final int MAX_PERIODS = 20;
    static final String EXTRA_SHOW_REMINDER_POPUP = "show_reminder_popup";
    static final String EXTRA_REMINDER_COURSE_NAME = "reminder_course_name";
    static final String EXTRA_REMINDER_COURSE_TIME = "reminder_course_time";
    static final String EXTRA_REMINDER_COURSE_ROOM = "reminder_course_room";
    static final String EXTRA_REMINDER_LEAD_MINUTES = "reminder_lead_minutes";
    private static final String EXTRA_SEND_REMINDER_TEST_NOTIFICATION = "send_reminder_test_notification";
    private static final int DAY_SWIPE_MIN_DISTANCE_DP = 72;
    private static final double DAY_FIRST_COLUMN_X = 99.08;
    private static final double DAY_COLUMN_WIDTH = 103.85;
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] DEFAULT_PERIOD_TIMES = {
            "08:00-08:45",
            "08:55-09:40",
            "10:00-10:45",
            "10:55-11:40",
            "14:30-15:15",
            "15:25-16:10",
            "16:20-17:05",
            "17:15-18:00",
            "19:30-20:15",
            "20:25-21:10",
            "21:20-22:05"
    };
    private static final int[] COURSE_COLORS = {
            Color.rgb(52, 123, 128),
            Color.rgb(68, 111, 166),
            Color.rgb(91, 126, 84),
            Color.rgb(132, 100, 151),
            Color.rgb(174, 105, 92),
            Color.rgb(172, 126, 58),
            Color.rgb(67, 135, 108),
            Color.rgb(89, 109, 158),
            Color.rgb(145, 91, 119),
            Color.rgb(118, 113, 79)
    };
    private static final String[] COURSE_COLOR_NAMES = {
            "\u9752\u8272", "\u84dd\u8272", "\u7eff\u8272", "\u7d2b\u8272", "\u9676\u571f\u7ea2",
            "\u8d6d\u9ec4", "\u58a8\u7eff", "\u84dd\u7070", "\u73ab\u7470\u7ea2", "\u6a44\u6984\u7eff"
    };
    private final SecureStorage secureStorage = new SecureStorage("mein_stundenplan_courses_key");
    private final List<Course> courses = new ArrayList<>();
    private final List<String> periodTimes = new ArrayList<>();
    private final Map<String, CountdownSnapshot> countdownSnapshots = new HashMap<>();
    private final Map<String, Long> stoppedShakeKeys = new HashMap<>();
    private final Map<String, Long> lastShakeTapMillisByKey = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            refreshTimedUi();
            scheduleMinuteTick();
        }
    };
    private FrameLayout dayTabFrame;
    private View daySelectionSlider;
    private LinearLayout dayTabs;
    private LinearLayout courseList;
    private TextView summaryText;
    // 「下一节」胶囊（brief §7 三层）：容器 + 四段子视图
    private LinearLayout statusChip;
    private android.widget.ImageView statusChipIcon;
    private TextView statusChipLabel;
    private TextView statusChipName;
    private LinearLayout statusChipMetaRow;
    private android.widget.ImageView statusChipMetaIcon;
    private TextView statusChipMeta;
    private TextView statusChipCountdown;
    private TextView statusChipDate;
    private TextView statusChipRoom;
    private TextView statusChipTeacher;
    private TextView statusChipWeeks;
    private LinearLayout statusChipDetails;
    private View settingsOverlay;
    private int selectedDay = 0;
    private int viewingWeek = 0; // 0=跟随真实当前周；非 0=预览该周
    private TextView weekRangeText;
    private ImageButton weekPrevButton;
    private ImageButton weekNextButton;
    private TextView weekLiveBadge;
    private LinearLayout weekNavRow;
    private AlertDialog weekPickerDialog;
    private HorizontalScrollView dayScroll;
    private int lastAutoScrolledDay = -1;
    private boolean storageLocked = false;
    private boolean isDarkMode = false;
    private boolean activityResumed = false;
    private Runnable pendingReminderPopup;
    private String renderedTemporalSignature = "";
    private boolean animateDaySlider = false;
    private float daySwipeDownX = 0f;
    private float daySwipeDownY = 0f;
    private boolean daySwipeTracking = false;
    private int daySwipeAxis = 0; // 0=undecided, 1=horizontal, 2=vertical
    private int daySwipePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean daySwipeChildCancelled = false;
    private int themeMode = 1; // 1=浅色, 2=深色
    private static final String STATE_SELECTED_DAY = "state_selected_day";
    private static final String STATE_VIEWING_WEEK = "state_viewing_week";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyAppNightMode();
        super.onCreate(savedInstanceState);
        loadThemeMode();
        loadPeriodTimes();
        configureWindow();
        selectedDay = currentSchoolDay();
        loadCourses();
        if (rebalanceCourseColorsIfNeeded()) {
            saveCourses();
        }
        removeExpiredCoursesIfNeeded(false);
        addTemporaryDynamicIslandCourseIfDebug();
        if (savedInstanceState != null) {
            // 主题切换 recreate() / 进程重建后：保留用户当前查看的星期与周次，
            // 避免被 currentSchoolDay() 重置（周六打开色调却跳去周六的问题）
            selectedDay = clamp(
                    savedInstanceState.getInt(STATE_SELECTED_DAY, selectedDay),
                    0,
                    DAYS.length - 1
            );
            viewingWeek = clamp(
                    savedInstanceState.getInt(STATE_VIEWING_WEEK, 0),
                    0,
                    Math.max(0, totalCalendarWeeks())
            );
        }
        buildLayout();
        render();
        maybeShowReminderPopupFromIntent(getIntent());
        ensureNotificationPermission();
        maybeSendDebugReminderTestNotification(getIntent());
        scheduleClassReminderNotifications();
        maybePromptCalendarSetup();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_DAY, selectedDay);
        outState.putInt(STATE_VIEWING_WEEK, viewingWeek);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybeShowReminderPopupFromIntent(intent);
        maybeSendDebugReminderTestNotification(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        removeExpiredCoursesIfNeeded(false);
        countdownSnapshots.clear();
        render();
        scheduleMinuteTick();
        scheduleClassReminderNotifications();
        if (pendingReminderPopup != null) {
            Runnable popup = pendingReminderPopup;
            pendingReminderPopup = null;
            uiHandler.post(popup);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        minuteHandler.removeCallbacks(minuteTick);
        cancelCourseCardAnimations(courseList);
        pauseTimedViews(courseList);
        resetCourseListAnimation();
        if (daySelectionSlider != null) {
            daySelectionSlider.animate().cancel();
            daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pendingReminderPopup = null;
        uiHandler.removeCallbacksAndMessages(null);
        minuteHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void pauseTimedViews(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        if (view instanceof CountdownRow) {
            ((CountdownRow) view).pauseTicks();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                pauseTimedViews(group.getChildAt(i));
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            daySwipeDownX = event.getX();
            daySwipeDownY = event.getY();
            daySwipeTracking = isDaySwipeStartAllowed(event);
            daySwipeAxis = 0;
            daySwipePointerId = event.getPointerId(0);
            daySwipeChildCancelled = false;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            daySwipeTracking = false;
            daySwipeAxis = 2;
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            int pointerIndex = event.getActionIndex();
            if (pointerIndex >= 0 && event.getPointerId(pointerIndex) == daySwipePointerId) {
                daySwipeTracking = false;
                daySwipeAxis = 2;
            }
        } else if (action == MotionEvent.ACTION_MOVE && daySwipeTracking) {
            int pointerIndex = event.findPointerIndex(daySwipePointerId);
            if (pointerIndex < 0) {
                daySwipeTracking = false;
            } else {
                float distanceX = event.getX(pointerIndex) - daySwipeDownX;
                float distanceY = event.getY(pointerIndex) - daySwipeDownY;
                float absX = Math.abs(distanceX);
                float absY = Math.abs(distanceY);
                if (daySwipeAxis == 0 && Math.max(absX, absY) >= dp(14)) {
                    if (absX >= absY * 1.35f && canSwitchDayBySwipe(distanceX)) {
                        daySwipeAxis = 1;
                        cancelChildTouch(event);
                    } else if (absY > absX) {
                        daySwipeAxis = 2;
                    }
                }
                if (daySwipeAxis == 1) {
                    return true;
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            float distanceX = event.getX() - daySwipeDownX;
            float distanceY = event.getY() - daySwipeDownY;
            if (daySwipeTracking
                    && daySwipeAxis != 2
                    && isDaySwipeGesture(distanceX, distanceY)
                    && canSwitchDayBySwipe(distanceX)) {
                cancelChildTouch(event);
                handleDaySwipe(distanceX, distanceY);
                daySwipeTracking = false;
                return true;
            }
            if (daySwipeAxis == 1) {
                daySwipeTracking = false;
                return true;
            }
            daySwipeTracking = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            daySwipeTracking = false;
            daySwipeAxis = 0;
            daySwipePointerId = MotionEvent.INVALID_POINTER_ID;
            daySwipeChildCancelled = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private void cancelChildTouch(MotionEvent source) {
        if (daySwipeChildCancelled) {
            return;
        }
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        daySwipeChildCancelled = true;
    }

    private boolean isDaySwipeStartAllowed(MotionEvent event) {
        if (settingsOverlay != null && settingsOverlay.getParent() != null) {
            return false;
        }
        return !isTouchInsideDayTabs(event) && !isTouchInsideWeekNav(event);
    }

    private boolean isTouchInsideDayTabs(MotionEvent event) {
        if (dayTabFrame == null || dayTabFrame.getWidth() <= 0 || dayTabFrame.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        dayTabFrame.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0]
                && x <= location[0] + dayTabFrame.getWidth()
                && y >= location[1]
                && y <= location[1] + dayTabFrame.getHeight();
    }

    private boolean isTouchInsideWeekNav(MotionEvent event) {
        if (weekNavRow == null || weekNavRow.getWidth() <= 0 || weekNavRow.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        weekNavRow.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0]
                && x <= location[0] + weekNavRow.getWidth()
                && y >= location[1]
                && y <= location[1] + weekNavRow.getHeight();
    }

    private boolean handleDaySwipe(float distanceX, float distanceY) {
        if (!isDaySwipeGesture(distanceX, distanceY)) {
            return false;
        }
        int direction = distanceX < 0 ? 1 : -1;
        int targetDay = selectedDay + direction;
        if (targetDay < 0 || targetDay >= DAYS.length) {
            return false;
        }
        switchToDay(targetDay, direction);
        return true;
    }

    private boolean canSwitchDayBySwipe(float distanceX) {
        int direction = distanceX < 0 ? 1 : -1;
        int targetDay = selectedDay + direction;
        return targetDay >= 0 && targetDay < DAYS.length;
    }

    private boolean isDaySwipeGesture(float distanceX, float distanceY) {
        float absX = Math.abs(distanceX);
        float absY = Math.abs(distanceY);
        if (absX < dp(DAY_SWIPE_MIN_DISTANCE_DP) || absX < absY * 1.35f) {
            return false;
        }
        return true;
    }

    private int normalizeDayIndex(int day) {
        int length = DAYS.length;
        return ((day % length) + length) % length;
    }

    private void applyAppNightMode() {
        // 按用户指示：去掉暗模式，只保留亮模式。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void loadThemeMode() {
        // 按用户指示：去掉暗模式，强制亮模式。
        themeMode = 1;
        applyThemeMode();
    }

    private boolean isSystemDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyThemeMode() {
        switch (themeMode) {
            case 1: // 强制浅色
                isDarkMode = false;
                break;
            case 2: // 强制深色
                isDarkMode = true;
                break;
            default: // 跟随系统
                isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                break;
        }
    }

    private void toggleTheme() {
        // 已废弃：暗模式按用户指示移除，主题切换按钮也已删除，此方法无调用方。
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importPdf(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            scheduleClassReminderNotifications();
        } else {
            Toast.makeText(this, "\u901a\u77e5\u6743\u9650\u672a\u5141\u8bb8\uff0c\u8bfe\u524d\u63d0\u9192\u4e0d\u4f1a\u5f39\u51fa", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureNotificationPermission() {
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            ClassReminderReceiver.ensureChannel(manager);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void scheduleClassReminderNotifications() {
        if (storageLocked || !notificationsEnabled()) {
            return;
        }
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            cancelScheduledClassReminders(alarmManager);
        }
        long now = System.currentTimeMillis();
        List<ReminderScheduler.Entry> entries = new ArrayList<>();
        Set<Integer> usedRequestCodes = new HashSet<>();
        for (Course course : courses) {
            if (course.temporary || course.name == null || course.name.trim().isEmpty()) {
                continue;
            }
            long[] triggerTimes = reminderTriggerTimes(course, now);
            if (triggerTimes.length == 0) {
                continue;
            }
            int requestCode = reminderRequestCode(course);
            while (usedRequestCodes.contains(requestCode)) {
                requestCode = requestCode == Integer.MAX_VALUE ? 1 : requestCode + 1;
            }
            usedRequestCodes.add(requestCode);
            entries.add(new ReminderScheduler.Entry(
                    requestCode,
                    course.name,
                    periodRangeTime(course),
                    course.room,
                    reminderLeadMinutes(course),
                    triggerTimes
            ));
        }
        ReminderScheduler.replaceSchedule(this, entries);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(REMINDER_CODES_KEY).apply();
    }

    private void cancelScheduledClassReminders(android.app.AlarmManager alarmManager) {
        String encoded = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(REMINDER_CODES_KEY, "[]");
        try {
            JSONArray codes = new JSONArray(encoded);
            for (int i = 0; i < codes.length(); i++) {
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                        this,
                        codes.optInt(i),
                        new Intent(this, ClassReminderReceiver.class),
                        android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        } catch (Exception ignored) {
            // Ignore corrupted reminder metadata; the next save will replace it.
        }
    }

    private boolean notificationsEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(NOTIFICATIONS_ENABLED_KEY, true);
    }

    private void applyNotificationToggle(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled)
                .apply();
        if (enabled) {
            ensureNotificationPermission();
            scheduleClassReminderNotifications();
        } else {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                cancelScheduledClassReminders(alarmManager);
            }
            ReminderScheduler.clear(this);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(REMINDER_CODES_KEY).apply();
        }
    }

    private Intent classReminderIntent(Course course) {
        Intent intent = new Intent(this, ClassReminderReceiver.class);
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, course.name);
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_TIME, periodRangeTime(course));
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_ROOM, course.room);
        intent.putExtra(ClassReminderReceiver.EXTRA_REMINDER_MINUTES, reminderLeadMinutes(course));
        return intent;
    }

    private int reminderLeadMinutes(Course course) {
        int period = course == null ? 0 : course.period;
        return period == 1 || period == 5 || period == 9
                ? LONG_CLASS_REMINDER_MINUTES
                : SHORT_CLASS_REMINDER_MINUTES;
    }

    private long[] reminderTriggerTimes(Course course, long now) {
        return TimetableRules.reminderTriggerTimes(
                calendarStartMillis(),
                calendarEndMillis(),
                calendarTotalWeeks(),
                course.day,
                courseStartMinutes(course),
                reminderLeadMinutes(course),
                parseWeekSet(course.weeks),
                now
        );
    }

    private int reminderRequestCode(Course course) {
        if (course == null) {
            return 1;
        }
        long id = course.id;
        if (id != 0L) {
            return (int) (id ^ (id >>> 32)) & 0x7fffffff;
        }
        // id 为 0（临时/测试课程等未分配 id 的课程）：组合「日 + 节次 + 跨节数 + 周次 + 名称」，
        // 避免同名课程（不同天/不同节次）碰撞出相同 requestCode 而互相覆盖。
        int hash = 17;
        hash = 31 * hash + course.day;
        hash = 31 * hash + course.period;
        hash = 31 * hash + Math.max(0, course.endPeriod - course.period);
        hash = 31 * hash + (course.weeks == null ? 0 : course.weeks.hashCode());
        hash = 31 * hash + (course.name == null ? 0 : course.name.hashCode());
        int result = hash & 0x7fffffff;
        return result == 0 ? 1 : result;
    }

    private void configureWindow() {
        Window window = getWindow();
        int bgColor = pageTopColor();
        window.setStatusBarColor(bgColor);
        window.setNavigationBarColor(bgColor);
        if (isDarkMode) {
            window.getDecorView().setSystemUiVisibility(0);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /**
     * Hero 氛围层：极淡的云 / 飘落花瓣 / 植物底图 + 角色背后的柔光晕（brief §3）。
     *
     * 底图**不做抠图**——云本身就是白的，与白底无法区分，键控会把云一起吃掉。
     * 改为保留白底、整体压到 22%：白色叠在 #F5F8FD 页面上只差约 2/255（看不出边界），
     * 而云和花瓣会作为很淡的纹理透出来。素材四周已做羽化，不会出现矩形硬边。
     */
    private void addHeroScene(FrameLayout safeFrame, boolean compact) {
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        int heroHeightDp = Math.min(Math.round(screenHeightDp * HERO_TARGET_HEIGHT_RATIO), HERO_CHARACTER_MAX_HEIGHT_DP);

        // 整张 Hero 图：窗 + 天空 + 云 + 白花 + 人物都在同一张画里，光照统一，不需要再分层。
        // 素材落库前已做四边羽化 + 底边大幅渐隐，所以直接铺满顶部，无需再叠天空渐变。
        android.widget.ImageView scene = new android.widget.ImageView(this);
        scene.setImageResource(R.drawable.hero_scene);
        scene.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // safeFrame 四边都有内边距（上＝状态栏+16dp，左右＝12/20dp），Hero 图若直接用
        // MATCH_PARENT 填的只是"内容盒"，四边都会露出一条底色，看起来像画面缺了一圈。
        // 用负 margin 把它顶满整个窗口，四个方向都补齐，视觉上才是一整块连续的画。
        int statusBarPx = 0;
        int statusBarRes = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusBarRes > 0) {
            statusBarPx = getResources().getDimensionPixelSize(statusBarRes);
        }
        int bleedTopPx = statusBarPx + dp(16);
        int bleedSidePx = dp(compact ? 12 : 20);
        FrameLayout.LayoutParams sceneParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(Math.round(heroHeightDp * HERO_SCENE_SCALE)) + bleedTopPx,
                Gravity.TOP);
        sceneParams.topMargin = -bleedTopPx;
        sceneParams.leftMargin = -bleedSidePx;
        sceneParams.rightMargin = -bleedSidePx;
        safeFrame.addView(scene, sceneParams);

        // 角色背后的淡圆形渐变光晕：纯代码绘制，不烘进角色图，便于跟随主题色
        int glowSizeDp = Math.round(heroReservedWidthDp(compact) * 1.7f);
        View glow = new View(this);
        GradientDrawable glowDrawable = new GradientDrawable();
        glowDrawable.setShape(GradientDrawable.OVAL);
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(glowSizeDp / 2));
        glowDrawable.setColors(new int[]{
                withAlpha(accentColor(), isDarkMode ? 56 : 32),
                Color.TRANSPARENT
        });
        glow.setBackground(glowDrawable);
        glow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                dp(glowSizeDp), dp(glowSizeDp), Gravity.TOP | Gravity.END);
        glowParams.topMargin = dp(Math.round(heroHeightDp * 0.05f));
        safeFrame.addView(glow, glowParams);
    }

    /**
     * 页面装饰（brief §8）：透明度 5%~20%，只贴边缘，不进入文字区，不做成满屏贴纸。
     * 都加在滚动容器之前，所以课程卡片始终压在它们上面，装饰不会影响任何课程信息。
     */
    private void addPageDecorations(FrameLayout safeFrame) {
        // 按反馈"下方左右两边用更大的图案、更融洽"：
        // 参考图里底部两角是大簇白花+叶子，并且**被屏幕边缘裁切**（不是完整摆进去）。
        // 所以这里放大到 90~150dp 并给出负 margin，让它们自然出血到画面之外，
        // 体量感和参考图一致；safeFrame 的 setClipChildren(true) 会在屏幕边缘裁切。
        addDecoration(safeFrame, R.drawable.deco_star,
                Gravity.TOP | Gravity.START, 28, 104, 8, 0.64f);
        // 左下角：雪莲花（主体，向左出血）
        addDecoration(safeFrame, R.drawable.deco_petal,
                Gravity.BOTTOM | Gravity.START, 124, 46, -20, 0.72f);
        // 左下角：叶子（压在雪莲旁，向下出血）
        addDecoration(safeFrame, R.drawable.deco_leaf,
                Gravity.BOTTOM | Gravity.START, 96, -14, 60, 0.62f);
        // 右下角：云（向右下出血）
        addDecoration(safeFrame, R.drawable.deco_cloud,
                Gravity.BOTTOM | Gravity.END, 152, -18, -28, 0.66f);
    }

    /** 放一个纯装饰小图。四个方向都设 margin，实际由 gravity 决定用哪几个。 */
    private void addDecoration(FrameLayout parent, int resId, int gravity,
                               int sizeDp, int verticalMarginDp, int horizontalMarginDp, float alpha) {
        android.widget.ImageView view = new android.widget.ImageView(this);
        view.setImageResource(resId);
        view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (isDarkMode) {
            // 装饰素材本身是极浅的蓝色线稿，压在近黑底上只剩一团没有颜色的灰斑，
            // 看起来像脏点。深色模式下改用主色染色、并适度提高不透明度，
            // 让它读起来是"有意的装饰"。
            view.setImageTintList(ColorStateList.valueOf(accentColor()));
            alpha = Math.min(0.55f, alpha * 2.2f);
        }
        view.setAlpha(alpha);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        params.topMargin = dp(verticalMarginDp);
        params.bottomMargin = dp(verticalMarginDp);
        params.leftMargin = dp(horizontalMarginDp);
        params.rightMargin = dp(horizontalMarginDp);
        parent.addView(view, params);
    }

    private void buildLayout() {
        boolean compact = isCompactWidth();
        int sideMargin = contentSideMarginDp();
        int actionButtonSize = ACTION_BUTTON_SIZE_DP;
        FrameLayout safeFrame = new FrameLayout(this);
        safeFrame.setBackground(pageBackground());
        safeFrame.setClipChildren(true);
        // 必须为 false：Hero 图要用负 topMargin 顶进状态栏区域，
        // 保持 true 的话会被裁到 padding 盒里，状态栏下方就会留出一条纯色空白。
        safeFrame.setClipToPadding(false);
        safeFrame.setOnApplyWindowInsetsListener((view, insets) -> {
            WindowInsets applied = insets;
            int horizontalPadding = dp(compact ? 12 : 20);
            view.setPadding(horizontalPadding, applied.getSystemWindowInsetTop() + dp(16), horizontalPadding, applied.getSystemWindowInsetBottom() + dp(16));
            return insets;
        });

        // Hero 整图：窗户 + 天空 + 云 + 白花 + 人物画在同一张画里（比分两层叠放更统一）。
        // 加在滚动容器之前，因此渲染在内容下层。
        if (SHOW_HERO_CHARACTER) {
            addHeroScene(safeFrame, compact);
        }
        addPageDecorations(safeFrame);

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setClipChildren(true);
        pageScroll.setClipToPadding(true);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        safeFrame.addView(pageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        pageScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        header.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(dp(sideMargin), 0, dp(sideMargin), 0);
        root.addView(header, headerParams);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleBlock, titleBlockParams);

        summaryText = new TextView(this);
        summaryText.setTextColor(primaryTextColor());
        summaryText.setTextSize(15);
        summaryText.setTypeface(appTypefaceMedium());
        summaryText.setAlpha(1f);
        summaryText.setSingleLine(true);
        summaryText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                compact ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMargins(0, dp(4), 0, 0);
        titleBlock.addView(summaryText, summaryParams);

        // P2: full-width information card below the Hero, with stable rows in every state.
        header.setMinimumHeight(dp(HERO_CHARACTER_MAX_HEIGHT_DP));
        statusChip = new LinearLayout(this);
        statusChip.setOrientation(LinearLayout.VERTICAL);
        statusChip.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        statusChipIcon = new android.widget.ImageView(this);
        statusChipIcon.setImageResource(R.drawable.ic_sparkle);
        statusChipIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams sparkleParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        sparkleParams.rightMargin = dp(6);
        labelRow.addView(statusChipIcon, sparkleParams);
        statusChipLabel = statusChipText(12, false);
        labelRow.addView(statusChipLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusChipCountdown = statusChipText(12, false);
        LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countdownParams.leftMargin = dp(8);
        labelRow.addView(statusChipCountdown, countdownParams);
        android.widget.ImageView chevron = new android.widget.ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setImageTintList(ColorStateList.valueOf(secondaryTextColor()));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        chevronParams.leftMargin = dp(4);
        labelRow.addView(chevron, chevronParams);
        statusChip.addView(labelRow);
        statusChipDate = statusChipText(12, false);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(6);
        statusChip.addView(statusChipDate, dateParams);
        statusChipName = statusChipText(compact ? 18 : 19, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(7);
        statusChip.addView(statusChipName, nameParams);
        statusChipDetails = new LinearLayout(this);
        statusChipDetails.setOrientation(LinearLayout.VERTICAL);
        statusChipMetaRow = new LinearLayout(this);
        statusChipMetaRow.setGravity(Gravity.CENTER_VERTICAL);
        statusChipMeta = statusChipText(12, false);
        statusChipRoom = statusChipText(12, false);
        statusChipMetaRow.addView(statusChipDetail(R.drawable.ic_clock_outline, statusChipMeta),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
        statusChipMetaRow.addView(statusChipDetail(R.drawable.ic_location, statusChipRoom),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusChipDetails.addView(statusChipMetaRow);
        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        statusChipTeacher = statusChipText(11, false);
        statusChipWeeks = statusChipText(11, false);
        secondaryRow.addView(statusChipDetail(R.drawable.ic_person, statusChipTeacher),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f));
        secondaryRow.addView(statusChipDetail(R.drawable.ic_calendar_outline, statusChipWeeks),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondaryParams.topMargin = dp(6);
        statusChipDetails.addView(secondaryRow, secondaryParams);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(7);
        statusChip.addView(statusChipDetails, detailParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(compact ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (compact) {
            actionsParams.setMargins(0, dp(76), 0, 0);
        }
        header.addView(actions, actionsParams);

        // 主题切换按钮已移除：按用户指示去掉暗模式、只保留亮模式后，
        // 这颗月牙钮不再有任何功能，留着只会误导（点它没反应）。
        ImageButton settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings_outline);
        settingsButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        settingsButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        settingsButton.setContentDescription("\u8bbe\u7f6e");
        settingsButton.setBackground(elevatedCardBackground(cardColor(), dp(ACTION_BUTTON_SIZE_DP / 2)));
        settingsButton.setElevation(dp(ELEVATION_SOFT));
        settingsButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        settingsButton.setOnClickListener(view -> showSettingsDialogV2());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize));
        settingsParams.setMargins(0, 0, dp(10), 0);
        actions.addView(settingsButton, settingsParams);

        ImageButton addButton = new ImageButton(this);
        addButton.setImageResource(R.drawable.ic_plus);
        addButton.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        addButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        addButton.setContentDescription("\u6dfb\u52a0\u8bfe\u7a0b");
        // 「＋」保持主色实心（示意图里它是唯一的实心钮，承担"主操作"）
        addButton.setBackground(interactiveButtonBackground(accentColor(), dp(ACTION_BUTTON_SIZE_DP / 2)));
        addButton.setElevation(dp(ELEVATION_SOFT));
        addButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        addButton.setOnClickListener(view -> showCourseDialog(null));
        actions.addView(addButton, new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize)));

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(dp(sideMargin), dp(8), dp(sideMargin), 0);
        root.addView(statusChip, statusParams);

        // 周切换导航条：‹ 第N周·日期范围 › [回到本周]
        weekNavRow = new LinearLayout(this);
        weekNavRow.setOrientation(LinearLayout.HORIZONTAL);
        weekNavRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams weekNavParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        weekNavParams.setMargins(dp(sideMargin), dp(compact ? 14 : 18), dp(sideMargin), dp(6));
        root.addView(weekNavRow, weekNavParams);

        weekPrevButton = new ImageButton(this);
        weekPrevButton.setImageResource(R.drawable.ic_chevron_right);
        weekPrevButton.setRotation(180f);
        weekPrevButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        weekPrevButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        weekPrevButton.setContentDescription("上一周");
        // brief §5：箭头做成"白底 + 细描边 + 轻阴影"的小圆角块
        weekPrevButton.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        weekPrevButton.setElevation(dp(ELEVATION_SOFT));
        weekPrevButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        weekPrevButton.setOnClickListener(view -> stepViewingWeek(-1));
        LinearLayout.LayoutParams weekPrevParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        weekPrevParams.setMargins(0, 0, dp(4), 0);
        weekNavRow.addView(weekPrevButton, weekPrevParams);

        weekRangeText = new TextView(this);
        weekRangeText.setTextSize(compact ? 13 : 14);
        weekRangeText.setTypeface(appTypeface(Typeface.BOLD));
        weekRangeText.setTextColor(primaryTextColor());
        weekRangeText.setGravity(Gravity.CENTER);
        weekRangeText.setSingleLine(true);
        weekRangeText.setEllipsize(TextUtils.TruncateAt.END);
        weekRangeText.setIncludeFontPadding(false);
        weekRangeText.setPadding(dp(8), dp(9), dp(8), dp(9));
        weekRangeText.setBackground(interactiveSurfaceBackground(tonalContainerColor(), dp(14)));
        weekRangeText.setContentDescription("选择周次");
        weekRangeText.setOnClickListener(view -> showWeekPickerDialog());
        weekNavRow.addView(weekRangeText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        weekNextButton = new ImageButton(this);
        weekNextButton.setImageResource(R.drawable.ic_chevron_right);
        weekNextButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        weekNextButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        weekNextButton.setContentDescription("下一周");
        weekNextButton.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        weekNextButton.setElevation(dp(ELEVATION_SOFT));
        weekNextButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        weekNextButton.setOnClickListener(view -> stepViewingWeek(1));
        LinearLayout.LayoutParams weekNextParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        weekNextParams.setMargins(dp(4), 0, dp(4), 0);
        weekNavRow.addView(weekNextButton, weekNextParams);

        weekLiveBadge = new TextView(this);
        weekLiveBadge.setText("本周");
        weekLiveBadge.setTextSize(13);
        weekLiveBadge.setTypeface(appTypeface(Typeface.BOLD));
        weekLiveBadge.setTextColor(accentColor());
        weekLiveBadge.setGravity(Gravity.CENTER);
        weekLiveBadge.setIncludeFontPadding(false);
        weekLiveBadge.setContentDescription("返回本周");
        weekLiveBadge.setPadding(dp(11), dp(9), dp(11), dp(9));
        weekLiveBadge.setBackground(interactiveTranslucentBackground(
                withAlpha(accentColor(), isDarkMode ? 64 : 34), dp(14)));
        weekLiveBadge.setOnClickListener(view -> returnToCurrentWeek());
        weekLiveBadge.setVisibility(View.GONE);
        weekNavRow.addView(weekLiveBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        this.dayScroll = dayScroll;
        dayScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams dayScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dayScrollParams.setMargins(dp(sideMargin), dp(compact ? 8 : 10), dp(sideMargin), dp(16));
        root.addView(dayScroll, dayScrollParams);

        dayTabFrame = new FrameLayout(this);
        dayTabFrame.setPadding(dp(4), dp(4), dp(4), dp(4));
        dayTabFrame.setClipChildren(false);
        dayTabFrame.setClipToPadding(false);
        // brief §6：日期条不再要整条灰色 track，标签直接落在页面背景上
        dayTabFrame.setBackground(null);
        dayScroll.addView(dayTabFrame);

        daySelectionSlider = new View(this);
        daySelectionSlider.setBackground(elevatedButtonBackground(todayColor(), dp(20)));
        daySelectionSlider.setClickable(false);
        dayTabFrame.addView(daySelectionSlider, new FrameLayout.LayoutParams(dp(72), dp(compact ? 48 : 52)));

        dayTabs = new LinearLayout(this);
        dayTabs.setOrientation(LinearLayout.HORIZONTAL);
        dayTabFrame.addView(dayTabs);

        courseList = new LinearLayout(this);
        courseList.setOrientation(LinearLayout.VERTICAL);
        courseList.setClipChildren(false);
        courseList.setClipToPadding(false);
        courseList.setPadding(0, dp(12), 0, dp(18));
        root.addView(courseList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(safeFrame);
    }

    private void render() {
        renderDayTabs();
        renderCourseList();
        renderWeekNav();
        Course currentCourse = viewingWeek == 0 ? currentCourseInProgress() : null;
        SummaryCourse nextCourse = currentCourse == null && viewingWeek == 0
                ? nextUpcomingCourseFromToday() : null;
        int realWeek = currentTeachingWeek();
        String weekText;
        if (viewingWeek != 0) {
            weekText = "第" + viewingWeek + "周";
        } else if (realWeek >= 1 && realWeek <= calendarTotalWeeks()) {
            weekText = "第" + realWeek + "周";
        } else if (startOfTodayMillis() < calendarStartMillis()) {
            weekText = "未开学";
        } else {
            weekText = "假期";
        }
        summaryText.setText(String.format(
                Locale.CHINA,
                "%s · %s · %s",
                weekText,
                DAYS[selectedDay],
                new SimpleDateFormat("M月d日", Locale.CHINA).format(new java.util.Date(
                        displayedDateMillis(displayedViewingWeek(), selectedDay)))
        ));
        if (viewingWeek != 0) {
            updateStatusChip(null, null, true);
        } else {
            updateStatusChip(currentCourse, nextCourse, false);
        }
        renderedTemporalSignature = temporalSignature();
    }

    private TextView statusChipText(int size, boolean prominent) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTypeface(prominent ? appTypefaceSemiBold() : appTypefaceMedium());
        text.setIncludeFontPadding(false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private LinearLayout statusChipDetail(int resource, TextView text) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, dp(6), 0);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(resource);
        icon.setImageTintList(ColorStateList.valueOf(secondaryTextColor()));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(13), dp(13));
        iconParams.rightMargin = dp(5);
        row.addView(icon, iconParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Presentation only: existing course lookup and date rules remain authoritative. */
    private void updateStatusChip(Course current, SummaryCourse next, boolean previewMode) {
        if (statusChip == null) return;
        long today = startOfTodayMillis();
        boolean beforeSemester = today < calendarStartMillis();
        boolean afterSemester = today > startOfDayMillis(calendarEndMillis());
        Course shown = current != null ? current : (next != null ? next.course : null);
        boolean ongoing = !previewMode && !beforeSemester && !afterSemester && current != null;
        boolean nextToday = next != null && startOfDayMillis(next.startAtMillis) == today;
        String label;
        String countdown = "";
        if (previewMode) {
            label = "预览第 " + viewingWeek + " 周";
            shown = null;
        } else if (beforeSemester) {
            label = "未开学";
            shown = next != null ? next.course : null;
        } else if (afterSemester) {
            label = "本学期已结束";
            shown = null;
        } else if (ongoing) {
            label = "正在上课";
            int minutes = Math.max(0, (courseEndSeconds(current) - currentSeconds() + 59) / 60);
            countdown = "剩余 " + minutes + " min";
        } else if (nextToday) {
            label = "下一节";
            long minutes = Math.max(1, (next.startAtMillis - System.currentTimeMillis() + 59999) / 60000);
            countdown = minutes + " min 后";
        } else {
            boolean hadToday = false;
            boolean allEnded = true;
            for (Course course : courses) {
                if (course.day == currentSchoolDay()
                        && courseTemporalState(course) != TimetableRules.TemporalState.UNAVAILABLE) {
                    hadToday = true;
                    if (courseTemporalState(course) != TimetableRules.TemporalState.COMPLETED) allEnded = false;
                }
            }
            if (hadToday && allEnded) label = "今日课程已结束";
            else if (next != null) label = "下一节 · " + DAYS[next.day];
            else label = "暂无后续课程";
        }
        boolean detail = shown != null;
        statusChipLabel.setText(label);
        statusChipCountdown.setText(countdown);
        statusChipCountdown.setVisibility(countdown.isEmpty() ? View.GONE : View.VISIBLE);
        boolean futureDate = detail && !ongoing && next != null && !nextToday;
        statusChipDate.setText(futureDate ? (beforeSemester ? "首课 · " : "下一节 · ")
                + formatDate(next.startAtMillis) + " · " + DAYS[next.day] : "");
        statusChipDate.setVisibility(futureDate ? View.VISIBLE : View.GONE);
        statusChipName.setText(detail ? shown.name : "");
        statusChipMeta.setText(detail ? periodRangeTime(shown) : "");
        statusChipRoom.setText(detail ? (TextUtils.isEmpty(shown.room) ? "地点未填" : shown.room) : "");
        statusChipTeacher.setText(detail ? (TextUtils.isEmpty(shown.teacher) ? "教师未填" : shown.teacher) : "");
        String weekDetail = detail ? shown.weeks + " · " + shown.periodLabel() : "";
        statusChipWeeks.setText(weekDetail);
        statusChipName.setVisibility(detail ? View.VISIBLE : View.GONE);
        statusChipDetails.setVisibility(detail ? View.VISIBLE : View.GONE);
        statusChipMetaRow.setVisibility(detail ? View.VISIBLE : View.GONE);
        int tint = ongoing || detail || previewMode ? accentColor() : secondaryTextColor();
        float amount = ongoing || previewMode ? 0.10f : 0.04f;
        statusChip.setBackground(verticalGradientBackground(
                mixColor(cardColor(), tint, amount),
                mixColor(cardColor(), tint, amount * 1.6f), dp(18)));
        statusChipLabel.setTextColor(tint);
        statusChipIcon.setImageTintList(ColorStateList.valueOf(tint));
        statusChipCountdown.setTextColor(accentColor());
        statusChipName.setTextColor(primaryTextColor());
        statusChipMeta.setTextColor(secondaryTextColor());
        statusChipRoom.setTextColor(secondaryTextColor());
        statusChipTeacher.setTextColor(secondaryTextColor());
        statusChipWeeks.setTextColor(secondaryTextColor());
        statusChipDate.setTextColor(secondaryTextColor());
        statusChip.setContentDescription(label + (countdown.isEmpty() ? "" : "，" + countdown)
                + (detail ? "，" + shown.name + "，" + statusChipMeta.getText() + "，" + statusChipRoom.getText()
                    + "，" + statusChipTeacher.getText() + "，" + statusChipDate.getText() + "，" + weekDetail : "")
                + (previewMode ? "，点击返回本周" : ""));
        statusChip.setOnClickListener(previewMode ? view -> returnToCurrentWeek() : null);
    }

    private int totalCalendarWeeks() {
        return Math.max(1, calendarTotalWeeks());
    }

    private int displayedViewingWeek() {
        if (viewingWeek != 0) {
            return clamp(viewingWeek, 1, totalCalendarWeeks());
        }
        int real = currentTeachingWeek();
        if (real < 1) {
            return 1;
        }
        return Math.min(real, totalCalendarWeeks());
    }

    private void stepViewingWeek(int delta) {
        setViewingWeek(displayedViewingWeek() + delta);
    }

    private void setViewingWeek(int week) {
        int total = totalCalendarWeeks();
        if (week < 1 || week > total) {
            return;
        }
        int from = displayedViewingWeek();
        int real = currentTeachingWeek();
        viewingWeek = (real >= 1 && real <= total && real == week) ? 0 : week;
        if (week == from) {
            render();
            return;
        }
        render();
        animateDaySwitch(Integer.compare(week, from));
    }

    private void returnToCurrentWeek() {
        if (viewingWeek == 0) {
            return;
        }
        int from = displayedViewingWeek();
        viewingWeek = 0;
        render();
        animateDaySwitch(Integer.compare(displayedViewingWeek(), from));
    }

    private String weekRangeLabel(int week) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(calendarStartMillis());
        calendar.add(Calendar.DAY_OF_YEAR, (week - 1) * 7);
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, 6);
        long end = calendar.getTimeInMillis();
        SimpleDateFormat format = new SimpleDateFormat("M.d", Locale.CHINA);
        return "第" + week + "周 · " + format.format(new java.util.Date(start))
                + "–" + format.format(new java.util.Date(end));
    }

    private void renderWeekNav() {
        if (weekRangeText == null) {
            return;
        }
        boolean previewing = viewingWeek != 0;
        int week = displayedViewingWeek();
        int total = totalCalendarWeeks();
        weekRangeText.setText(displayedWeekRangeLabel(week));
        weekRangeText.setContentDescription("第" + week + "周，" + displayedWeekRangeLabel(week) + "，点击选择周次");
        weekRangeText.setTextColor(previewing ? accentColor() : primaryTextColor());
        weekRangeText.setBackground(interactiveSurfaceBackground(
                previewing ? accentContainerColor() : tonalContainerColor(), dp(14)));
        boolean canPrev = week > 1;
        boolean canNext = week < total;
        weekPrevButton.setEnabled(canPrev);
        weekPrevButton.setAlpha(canPrev ? 1f : 0.35f);
        weekNextButton.setEnabled(canNext);
        weekNextButton.setAlpha(canNext ? 1f : 0.35f);
        weekLiveBadge.setVisibility(previewing ? View.VISIBLE : View.GONE);
    }

    private void showWeekPickerDialog() {
        if (weekPickerDialog != null && weekPickerDialog.isShowing()) {
            return;
        }
        int total = totalCalendarWeeks();
        int selected = displayedViewingWeek();
        int realWeek = currentTeachingWeek();
        boolean realValid = realWeek >= 1 && realWeek <= total;

        ScrollView pickerScroll = new ScrollView(this);
        pickerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.VERTICAL);
        picker.setPadding(dp(12), dp(4), dp(12), dp(8));
        pickerScroll.addView(picker);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final int columns = 5;
        for (int start = 1; start <= total; start += columns) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int end = Math.min(start + columns, total + 1);
            for (int week = start; week < end; week++) {
                final int targetWeek = week;
                boolean isRealWeek = realValid && week == realWeek;
                String number = String.valueOf(week);
                TextView cell = new TextView(this);
                SpannableString cellText = new SpannableString(
                        isRealWeek ? number + "\n今天" : number);
                if (isRealWeek) {
                    cellText.setSpan(new RelativeSizeSpan(0.62f), number.length(), cellText.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                cell.setText(cellText);
                cell.setTextSize(15);
                cell.setTypeface(appTypeface(Typeface.BOLD));
                cell.setIncludeFontPadding(false);
                cell.setGravity(Gravity.CENTER);
                cell.setContentDescription("第" + week + "周" + (isRealWeek ? "，本周" : ""));
                boolean isSelectedCell = week == selected;
                cell.setTextColor(isSelectedCell ? Color.WHITE : primaryTextColor());
                cell.setBackground(isSelectedCell
                        ? interactiveButtonBackground(accentColor(), dp(14))
                        : interactiveTranslucentBackground(tonalContainerColor(), dp(14)));
                cell.setOnClickListener(view -> {
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                    setViewingWeek(targetWeek);
                });
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, dp(46), 1f
                );
                cellParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(cell, cellParams);
            }
            // 末行用占位 View 补齐列数，保持网格对齐
            for (int pad = end; pad < start + columns; pad++) {
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        0, dp(46), 1f
                );
                spacerParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(new View(this), spacerParams);
            }
            picker.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("选择周次")
                .setView(pickerScroll)
                .setNegativeButton("关闭", null)
                .create();
        dialogRef[0] = dialog;
        weekPickerDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (weekPickerDialog == dialog) {
                weekPickerDialog = null;
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void refreshTimedUi() {
        String currentSignature = temporalSignature();
        if (!TextUtils.equals(renderedTemporalSignature, currentSignature)) {
            render();
            return;
        }
        if (viewingWeek != 0) updateStatusChip(null, null, true);
        else {
            Course current = currentCourseInProgress();
            updateStatusChip(current, current == null ? nextUpcomingCourseFromToday() : null, false);
        }
        invalidateCourseProgressViews(courseList);
    }

    private String temporalSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(displayedViewingWeek())
                .append(':')
                .append(viewingWeek)
                .append(':')
                .append(currentSchoolDay())
                .append(':')
                .append(selectedDay);
        for (Course course : courses) {
            signature.append('|')
                    .append(course.id)
                    .append('=')
                    .append(courseTemporalState(course).ordinal());
        }
        return signature.toString();
    }

    private void invalidateCourseProgressViews(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof CourseProgressView) {
            view.invalidate();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateCourseProgressViews(group.getChildAt(i));
            }
        }
    }

    private void scheduleMinuteTick() {
        minuteHandler.removeCallbacks(minuteTick);
        long delay = PROGRESS_REFRESH_MS - (System.currentTimeMillis() % PROGRESS_REFRESH_MS) + 120L;
        minuteHandler.postDelayed(minuteTick, delay);
    }

    private void addTemporaryDynamicIslandCourseIfDebug() {
        if (!BuildConfig.ENABLE_TEST_COURSE || storageLocked) {
            return;
        }
        for (Course course : courses) {
            if (course.temporary) {
                return;
            }
        }
        int period = currentPeriod();
        if (period < 1) {
            period = nearestDisplayPeriod();
        }
        int start = Math.min(currentMinutes() + 5, 23 * 60 + 58);
        int end = Math.min(start + 45, 23 * 60 + 59);
        Course testCourse = new Course();
        testCourse.id = Long.MIN_VALUE;
        testCourse.day = currentSchoolDay();
        testCourse.period = period;
        testCourse.endPeriod = period;
        testCourse.name = "灵动岛测试课程";
        testCourse.weeks = "临时测试";
        testCourse.room = "当前节次";
        testCourse.teacher = "不保存";
        testCourse.color = Color.rgb(28, 126, 214);
        testCourse.temporary = true;
        testCourse.customStartMinutes = start;
        testCourse.customEndMinutes = end;
        courses.add(testCourse);
        selectedDay = testCourse.day;
    }

    private void renderDayTabs() {
        dayTabs.removeAllViews();
        boolean compact = isCompactWidth();
        int week = displayedViewingWeek();
        long today = startOfTodayMillis();
        SimpleDateFormat dateFormat = new SimpleDateFormat("M/d", Locale.CHINA);
        for (int i = 0; i < DAYS.length; i++) {
            final int day = i;
            LinearLayout tabContainer = new LinearLayout(this);
            tabContainer.setOrientation(LinearLayout.VERTICAL);
            tabContainer.setGravity(Gravity.CENTER);
            tabContainer.setPadding(dp(4), dp(compact ? 7 : 8), dp(4), dp(compact ? 7 : 8));

            // 状态：今天用深蓝色，选中用青绿色
            int textColor;
            long displayedDate = displayedDateMillis(week, day);
            boolean isToday = displayedDate == today;
            boolean isSelected = day == selectedDay;

            if (isSelected) {
                textColor = accentColor();
            } else if (isToday) {
                textColor = todayColor();
            } else {
                textColor = secondaryTextColor();
            }

            tabContainer.setBackground(buttonBackground(Color.TRANSPARENT, dp(18)));
            tabContainer.setSelected(isSelected);
            tabContainer.setContentDescription(DAYS[i] + "，" + formatDate(displayedDate)
                    + (isToday ? "\uff0c\u4eca\u5929" : "")
                    + (isSelected ? "\uff0c\u5df2\u9009\u62e9" : ""));
            tabContainer.setOnClickListener(view -> switchToDay(day));

            TextView dayText = new TextView(this);
            dayText.setText(DAYS[i]);
            dayText.setTextSize(compact ? 15 : 16);
            dayText.setGravity(Gravity.CENTER);
            dayText.setTypeface(appTypeface(Typeface.BOLD));
            dayText.setTextColor(textColor);
            tabContainer.addView(dayText);

            TextView dateText = new TextView(this);
            dateText.setText(dateFormat.format(new java.util.Date(displayedDate)));
            dateText.setTextSize(12);
            dateText.setTypeface(appTypefaceMedium());
            dateText.setGravity(Gravity.CENTER);
            dateText.setTextColor(textColor);
            dateText.setIncludeFontPadding(false);
            dateText.setSingleLine(true);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dateParams.topMargin = dp(2);
            tabContainer.addView(dateText, dateParams);

            // 今天添加小圆点指示器，颜色跟随文字状态
            View todayMarker = new View(this);
            int markerColor = isSelected ? accentColor() : todayColor();
            todayMarker.setBackground(buttonBackground(isToday ? markerColor : Color.TRANSPARENT, dp(3)));
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            markerParams.setMargins(0, dp(4), 0, 0);
            tabContainer.addView(todayMarker, markerParams);

            tabContainer.setMinimumHeight(dp(compact ? 64 : 68));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(compact ? 62 : 70), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(2), 0);
            dayTabs.addView(tabContainer, params);
        }
        boolean shouldAnimateSlider = animateDaySlider;
        animateDaySlider = false;
        dayTabs.post(() -> {
            updateDaySelectionSlider(shouldAnimateSlider);
            if (selectedDay != lastAutoScrolledDay) {
                scrollSelectedDayIntoView();
                lastAutoScrolledDay = selectedDay;
            }
        });
    }

    private void scrollSelectedDayIntoView() {
        if (dayScroll == null || dayTabs == null) {
            return;
        }
        if (selectedDay < 0 || selectedDay >= dayTabs.getChildCount()) {
            return;
        }
        View selectedTab = dayTabs.getChildAt(selectedDay);
        if (selectedTab.getWidth() <= 0) {
            return;
        }
        // 让选中日期居中显示：计算选中标签相对整个标签条的偏移
        int tabLeft = selectedTab.getLeft();
        int tabWidth = selectedTab.getWidth();
        int scrollWidth = dayScroll.getWidth();
        if (scrollWidth <= 0) {
            return;
        }
        int targetScroll = tabLeft - (scrollWidth - tabWidth) / 2;
        dayScroll.smoothScrollTo(Math.max(0, targetScroll), 0);
    }

    private void switchToDay(int day) {
        day = normalizeDayIndex(day);
        if (day == selectedDay) {
            return;
        }
        int direction = daySwitchDirection(day);
        switchToDay(day, direction);
    }

    private void switchToDay(int day, int direction) {
        day = normalizeDayIndex(day);
        if (day == selectedDay) {
            return;
        }
        resetCourseListAnimation();
        animateDaySlider = true;
        selectedDay = day;
        render();
        animateDaySwitch(direction);
    }

    private int daySwitchDirection(int day) {
        return day >= selectedDay ? 1 : -1;
    }

    private void resetCourseListAnimation() {
        if (courseList == null) {
            return;
        }
        courseList.animate().cancel();
        courseList.setAlpha(1f);
        courseList.setTranslationX(0f);
        courseList.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void updateDaySelectionSlider(boolean animate) {
        if (daySelectionSlider == null || dayTabs == null || selectedDay < 0 || selectedDay >= dayTabs.getChildCount()) {
            return;
        }
        View selectedTab = dayTabs.getChildAt(selectedDay);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) daySelectionSlider.getLayoutParams();
        params.width = selectedTab.getWidth();
        params.height = selectedTab.getHeight();
        daySelectionSlider.setLayoutParams(params);
        // brief §6：选中态是"浅蓝紫药丸 + 蓝字 + 下方圆点"，不要描边
        daySelectionSlider.setBackground(roundedSurface(selectedDayColor(), dp(20), Color.TRANSPARENT));

        float target = selectedTab.getLeft();
        daySelectionSlider.animate().cancel();
        if (animate && animationsEnabled()) {
            daySelectionSlider.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            daySelectionSlider.animate()
                    .translationX(target)
                    .setDuration(280L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                    .withEndAction(() -> daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null))
                    .start();
        } else {
            daySelectionSlider.setTranslationX(target);
            daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void animateDaySwitch(int direction) {
        if (!animationsEnabled()) {
            resetCourseListAnimation();
            return;
        }
        float offset = dp(18) * (direction >= 0 ? 1 : -1);
        courseList.animate().cancel();
        courseList.setAlpha(1f);
        courseList.setTranslationX(offset);
        courseList.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        courseList.animate()
                .translationX(0f)
                .setDuration(260L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    courseList.setAlpha(1f);
                    courseList.setTranslationX(0f);
                    courseList.setLayerType(View.LAYER_TYPE_NONE, null);
                })
                .start();
    }

    private void renderCourseList() {
        cancelCourseCardAnimations(courseList);
        courseList.removeAllViews();
        List<Course> dayCourses = new ArrayList<>();
        if (storageLocked) {
            TextView warning = new TextView(this);
            warning.setText("加密课表无法读取\n\n为避免覆盖原数据，已暂停保存。\n请检查系统密钥或清除应用数据后重新开始。");
            warning.setTextSize(15);
            warning.setTextColor(dangerColor());
            warning.setGravity(Gravity.CENTER);
            // brief：全项目不使用 emoji。原来这里是一个警示 emoji，现改用矢量图标，置于文字上方并跟随危险色。
            android.graphics.drawable.Drawable warningIcon = getDrawable(R.drawable.ic_warning);
            if (warningIcon != null) {
                warningIcon.setTint(dangerColor());
                warningIcon.setBounds(0, 0, dp(30), dp(30));
                warning.setCompoundDrawables(null, warningIcon, null, null);
                warning.setCompoundDrawablePadding(dp(14));
            }
            warning.setBackground(elevatedCardBackground(dangerContainerColor(), dp(16)));
            warning.setPadding(dp(24), dp(32), dp(24), dp(32));
            warning.setLineSpacing(dp(4), 1.0f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            courseList.addView(warning, params);
            return;
        }

        int currentWeek = viewingWeek != 0 ? viewingWeek : currentTeachingWeek();
        for (Course course : courses) {
            if (viewingWeek != 0 && course.temporary) {
                continue;
            }
            if (course.day == selectedDay && courseOccursInTeachingWeek(course, currentWeek)) {
                dayCourses.add(course);
            }
        }
        Collections.sort(dayCourses, (left, right) -> {
            int result = Integer.compare(left.temporary ? 0 : 1, right.temporary ? 0 : 1);
            if (result == 0) result = Integer.compare(left.period, right.period);
            if (result == 0) result = Integer.compare(left.endPeriod, right.endPeriod);
            if (result == 0) result = left.name.compareTo(right.name);
            return result;
        });

        if (dayCourses.isEmpty()) {
            MaterialCardView emptyState = new MaterialCardView(this);
            emptyState.setCardBackgroundColor(cardColor());
            emptyState.setCardElevation(dp(ELEVATION_SOFT));
            emptyState.setRadius(dp(RADIUS_CARD));
            emptyState.setStrokeColor(borderColor());
            emptyState.setStrokeWidth(dp(1));
            emptyState.setContentPadding(dp(20), dp(24), dp(20), dp(32));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            emptyState.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // brief §12：空状态是二次元插画唯一可以"放开"的地方——没有课程要展示时，
            // 角色的存在感可以明显高于其它位置，这是刻意的。
            ImageView emptyArt = new ImageView(this);
            emptyArt.setImageResource(R.drawable.empty_art);
            emptyArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            emptyArt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(emptyArt, new LinearLayout.LayoutParams(dp(170), dp(157)));

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("今天没有课程");
            emptyTitle.setTextSize(18);
            emptyTitle.setTypeface(appTypeface(Typeface.BOLD));
            emptyTitle.setTextColor(primaryTextColor());
            emptyTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, dp(20), 0, 0);
            content.addView(emptyTitle, titleParams);

            TextView emptyDate = new TextView(this);
            emptyDate.setText(selectedDayDateLabel());
            emptyDate.setTextSize(14);
            emptyDate.setTypeface(appTypeface(Typeface.NORMAL));
            emptyDate.setTextColor(secondaryTextColor());
            emptyDate.setGravity(Gravity.CENTER);
            emptyDate.setSingleLine(true);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dateParams.setMargins(0, dp(6), 0, dp(14));
            content.addView(emptyDate, dateParams);

            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);

            TextView addCta = new TextView(this);
            addCta.setText("\uff0b \u6dfb\u52a0\u8bfe\u7a0b");
            addCta.setTextSize(15);
            addCta.setTypeface(appTypeface(Typeface.BOLD));
            addCta.setTextColor(Color.WHITE);
            addCta.setGravity(Gravity.CENTER);
            addCta.setBackground(interactiveButtonBackground(accentColor(), dp(14)));
            addCta.setPadding(dp(22), dp(12), dp(22), dp(12));
            addCta.setOnClickListener(v -> showCourseDialog(null));

            TextView importCta = new TextView(this);
            importCta.setText(getString(R.string.import_pdf_cta));
            importCta.setTextSize(15);
            importCta.setTypeface(appTypeface(Typeface.BOLD));
            importCta.setTextColor(accentColor());
            importCta.setGravity(Gravity.CENTER);
            importCta.setBackground(interactiveTranslucentBackground(
                    accentContainerColor(), dp(14)));
            importCta.setPadding(dp(22), dp(12), dp(22), dp(12));
            importCta.setOnClickListener(v -> openPdfPicker());

            LinearLayout.LayoutParams addCtaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            addCtaParams.setMargins(0, 0, dp(10), 0);
            buttonRow.addView(addCta, addCtaParams);
            buttonRow.addView(importCta);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, dp(20), 0, 0);
            content.addView(buttonRow, rowParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            courseList.addView(emptyState, params);
            return;
        }

        for (int i = 0; i < dayCourses.size(); i++) {
            courseList.addView(createTimelineRow(
                    dayCourses.get(i), i == 0, i == dayCourses.size() - 1));
        }
    }

    /**
     * 课程卡片右下角装饰插画：按课程名关键词匹配主题小物件。
     *
     * 归类顺序有意固定：外语 → 程序/数据 → 计算机硬件 → 数理，避免"计算机组成原理"
     * 被更宽泛的规则先截走。全部不命中时按课程名哈希稳定选取，保证同一门课每次渲染一致。
     */
    private int courseArtResId(Course course) {
        String name = course.name == null
                ? ""
                : course.name.toLowerCase(java.util.Locale.ROOT);

        if (containsAny(name, "英语", "english", "日语", "德语", "法语", "俄语", "外语",
                "口语", "听力", "翻译", "写作")) {
            return R.drawable.art_english;
        }
        if (containsAny(name, "数据结构", "算法", "编程", "程序", "代码", "软件", "数据库",
                "操作系统", "编译", "语言", "人工智能", "机器学习")) {
            return R.drawable.art_data;
        }
        if (containsAny(name, "计算机", "组成", "计组", "芯片", "硬件", "电路", "数字逻辑",
                "微机", "嵌入式", "网络", "通信", "电子")) {
            return R.drawable.art_computer;
        }
        if (containsAny(name, "数学", "高数", "代数", "几何", "微积分", "统计", "概率",
                "线性", "离散", "物理", "化学", "力学", "分析", "数值")) {
            return R.drawable.art_math;
        }

        int[] pool = {
                R.drawable.art_math,
                R.drawable.art_data,
                R.drawable.art_computer,
                R.drawable.art_english
        };
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = hash * 31 + name.charAt(i);
        }
        return pool[Math.abs(hash % pool.length)];
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一的线性小图标视图：固定正方形尺寸 + 染色 + 右侧间距。
     * 装饰性图标不参与无障碍朗读（文本已经表达了同样信息）。
     */
    private android.widget.ImageView iconView(int resId, int color, int sizeDp, int marginRightPx) {
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(resId);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMargins(0, 0, marginRightPx, 0);
        icon.setLayoutParams(params);
        return icon;
    }

    /** 卡片详情里的一个「线性图标 + 文本」小组，用于地点 / 教师。 */
    private LinearLayout detailGroup(int iconResId, String text, int color, boolean first) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        // 两组均分行宽：房间名可能很长（例如"李小平教育大楼201"）。
        // 若都用 WRAP_CONTENT，前一组会把宽度吃光，后一组被挤成一个孤零零的图标；
        // 均分之后两组各自省略，教师至少还能看到名字开头。
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) {
            groupParams.setMargins(dp(7), 0, 0, 0);
        }
        group.setLayoutParams(groupParams);

        // 图标与字号都收到 11：时间轴改成"时间+圆点"同行后 gutter 加宽到 50dp，
        // 详情行必须再紧一档，才能把"锡科503"这类地点完整放下。
        group.addView(iconView(iconResId, color, 11, dp(4)));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(11);
        label.setTypeface(appTypeface(Typeface.NORMAL));
        label.setTextColor(color);
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        // 文本占满组内剩余宽度，过长时按组宽省略
        group.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return group;
    }

    /** 课程是否处于"正在上课 / 已进入提醒窗口"，卡片高亮与时间轴圆点共用这一个判断。 */
    private boolean isCourseActive(Course course) {
        TimetableRules.TemporalState state = courseTemporalState(course);
        return state == TimetableRules.TemporalState.REMINDER_WINDOW
                || state == TimetableRules.TemporalState.IN_PROGRESS;
    }

    /**
     * 时间轴行（brief §六）：左侧 gutter 放时间、贯穿细竖线、圆点与「当前」标签，右侧是课程卡片。
     *
     * 关键细节：行间距做成**行内 padding** 而不是 layout_margin。
     * 用 margin 的话竖线只能画到卡片底部，行与行之间会出现虚线断口；
     * 放在 padding 里竖线就能连续贯穿整行，看起来才是一条完整的时间轴。
     */
    private View createTimelineRow(Course course, boolean isFirst, boolean isLast) {
        boolean compact = isCompactWidth();
        boolean current = isCourseActive(course);
        boolean completed = courseTemporalState(course) == TimetableRules.TemporalState.COMPLETED;
        int gapDp = current ? 18 : 12;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, 0, 0, dp(gapDp));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(dp(contentSideMarginDp()), 0, 0, 0);
        row.setLayoutParams(rowParams);

        // ── 左 gutter：贯穿竖线（背景层）+ 时间 / 圆点 /「当前」标签（内容层）──
        // 竖线必须做背景层而不是堆叠元素：若堆叠，它只能排在圆点之后，
        // 下一行顶部的时间文字会把线断开，整体看起来是虚线而不是一条时间轴。
        FrameLayout gutter = new FrameLayout(this);
        // gutter 50dp：时间和圆点要排在同一行（效果图是 "08:00 ●"），比原来纵向堆叠更占宽。
        // 再多就会把卡片里的周次和地点挤成省略号。
        int gutterWidthDp = compact ? 50 : 58;
        LinearLayout.LayoutParams gutterParams = new LinearLayout.LayoutParams(
                dp(gutterWidthDp), ViewGroup.LayoutParams.MATCH_PARENT);
        // 关键：row 有 gapDp 的底部 padding，而 MATCH_PARENT 不含 padding 区——
        // 竖线会在每两张卡片之间断开 gapDp（实测 12dp 一段，用户指出的"空缺"）。
        // 负 bottomMargin 抵消 padding，让 gutter（和其中的竖线）贯穿到下一行的圆点。
        gutterParams.bottomMargin = -dp(gapDp);
        row.addView(gutter, gutterParams);

        // 圆点尺寸必须取偶数：rightMargin = INSET - dotSize/2 依赖整除，
        // 奇数（如 9）会丢 0.5dp，导致圆点圆心和竖线中心错开半个像素。
        int dotSizeDp = current ? 8 : 6;
        if (!(isFirst && isLast)) {
            View line = new View(this);
            // 竖线要"看得见"：原来用 borderColor()（#DDE4EE），和页面背景 #F0F5FE
            // 只差 ~15/255，真机上几乎隐形（用户照片里三颗圆点之间看不到线）。
            // 加深一档让它成为可辨识的时间轴，但仍远轻于卡片描边。
            line.setBackgroundColor(Color.rgb(191, 203, 224));
            // 末行的线在圆点处收住；其余行贯穿整行（含行内 padding），与下一行自然相接。
            // 靠右对齐 + rightMargin，让竖线正好穿过圆点中心。
            // 竖线取 2dp：1dp 线的中心落在半 dp 上，永远无法和整 dp 的圆点圆心对齐。
            // 2dp 时线中心 = W - rightMargin - 1，与圆点圆心（= W - INSET）精确相等。
            FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                    dp(2),
                    isLast ? dp(TIMELINE_DOT_TOP_DP + dotSizeDp / 2)
                           : ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.END);
            lineParams.rightMargin = dp(TIMELINE_LINE_INSET_DP - 1);
            if (isFirst) {
                lineParams.topMargin = dp(TIMELINE_DOT_TOP_DP);
            }
            gutter.addView(line, lineParams);
        }

        LinearLayout gutterContent = new LinearLayout(this);
        gutterContent.setOrientation(LinearLayout.VERTICAL);
        gutterContent.setGravity(Gravity.END);
        gutter.addView(gutterContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 时间与圆点同一行，整组右对齐 —— 效果图是 "08:00 ●"，竖线穿过圆点
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        // 固定高度 = 2 * TIMELINE_DOT_TOP_DP，让圆点（垂直居中）的圆心精确落在 8dp，
        // 与竖线首行的 topMargin(8dp) 对齐 —— 否则圆点圆心在时钟行高度一半(~6dp)，
        // 和竖线起点差 2dp，形成"点对不进空缺"的错位。
        clockRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TIMELINE_DOT_TOP_DP * 2)));

        TextView clock = new TextView(this);
        clock.setText(clockText(courseStartMinutes(course)));
        clock.setTextSize(10);
        clock.setTypeface(current ? appTypefaceMedium() : appTypeface(Typeface.NORMAL));
        clock.setTextColor(current ? accentColor() : secondaryTextColor());
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        clockRow.addView(clock);

        View dot = new View(this);
        // 圆点跟随课程色（效果图里 08:00 是绿色、14:30 是蓝色），已完成的上降饱和
        dot.setBackground(buttonBackground(
                current ? accentColor()
                        : (completed ? mixColor(cardColor(), course.color, 0.45f) : course.color),
                dp(dotSizeDp / 2 + 1)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(dotSizeDp), dp(dotSizeDp));
        // 让圆点圆心正好落在竖线上
        dotParams.setMargins(dp(4), 0, dp(TIMELINE_LINE_INSET_DP - dotSizeDp / 2), 0);
        clockRow.addView(dot, dotParams);
        gutterContent.addView(clockRow);

        if (current) {
            LinearLayout nowRow = new LinearLayout(this);
            nowRow.setOrientation(LinearLayout.HORIZONTAL);
            nowRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nowTag = new TextView(this);
            nowTag.setText("当前");
            nowTag.setTextSize(10);
            nowTag.setTypeface(appTypefaceMedium());
            nowTag.setTextColor(Color.WHITE);
            nowTag.setIncludeFontPadding(false);
            nowTag.setGravity(Gravity.CENTER);
            // 内边距收到 5dp：gutter 只有 40dp，还要塞下右侧那颗指向卡片的小三角
            nowTag.setPadding(dp(5), dp(2), dp(5), dp(2));
            nowTag.setBackground(buttonBackground(accentColor(), dp(100)));
            nowRow.addView(nowTag);

            // 效果图里「当前」胶囊右侧那颗指向课程卡片的小三角
            android.widget.ImageView pointer = new android.widget.ImageView(this);
            pointer.setImageResource(R.drawable.ic_pointer);
            pointer.setImageTintList(ColorStateList.valueOf(accentColor()));
            pointer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nowRow.addView(pointer, new LinearLayout.LayoutParams(dp(8), dp(8)));

            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tagParams.setMargins(0, dp(5), 0, 0);
            gutterContent.addView(nowRow, tagParams);
        }

        // ── 右：课程卡片 ───────────────────────────────────────────────
        row.addView(createCourseCard(course), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    private View createCourseCard(Course course) {
        TimetableRules.TemporalState temporalState = courseTemporalState(course);
        boolean active = isCourseActive(course);
        boolean completed = temporalState == TimetableRules.TemporalState.COMPLETED;
        boolean compact = isCompactWidth();
        // 详情拆成两行后卡片变高，下限与进行中卡片的锁定高度同步放宽，避免裁切
        int minHeightDp = active ? (compact ? 152 : 160) : (compact ? 108 : 116);
        BoundedMaterialCardView shell = new BoundedMaterialCardView(this);
        shell.setMinimumHeight(dp(minHeightDp));
        // 仅进行中卡片锁定最大高度（稳定倒计时/进度动画）；
        // 普通与灰化卡片高度随内容自适应，两行课程名 + 时间胶囊 + 信息行不再被裁切，
        // 系统大字号、竖屏窄屏同样安全。
        shell.setMaxHeightPx(active ? dp(compact ? 176 : 186) : 0);
        // brief §10：主要卡片统一 16dp 圆角（原来是 24/28，偏"胖"）
        shell.setRadius(dp(RADIUS_CARD));
        shell.setCardElevation(dp(active ? 5 : 1));
        // brief §5 + 效果图：当前课是"浅蓝紫底 + 细蓝描边 + 深色字"。
        // 填充与描边的浓度是照效果图量的：描边必须明显看得出来，否则整屏会糊成一片白。
        shell.setCardBackgroundColor(active
                ? mixColor(cardColor(), accentColor(), isDarkMode ? 0.38f : 0.24f)
                : mixColor(cardColor(), course.color, isDarkMode ? 0.26f : 0.20f));
        shell.setStrokeWidth(dp(active ? 2 : 1));
        shell.setStrokeColor(active
                ? mixColor(cardColor(), accentColor(), isDarkMode ? 0.88f : 0.62f)
                : mixColor(cardColor(), course.color, isDarkMode ? 0.62f : 0.52f));
        shell.setClipChildren(true);
        shell.setClipToPadding(true);
        shell.setClipToOutline(true);
        shell.setClickable(false);

        if (active) {
            shell.addView(createCurrentCourseProgressLayer(course), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }

        // 插画浓度：所有卡片（含当前课）统一用 COURSE_ART_ALPHA（0.95），
        // 与预览模式一致；不再按 active 压低，也不再上色滤镜（色滤会把水彩变成单色剪影）。
        android.widget.ImageView courseArt = new android.widget.ImageView(this);
        courseArt.setImageResource(courseArtResId(course));
        courseArt.setAlpha(COURSE_ART_ALPHA);
        courseArt.setScaleType(android.widget.ImageView.ScaleType.FIT_END);
        courseArt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                dp(compact ? 124 : 140),
                dp(compact ? 100 : 112),
                Gravity.BOTTOM | Gravity.END
        );
        artParams.setMargins(0, 0, dp(6), dp(3));
        shell.addView(courseArt, artParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        card.setPadding(
                dp(active ? (compact ? 16 : 20) : (compact ? 14 : 16)),
                dp(active ? (compact ? 20 : 22) : (compact ? 14 : 16)),
                dp(active ? (compact ? 16 : 20) : (compact ? 14 : 16)),
                dp(active ? (compact ? 20 : 22) : (compact ? 14 : 16))
        );
        shell.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
        ));

        // 左侧彩色节次徽章
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(compact ? 10 : 12), dp(8), dp(compact ? 10 : 12), dp(8));
        // 当前课现在是浅底，徽章回到实心课程色（原来半透明白是为了配深色实心底）
        badge.setBackground(buttonBackground(course.color, dp(active ? 20 : 16)));

        TextView periodNum = new TextView(this);
        String periodNumText;
        if (course.endPeriod > course.period) {
            periodNumText = String.format(Locale.CHINA, "%d-%d", course.period, course.endPeriod);
        } else {
            periodNumText = String.valueOf(course.period);
        }
        periodNum.setText(periodNumText);
        periodNum.setTextColor(active ? Color.WHITE : Color.WHITE);
        periodNum.setTypeface(appTypeface(Typeface.BOLD));
        float periodNumSize = active ? (compact ? 21 : 22) : (compact ? 19 : 20);
        if (periodNumText.length() >= 5) {
            periodNumSize = active ? (compact ? 14 : 15) : (compact ? 13 : 14);
        } else if (periodNumText.length() >= 3) {
            periodNumSize = active ? (compact ? 16 : 17) : (compact ? 15 : 16);
        }
        periodNum.setTextSize(periodNumSize);
        periodNum.setLetterSpacing(0f);
        periodNum.setGravity(Gravity.CENTER);
        periodNum.setSingleLine(true);
        periodNum.setIncludeFontPadding(false);
        badge.addView(periodNum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView periodLabel = new TextView(this);
        periodLabel.setText("节");
        periodLabel.setTextColor(Color.WHITE);
        periodLabel.setTextSize(12);
        periodLabel.setTypeface(appTypeface(Typeface.NORMAL));
        periodLabel.setAlpha(active ? 0.95f : 0.9f);
        periodLabel.setGravity(Gravity.CENTER);
        periodLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(2), 0, 0);
        badge.addView(periodLabel, labelParams);

        card.addView(badge, new LinearLayout.LayoutParams(dp(active ? 64 : 56), ViewGroup.LayoutParams.WRAP_CONTENT));

        // 中间课程信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(true);
        info.setClipToPadding(true);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(dp(compact ? 12 : 16), 0, dp(compact ? 8 : 12), 0);
        card.addView(info, infoParams);

        TextView name = new TextView(this);
        name.setText(course.name);
        name.setTextColor(primaryTextColor());
        name.setTextSize(active ? (compact ? 20 : 21) : (compact ? 17 : 18));
        name.setTypeface(appTypefaceSemiBold());
        name.setLetterSpacing(0f);
        name.setMaxLines(active ? 1 : 2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);

        // 时间行：线性时钟 + 时间。照示意图不加药丸底色，也不在这里塞周次
        // （周次已挪到下面的详情行，与地点、教师并排）。
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        // 效果图里时间文字下面垫着一层极淡的圆角底
        timeRow.setPadding(dp(7), dp(3), dp(9), dp(3));
        timeRow.setBackground(buttonBackground(
                active ? Color.argb(56, 255, 255, 255) : tonalContainerColor(), dp(9)));

        int timeFg = accentColor();
        timeRow.addView(iconView(R.drawable.ic_clock_outline, timeFg, 12, dp(4)));

        TextView time = new TextView(this);
        time.setText(periodRangeTime(course));
        time.setTextColor(timeFg);
        time.setTextSize(12);
        time.setTypeface(appTypefaceMedium());
        time.setSingleLine(true);
        time.setEllipsize(TextUtils.TruncateAt.END);
        time.setIncludeFontPadding(false);
        timeRow.addView(time);

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, dp(6), 0, 0);
        info.addView(timeRow, timeParams);

        if (active) {
            View remaining = createRemainingCountdownView(course, compact);
            LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            remainingParams.setMargins(0, dp(6), 0, 0);
            info.addView(remaining, remainingParams);
        }

        // 详情拆两行。保留时间轴后横向只剩约 140dp，而三组一行实测需要 168dp
        // （周次 46 + 地点 56 + 教师 52 + 间距 14），硬排会把三组全部截断：
        //   第一行：周次 + 地点      第二行：教师
        int detailFg = secondaryTextColor();
        String weeksText = course.invalidWeeks ? "上课周待修正"
                : (course.weeks == null ? "" : course.weeks);
        boolean hasWeeks = !weeksText.isEmpty();
        boolean hasRoom = course.room != null && !course.room.isEmpty();
        boolean hasTeacher = course.teacher != null && !course.teacher.isEmpty();

        if (hasWeeks || hasRoom) {
            LinearLayout detailLine1 = new LinearLayout(this);
            detailLine1.setOrientation(LinearLayout.HORIZONTAL);
            detailLine1.setGravity(Gravity.CENTER_VERTICAL);
            boolean first = true;
            if (hasWeeks) {
                detailLine1.addView(detailGroup(
                        course.invalidWeeks ? R.drawable.ic_warning : R.drawable.ic_calendar_outline,
                        weeksText, detailFg, first));
                first = false;
            }
            if (hasRoom) {
                detailLine1.addView(detailGroup(R.drawable.ic_location, course.room, detailFg, first));
            }
            LinearLayout.LayoutParams line1Params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            line1Params.setMargins(0, dp(6), 0, 0);
            info.addView(detailLine1, line1Params);
        }

        if (hasTeacher) {
            LinearLayout detailLine2 = new LinearLayout(this);
            detailLine2.setOrientation(LinearLayout.HORIZONTAL);
            detailLine2.setGravity(Gravity.CENTER_VERTICAL);
            detailLine2.addView(detailGroup(R.drawable.ic_person, course.teacher, detailFg, true));
            LinearLayout.LayoutParams line2Params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            line2Params.setMargins(0, dp(3), 0, 0);
            info.addView(detailLine2, line2Params);
        }

        // 右侧编辑按钮：按示意图做成"右上角的小白圆"——更小、贴顶、对比更强。
        ImageButton edit = new ImageButton(this);
        edit.setImageResource(R.drawable.ic_more_horizontal);
        edit.setImageTintList(ColorStateList.valueOf(primaryTextColor()));
        edit.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        // 近白卡片上纯色圆块对比不足，看不出是个按钮；改用"白底 + 1dp 细描边 + 轻阴影"，
        // 与示意图里那颗浮起的白圆一致。当前课现在也是浅底，所以两种状态共用同一种做法。
        edit.setBackground(elevatedCardBackground(cardColor(), dp(18)));
        edit.setElevation(dp(ELEVATION_SOFT));
        edit.setPadding(dp(8), dp(8), dp(8), dp(8));
        edit.setContentDescription("\u7f16\u8f91\u8bfe\u7a0b");
        edit.setOnClickListener(view -> showCourseDialog(course));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        // 水平 LinearLayout 中 layout_gravity 控制纵向位置：贴顶对齐，和示意图一致
        editParams.gravity = Gravity.TOP;
        card.addView(edit, editParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        // 左边距与行间距都交给 createTimelineRow（竖线要连续贯穿整行），这里只保留右边距
        params.setMargins(0, 0, dp(contentSideMarginDp()), 0);
        shell.setLayoutParams(params);
        // 「已完成」原来是在卡片上盖一层 36% 不透明的灰蓝罩（argb(92,156,163,174)）。
        // 实测它会把卡片填充色、彩色描边和右下角插画全部糊掉，整列看过去是一片灰紫。
        // 现在不再压灰罩：已完成的课程靠"时间轴圆点降饱和 + 插画减淡"来区分，
        // 卡片本身保持和其它课程一样的通透度。
        // 已完成课程不再压灰罩，靠"时间轴圆点降饱和 + 插画减淡"区分。
        // 插画减淡原来取 0.10（实测等于隐形，用户照片里三张卡两张看不到图案），
        // 提到 0.40：仍明显弱于普通卡的 0.95，但图案能认出来。
        if (completed) {
            courseArt.setAlpha(0.40f);
        }
        boolean approaching = temporalState == TimetableRules.TemporalState.REMINDER_WINDOW;
        boolean shakeEnabled = approaching && animationsEnabled();
        if (shakeEnabled) {
            attachShakeStopDoubleTap(shell, course);
        }
        if (shakeEnabled && !isShakeStopped(course)) {
            applyUpcomingCourseShake(shell, course);
        }
        return shell;
    }

    private View createCurrentCourseProgressLayer(Course course) {
        return new CourseProgressView(course);
    }

    private View createRemainingCountdownView(Course course, boolean compact) {
        return new CountdownRow(course, compact);
    }

    private CountdownState remainingCountdownState(Course course) {
        int start = courseStartSeconds(course);
        int end = courseEndSeconds(course);
        int now = currentSeconds();
        if (start < 0 || end < 0) {
            return CountdownState.plain("\u8ddd\u79bb\u4e0b\u8bfe\u8fd8\u6709 --");
        }
        if (now < start) {
            return countdownStateForRemaining(true, start - now);
        }
        if (now >= end) {
            return CountdownState.plain("\u5df2\u4e0b\u8bfe");
        }
        return countdownStateForRemaining(false, end - now);
    }

    private CountdownState countdownStateForRemaining(boolean starts, int remainingSeconds) {
        String prefix = starts
                ? "\u8ddd\u79bb\u4e0a\u8bfe\u8fd8\u6709 "
                : "\u8ddd\u79bb\u4e0b\u8bfe\u8fd8\u6709 ";
        if (remainingSeconds <= 0) {
            return CountdownState.plain(starts ? "\u5373\u5c06\u4e0a\u8bfe" : "\u5df2\u4e0b\u8bfe");
        }
        if (remainingSeconds < COUNTDOWN_SECONDS_THRESHOLD) {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            String numberPrefix = minutes > 0 ? prefix + minutes + " \u5206 " : prefix;
            return CountdownState.seconds(
                    numberPrefix,
                    String.valueOf(seconds),
                    " \u79d2",
                    starts ? "start" : "end",
                    remainingSeconds
            );
        }

        int totalMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return CountdownState.plain(prefix + hours + " \u5c0f\u65f6 " + minutes + " \u5206\u949f");
        }
        if (hours > 0) {
            return CountdownState.plain(prefix + hours + " \u5c0f\u65f6");
        }
        return CountdownState.plain(prefix + totalMinutes + " \u5206\u949f");
    }

    private TextView createCountdownTextView(boolean compact) {
        TextView text = new TextView(this);
        // 当前课已改成浅底，倒计时不能再用白字（原来是为实心主色底写的），改用主色保证对比度
        text.setTextColor(accentColor());
        text.setTextSize(compact ? 14 : 15);
        text.setTypeface(appTypeface(Typeface.BOLD));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private String countdownKey(Course course, String phase) {
        long id = course.id != 0L
                ? course.id
                : ((long) (course.name == null ? 0 : course.name.hashCode()) << 32)
                ^ (course.day * 131L + course.period * 17L + course.endPeriod);
        return id + ":" + phase + ":" + course.day + ":" + course.period + ":" + course.endPeriod;
    }

    private CountdownSnapshot recentCountdownSnapshot(String key) {
        CountdownSnapshot snapshot = countdownSnapshots.get(key);
        if (snapshot == null) {
            return null;
        }
        long age = android.os.SystemClock.uptimeMillis() - snapshot.updatedAtMillis;
        return age <= PROGRESS_REFRESH_MS + COUNTDOWN_REFRESH_MS * 2L ? snapshot : null;
    }

    private void rememberCountdownSnapshot(String key, String secondsText) {
        if (countdownSnapshots.size() > MAX_COURSES * 4) {
            countdownSnapshots.clear();
        }
        countdownSnapshots.put(key, new CountdownSnapshot(secondsText, android.os.SystemClock.uptimeMillis()));
    }

    private String remainingUntilClassEndsText(Course course) {
        int start = courseStartMinutes(course);
        int end = courseEndMinutes(course);
        int now = currentMinutes();
        if (start < 0 || end < 0) {
            return "距离下课还有 --";
        }
        if (now < start) {
            return remainingUntilClassStartsText(start - now);
        }
        if (now >= end) {
            return "已下课";
        }
        int remaining = end - now;
        if (remaining <= 0) {
            return "距离下课还有 不到 1 分钟";
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return "距离下课还有 " + hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return "距离下课还有 " + hours + " 小时";
        }
        return "距离下课还有 " + minutes + " 分钟";
    }

    private String remainingUntilClassStartsText(int remaining) {
        if (remaining <= 0) {
            return "即将上课";
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return "距离上课还有 " + hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return "距离上课还有 " + hours + " 小时";
        }
        return "距离上课还有 " + minutes + " 分钟";
    }

    private boolean isCourseApproachingStart(Course course) {
        return courseTemporalState(course) == TimetableRules.TemporalState.REMINDER_WINDOW;
    }

    private TimetableRules.TemporalState courseTemporalState(Course course) {
        if (viewingWeek != 0) {
            // 预览模式：不做实时状态，只按日期比较（过去灰化 / 未来正常）
            int previewWeek = clamp(viewingWeek, 1, calendarTotalWeeks());
            if (!courseOccursInTeachingWeek(course, previewWeek)) {
                return TimetableRules.TemporalState.UNAVAILABLE;
            }
            return courseDateMillisForWeek(course.day, previewWeek) < startOfTodayMillis()
                    ? TimetableRules.TemporalState.COMPLETED
                    : TimetableRules.TemporalState.UPCOMING;
        }
        int currentWeek = currentTeachingWeek();
        boolean occursThisWeek = course.temporary
                || (!course.invalidWeeks && courseOccursInTeachingWeek(course, currentWeek));
        int dateComparison;
        if (course.temporary) {
            dateComparison = Integer.compare(course.day, currentSchoolDay());
        } else if (occursThisWeek) {
            dateComparison = Long.compare(
                    courseDateMillisForWeek(course.day, currentWeek),
                    startOfTodayMillis()
            );
        } else {
            dateComparison = 0;
        }
        return TimetableRules.temporalStateForDateComparison(
                occursThisWeek,
                dateComparison,
                courseStartSeconds(course),
                courseEndSeconds(course),
                currentSeconds(),
                upcomingShakeStartMinutes(course) * 60
        );
    }

    private void attachShakeStopDoubleTap(View target, Course course) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        target.setContentDescription(course.name + "，双击停止课前抖动");
        target.setOnClickListener(view -> stopCourseShake(view, course));
        target.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
                downY[0] = event.getY();
                return true;
            }
            if (action != MotionEvent.ACTION_UP) {
                return true;
            }
            if (Math.abs(event.getX() - downX[0]) > dp(10) || Math.abs(event.getY() - downY[0]) > dp(10)) {
                return true;
            }
            String key = courseInteractionKey(course);
            long now = android.os.SystemClock.uptimeMillis();
            Long lastTap = lastShakeTapMillisByKey.get(key);
            if (lastTap != null
                    && now - lastTap <= android.view.ViewConfiguration.getDoubleTapTimeout()) {
                lastShakeTapMillisByKey.remove(key);
                view.performClick();
                return true;
            }
            if (lastShakeTapMillisByKey.size() > MAX_COURSES * 2) {
                lastShakeTapMillisByKey.clear();
            }
            lastShakeTapMillisByKey.put(key, now);
            return true;
        });
        target.setClickable(true);
    }

    private void stopCourseShake(View target, Course course) {
        if (stoppedShakeKeys.size() > MAX_COURSES * 2) {
            stoppedShakeKeys.clear();
        }
        stoppedShakeKeys.put(courseInteractionKey(course), android.os.SystemClock.uptimeMillis());
        cancelCourseCardAnimations(target);
        Toast.makeText(this, "已停止本节课抖动", Toast.LENGTH_SHORT).show();
        target.post(this::render);
    }

    private boolean isShakeStopped(Course course) {
        if (!isCourseApproachingStart(course)) {
            clearShakeStop(course);
            return false;
        }
        return stoppedShakeKeys.containsKey(courseInteractionKey(course));
    }

    private void clearShakeStop(Course course) {
        String key = courseInteractionKey(course);
        stoppedShakeKeys.remove(key);
        lastShakeTapMillisByKey.remove(key);
    }

    private String courseInteractionKey(Course course) {
        Calendar calendar = Calendar.getInstance();
        String identity = course.id != 0L
                ? String.valueOf(course.id)
                : String.valueOf(course.name);
        return calendar.get(Calendar.YEAR)
                + ":" + calendar.get(Calendar.DAY_OF_YEAR)
                + ":" + identity
                + ":" + course.day
                + ":" + course.period
                + ":" + course.endPeriod
                + ":" + course.customStartMinutes
                + ":" + course.customEndMinutes;
    }

    private void applyUpcomingCourseShake(View target, Course course) {
        if (!animationsEnabled()) {
            return;
        }
        int start = courseStartMinutes(course);
        int remaining = Math.max(0, start - currentMinutes());
        int window = Math.max(1, upcomingShakeStartMinutes(course));
        float rawIntensity = 1f - Math.min(window, remaining) / (float) window;
        float intensity = rawIntensity * rawIntensity;
        float density = getResources().getDisplayMetrics().density;
        float xAmplitude = (0.35f + 5.9f * intensity) * density;
        float yAmplitude = (0.18f + 3.6f * intensity) * density;
        long duration = Math.max(980L, (long) (2800L - 1400L * intensity));
        int repeatCount = BuildConfig.ENABLE_TEST_COURSE && course.temporary
                ? 24
                : 1 + Math.round(2f * intensity);
        java.util.Random random = new java.util.Random(
                System.nanoTime() ^ (course.name == null ? 0 : course.name.hashCode())
        );
        float[][] path = new float[2][7];
        fillRandomFloatingPath(path, random, xAmplitude, yAmplitude);
        android.animation.ValueAnimator shake = android.animation.ValueAnimator.ofFloat(0f, 1f);
        shake.setDuration(duration);
        shake.setRepeatCount(repeatCount);
        shake.setRepeatMode(android.animation.ValueAnimator.RESTART);
        shake.setStartDelay(Math.abs(course.name == null ? 0 : course.name.hashCode()) % 180);
        shake.setInterpolator(new android.view.animation.LinearInterpolator());
        shake.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            int segmentCount = path[0].length - 1;
            int segment = Math.min(segmentCount - 1, (int) (fraction * segmentCount));
            float local = fraction * segmentCount - segment;
            float eased = local * local * (3f - 2f * local);
            target.setTranslationX(lerp(path[0][segment], path[0][segment + 1], eased));
            target.setTranslationY(lerp(path[1][segment], path[1][segment + 1], eased));
        });
        shake.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                fillRandomFloatingPath(path, random, xAmplitude, yAmplitude);
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                target.setTranslationX(0f);
                target.setTranslationY(0f);
                if (target.getTag() == animation) {
                    target.setTag(null);
                }
                target.setLayerType(View.LAYER_TYPE_NONE, null);
                if (!cancelled && target.isAttachedToWindow()) {
                    target.postDelayed(() -> {
                        if (activityResumed
                                && target.isAttachedToWindow()
                                && target.getTag() == null
                                && isCourseApproachingStart(course)
                                && !isShakeStopped(course)) {
                            applyUpcomingCourseShake(target, course);
                        }
                    }, 1400L);
                }
            }
        });
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        target.setTag(shake);
        shake.start();
    }

    private int upcomingShakeStartMinutes(Course course) {
        return Math.min(FLOATING_START_MINUTES, reminderLeadMinutes(course));
    }

    private void fillRandomFloatingPath(float[][] path, java.util.Random random, float xAmplitude, float yAmplitude) {
        path[0][0] = 0f;
        path[1][0] = 0f;
        for (int i = 1; i < path[0].length - 1; i++) {
            float scale = 0.45f + random.nextFloat() * 0.55f;
            path[0][i] = (random.nextFloat() * 2f - 1f) * xAmplitude * scale;
            path[1][i] = (random.nextFloat() * 2f - 1f) * yAmplitude * scale;
        }
        path[0][path[0].length - 1] = 0f;
        path[1][path[1].length - 1] = 0f;
    }

    private float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }

    private void cancelCourseCardAnimations(View view) {
        if (view == null) {
            return;
        }
        Object tag = view.getTag();
        if (tag instanceof android.animation.Animator) {
            ((android.animation.Animator) tag).cancel();
            view.setTag(null);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                cancelCourseCardAnimations(group.getChildAt(i));
            }
        }
    }

    private float courseProgress(Course course) {
        int start = courseStartSeconds(course);
        int end = courseEndSeconds(course);
        int now = currentSeconds();
        if (start < 0 || end <= start) {
            return 0f;
        }
        if (now < start) {
            return 0f;
        }
        if (now > end) {
            return 1f;
        }
        return (now - start) / (float) (end - start);
    }

    private int currentSeconds() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 3600
                + calendar.get(Calendar.MINUTE) * 60
                + calendar.get(Calendar.SECOND);
    }

    private int currentMinutes() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private int courseStartSeconds(Course course) {
        int minutes = courseStartMinutes(course);
        return minutes < 0 ? -1 : minutes * 60;
    }

    private int courseEndSeconds(Course course) {
        int minutes = courseEndMinutes(course);
        return minutes < 0 ? -1 : minutes * 60;
    }

    private int courseStartMinutes(Course course) {
        if (course.customStartMinutes >= 0) {
            return course.customStartMinutes;
        }
        return periodStartMinutes(course.period);
    }

    private int courseEndMinutes(Course course) {
        if (course.customEndMinutes >= 0) {
            return course.customEndMinutes;
        }
        return periodEndMinutes(course.endPeriod);
    }

    private int periodCount() {
        if (periodTimes.isEmpty()) {
            periodTimes.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
        }
        return periodTimes.size();
    }

    private int currentPeriod() {
        int now = currentMinutes();
        for (int period = 1; period <= periodCount(); period++) {
            int start = periodStartMinutes(period);
            int end = periodEndMinutes(period);
            if (start >= 0 && end >= 0 && now >= start && now <= end) {
                return period;
            }
        }
        return -1;
    }

    private int nearestDisplayPeriod() {
        int now = currentMinutes();
        for (int period = 1; period <= periodCount(); period++) {
            int end = periodEndMinutes(period);
            if (end >= now) {
                return period;
            }
        }
        return periodCount();
    }

    private int periodStartMinutes(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return -1;
        }
        String[] parts = periodTimes.get(index).split("-");
        return parts.length == 2 ? clockMinutes(parts[0]) : -1;
    }

    private int periodEndMinutes(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return -1;
        }
        String[] parts = periodTimes.get(index).split("-");
        return parts.length == 2 ? clockMinutes(parts[1]) : -1;
    }

    private int clockMinutes(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return -1;
        }
        int hour = parseInt(parts[0], -1);
        int minute = parseInt(parts[1], -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private void showCourseDialog(Course editing) {
        boolean isEditing = editing != null;
        Course draft = isEditing ? editing.copy() : new Course();
        if (!isEditing) {
            draft.id = System.currentTimeMillis();
            draft.day = selectedDay;
            draft.period = 1;
            draft.endPeriod = 1;
            draft.color = chooseCourseColorForDay(draft.day, draft.id);
        }

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.setClipToPadding(false);
        formScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(14));
        formScroll.addView(form);

        Spinner daySpinner = spinner(DAYS);
        daySpinner.setSelection(draft.day);
        form.addView(label("星期"));
        form.addView(daySpinner, fieldParams());

        String[] periods = new String[periodCount()];
        for (int i = 0; i < periods.length; i++) {
            periods[i] = "第 " + (i + 1) + " 节 · " + periodTime(i + 1);
        }
        Spinner periodSpinner = spinner(periods);
        periodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.period - 1)));
        form.addView(label("开始节次"));
        form.addView(periodSpinner, fieldParams());

        Spinner endPeriodSpinner = spinner(periods);
        endPeriodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.endPeriod - 1)));
        form.addView(label("结束节次"));
        form.addView(endPeriodSpinner, fieldParams());

        EditText nameInput = input("课程名称", draft.name);
        form.addView(label("课程"));
        form.addView(nameInput, fieldParams());

        EditText weeksInput = input("例如 1-6周,9-17周,1-16周单周", draft.weeks);
        form.addView(label("上课周"));
        form.addView(weeksInput, fieldParams());

        Button temporaryWeeksButton = new Button(this);
        temporaryWeeksButton.setText("临时课程：从本周开始上 N 周");
        temporaryWeeksButton.setAllCaps(false);
        temporaryWeeksButton.setTextColor(accentColor());
        temporaryWeeksButton.setTextSize(14);
        temporaryWeeksButton.setTypeface(appTypeface(Typeface.BOLD));
        temporaryWeeksButton.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        temporaryWeeksButton.setPadding(dp(12), 0, dp(12), 0);
        temporaryWeeksButton.setOnClickListener(view -> showTemporaryWeeksDialog(weeksInput));
        LinearLayout.LayoutParams temporaryWeeksParams = fieldParams();
        temporaryWeeksParams.setMargins(0, 0, 0, dp(10));
        form.addView(temporaryWeeksButton, temporaryWeeksParams);

        EditText roomInput = input("教室 / 地点", draft.room);
        form.addView(label("地点"));
        form.addView(roomInput, fieldParams());

        EditText teacherInput = input("老师", draft.teacher);
        form.addView(label("老师"));
        form.addView(teacherInput, fieldParams());

        final int[] selectedColor = {safeColor(draft.color)};
        final boolean[] colorManuallySelected = {isEditing};
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(label("颜色"));
        colorScroll.addView(colorRow);
        form.addView(colorScroll, fieldParams());
        renderColorChoices(colorRow, selectedColor, () -> colorManuallySelected[0] = true);

        daySpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.day = position;
            if (!colorManuallySelected[0]) {
                selectedColor[0] = chooseCourseColorForDay(draft.day, draft.id);
                renderColorChoices(colorRow, selectedColor, () -> colorManuallySelected[0] = true);
            }
        }));
        periodSpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.period = position + 1;
            if (draft.endPeriod < draft.period) {
                draft.endPeriod = draft.period;
                endPeriodSpinner.setSelection(draft.endPeriod - 1);
            }
        }));
        endPeriodSpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.endPeriod = position + 1;
            if (draft.endPeriod < draft.period) {
                draft.endPeriod = draft.period;
                endPeriodSpinner.setSelection(draft.endPeriod - 1);
            }
        }));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEditing ? "编辑课程" : "添加课程")
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                if (storageLocked) {
                    Toast.makeText(this, "加密数据未解锁，暂时不能保存新内容", Toast.LENGTH_LONG).show();
                    return;
                }
                String name = cleanField(nameInput.getText().toString());
                if (name.isEmpty()) {
                    Toast.makeText(this, "课程名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isEditing && courses.size() >= MAX_COURSES) {
                    Toast.makeText(this, "课程数量已达到上限", Toast.LENGTH_SHORT).show();
                    return;
                }
                draft.name = name;
                draft.weeks = cleanField(weeksInput.getText().toString());
                TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(draft.weeks);
                if (!weekResult.valid) {
                    weeksInput.requestFocus();
                    Toast.makeText(this, weekResult.error, Toast.LENGTH_LONG).show();
                    return;
                }
                draft.invalidWeeks = false;
                draft.room = cleanField(roomInput.getText().toString());
                draft.teacher = cleanField(teacherInput.getText().toString());
                draft.color = selectedColor[0];
                if (hasCourseConflict(draft)) {
                    Toast.makeText(this, "该节次和周次已有课程", Toast.LENGTH_SHORT).show();
                    return;
                }
                upsertCourse(draft);
                selectedDay = draft.day;
                saveCourses();
                render();
                dialog.dismiss();
            });

            if (isEditing) {
                Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (delete != null) {
                    delete.setTextColor(dangerColor());
                }
            }
        });

        if (isEditing) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "删除", (d, which) -> {
                for (int i = courses.size() - 1; i >= 0; i--) {
                    if (courses.get(i).id == editing.id) {
                        courses.remove(i);
                    }
                }
                saveCourses();
                render();
            });
        }

        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showTemporaryWeeksDialog(EditText weeksInput) {
        int currentWeek = currentTeachingWeek();
        int totalWeeks = calendarTotalWeeks();
        if (currentWeek <= 0 || currentWeek > totalWeeks) {
            Toast.makeText(this, "当前不在校历教学周内，无法设置临时周次", Toast.LENGTH_LONG).show();
            return;
        }

        EditText durationInput = new EditText(this);
        durationInput.setSingleLine(true);
        durationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durationInput.setText("1");
        durationInput.setSelectAllOnFocus(true);
        durationInput.setTextColor(primaryTextColor());
        durationInput.setHintTextColor(secondaryTextColor());
        durationInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        durationInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        durationInput.setBackground(elevatedCardBackground(cardColor(), dp(12)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("临时课程周数")
                .setMessage("从当前第 " + currentWeek + " 周开始，只上 N 周。")
                .setView(durationInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                int duration = parseInt(durationInput.getText().toString(), 0);
                if (duration <= 0) {
                    Toast.makeText(this, "周数必须大于 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                int endWeek = Math.min(totalWeeks, currentWeek + duration - 1);
                String weeksText = currentWeek == endWeek ? currentWeek + "周" : currentWeek + "-" + endWeek + "周";
                weeksInput.setText(weeksText);
                weeksInput.setSelection(weeksInput.getText().length());
                if (endWeek < currentWeek + duration - 1) {
                    Toast.makeText(this, "已按本学期最后一周截断为 " + weeksText, Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showSettingsDialogV2() {
        if (settingsOverlay != null && settingsOverlay.getParent() != null) {
            settingsOverlay.requestFocus();
            return;
        }
        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) {
            return;
        }
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(isDarkMode ? Color.argb(116, 0, 0, 0) : Color.argb(82, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        final Runnable[] dismissSettings = new Runnable[1];
        dismissSettings[0] = () -> {
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            if (settingsOverlay == overlay) {
                settingsOverlay = null;
            }
        };
        overlay.setOnClickListener(view -> dismissSettings[0].run());
        overlay.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                dismissSettings[0].run();
                return true;
            }
            return false;
        });

        LinearLayout glassPanel = new LinearLayout(this);
        glassPanel.setOrientation(LinearLayout.VERTICAL);
        glassPanel.setPadding(dp(20), dp(18), dp(20), dp(18));
        glassPanel.setBackground(glassPanelBackground(dp(26)));
        glassPanel.setClickable(true);
        glassPanel.setFocusable(true);
        glassPanel.setOnClickListener(view -> {
        });
        glassPanel.setElevation(dp(24));
        glassPanel.setTranslationZ(dp(3));
        glassPanel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(2), 0, 0, dp(10));

        TextView title = new TextView(this);
        title.setText("\u8bbe\u7f6e");
        title.setTextSize(22);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setTextColor(primaryTextColor());
        title.setIncludeFontPadding(false);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("\u5173\u95ed");
        close.setTextSize(13);
        close.setTypeface(appTypeface(Typeface.BOLD));
        close.setTextColor(accentColor());
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(dp(58));
        close.setMinHeight(dp(38));
        close.setPadding(dp(12), 0, dp(12), 0);
        close.setBackground(interactiveGlassSurfaceBackground(glassControlColor(), dp(18)));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> dismissSettings[0].run());
        titleRow.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        glassPanel.addView(titleRow);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(2), dp(4), dp(2), 0);
        scroller.addView(panel);

        panel.addView(settingsSectionLabel("\u8bfe\u8868"));
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, "\u5bfc\u5165 PDF \u8bfe\u8868", "\u4ece\u6587\u4ef6\u89e3\u6790\u8bfe\u7a0b\u3001\u5468\u6b21\u548c\u6559\u5ba4", accentColor(), view -> {
            dismissSettings[0].run();
            openPdfPicker();
        }), settingsRowParams());

        if (BuildConfig.ENABLE_TEST_COURSE) {
            panel.addView(settingsMenuRow(R.drawable.ic_bell, "\u6d4b\u8bd5\u8bfe\u524d\u901a\u77e5", "\u5728\u8c03\u8bd5\u5305\u4e2d\u7acb\u5373\u5f39\u51fa\u4e00\u6b21\u63d0\u9192", accentColor(), view -> {
                dismissSettings[0].run();
                sendDebugReminderTestNotification();
            }), settingsRowParams());
        }
        panel.addView(settingsMenuRow(
                R.drawable.ic_bell,
                "通知状态",
                notificationStatusText(),
                accentColor(),
                view -> {
                    dismissSettings[0].run();
                    openReminderSystemSettings();
                }
        ), settingsRowParams());

        panel.addView(settingsToggleRow(
                R.drawable.ic_bell,
                "\u8bfe\u7a0b\u63d0\u9192",
                "\u5f00\u542f\u540e\u6309\u8bfe\u8868\u5728\u4e0a\u8bfe\u524d\u53d1\u9001\u63d0\u9192\u901a\u77e5",
                accentColor(),
                notificationsEnabled(),
                (buttonView, isChecked) -> applyNotificationToggle(isChecked)
        ), settingsRowParams());

        panel.addView(settingsSectionLabel("\u65f6\u95f4"));
        panel.addView(settingsMenuRow(R.drawable.ic_clock_outline, "\u8bfe\u7a0b\u8282\u6b21\u8bbe\u7f6e", "\u8bbe\u7f6e\u7b2c 1 \u8282\u5230\u7b2c N \u8282\u7684\u65f6\u95f4", accentColor(), view -> {
            dismissSettings[0].run();
            showPeriodSettingsDialog();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_calendar_outline, "\u6821\u5386\u8bbe\u7f6e", "\u8bbe\u7f6e\u5f00\u5b66\u65e5\u548c\u6563\u5b66\u65e5", accentColor(), view -> {
            dismissSettings[0].run();
            showCalendarImportDialog(false);
        }), settingsRowParams());

        panel.addView(settingsSectionLabel("\u5916\u89c2"));
        panel.addView(settingsMenuRow(R.drawable.ic_palette_outline, "\u754c\u9762\u8272\u8c03", "\u9009\u62e9\u4e3b\u8272\u8c03\uff0c\u5168\u5c40\u751f\u6548", accentColor(), view -> {
            showAccentColorDialog();
        }), settingsRowParams());

        panel.addView(settingsSectionLabel("\u6570\u636e"));
        panel.addView(settingsMenuRow(R.drawable.ic_trash_outline, "\u5220\u9664\u5f53\u524d\u914d\u7f6e", "\u5220\u9664\u8bfe\u7a0b\u3001\u6821\u5386\u548c\u63d0\u9192\u6392\u7a0b", dangerColor(), view -> {
            dismissSettings[0].run();
            confirmDeleteCurrentConfiguration();
        }), settingsRowParams());

        glassPanel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(isCompactWidth() ? 28 : 56);
        FrameLayout.LayoutParams glassParams = new FrameLayout.LayoutParams(
                Math.min(availableWidth, dp(430)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlay.addView(glassPanel, glassParams);
        contentRoot.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        settingsOverlay = overlay;
        overlay.requestFocus();
    }

    private void showAccentColorDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(10), dp(4), dp(6));
        int[] selected = new int[]{accentPresetIndex()};

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("\u754c\u9762\u8272\u8c03")
                .setView(panel)
                .setNegativeButton("\u53d6\u6d88", null)
                .setPositiveButton("\u786e\u5b9a", null)
                .create();
        renderAccentChoices(panel, selected, dialog);
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (confirm != null) {
                confirm.setOnClickListener(view -> {
                    int index = selected[0];
                    if (index != accentPresetIndex()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(ACCENT_KEY, index).apply();
                        dialog.dismiss();
                        recreate();
                    } else {
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void renderAccentChoices(LinearLayout panel, int[] selected, AlertDialog dialog) {
        panel.removeAllViews();
        final int columns = 4;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(grid, gridParams);

        for (int i = 0; i < ACCENT_PRESETS.length; i++) {
            final int index = i;
            int color = ACCENT_PRESETS[i][0];
            boolean isSelected = index == selected[0];

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClickable(true);
            cell.setFocusable(true);
            android.graphics.drawable.StateListDrawable cellBg = new android.graphics.drawable.StateListDrawable();
            cellBg.addState(new int[]{android.R.attr.state_pressed},
                    buttonBackground(withAlpha(accentColor(), isDarkMode ? 44 : 26), dp(14)));
            cellBg.addState(new int[]{}, buttonBackground(Color.TRANSPARENT, dp(14)));
            cell.setBackground(cellBg);
            cell.setContentDescription(ACCENT_PRESET_NAMES[index]
                    + (isSelected ? "\uff0c\u5df2\u9009" : "")
                    + "\uff0c\u70b9\u6309\u9009\u62e9");
            cell.setOnClickListener(view -> {
                selected[0] = index;
                renderAccentChoices(panel, selected, dialog);
            });

            TextView swatch = new TextView(this);
            swatch.setText(isSelected ? "\u2713" : "");
            swatch.setTextColor(Color.WHITE);
            swatch.setTextSize(18);
            swatch.setTypeface(appTypeface(Typeface.BOLD));
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(swatchStateListBackground(color, isSelected));
            swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(swatch, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView name = new TextView(this);
            name.setText(ACCENT_PRESET_NAMES[index]);
            name.setTextSize(11);
            name.setTextColor(isSelected ? accentColor() : secondaryTextColor());
            name.setTypeface(appTypeface(isSelected ? Typeface.BOLD : Typeface.NORMAL));
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            nameParams.topMargin = dp(5);
            cell.addView(name, nameParams);

            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns, GridLayout.CENTER)
            );
            cellParams.setMargins(dp(6), dp(8), dp(6), dp(8));
            grid.addView(cell, cellParams);
        }
    }

    private TextView settingsSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(appTypeface(Typeface.BOLD));
        label.setLetterSpacing(0.08f);
        label.setTextColor(secondaryTextColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(6), dp(12), dp(6), dp(5));
        return label;
    }

    private LinearLayout settingsMenuRow(int iconResId, String titleText, String subtitleText, int tintColor, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(interactiveGlassSurfaceBackground(glassRowColor(), dp(18)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(titleText + "\uff0c" + subtitleText);
        row.setOnClickListener(listener);

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(buttonBackground(glassIconTintColor(tintColor), dp(14)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setImageTintList(ColorStateList.valueOf(tintColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconSurface.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconSurface, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(primaryTextColor());
        title.setTextSize(15);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(secondaryTextColor());
        subtitle.setTextSize(12);
        subtitle.setTypeface(appTypeface(Typeface.NORMAL));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(3), 0, 0);
        texts.addView(subtitle, subtitleParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(secondaryTextColor()));
        arrow.setAlpha(0.72f);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        return row;
    }

    private LinearLayout settingsToggleRow(int iconResId, String titleText, String subtitleText, int tintColor, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(interactiveGlassSurfaceBackground(glassRowColor(), dp(18)));

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(buttonBackground(glassIconTintColor(tintColor), dp(14)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setImageTintList(ColorStateList.valueOf(tintColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconSurface.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconSurface, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(primaryTextColor());
        title.setTextSize(15);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(secondaryTextColor());
        subtitle.setTextSize(12);
        subtitle.setTypeface(appTypeface(Typeface.NORMAL));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(3), 0, 0);
        texts.addView(subtitle, subtitleParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(checked);
        // Material 的 Switch 默认取主题色，而本项目主题是 M3 默认调色板（primary 是紫色），
        // 于是滑块会渲成紫色，和全 app 的蓝白体系不一致。这里显式指定滑块与轨道配色。
        int thumbOff = isDarkMode ? Color.rgb(120, 128, 140) : Color.rgb(196, 204, 218);
        int trackOff = isDarkMode ? Color.rgb(58, 66, 78) : Color.rgb(226, 232, 240);
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ accentColor(), thumbOff }));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} },
                new int[]{ withAlpha(accentColor(), 120), trackOff }));
        toggle.setContentDescription(titleText);
        toggle.setOnCheckedChangeListener(listener);
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 整行可点击切换，而不只是右侧的小开关
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(titleText + "，" + (checked ? "已开启" : "已关闭") + "，点按切换");
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));

        return row;
    }

    private LinearLayout.LayoutParams settingsRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private int dangerColor() {
        return isDarkMode ? Color.rgb(244, 128, 112) : Color.rgb(177, 67, 55);
    }

    private int dangerContainerColor() {
        return isDarkMode ? Color.rgb(60, 20, 20) : Color.rgb(254, 242, 242);
    }

    private int softTintColor(int color) {
        return mixColor(color, cardColor(), isDarkMode ? 0.78f : 0.86f);
    }

    /**
     * 弹窗面板底色。
     *
     * 原来浅色用的是半透明白 argb(226,255,255,255)（约 89% 不透明），
     * 结果背后 Dialog 窗口主题的紫色透了上来，实测面板渲成 #ECE6F0
     * ——绿通道最低，是明显的紫粉调，和全 app 的蓝色体系割裂。
     * 改成不透明的蓝白渐变，既杜绝透色，也和主屏的干净平面风一致。
     */
    private int glassPanelTopColor() {
        return isDarkMode ? Color.rgb(31, 36, 47) : Color.rgb(255, 255, 255);
    }

    private int glassPanelBottomColor() {
        return isDarkMode ? Color.rgb(23, 27, 36) : Color.rgb(240, 246, 254);
    }

    private int glassRowColor() {
        return isDarkMode ? Color.argb(56, 255, 255, 255) : Color.argb(126, 255, 255, 255);
    }

    private int glassControlColor() {
        return isDarkMode ? Color.argb(68, 255, 255, 255) : Color.argb(146, 255, 255, 255);
    }

    private int glassBorderColor() {
        return isDarkMode ? Color.argb(72, 255, 255, 255) : Color.argb(164, 255, 255, 255);
    }

    private int glassOuterEdgeColor() {
        return isDarkMode ? Color.argb(98, 255, 255, 255) : Color.argb(190, 255, 255, 255);
    }

    private int glassIconTintColor(int color) {
        return Color.argb(isDarkMode ? 72 : 50, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int mixColor(int from, int to, float amount) {
        float safeAmount = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) * (1f - safeAmount) + Color.red(to) * safeAmount);
        int green = Math.round(Color.green(from) * (1f - safeAmount) + Color.green(to) * safeAmount);
        int blue = Math.round(Color.blue(from) * (1f - safeAmount) + Color.blue(to) * safeAmount);
        return Color.rgb(red, green, blue);
    }

    /**
     * 统一 AlertDialog 的窗口背景与按钮配色。
     *
     * 这些弹窗走的是 MaterialAlertDialogBuilder，底色来自**主题的 colorSurface**；
     * 而本项目用的是 Material 3 默认调色板，colorSurface 自带紫色调 ——
     * 实测浅色 #ECE6F0、深色 #2B2930，两者**绿通道都最低**，是明显的紫粉调，
     * 和全 app 的蓝白体系割裂。
     *
     * 这里直接覆盖窗口背景为项目自己的面板样式，不去改主题本身：
     * 改主题会连带影响所有 Material 组件的默认取色，波及面比这大得多。
     * 一处修改覆盖全部 7 个 AlertDialog（课程编辑／周次选择／临时周／主题色／
     * 节次设置／节次规则／校历导入）。
     */
    private void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(glassPanelBackground(dp(24)));
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(accentColor());
            positive.setTypeface(appTypeface(Typeface.BOLD));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(positive == null ? accentColor() : secondaryTextColor());
            negative.setTypeface(appTypeface(Typeface.BOLD));
        }
    }

    private void showSettingsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("设置")
                .setView(panel)
                .setNegativeButton("关闭", null)
                .create();

        if (BuildConfig.ENABLE_TEST_COURSE) {
            Button testReminder = settingsActionButton("\u6d4b\u8bd5\u8bfe\u524d\u901a\u77e5", accentColor());
            testReminder.setOnClickListener(view -> {
                dialog.dismiss();
                sendDebugReminderTestNotification();
            });
            panel.addView(testReminder, fieldParams());
        }

        Button importPdf = settingsActionButton("导入 PDF 课表", accentColor());
        importPdf.setOnClickListener(view -> {
            dialog.dismiss();
            openPdfPicker();
        });
        panel.addView(importPdf, fieldParams());

        Button periodSettings = settingsActionButton("\u8bfe\u7a0b\u8282\u6b21\u8bbe\u7f6e", accentColor());
        periodSettings.setOnClickListener(view -> {
            dialog.dismiss();
            showPeriodSettingsDialog();
        });
        panel.addView(periodSettings, fieldParams());

        Button calendar = settingsActionButton("校历设置", accentColor());
        calendar.setOnClickListener(view -> {
            dialog.dismiss();
            showCalendarImportDialog(false);
        });
        panel.addView(calendar, fieldParams());

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.setMargins(0, dp(12), 0, dp(12));
        panel.addView(divider, dividerParams);

        Button deleteConfig = settingsActionButton("删除当前配置", dangerColor());
        deleteConfig.setOnClickListener(view -> {
            dialog.dismiss();
            confirmDeleteCurrentConfiguration();
        });
        panel.addView(deleteConfig, fieldParams());

        dialog.show();
    }

    private void showPeriodSettingsDialog() {
        periodCount();
        List<String> draft = new ArrayList<>(periodTimes);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        TextView hint = label("每条规则对应一天中的一节课，按从早到晚排列");
        panel.addView(hint);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        );
        scrollParams.weight = 1f;
        panel.addView(scrollView, scrollParams);

        Button addRule = settingsActionButton("添加节次规则", accentColor());
        LinearLayout.LayoutParams addRuleParams = fieldParams();
        addRuleParams.topMargin = dp(12);
        panel.addView(addRule, addRuleParams);

        Button resetDefault = settingsActionButton("恢复默认 1-11 节", secondaryTextColor());
        panel.addView(resetDefault, fieldParams());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("课程节次设置")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> renderPeriodRuleList(list, draft, refresh[0]);
        refresh[0].run();

        addRule.setOnClickListener(view -> {
            if (draft.size() >= MAX_PERIODS) {
                Toast.makeText(this, "最多只能设置 " + MAX_PERIODS + " 节", Toast.LENGTH_SHORT).show();
                return;
            }
            showPeriodRuleDialog(draft, -1, refresh[0]);
        });
        resetDefault.setOnClickListener(view -> {
            draft.clear();
            draft.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
            refresh[0].run();
        });

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String error = validatePeriodRules(draft);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return;
                }
                int maxUsedPeriod = maxUsedPeriod();
                if (draft.size() < maxUsedPeriod) {
                    Toast.makeText(this, "已有课程使用第 " + maxUsedPeriod + " 节，不能删到更少", Toast.LENGTH_LONG).show();
                    return;
                }
                if (savePeriodTimes(draft)) {
                    render();
                    scheduleClassReminderNotifications();
                    dialog.dismiss();
                }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void maybeShowReminderPopupFromIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SHOW_REMINDER_POPUP, false)) {
            return;
        }
        String name = intent.getStringExtra(EXTRA_REMINDER_COURSE_NAME);
        String time = intent.getStringExtra(EXTRA_REMINDER_COURSE_TIME);
        String room = intent.getStringExtra(EXTRA_REMINDER_COURSE_ROOM);
        int minutes = intent.getIntExtra(EXTRA_REMINDER_LEAD_MINUTES, LONG_CLASS_REMINDER_MINUTES);
        intent.removeExtra(EXTRA_SHOW_REMINDER_POPUP);
        Runnable popup = () -> showClassReminderPopup(name, time, room, minutes);
        uiHandler.postDelayed(
                () -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (activityResumed) {
                        popup.run();
                    } else {
                        pendingReminderPopup = popup;
                    }
                },
                350L
        );
    }

    private void maybeSendDebugReminderTestNotification(Intent intent) {
        if (!BuildConfig.ENABLE_TEST_COURSE
                || intent == null
                || !intent.getBooleanExtra(EXTRA_SEND_REMINDER_TEST_NOTIFICATION, false)) {
            return;
        }
        intent.removeExtra(EXTRA_SEND_REMINDER_TEST_NOTIFICATION);
        uiHandler.postDelayed(
                this::sendDebugReminderTestNotification,
                650L
        );
    }

    private void showClassReminderPopup(String name, String time, String room, int minutes) {
        String courseName = TextUtils.isEmpty(name) ? "\u4e0b\u4e00\u8282\u8bfe" : name.trim();
        StringBuilder message = new StringBuilder("\u8ddd\u79bb\u4e0a\u8bfe\u8fd8\u6709 " + minutes + " \u5206\u949f");
        if (!TextUtils.isEmpty(time)) {
            message.append("\n").append("\u65f6\u95f4\uff1a").append(time.trim());
        }
        if (!TextUtils.isEmpty(room)) {
            message.append("\n").append("\u5730\u70b9\uff1a").append(room.trim());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(courseName)
                .setMessage(message.toString())
                .setPositiveButton("\u77e5\u9053\u4e86", null)
                .show();
    }

    private void renderPeriodRuleList(LinearLayout list, List<String> draft, Runnable refresh) {
        list.removeAllViews();
        for (int i = 0; i < draft.size(); i++) {
            final int index = i;
            Button row = settingsActionButton("第 " + (i + 1) + " 节 · " + draft.get(i), primaryTextColor());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnClickListener(view -> showPeriodRuleDialog(draft, index, refresh));
            list.addView(row, fieldParams());
        }
    }

    private void showPeriodRuleDialog(List<String> draft, int index, Runnable refresh) {
        boolean editing = index >= 0;
        String value = editing ? draft.get(index) : suggestedNextPeriodTime(draft);
        String[] parts = value.split("-");

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("HH:mm", parts.length == 2 ? parts[0] : "");
        EditText endInput = input("HH:mm", parts.length == 2 ? parts[1] : "");
        form.addView(label("开始时间"));
        form.addView(startInput, fieldParams());
        form.addView(label("结束时间"));
        form.addView(endInput, fieldParams());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(editing ? "第 " + (index + 1) + " 节" : "添加节次规则")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        if (editing && draft.size() > 1) {
            builder.setNeutralButton("删除", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String range = periodRangeFromInputs(startInput, endInput);
                if (range == null) {
                    return;
                }
                if (editing) {
                    draft.set(index, range);
                } else {
                    draft.add(range);
                }
                refresh.run();
                dialog.dismiss();
            });
            Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (delete != null) {
                delete.setOnClickListener(view -> {
                    draft.remove(index);
                    refresh.run();
                    dialog.dismiss();
                });
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private String periodRangeFromInputs(EditText startInput, EditText endInput) {
        String start = normalizeClock(startInput.getText().toString());
        String end = normalizeClock(endInput.getText().toString());
        if (start == null || end == null) {
            Toast.makeText(this, "时间格式应为 HH:mm，例如 08:00", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (clockMinutes(end) <= clockMinutes(start)) {
            Toast.makeText(this, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
            return null;
        }
        return start + "-" + end;
    }

    private String normalizeClock(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        Matcher matcher = Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int hour = parseInt(matcher.group(1), -1);
        int minute = parseInt(matcher.group(2), -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private String suggestedNextPeriodTime(List<String> draft) {
        if (draft.isEmpty()) {
            return DEFAULT_PERIOD_TIMES[0];
        }
        String[] parts = draft.get(draft.size() - 1).split("-");
        int start = parts.length == 2 ? clockMinutes(parts[1]) + 10 : 8 * 60;
        if (start < 0 || start >= 23 * 60) {
            start = 8 * 60;
        }
        int end = Math.min(start + 45, 23 * 60 + 59);
        return clockText(start) + "-" + clockText(end);
    }

    private String clockText(int minutes) {
        int safe = clamp(minutes, 0, 23 * 60 + 59);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }

    private String validatePeriodRules(List<String> rules) {
        if (rules.isEmpty()) {
            return "至少需要保留 1 节课";
        }
        if (rules.size() > MAX_PERIODS) {
            return "最多只能设置 " + MAX_PERIODS + " 节";
        }
        int previousEnd = -1;
        for (int i = 0; i < rules.size(); i++) {
            String[] parts = rules.get(i).split("-");
            if (parts.length != 2) {
                return "第 " + (i + 1) + " 节格式不正确";
            }
            int start = clockMinutes(parts[0]);
            int end = clockMinutes(parts[1]);
            if (start < 0 || end <= start) {
                return "第 " + (i + 1) + " 节时间不正确";
            }
            if (previousEnd > start) {
                return "节次时间需要按从早到晚排列，且不能重叠";
            }
            previousEnd = end;
        }
        return null;
    }

    private int maxUsedPeriod() {
        int max = 1;
        for (Course course : courses) {
            if (!course.temporary) {
                max = Math.max(max, course.endPeriod);
            }
        }
        return max;
    }

    private void loadPeriodTimes() {
        periodTimes.clear();
        String encoded = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PERIOD_TIMES_KEY, null);
        if (encoded != null) {
            try {
                JSONArray array = new JSONArray(encoded);
                List<String> loaded = new ArrayList<>();
                for (int i = 0; i < array.length() && loaded.size() < MAX_PERIODS; i++) {
                    String value = array.optString(i, "").trim();
                    if (!value.isEmpty()) {
                        loaded.add(value);
                    }
                }
                if (validatePeriodRules(loaded) == null) {
                    periodTimes.addAll(loaded);
                }
            } catch (Exception ignored) {
                periodTimes.clear();
            }
        }
        if (periodTimes.isEmpty()) {
            periodTimes.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
        }
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断写入成败，不能改 apply
    private boolean savePeriodTimes(List<String> rules) {
        JSONArray array = new JSONArray();
        for (String rule : rules) {
            array.put(rule);
        }
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PERIOD_TIMES_KEY, array.toString())
                .commit();
        if (!committed) {
            Toast.makeText(this, "节次设置保存失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            return false;
        }
        periodTimes.clear();
        periodTimes.addAll(rules);
        Toast.makeText(this, "节次设置已保存", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void sendDebugReminderTestNotification() {
        if (!BuildConfig.ENABLE_TEST_COURSE) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ensureNotificationPermission();
            Toast.makeText(this, "\u8bf7\u5148\u5141\u8bb8\u901a\u77e5\u6743\u9650", Toast.LENGTH_SHORT).show();
            return;
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            Toast.makeText(this, "系统已关闭本应用通知，请先在通知设置中开启", Toast.LENGTH_LONG).show();
            openNotificationSettings();
            return;
        }
        Course course = new Course();
        int period = currentPeriod();
        if (period < 1) {
            period = nearestDisplayPeriod();
        }
        course.name = "\u8bfe\u524d\u901a\u77e5\u6d4b\u8bd5";
        course.period = period;
        course.endPeriod = period;
        course.room = "\u6a21\u62df\u5668\u6d4b\u8bd5";
        int minutes = reminderLeadMinutes(course);
        Intent testIntent = classReminderIntent(course);
        testIntent.putExtra(ClassReminderReceiver.EXTRA_DEBUG_REMINDER, true);
        new ClassReminderReceiver().onReceive(this, testIntent);
        showClassReminderPopup(course.name, periodRangeTime(course), course.room, minutes);
        Toast.makeText(this, "\u5df2\u53d1\u9001\u4e00\u6761\u6d4b\u8bd5\u901a\u77e5", Toast.LENGTH_SHORT).show();
    }

    private String notificationStatusText() {
        if (!notificationsEnabled()) {
            return "\u5df2\u5173\u95ed\uff0c\u53ef\u5728\u4e0b\u65b9\u5f00\u542f";
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "未授权，点击前往系统设置";
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return "系统通知服务不可用";
        }
        if (android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            return "已被系统关闭，点击恢复";
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
            if (channel != null && channel.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) {
                return "课程提醒频道已关闭，点击恢复";
            }
        }
        ReminderScheduler.Status status = ReminderScheduler.status(this);
        if (!status.submissionOk) {
            return "提醒规则已保存，但系统闹钟提交失败；点击检查设置";
        }
        if (status.pendingCourses <= 0 || status.nextTriggerAt <= 0L) {
            return "可用；当前没有待触发的课程提醒";
        }
        String nextTime = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new java.util.Date(status.nextTriggerAt));
        String precision = status.exactAlarmAllowed ? "精确" : "可能受系统节电策略延迟";
        return "可用；已安排 " + status.pendingCourses + " 门，下一次 " + nextTime + "（" + precision + "）";
    }

    private void openReminderSystemSettings() {
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean notificationsEnabled = manager != null
                && (android.os.Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled());
        boolean permissionGranted = android.os.Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean channelEnabled = true;
        if (manager != null && android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
            channelEnabled = channel == null
                    || channel.getImportance() != android.app.NotificationManager.IMPORTANCE_NONE;
        }
        ReminderScheduler.Status status = ReminderScheduler.status(this);
        if (permissionGranted && notificationsEnabled && channelEnabled
                && status.pendingCourses > 0
                && !status.exactAlarmAllowed
                && openExactAlarmSettings()) {
            return;
        }
        openNotificationSettings();
    }

    private boolean openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
            ));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ClassReminderReceiver.CHANNEL_ID);
        } else {
            intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("app_package", getPackageName())
                    .putExtra("app_uid", getApplicationInfo().uid);
        }
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Intent details = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(details);
        }
    }

    private Button settingsActionButton(String text, int textColor) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(appTypeface(Typeface.BOLD));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(50));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(14));
        button.setBackgroundColor(cardColor());
        button.setStrokeColor(ColorStateList.valueOf(borderColor()));
        button.setStrokeWidth(dp(1));
        button.setRippleColor(ColorStateList.valueOf(withAlpha(accentColor(), isDarkMode ? 72 : 36)));
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private void confirmDeleteCurrentConfiguration() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除当前配置")
                .setMessage("这会删除已保存的课程、校历设置和提醒排程。亮暗模式不会被删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteCurrentConfiguration())
                .show();
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断删除是否完全写入，不能改 apply
    private void deleteCurrentConfiguration() {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            cancelScheduledClassReminders(alarmManager);
        }
        ReminderScheduler.clear(this);
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(COURSES_KEY)
                .remove(LEGACY_COURSES_KEY)
                .remove(PERIOD_TIMES_KEY)
                .remove(CALENDAR_START_KEY)
                .remove(CALENDAR_END_KEY)
                .remove(CALENDAR_WEEKS_KEY)
                .remove(CALENDAR_NAME_KEY)
                .remove(CALENDAR_INITIALIZED_KEY)
                .remove(REMINDER_CODES_KEY)
                .commit();
        courses.clear();
        storageLocked = false;
        loadPeriodTimes();
        selectedDay = currentSchoolDay();
        viewingWeek = 0;
        addTemporaryDynamicIslandCourseIfDebug();
        render();
        Toast.makeText(this, committed ? "当前配置已删除" : "配置删除可能未完全写入", Toast.LENGTH_LONG).show();
    }

    private void openPdfPicker() {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，暂时不能导入", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_PDF);
    }

    private void maybePromptCalendarSetup() {
        boolean initialized = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(CALENDAR_INITIALIZED_KEY, containsLegacyCalendar());
        if (!initialized) {
            showCalendarImportDialog(true);
        }
    }

    private boolean containsLegacyCalendar() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.contains(CALENDAR_START_KEY) && prefs.contains(CALENDAR_END_KEY);
    }

    private void showCalendarImportDialog(boolean firstRun) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("YYYY-MM-DD", firstRun ? "" : formatDate(calendarStartMillis()));
        EditText endInput = input("YYYY-MM-DD", firstRun ? "" : formatDate(calendarEndMillis()));
        form.addView(label("开学日（第1周起点）"));
        form.addView(startInput, fieldParams());
        form.addView(label("散学日"));
        form.addView(endInput, fieldParams());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(firstRun ? "欢迎使用：请先设置校历" : "校历设置")
                .setMessage(firstRun
                        ? "课程周次、倒计时和提醒都依赖校历，请填写本学期的开学日和散学日。"
                        : "总周数会按开学日和散学日自动计算。")
                .setView(form)
                .setPositiveButton("保存", null);
        if (!firstRun) {
            builder.setNegativeButton("取消", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!firstRun);
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                Long start = parseDateMillis(startInput.getText().toString());
                Long end = parseDateMillis(endInput.getText().toString());
                if (start == null || end == null) {
                    Toast.makeText(this, "日期格式应为 YYYY-MM-DD", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (end < start) {
                    Toast.makeText(this, "散学日不能早于开学日", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveAcademicCalendar("手动设置校历", start, end);
                dialog.dismiss();
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断校历保存成败，不能改 apply
    private void saveAcademicCalendar(String name, long startMillis, long endMillis) {
        int totalWeeks = Math.max(1, (int) (TimetableRules.daysBetweenDates(startMillis, endMillis) / 7) + 1);
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(CALENDAR_START_KEY, startMillis)
                .putLong(CALENDAR_END_KEY, endMillis)
                .putInt(CALENDAR_WEEKS_KEY, totalWeeks)
                .putString(CALENDAR_NAME_KEY, name)
                .putBoolean(CALENDAR_INITIALIZED_KEY, true)
                .commit();
        if (!committed) {
            Toast.makeText(this, "校历保存失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            return;
        }
        int before = courses.size();
        removeExpiredCoursesIfNeeded(false);
        int removed = Math.max(0, before - courses.size());
        viewingWeek = 0;
        render();
        scheduleClassReminderNotifications();
        Toast.makeText(this, "校历已保存：共 " + totalWeeks + " 周，当前第 " + currentTeachingWeek() + " 周，已删除 " + removed + " 门过期课程", Toast.LENGTH_LONG).show();
    }

    private void importPdf(Uri uri) {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，暂时不能导入", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在解析 PDF 课表...", Toast.LENGTH_SHORT).show();
        final int maxPeriods = periodCount();
        new Thread(() -> {
            try {
                byte[] pdfBytes = readUriBytes(uri, MAX_PDF_BYTES);

                PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                        pdfBytes, maxPeriods, MAX_STREAM_BYTES, MAX_COURSES);
                android.util.Log.d("PDFImport", result.debugLog);
                runOnUiThread(() -> parsedAndShowCourses(result.courses));
            } catch (ImportException error) {
                runOnUiThread(() -> Toast.makeText(this, "导入错误: " + error.getMessage(), Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "PDF 导入失败: " + error.getClass().getSimpleName() + " - " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }


    private void parsedAndShowCourses(List<PdfCourseParser.ParsedCourse> parsedItems) {
        List<Course> parsedCourses = new ArrayList<>();
        for (PdfCourseParser.ParsedCourse item : parsedItems) {
            Course course = new Course();
            course.day = item.day;
            course.period = item.period;
            course.endPeriod = item.endPeriod;
            course.name = item.name;
            course.weeks = item.weeks;
            course.room = item.room;
            course.teacher = item.teacher;
            course.color = previewCourseColorForDay(parsedCourses, course.day);
            parsedCourses.add(course);
        }

        if (parsedCourses.isEmpty()) {
            Toast.makeText(this, "没有在 PDF 中识别到课程", Toast.LENGTH_LONG).show();
            return;
        }

        // 显示解析结果预览 + 调试信息
        StringBuilder preview = new StringBuilder();
        preview.append("【解析统计】\n");
        preview.append("成功解析: ").append(parsedCourses.size()).append(" 门课程\n\n");

        preview.append("【课程分布】\n");
        int[] perDay = new int[7];
        for (Course c : parsedCourses) {
            perDay[c.day]++;
        }
        for (int i = 0; i < 7; i++) {
            if (perDay[i] > 0) {
                preview.append(DAYS[i]).append(": ").append(perDay[i]).append("门  ");
            }
        }
        preview.append("\n\n");

        preview.append("【节次分布】\n");
        int[] perPeriod = new int[periodCount() + 1];
        for (Course c : parsedCourses) {
            for (int p = c.period; p <= c.endPeriod; p++) {
                if (p >= 1 && p <= periodCount()) perPeriod[p]++;
            }
        }
        for (int i = 1; i <= periodCount(); i++) {
            if (perPeriod[i] > 0) {
                preview.append("第").append(i).append("节: ").append(perPeriod[i]).append("次  ");
                if (i % 4 == 0) preview.append("\n");
            }
        }
        preview.append("\n\n");

        preview.append("【课程预览】\n");
        for (int i = 0; i < Math.min(5, parsedCourses.size()); i++) {
            Course c = parsedCourses.get(i);
            preview.append(String.format(Locale.CHINA, "%d. %s\n   %s 第%d-%d节 %s\n",
                i+1, c.name, DAYS[c.day], c.period, c.endPeriod, c.weeks));
        }
        if (parsedCourses.size() > 5) {
            preview.append("... 还有 ").append(parsedCourses.size() - 5).append(" 门");
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("步骤2: 课程解析结果")
            .setMessage(preview.toString())
            .setPositiveButton("确认导入", (dialog, which) -> {
                int imported = 0;
                int skipped = 0;
                long baseId = System.currentTimeMillis();
                StringBuilder skipLog = new StringBuilder();

                for (Course parsed : parsedCourses) {
                    if (parsed.period < 1 || parsed.endPeriod < parsed.period
                            || parsed.endPeriod > periodCount()) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 节次超出当前设置，请重新导入\n");
                        continue;
                    }
                    if (courses.size() >= MAX_COURSES) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 达到上限\n");
                        continue;
                    }
                    parsed.id = baseId + imported + skipped;
                    parsed.color = chooseCourseColorForDay(parsed.day, parsed.id);
                    if (hasImportedCourseDuplicate(parsed)) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 已存在\n");
                        continue;
                    }
                    if (hasCourseConflict(parsed)) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 与已有课程的节次和周次冲突\n");
                        continue;
                    }
                    courses.add(parsed);
                    imported++;
                }

                int removedExpired = 0;
                if (imported > 0) {
                    selectedDay = parsedCourses.get(0).day;
                    viewingWeek = 0;
                    saveCourses();
                    int beforeClean = courses.size();
                    removeExpiredCoursesIfNeeded(false);
                    removedExpired = Math.max(0, beforeClean - courses.size());
                    render();
                }

                String result = "导入 " + imported + " 门课程，跳过 " + skipped + " 门";
                if (removedExpired > 0) {
                    result += "，自动删除过期 " + removedExpired + " 门";
                }
                if (skipped > 0 && skipLog.length() > 0) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(result)
                        .setMessage("跳过原因:\n" + skipLog.toString())
                        .setPositiveButton("确定", null)
                        .show();
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws IOException, ImportException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new ImportException("无法读取 PDF 文件");
            }
            return readWithLimit(inputStream, maxBytes, "PDF 文件超过 5MB，已拒绝导入");
        }
    }

    private byte[] readWithLimit(InputStream inputStream, int maxBytes, String limitMessage) throws IOException, ImportException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) {
                throw new ImportException(limitMessage);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }






























    private void renderColorChoices(LinearLayout row, int[] selectedColor) {
        renderColorChoices(row, selectedColor, null);
    }

    private void renderColorChoices(LinearLayout row, int[] selectedColor, Runnable onSelect) {
        row.removeAllViews();
        for (int colorIndex = 0; colorIndex < COURSE_COLORS.length; colorIndex++) {
            int color = COURSE_COLORS[colorIndex];
            TextView swatch = new TextView(this);
            boolean selected = color == selectedColor[0];
            swatch.setText(selected ? "\u2713" : "");
            swatch.setTextColor(Color.WHITE);
            swatch.setTextSize(18);
            swatch.setTypeface(appTypeface(Typeface.BOLD));
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(swatchStateListBackground(color, selected));
            swatch.setContentDescription(COURSE_COLOR_NAMES[colorIndex] + (selected ? "\uff0c\u5df2\u9009" : ""));
            swatch.setOnClickListener(view -> {
                selectedColor[0] = color;
                if (onSelect != null) {
                    onSelect.run();
                }
                renderColorChoices(row, selectedColor, onSelect);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
            params.setMargins(0, 0, dp(7), 0);
            row.addView(swatch, params);
        }
    }

    private int chooseCourseColorForDay(int day, long ignoredCourseId) {
        List<Integer> usedColors = new ArrayList<>();
        for (Course course : courses) {
            if (course.temporary || course.id == ignoredCourseId || course.day != day) {
                continue;
            }
            usedColors.add(safeColor(course.color));
        }
        return bestCourseColor(usedColors);
    }

    private int previewCourseColorForDay(List<Course> parsed, int day) {
        List<Integer> usedColors = new ArrayList<>();
        for (Course course : parsed) {
            if (course.day == day) {
                usedColors.add(safeColor(course.color));
            }
        }
        return bestCourseColor(usedColors);
    }

    private int bestCourseColor(List<Integer> usedColors) {
        if (usedColors.isEmpty()) {
            return COURSE_COLORS[0];
        }
        int bestColor = COURSE_COLORS[0];
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0; i < COURSE_COLORS.length; i++) {
            int candidate = COURSE_COLORS[i];
            int exactCount = 0;
            float minDistance = Float.MAX_VALUE;
            for (int used : usedColors) {
                if (used == candidate) {
                    exactCount++;
                }
                minDistance = Math.min(minDistance, courseColorDistance(candidate, used));
            }
            float score = minDistance - exactCount * 220f - i * 0.01f;
            if (score > bestScore) {
                bestScore = score;
                bestColor = candidate;
            }
        }
        return bestColor;
    }

    private float courseColorDistance(int left, int right) {
        float[] leftHsv = new float[3];
        float[] rightHsv = new float[3];
        Color.colorToHSV(left, leftHsv);
        Color.colorToHSV(right, rightHsv);
        float hueDelta = Math.abs(leftHsv[0] - rightHsv[0]);
        hueDelta = Math.min(hueDelta, 360f - hueDelta) / 180f;
        float saturationDelta = Math.abs(leftHsv[1] - rightHsv[1]);
        float valueDelta = Math.abs(leftHsv[2] - rightHsv[2]);
        return hueDelta * 120f + saturationDelta * 38f + valueDelta * 26f;
    }

    private boolean rebalanceCourseColorsIfNeeded() {
        boolean changed = false;
        for (int day = 0; day < DAYS.length; day++) {
            List<Course> dayCourses = new ArrayList<>();
            for (Course course : courses) {
                if (!course.temporary && course.day == day) {
                    dayCourses.add(course);
                }
            }
            Collections.sort(dayCourses, (left, right) -> {
                int result = Integer.compare(left.period, right.period);
                if (result == 0) result = Integer.compare(left.endPeriod, right.endPeriod);
                if (result == 0) result = left.name.compareTo(right.name);
                return result;
            });

            List<Integer> usedColors = new ArrayList<>();
            for (Course course : dayCourses) {
                int safe = safeColor(course.color);
                if (courseColorTooCloseToAny(safe, usedColors)) {
                    safe = bestCourseColor(usedColors);
                }
                if (course.color != safe) {
                    course.color = safe;
                    changed = true;
                }
                usedColors.add(safe);
            }
        }
        return changed;
    }

    private boolean courseColorTooCloseToAny(int color, List<Integer> usedColors) {
        for (int used : usedColors) {
            if (courseColorDistance(color, used) < 30f) {
                return true;
            }
        }
        return false;
    }

    private void upsertCourse(Course draft) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == draft.id) {
                courses.set(i, draft.copy());
                return;
            }
        }
        courses.add(draft.copy());
    }

    private boolean hasCourseConflict(Course draft) {
        for (Course course : courses) {
            if (course.temporary || course.id == draft.id || course.day != draft.day) {
                continue;
            }
            if (periodsOverlap(course, draft) && weeksOverlap(course.weeks, draft.weeks)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasImportedCourseDuplicate(Course draft) {
        for (Course course : courses) {
            if (course.day == draft.day
                    && course.period == draft.period
                    && course.endPeriod == draft.endPeriod
                    && sameCourseText(course.name, draft.name)
                    && TimetableRules.sameWeekExpression(course.weeks, draft.weeks)
                    && sameCourseText(course.room, draft.room)
                    && sameCourseText(course.teacher, draft.teacher)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCourseText(String left, String right) {
        return TimetableRules.normalizedCourseText(cleanField(left == null ? "" : left))
                .equals(TimetableRules.normalizedCourseText(cleanField(right == null ? "" : right)));
    }

    private boolean periodsOverlap(Course left, Course right) {
        return left.period <= right.endPeriod && right.period <= left.endPeriod;
    }

    private boolean weeksOverlap(String left, String right) {
        return TimetableRules.weeksOverlap(left, right);
    }

    private boolean[] parseWeekSet(String value) {
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(value);
        if (!result.valid) {
            return new boolean[TimetableRules.MAX_WEEK + 1];
        }
        return result.explicit ? result.weeks : null;
    }

    private boolean removeExpiredCoursesIfNeeded(boolean showToast) {
        if (storageLocked) {
            return false;
        }
        int currentWeek = currentTeachingWeek();
        if (currentWeek <= 0 || currentWeek > calendarTotalWeeks()) {
            // 今天不在校历范围内：校历未生效或已过期（多半是还没换新学期校历）。
            // 此时按教学周判断"过期"会把新学期课程全部误删，必须跳过自动清理。
            return false;
        }
        int removed = 0;
        for (int i = courses.size() - 1; i >= 0; i--) {
            Course course = courses.get(i);
            if (!course.temporary && isCourseExpiredByTeachingWeek(course, currentWeek)) {
                courses.remove(i);
                removed++;
            }
        }
        if (removed <= 0) {
            return false;
        }
        saveCourses();
        if (showToast) {
            Toast.makeText(this, "已自动删除 " + removed + " 门上课周已结束的课程", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private boolean isCourseExpiredByTeachingWeek(Course course, int currentWeek) {
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(course.weeks);
        if (!result.valid || !result.explicit) {
            return false;
        }
        boolean[] weeks = result.weeks;
        int maxWeek = Math.max(currentWeek, calendarTotalWeeks() + 1);
        for (int week = currentWeek; week <= maxWeek && week < weeks.length; week++) {
            if (weeks[week]) {
                return false;
            }
        }
        return true;
    }

    private boolean courseOccursInTeachingWeek(Course course, int week) {
        if (course.temporary) {
            return true;
        }
        if (week <= 0 || week > calendarTotalWeeks()) {
            return false;
        }
        long courseDate = courseDateMillisForWeek(course.day, week);
        if (courseDate < calendarStartMillis() || courseDate > calendarEndMillis()) {
            return false;
        }
        if (course.invalidWeeks) {
            return true;
        }
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(course.weeks);
        if (!result.valid) {
            return false;
        }
        return !result.explicit || (week < result.weeks.length && result.weeks[week]);
    }

    private int currentTeachingWeek() {
        long start = calendarStartMillis();
        long today = startOfTodayMillis();
        if (today < start) {
            return 0;
        }
        if (today > calendarEndMillis()) {
            return calendarTotalWeeks() + 1;
        }
        return (int) (TimetableRules.daysBetweenDates(start, today) / 7) + 1;
    }

    private int calendarTotalWeeks() {
        long start = calendarStartMillis();
        long end = calendarEndMillis();
        if (end < start) {
            return 20;
        }
        return Math.max(1, (int) (TimetableRules.daysBetweenDates(start, end) / 7) + 1);
    }

    private long calendarStartMillis() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(CALENDAR_START_KEY, dateMillis(2026, Calendar.MARCH, 2));
    }

    private long calendarEndMillis() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(CALENDAR_END_KEY, dateMillis(2026, Calendar.JULY, 19));
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long dateMillis(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(millis));
    }

    private Long parseDateMillis(String value) {
        if (value == null || !value.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            java.util.Date date = format.parse(value.trim());
            return date == null ? null : startOfDayMillis(date.getTime());
        } catch (Exception ignored) {
            return null;
        }
    }

    private long startOfDayMillis(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** The sole UI entry point for teaching-week/day dates; delegates to existing rules. */
    private long displayedDateMillis(int week, int day) {
        return courseDateMillisForWeek(day, week);
    }

    private String displayedWeekRangeLabel(int week) {
        SimpleDateFormat format = new SimpleDateFormat("M.d", Locale.CHINA);
        return format.format(new java.util.Date(displayedDateMillis(week, 0)))
                + "–" + format.format(new java.util.Date(displayedDateMillis(week, 6)));
    }

    private long courseDateMillisForWeek(int schoolDay, int teachingWeek) {
        return TimetableRules.courseDateMillisForWeek(
                calendarStartMillis(),
                teachingWeek,
                schoolDay
        );
    }

    private long timeOnDateMillis(long dateMillis, int minutes) {
        if (minutes < 0) {
            return Long.MAX_VALUE;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private SummaryCourse nextUpcomingCourseFromToday() {
        long now = System.currentTimeMillis();
        Course next = null;
        long nextStartAt = Long.MAX_VALUE;
        int totalWeeks = Math.min(calendarTotalWeeks(), TimetableRules.MAX_WEEK);
        for (Course course : courses) {
            if (course.invalidWeeks) {
                continue;
            }
            if (course.temporary) {
                if (course.day != currentSchoolDay()) {
                    continue;
                }
                long startAt = timeOnDateMillis(startOfTodayMillis(), courseStartMinutes(course));
                if (startAt > now && startAt < nextStartAt) {
                    next = course;
                    nextStartAt = startAt;
                }
                continue;
            }
            for (int week = 1; week <= totalWeeks; week++) {
                if (!courseOccursInTeachingWeek(course, week)) {
                    continue;
                }
                long startAt = timeOnDateMillis(
                        courseDateMillisForWeek(course.day, week),
                        courseStartMinutes(course)
                );
                if (startAt > now && startAt < nextStartAt) {
                    next = course;
                    nextStartAt = startAt;
                }
            }
        }
        return next == null ? null : new SummaryCourse(next, next.day, nextStartAt);
    }

    private Course currentCourseInProgress() {
        Course current = null;
        for (Course course : courses) {
            if (courseTemporalState(course) != TimetableRules.TemporalState.IN_PROGRESS) {
                continue;
            }
            if (current == null || compareCourseTime(course, current) < 0) {
                current = course;
            }
        }
        return current;
    }

    private int compareCourseTime(Course left, Course right) {
        int leftStart = courseStartSeconds(left);
        int rightStart = courseStartSeconds(right);
        if (leftStart != rightStart) {
            return Integer.compare(leftStart, rightStart);
        }
        int leftEnd = courseEndSeconds(left);
        int rightEnd = courseEndSeconds(right);
        if (leftEnd != rightEnd) {
            return Integer.compare(leftEnd, rightEnd);
        }
        String leftName = left.name == null ? "" : left.name;
        String rightName = right.name == null ? "" : right.name;
        return leftName.compareTo(rightName);
    }

    private String periodRangeTime(Course course) {
        if (course.customStartMinutes >= 0 && course.customEndMinutes >= 0) {
            return clockText(course.customStartMinutes) + "-" + clockText(course.customEndMinutes);
        }
        int startIndex = clamp(course.period, 1, periodCount()) - 1;
        int endIndex = clamp(course.endPeriod, 1, periodCount()) - 1;
        String[] startParts = periodTimes.get(startIndex).split("-");
        String[] endParts = periodTimes.get(endIndex).split("-");
        if (startParts.length == 2 && endParts.length == 2) {
            return startParts[0] + "-" + endParts[1];
        }
        return periodTime(course.period);
    }

    private String periodTime(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return "时间未设置";
        }
        return periodTimes.get(index);
    }

    private int currentSchoolDay() {
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SUNDAY ? 6 : dayOfWeek - Calendar.MONDAY;
    }

    private String selectedDayDateLabel() {
        Calendar calendar = Calendar.getInstance();
        if (viewingWeek != 0) {
            calendar.setTimeInMillis(courseDateMillisForWeek(selectedDay, clamp(viewingWeek, 1, calendarTotalWeeks())));
        } else {
            int currentWeek = currentTeachingWeek();
            if (currentWeek >= 1 && currentWeek <= calendarTotalWeeks()) {
                calendar.setTimeInMillis(courseDateMillisForWeek(selectedDay, currentWeek));
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, selectedDay - currentSchoolDay());
            }
        }
        return new SimpleDateFormat("MMMM d", Locale.ENGLISH).format(calendar.getTime());
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(52));
        spinner.setPopupBackgroundDrawable(elevatedCardBackground(cardColor(), dp(14)));
        return spinner;
    }

    private void styleSpinnerText(View view, boolean dropdown) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView text = (TextView) view;
        text.setTextColor(primaryTextColor());
        text.setTextSize(dropdown ? 15 : 16);
        text.setTypeface(appTypeface(Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMinHeight(dp(dropdown ? 46 : 52));
        text.setPadding(dp(14), 0, dp(dropdown ? 14 : 42), 0);
        text.setBackground(dropdown
                ? buttonBackground(cardColor(), 0)
                : elevatedCardBackground(cardColor(), dp(14)));
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTypeface(appTypeface(Typeface.BOLD));
        label.setLetterSpacing(0.05f);
        label.setTextColor(secondaryTextColor());
        label.setPadding(0, dp(10), 0, dp(5));
        return label;
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setSingleLine(true);
        editText.setTextSize(16);
        editText.setTextColor(primaryTextColor());
        editText.setHintTextColor(secondaryTextColor());
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        editText.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_FIELD_LENGTH)});
        return editText;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private android.graphics.drawable.Drawable interactiveSurfaceBackground(int color, int radius) {
        return rippleBackground(color, withAlpha(accentColor(), isDarkMode ? 72 : 36), radius, borderColor());
    }

    private android.graphics.drawable.Drawable interactiveButtonBackground(int color, int radius) {
        return rippleBackground(color, Color.argb(64, 255, 255, 255), radius, Color.TRANSPARENT);
    }

    private android.graphics.drawable.Drawable interactiveGlassSurfaceBackground(int color, int radius) {
        return rippleBackground(color, withAlpha(accentColor(), isDarkMode ? 72 : 36), radius, glassBorderColor());
    }

    private android.graphics.drawable.Drawable rippleBackground(int contentColor, int rippleColor, int radius, int strokeColor) {
        GradientDrawable content = roundedSurface(contentColor, radius, strokeColor);
        GradientDrawable mask = roundedSurface(Color.WHITE, radius, Color.TRANSPARENT);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                content,
                mask
        );
    }

    private GradientDrawable roundedSurface(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable glassPanelBackground(int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{glassPanelTopColor(), glassPanelBottomColor()}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(2), glassOuterEdgeColor());
        return drawable;
    }

    private GradientDrawable buttonBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable colorSwatchBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        // 原来是"选中＝纯白 3dp / 未选中＝27% 白 1dp"，那是给深色底写的：
        // 弹窗面板本身是白色，白圈在白底上等于没有，选中哪一格完全看不出来。
        // 现在选中用主色圈、未选中用描边色，浅色主题下都能看清。
        drawable.setStroke(dp(selected ? 3 : 1), selected ? accentColor() : borderColor());
        return drawable;
    }

    private android.graphics.drawable.StateListDrawable swatchStateListBackground(int color, boolean selected) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, colorSwatchBackground(pressedButtonColor(color), selected));
        states.addState(new int[]{}, colorSwatchBackground(color, selected));
        return states;
    }

    private android.graphics.drawable.StateListDrawable interactiveTranslucentBackground(int color, int radius) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int alpha = Color.alpha(color);
        int pressed = Color.argb(Math.min(255, alpha + 32),
                Math.round(Color.red(color) * 0.88f),
                Math.round(Color.green(color) * 0.88f),
                Math.round(Color.blue(color) * 0.88f));
        states.addState(new int[]{android.R.attr.state_pressed}, buttonBackground(pressed, radius));
        states.addState(new int[]{}, buttonBackground(color, radius));
        return states;
    }

    private GradientDrawable cardBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), borderColor());
        return drawable;
    }

    private GradientDrawable completedCourseFilterBackground(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode
                ? Color.argb(102, 44, 48, 56)
                : Color.argb(92, 156, 163, 174));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable elevatedCardBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), borderColor());
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // 在支持的设备上添加轻微阴影效果
            drawable.setStroke(dp(1), borderColor());
        }
        return drawable;
    }

    private GradientDrawable elevatedButtonBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    /**
     * 页面背景：三段竖直渐变（brief §1 #F5F8FD → #F3F7FC → #F7F9FD）。
     * 三色差极小，目的只是去掉"一整片纯色"的平板感，让卡片像浮在很浅的冷色纸上。
     */
    private android.graphics.drawable.Drawable pageBackground() {
        int[] stops = isDarkMode
                ? new int[]{ Color.rgb(15, 18, 23), Color.rgb(13, 16, 22), Color.rgb(16, 19, 25) }
                : new int[]{ PAGE_TOP_LIGHT, PAGE_MID_LIGHT, PAGE_BOTTOM_LIGHT };
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, stops);
    }

    /** 渐变顶部色。状态栏/导航栏跟随它，避免和页面背景割裂出两种颜色。 */
    private int pageTopColor() {
        return isDarkMode ? Color.rgb(15, 18, 23) : PAGE_TOP_LIGHT;
    }

    private int bgColor() {
        return isDarkMode ? Color.rgb(15, 18, 23) : Color.rgb(245, 248, 252);
    }

    private int cardColor() {
        return isDarkMode ? Color.rgb(27, 31, 38) : Color.rgb(255, 255, 255);
    }

    private int segmentedTrackColor() {
        return isDarkMode ? Color.rgb(22, 26, 32) : Color.rgb(236, 242, 249);
    }

    private int pressedButtonColor(int color) {
        return mixColor(color, Color.BLACK, isDarkMode ? 0.12f : 0.08f);
    }

    private int primaryTextColor() {
        return isDarkMode ? Color.rgb(243, 246, 250) : Color.rgb(18, 24, 33);
    }

    private int secondaryTextColor() {
        return isDarkMode ? Color.rgb(164, 173, 184) : Color.rgb(91, 101, 114);
    }

    private int borderColor() {
        return isDarkMode ? Color.rgb(51, 59, 70) : Color.rgb(221, 228, 238);
    }

    private int accentColor() {
        return ACCENT_PRESETS[accentPresetIndex()][isDarkMode ? 1 : 0];
    }

    private int todayColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(accentColor(), hsv);
        hsv[0] = (hsv[0] + 42f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] + 0.05f);
        hsv[2] = isDarkMode ? Math.min(1f, hsv[2] + 0.22f) : Math.max(0.14f, hsv[2] - 0.10f);
        return Color.HSVToColor(hsv);
    }

    private int accentPresetIndex() {
        int saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(ACCENT_KEY, DEFAULT_ACCENT_PRESET);
        if (saved < 0 || saved >= ACCENT_PRESETS.length) {
            return DEFAULT_ACCENT_PRESET;
        }
        return saved;
    }

    static int accentColorFor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(ACCENT_KEY, DEFAULT_ACCENT_PRESET);
        int index = (saved >= 0 && saved < ACCENT_PRESETS.length) ? saved : DEFAULT_ACCENT_PRESET;
        boolean dark = isDarkModeFor(context, prefs);
        return ACCENT_PRESETS[index][dark ? 1 : 0];
    }

    private static boolean isDarkModeFor(Context context, SharedPreferences prefs) {
        int mode = prefs.getInt(THEME_KEY, 0);
        if (mode == 2) {
            return true;
        }
        if (mode == 1) {
            return false;
        }
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int accentContainerColor() {
        int accent = accentColor();
        return Color.argb(isDarkMode ? 46 : 26, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    private int tonalContainerColor() {
        return mixColor(accentColor(), cardColor(), 0.90f);
    }

    /**
     * 选中日期药丸色。
     *
     * 原来直接复用 tonalContainerColor()（10% 主色），实测得到 #EDEFFB，
     * 而效果图里选中日是清楚的浅蓝 #DDE8FE，浓度差了一倍，选中态看不出来。
     */
    private int selectedDayColor() {
        return mixColor(accentColor(), cardColor(), isDarkMode ? 0.72f : 0.80f);
    }

    private void loadCourses() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String encrypted = prefs.getString(COURSES_KEY, null);
        String raw;
        boolean migrateLegacy = false;
        boolean encryptedSource = encrypted != null;
        try {
            if (encryptedSource) {
                raw = secureStorage.decrypt(encrypted);
            } else {
                raw = prefs.getString(LEGACY_COURSES_KEY, "[]");
                migrateLegacy = !raw.equals("[]");
            }
        } catch (Exception error) {
            courses.clear();
            storageLocked = true;
            Toast.makeText(this, "课表数据解密失败，已阻止覆盖原数据", Toast.LENGTH_LONG).show();
            return;
        }

        courses.clear();
        try {
            JSONArray array = new JSONArray(raw);
            int invalidWeekCount = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Course course = new Course();
                course.id = object.optLong("id", System.currentTimeMillis() + i);
                course.day = clamp(object.optInt("day", 0), 0, DAYS.length - 1);
                course.period = clamp(object.optInt("period", 1), 1, periodCount());
                course.endPeriod = clamp(object.optInt("endPeriod", course.period), course.period, periodCount());
                course.name = cleanField(object.optString("name", ""));
                course.weeks = cleanField(object.optString("weeks", ""));
                course.invalidWeeks = !TimetableRules.parseWeeks(course.weeks).valid;
                if (course.invalidWeeks) {
                    invalidWeekCount++;
                }
                course.room = cleanField(object.optString("room", ""));
                course.teacher = cleanField(object.optString("teacher", ""));
                course.color = safeColor(object.optInt("color", COURSE_COLORS[i % COURSE_COLORS.length]));
                if (!course.name.isEmpty()) {
                    courses.add(course);
                }
                if (courses.size() >= MAX_COURSES) {
                    break;
                }
            }
            if (migrateLegacy) {
                saveCourses();
            }
            if (invalidWeekCount > 0) {
                Toast.makeText(
                        this,
                        "有 " + invalidWeekCount + " 门课程的上课周格式无法识别，请打开课程并修正；这些课程不会安排通知",
                        Toast.LENGTH_LONG
                ).show();
            }
        } catch (Exception ignored) {
            courses.clear();
            storageLocked = encryptedSource;
            Toast.makeText(this, "课表数据格式异常，已忽略损坏内容", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断课程数据写入成败，不能改 apply
    private void saveCourses() {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，已拒绝覆盖保存", Toast.LENGTH_LONG).show();
            return;
        }
        JSONArray array = new JSONArray();
        try {
            for (Course course : courses) {
                if (course.temporary) {
                    continue;
                }
                JSONObject object = new JSONObject();
                object.put("id", course.id);
                object.put("day", course.day);
                object.put("period", course.period);
                object.put("endPeriod", course.endPeriod);
                object.put("name", course.name);
                object.put("weeks", course.weeks);
                object.put("room", course.room);
                object.put("teacher", course.teacher);
                object.put("color", course.color);
                array.put(object);
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "课表序列化失败", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encrypted = secureStorage.encrypt(array.toString());
            boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(COURSES_KEY, encrypted)
                    .remove(LEGACY_COURSES_KEY)
                    .commit();
            if (!committed) {
                Toast.makeText(this, "课表写入失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            } else {
                scheduleClassReminderNotifications();
            }
        } catch (Exception error) {
            Toast.makeText(this, "课表加密保存失败", Toast.LENGTH_LONG).show();
        }
    }


    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_FIELD_LENGTH ? trimmed : trimmed.substring(0, MAX_FIELD_LENGTH);
    }

    private int safeColor(int color) {
        for (int allowed : COURSE_COLORS) {
            if (allowed == color) {
                return color;
            }
        }
        int closest = COURSE_COLORS[0];
        float bestDistance = Float.MAX_VALUE;
        for (int allowed : COURSE_COLORS) {
            float distance = rgbDistance(color, allowed);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = allowed;
            }
        }
        return closest;
    }

    private float rgbDistance(int left, int right) {
        int red = Color.red(left) - Color.red(right);
        int green = Color.green(left) - Color.green(right);
        int blue = Color.blue(left) - Color.blue(right);
        return red * red + green * green + blue * blue;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean animationsEnabled() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return android.animation.ValueAnimator.areAnimatorsEnabled();
        }
        try {
            return android.provider.Settings.Global.getFloat(
                    getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            ) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isCompactWidth() {
        return getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density < 430f;
    }

    private int contentSideMarginDp() {
        return isCompactWidth() ? 12 : CONTENT_SIDE_MARGIN_DP;
    }

    /**
     * Hero 角色占用的宽度（dp）。brief 要求角色占屏宽 35%~45%。
     * 「下一节」胶囊也用它做右侧留白，保证两者不会叠在一起。
     */
    private int heroReservedWidthDp(boolean compact) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        return Math.round(screenWidthDp * (compact ? 0.42f : 0.38f));
    }

    /** 中黑字重：Android 没有 semibold 字族，这里用系统自带的 medium。 */
    private Typeface appTypefaceMedium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /**
     * SemiBold。{@code Typeface.create(family, weight, italic)} 需要 API 28，
     * 本项目 minSdk 23，所以更低版本降级到 medium，避免整段文字回退成 Regular。
     */
    private Typeface appTypefaceSemiBold() {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false);
        }
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** 竖直双色渐变 + 圆角，用于「下一节」胶囊。 */
    private GradientDrawable verticalGradientBackground(int top, int bottom, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private Typeface appTypeface(int style) {
        // Use the platform's native typeface stack (Roboto + Noto Sans CJK)
        // so the UI matches native Android typography instead of a bundled font.
        return Typeface.create("sans-serif", style);
    }

    private interface PositionCallback {
        void onSelected(int position);
    }

    private static class SimpleSelectedListener implements AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;

        SimpleSelectedListener(PositionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private class CountdownRow extends LinearLayout {
        private final Course course;
        private final boolean compact;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        };
        private TextView plainView;
        private TextView prefixView;
        private TextView suffixView;
        private FrameLayout numberSlot;
        private String lastPhase = "";
        private String lastSecondsText;

        CountdownRow(Course course, boolean compact) {
            super(MainActivity.this);
            this.course = course;
            this.compact = compact;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBaselineAligned(false);
            setClipChildren(true);
            setClipToPadding(true);
            refresh();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refresh();
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            cancelChildViewAnimations(this);
            super.onDetachedFromWindow();
        }

        private void cancelChildViewAnimations(View view) {
            view.animate().cancel();
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    cancelChildViewAnimations(group.getChildAt(i));
                }
            }
        }

        private void refresh() {
            CountdownState state = remainingCountdownState(course);
            if (state.showSeconds) {
                showSecondsState(state);
            } else {
                showPlainState(state.text);
            }
            scheduleNextTick(state.showSeconds);
        }

        private void scheduleNextTick(boolean enabled) {
            removeCallbacks(tick);
            if (!enabled || !activityResumed || !isAttachedToWindow()) {
                return;
            }
            long delay = COUNTDOWN_REFRESH_MS - (System.currentTimeMillis() % COUNTDOWN_REFRESH_MS) + 36L;
            postDelayed(tick, delay);
        }

        private void pauseTicks() {
            removeCallbacks(tick);
            cancelChildViewAnimations(this);
        }

        private void showPlainState(String text) {
            lastSecondsText = null;
            if (plainView == null || getChildCount() != 1) {
                removeAllViews();
                prefixView = null;
                suffixView = null;
                numberSlot = null;
                plainView = createCountdownTextView(compact);
                addView(plainView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            plainView.setText(text);
        }

        private void showSecondsState(CountdownState state) {
            if (numberSlot == null || plainView != null || !TextUtils.equals(lastPhase, state.phase)) {
                rebuildSecondsLayout();
                lastPhase = state.phase;
            }
            prefixView.setText(state.prefix);
            suffixView.setText(state.suffix);

            String previous = lastSecondsText;
            String key = countdownKey(course, state.phase);
            CountdownSnapshot snapshot = previous == null ? recentCountdownSnapshot(key) : null;
            if (snapshot != null) {
                previous = snapshot.secondsText;
            }
            boolean animate = animationsEnabled()
                    && previous != null
                    && !TextUtils.equals(previous, state.secondsText);
            setNumberText(previous, state.secondsText, animate);
            lastSecondsText = state.secondsText;
            rememberCountdownSnapshot(key, state.secondsText);
        }

        private void rebuildSecondsLayout() {
            removeAllViews();
            plainView = null;
            prefixView = createCountdownTextView(compact);
            suffixView = createCountdownTextView(compact);
            numberSlot = new FrameLayout(MainActivity.this);
            numberSlot.setClipChildren(true);
            numberSlot.setClipToPadding(true);

            addView(prefixView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            addView(numberSlot, new LinearLayout.LayoutParams(
                    dp(compact ? 21 : 23),
                    dp(compact ? 20 : 22)
            ));
            addView(suffixView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        private void setNumberText(String previous, String current, boolean animate) {
            numberSlot.removeAllViews();
            LinearLayout digits = new LinearLayout(MainActivity.this);
            digits.setOrientation(LinearLayout.HORIZONTAL);
            digits.setGravity(Gravity.CENTER);
            digits.setClipChildren(true);
            digits.setClipToPadding(true);
            numberSlot.addView(digits, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            for (int i = 0; i < current.length(); i++) {
                FrameLayout digitSlot = new FrameLayout(MainActivity.this);
                digitSlot.setClipChildren(true);
                digitSlot.setClipToPadding(true);
                digits.addView(digitSlot, new LinearLayout.LayoutParams(
                        dp(compact ? 9 : 10),
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

                String previousDigit = previous != null && previous.length() == current.length()
                        ? String.valueOf(previous.charAt(i))
                        : null;
                String currentDigit = String.valueOf(current.charAt(i));
                boolean digitChanged = animate
                        && previousDigit != null
                        && !TextUtils.equals(previousDigit, currentDigit);
                setDigitText(digitSlot, previousDigit, currentDigit, digitChanged);
            }
        }

        private void setDigitText(FrameLayout digitSlot, String previous, String current, boolean animate) {
            TextView currentView = createCountdownNumberView();
            currentView.setText(current);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            );

            if (!animate || previous == null) {
                digitSlot.addView(currentView, params);
                return;
            }

            TextView previousView = createCountdownNumberView();
            previousView.setText(previous);
            digitSlot.addView(previousView, params);

            currentView.setTranslationY(dp(compact ? 15 : 17));
            currentView.setAlpha(0.55f);
            currentView.setScaleY(0.96f);
            digitSlot.addView(currentView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            previousView.animate()
                    .translationY(-dp(compact ? 13 : 15))
                    .alpha(0f)
                    .scaleY(0.96f)
                    .setDuration(COUNTDOWN_ANIMATION_MS - 40L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
                    .withEndAction(() -> digitSlot.removeView(previousView))
                    .start();
            currentView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleY(1f)
                    .setDuration(COUNTDOWN_ANIMATION_MS)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                    .start();
        }

        private TextView createCountdownNumberView() {
            TextView text = createCountdownTextView(compact);
            text.setGravity(Gravity.CENTER);
            text.setIncludeFontPadding(false);
            return text;
        }
    }

    private static class CountdownState {
        final boolean showSeconds;
        final String text;
        final String prefix;
        final String secondsText;
        final String suffix;
        final String phase;
        final int remainingSeconds;

        private CountdownState(
                boolean showSeconds,
                String text,
                String prefix,
                String secondsText,
                String suffix,
                String phase,
                int remainingSeconds
        ) {
            this.showSeconds = showSeconds;
            this.text = text;
            this.prefix = prefix;
            this.secondsText = secondsText;
            this.suffix = suffix;
            this.phase = phase;
            this.remainingSeconds = remainingSeconds;
        }

        static CountdownState plain(String text) {
            return new CountdownState(false, text, "", "", "", "", -1);
        }

        static CountdownState seconds(String prefix, String secondsText, String suffix, String phase, int remainingSeconds) {
            return new CountdownState(true, "", prefix, secondsText, suffix, phase, remainingSeconds);
        }
    }

    private static class CountdownSnapshot {
        final String secondsText;
        final long updatedAtMillis;

        CountdownSnapshot(String secondsText, long updatedAtMillis) {
            this.secondsText = secondsText;
            this.updatedAtMillis = updatedAtMillis;
        }
    }

    private static class SummaryCourse {
        final Course course;
        final int day;
        final long startAtMillis;

        SummaryCourse(Course course, int day, long startAtMillis) {
            this.course = course;
            this.day = day;
            this.startAtMillis = startAtMillis;
        }
    }

    private final class CourseProgressView extends View {
        private final Course course;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        CourseProgressView(Course course) {
            super(MainActivity.this);
            this.course = course;
            // 当前课是浅底，白色叠加层会看不见；改成主色淡染，进度越深颜色越明显
            paint.setColor(withAlpha(accentColor(), isDarkMode ? 44 : 24));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float progress = Math.max(0f, Math.min(1f, courseProgress(course)));
            if (progress > 0f) {
                canvas.drawRect(0f, 0f, getWidth() * progress, getHeight(), paint);
            }
        }
    }

    private static class BoundedMaterialCardView extends MaterialCardView {
        private int maxHeightPx;

        BoundedMaterialCardView(android.content.Context context) {
            super(context);
        }

        void setMaxHeightPx(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (maxHeightPx > 0 && getMeasuredHeight() > maxHeightPx) {
                int cappedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        }
    }

    private static class Course {
        long id;
        int day;
        int period;
        int endPeriod = 1;
        String name = "";
        String weeks = "";
        String room = "";
        String teacher = "";
        int color = COURSE_COLORS[0];
        boolean temporary = false;
        boolean invalidWeeks = false;
        int customStartMinutes = -1;
        int customEndMinutes = -1;

        String periodLabel() {
            if (endPeriod > period) {
                return "第" + period + "-" + endPeriod + "节";
            }
            return "第" + period + "节";
        }

        Course copy() {
            Course copy = new Course();
            copy.id = id;
            copy.day = day;
            copy.period = period;
            copy.endPeriod = endPeriod;
            copy.name = name;
            copy.weeks = weeks;
            copy.room = room;
            copy.teacher = teacher;
            copy.color = color;
            copy.temporary = temporary;
            copy.invalidWeeks = invalidWeeks;
            copy.customStartMinutes = customStartMinutes;
            copy.customEndMinutes = customEndMinutes;
            return copy;
        }

        private static String join(List<String> parts, String separator) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    builder.append(separator);
                }
                builder.append(parts.get(i));
            }
            return builder.toString();
        }
    }
}

