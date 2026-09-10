package com.example.meinstundenplan;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TimetableRules {
    static final int MAX_WEEK = 30;

    private static final Pattern WEEK_RANGE_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,2})(?:\\s*(?:-|~|～|—|–|至|到)\\s*(\\d{1,2}))?\\s*周?"
    );
    private static final Pattern PERIOD_WEEKS_PATTERN = Pattern.compile(
            "(?:第\\s*|[（(]?)([1-9]\\d?)(?:\\s*(?:-|~|～|—|–|至|到)\\s*([1-9]\\d?))?\\s*节\\s*[）)]?\\s*([^/\\r\\n]*)"
    );

    enum TemporalState {
        UNAVAILABLE,
        UPCOMING,
        REMINDER_WINDOW,
        IN_PROGRESS,
        COMPLETED
    }

    private TimetableRules() {
    }

    static TemporalState temporalState(
            boolean occursThisWeek,
            int courseDay,
            int today,
            int startSeconds,
            int endSeconds,
            int nowSeconds,
            int reminderLeadSeconds
    ) {
        return temporalStateForDateComparison(
                occursThisWeek,
                Integer.compare(courseDay, today),
                startSeconds,
                endSeconds,
                nowSeconds,
                reminderLeadSeconds
        );
    }

    static TemporalState temporalStateForDateComparison(
            boolean occursThisWeek,
            int dateComparison,
            int startSeconds,
            int endSeconds,
            int nowSeconds,
            int reminderLeadSeconds
    ) {
        if (!occursThisWeek || startSeconds < 0 || endSeconds <= startSeconds) {
            return TemporalState.UNAVAILABLE;
        }
        if (dateComparison < 0) {
            return TemporalState.COMPLETED;
        }
        if (dateComparison > 0) {
            return TemporalState.UPCOMING;
        }
        if (nowSeconds >= endSeconds) {
            return TemporalState.COMPLETED;
        }
        if (nowSeconds >= startSeconds) {
            return TemporalState.IN_PROGRESS;
        }
        int reminderStart = Math.max(0, startSeconds - Math.max(0, reminderLeadSeconds));
        return nowSeconds >= reminderStart ? TemporalState.REMINDER_WINDOW : TemporalState.UPCOMING;
    }

    static WeekParseResult parseWeeks(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new WeekParseResult(true, false, new boolean[MAX_WEEK + 1], null);
        }
        if (value.trim().matches("(?:每周|全周|全部周|所有周)")) {
            return new WeekParseResult(true, false, new boolean[MAX_WEEK + 1], null);
        }

        String normalized = normalizeWeekText(value);
        boolean[] weeks = new boolean[MAX_WEEK + 1];
        Matcher matcher = WEEK_RANGE_PATTERN.matcher(normalized);
        boolean found = false;
        StringBuilder unmatched = new StringBuilder(normalized);
        while (matcher.find()) {
            int start = parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : parseInt(matcher.group(2));
            if (start < 1 || end < 1 || start > MAX_WEEK || end > MAX_WEEK) {
                return WeekParseResult.invalid("周次应在 1-" + MAX_WEEK + " 之间");
            }
            if (end < start) {
                return WeekParseResult.invalid("周次范围的结束周不能早于开始周");
            }
            String context = tokenContext(normalized, matcher.start(), matcher.end());
            if (context.contains("单") && context.contains("双")) {
                return WeekParseResult.invalid("同一周次范围不能同时标记为单周和双周");
            }
            boolean oddOnly = context.contains("单");
            boolean evenOnly = context.contains("双");
            boolean tokenSelected = false;
            for (int week = start; week <= end; week++) {
                if (oddOnly && week % 2 == 0) {
                    continue;
                }
                if (evenOnly && week % 2 != 0) {
                    continue;
                }
                weeks[week] = true;
                tokenSelected = true;
            }
            if (!tokenSelected) {
                return WeekParseResult.invalid("周次范围与单周/双周条件不匹配");
            }
            for (int i = matcher.start(); i < matcher.end(); i++) {
                unmatched.setCharAt(i, ' ');
            }
            clearParityMarkers(unmatched, normalized, matcher.start(), matcher.end());
            found = true;
        }

        String remainder = unmatched.toString()
                .replaceAll("[\\s,，;；、/|及和()（）\\[\\]第周次]+", "");
        if (!found || !remainder.isEmpty()) {
            return WeekParseResult.invalid("无法识别上课周，请使用如 1-6周、9-17周 或 1-16周单周");
        }
        return new WeekParseResult(true, true, weeks, null);
    }

    static PeriodMatch matchPeriodWeeks(String text, int maxPeriods) {
        if (text == null) {
            return PeriodMatch.notMatched();
        }
        Matcher matcher = PERIOD_WEEKS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return PeriodMatch.notMatched();
        }
        int start = parseInt(matcher.group(1));
        int end = matcher.group(2) == null ? start : parseInt(matcher.group(2));
        String trailingText = matcher.group(3) == null ? "" : matcher.group(3);
        boolean valid = start >= 1 && end >= start && end <= maxPeriods;
        return new PeriodMatch(true, valid, start, end, trailingText);
    }

    static boolean weeksOverlap(String left, String right) {
        WeekParseResult leftResult = parseWeeks(left);
        WeekParseResult rightResult = parseWeeks(right);
        if (!leftResult.valid || !rightResult.valid) {
            return true;
        }
        if (!leftResult.explicit || !rightResult.explicit) {
            return true;
        }
        for (int week = 1; week <= MAX_WEEK; week++) {
            if (leftResult.weeks[week] && rightResult.weeks[week]) {
                return true;
            }
        }
        return false;
    }

    static boolean sameWeekExpression(String left, String right) {
        WeekParseResult leftResult = parseWeeks(left);
        WeekParseResult rightResult = parseWeeks(right);
        if (!leftResult.valid || !rightResult.valid || leftResult.explicit != rightResult.explicit) {
            return false;
        }
        if (!leftResult.explicit) {
            return true;
        }
        for (int week = 1; week <= MAX_WEEK; week++) {
            if (leftResult.weeks[week] != rightResult.weeks[week]) {
                return false;
            }
        }
        return true;
    }

    static String normalizedCourseText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static long[] reminderTriggerTimes(
            long calendarStartMillis,
            long calendarEndMillis,
            int totalWeeks,
            int courseDay,
            int startMinutes,
            int leadMinutes,
            boolean[] explicitWeeks,
            long afterMillis
    ) {
        if (totalWeeks < 1 || courseDay < 0 || courseDay > 6
                || startMinutes < 0 || startMinutes >= 24 * 60) {
            return new long[0];
        }
        int safeWeeks = Math.min(totalWeeks, MAX_WEEK);
        List<Long> triggers = new ArrayList<>();
        for (int week = 1; week <= safeWeeks; week++) {
            if (explicitWeeks != null
                    && (week >= explicitWeeks.length || !explicitWeeks[week])) {
                continue;
            }
            Calendar target = Calendar.getInstance();
            long courseDate = courseDateMillisForWeek(calendarStartMillis, week, courseDay);
            if (courseDate < calendarStartMillis || courseDate > calendarEndMillis) {
                continue;
            }
            target.setTimeInMillis(courseDate);
            target.set(Calendar.HOUR_OF_DAY, startMinutes / 60);
            target.set(Calendar.MINUTE, startMinutes % 60);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);
            target.add(Calendar.MINUTE, -Math.max(0, leadMinutes));
            if (target.getTimeInMillis() > afterMillis) {
                triggers.add(target.getTimeInMillis());
            }
        }
        long[] result = new long[triggers.size()];
        for (int i = 0; i < triggers.size(); i++) {
            result[i] = triggers.get(i);
        }
        return result;
    }

    static long courseDateMillisForWeek(long calendarStartMillis, int teachingWeek, int courseDay) {
        if (teachingWeek < 1 || courseDay < 0 || courseDay > 6) {
            return -1L;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(calendarStartMillis);
        int startDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int startSchoolDay = startDayOfWeek == Calendar.SUNDAY
                ? 6
                : startDayOfWeek - Calendar.MONDAY;
        int dayOffset = (courseDay - startSchoolDay + 7) % 7;
        calendar.add(Calendar.DAY_OF_YEAR, (teachingWeek - 1) * 7 + dayOffset);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    static long nextTriggerAfter(long[] triggerTimes, long afterMillis) {
        if (triggerTimes == null) {
            return -1L;
        }
        for (long triggerTime : triggerTimes) {
            if (triggerTime > afterMillis) {
                return triggerTime;
            }
        }
        return -1L;
    }

    private static String normalizeWeekText(String value) {
        return value.trim()
                .replace('－', '-')
                .replace('﹣', '-')
                .replace('〜', '～');
    }

    private static String tokenContext(String value, int start, int end) {
        int left = start;
        while (left > 0 && !isSeparator(value.charAt(left - 1))) {
            left--;
        }
        int right = end;
        while (right < value.length() && !isSeparator(value.charAt(right))) {
            right++;
        }
        return value.substring(left, right);
    }

    private static void clearParityMarkers(StringBuilder unmatched, String value, int start, int end) {
        int left = start;
        while (left > 0 && !isSeparator(value.charAt(left - 1))) {
            left--;
        }
        int right = end;
        while (right < value.length() && !isSeparator(value.charAt(right))) {
            right++;
        }
        for (int i = left; i < right; i++) {
            char marker = value.charAt(i);
            if (marker == '单' || marker == '双') {
                unmatched.setCharAt(i, ' ');
            }
        }
    }

    private static boolean isSeparator(char value) {
        return value == ',' || value == '，'
                || value == ';' || value == '；'
                || value == '、' || value == '/' || value == '及' || value == '和'
                || value == '\n' || value == '\r'
                || value == '|';
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return -1;
        }
    }

    static final class WeekParseResult {
        final boolean valid;
        final boolean explicit;
        final boolean[] weeks;
        final String error;

        WeekParseResult(boolean valid, boolean explicit, boolean[] weeks, String error) {
            this.valid = valid;
            this.explicit = explicit;
            this.weeks = weeks;
            this.error = error;
        }

        static WeekParseResult invalid(String error) {
            return new WeekParseResult(false, false, new boolean[MAX_WEEK + 1], error);
        }
    }

    static final class PeriodMatch {
        final boolean matched;
        final boolean valid;
        final int startPeriod;
        final int endPeriod;
        final String trailingText;

        PeriodMatch(boolean matched, boolean valid, int startPeriod, int endPeriod, String trailingText) {
            this.matched = matched;
            this.valid = valid;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.trailingText = trailingText;
        }

        static PeriodMatch notMatched() {
            return new PeriodMatch(false, false, -1, -1, "");
        }
    }
}
