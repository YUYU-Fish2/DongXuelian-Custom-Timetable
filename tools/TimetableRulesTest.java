package com.example.meinstundenplan;

import java.util.Calendar;

public final class TimetableRulesTest {
    public static void main(String[] args) {
        testTemporalStateBoundaries();
        testWeekMatching();
        testTextMatching();
        testPeriodMatching();
        testReminderChain();
        System.out.println("TimetableRulesTest: all checks passed");
    }

    private static void testTemporalStateBoundaries() {
        int start = 8 * 3600;
        int end = start + 45 * 60;
        int lead = 20 * 60;
        expect(TimetableRules.TemporalState.UPCOMING,
                TimetableRules.temporalState(true, 0, 0, start, end, start - lead - 1, lead));
        expect(TimetableRules.TemporalState.REMINDER_WINDOW,
                TimetableRules.temporalState(true, 0, 0, start, end, start - lead, lead));
        expect(TimetableRules.TemporalState.IN_PROGRESS,
                TimetableRules.temporalState(true, 0, 0, start, end, start, lead));
        expect(TimetableRules.TemporalState.COMPLETED,
                TimetableRules.temporalState(true, 0, 0, start, end, end, lead));
        expect(TimetableRules.TemporalState.UNAVAILABLE,
                TimetableRules.temporalState(false, 0, 0, start, end, start, lead));
        expect(TimetableRules.TemporalState.COMPLETED,
                TimetableRules.temporalState(true, 0, 1, start, end, 0, lead));
        expect(TimetableRules.TemporalState.UPCOMING,
                TimetableRules.temporalState(true, 2, 1, start, end, 23 * 3600, lead));
    }

    private static void testWeekMatching() {
        TimetableRules.WeekParseResult ranges = TimetableRules.parseWeeks("第1-6周，9至17周");
        check(ranges.valid && ranges.explicit, "range expression should be valid");
        check(ranges.weeks[1] && ranges.weeks[6] && !ranges.weeks[7]
                && ranges.weeks[9] && ranges.weeks[17], "range membership is wrong");

        TimetableRules.WeekParseResult odd = TimetableRules.parseWeeks("1-16周单周");
        check(odd.valid && odd.weeks[1] && !odd.weeks[2] && odd.weeks[15] && !odd.weeks[16],
                "odd-week matching is wrong");

        TimetableRules.WeekParseResult even = TimetableRules.parseWeeks("双周 2～16周");
        check(even.valid && even.weeks[2] && !even.weeks[3] && even.weeks[16],
                "even-week matching is wrong");

        check(!TimetableRules.parseWeeks("6-1周").valid, "reverse range must be rejected");
        check(!TimetableRules.parseWeeks("下半学期").valid, "unknown expression must be rejected");
        check(TimetableRules.parseWeeks("1-6周及9-17周").valid,
                "Chinese conjunctions should separate week ranges");
        check(!TimetableRules.parseWeeks("1-16周，双周").valid,
                "a detached odd/even qualifier must be rejected");
        check(!TimetableRules.parseWeeks("1-16周单双周").valid,
                "contradictory odd/even qualifiers must be rejected");
        check(!TimetableRules.parseWeeks("2周单周").valid,
                "an odd-only filter that selects no week must be rejected");
        check(!TimetableRules.parseWeeks("1周双周").valid,
                "an even-only filter that selects no week must be rejected");
        check(TimetableRules.weeksOverlap("1-16周单周", "2-16周双周") == false,
                "odd and even ranges must not conflict");
        check(TimetableRules.weeksOverlap("", "2-16周双周"),
                "blank week expression means every teaching week");
        check(TimetableRules.weeksOverlap("每周", "2-16周双周"),
                "the every-week alias should mean every teaching week");
        check(TimetableRules.weeksOverlap("损坏周次", "2-16周双周"),
                "an unknown legacy week expression must conflict conservatively");
        check(TimetableRules.sameWeekExpression("第1至6周", "1-6周"),
                "equivalent week expressions should match");
    }

    private static void testTextMatching() {
        String left = TimetableRules.normalizedCourseText("Data   Structures");
        String right = TimetableRules.normalizedCourseText(" data structures ");
        check(left.equals(right), "course matching should ignore case and repeated whitespace");
    }

    private static void testPeriodMatching() {
        TimetableRules.PeriodMatch match = TimetableRules.matchPeriodWeeks("第9至10节 1-16周单周", 11);
        check(match.matched && match.valid && match.startPeriod == 9 && match.endPeriod == 10,
                "period matcher should support Chinese range separators");
        check(match.trailingText.contains("1-16周单周"), "week suffix should be preserved");
        check(!TimetableRules.matchPeriodWeeks("第12节 1-16周", 11).valid,
                "periods outside configured rules must be rejected");
        check(!TimetableRules.matchPeriodWeeks("第10-9节", 11).valid,
                "reverse period ranges must be rejected");
        check(!TimetableRules.matchPeriodWeeks("高等数学", 11).matched,
                "ordinary course names must not match period metadata");
    }

    private static void testReminderChain() {
        Calendar start = Calendar.getInstance();
        start.set(2026, Calendar.MARCH, 2, 0, 0, 0); // Monday
        start.set(Calendar.MILLISECOND, 0);
        boolean[] weeks = TimetableRules.parseWeeks("1周,3周").weeks;
        long[] triggers = TimetableRules.reminderTriggerTimes(
                start.getTimeInMillis(),
                start.getTimeInMillis() + 20L * 24L * 60L * 60L * 1000L,
                3, 2, 8 * 60, 30, weeks, start.getTimeInMillis() - 1
        );
        check(triggers.length == 2, "week gaps should produce two reminder occurrences");
        check(triggers[1] > triggers[0], "reminders must be ordered");
        check(TimetableRules.nextTriggerAfter(triggers, triggers[0]) == triggers[1],
                "a delivered alarm must advance to the next occurrence");
        check(TimetableRules.nextTriggerAfter(triggers, triggers[1]) == -1L,
                "the chain must terminate after the final occurrence");

        Calendar wednesdayStart = Calendar.getInstance();
        wednesdayStart.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        wednesdayStart.set(Calendar.MILLISECOND, 0);
        long followingMonday = TimetableRules.courseDateMillisForWeek(
                wednesdayStart.getTimeInMillis(), 1, 0
        );
        Calendar monday = Calendar.getInstance();
        monday.setTimeInMillis(followingMonday);
        check(monday.get(Calendar.DAY_OF_MONTH) == 9,
                "a non-Monday calendar start must map week-one Monday into the future");
        long[] clipped = TimetableRules.reminderTriggerTimes(
                wednesdayStart.getTimeInMillis(),
                wednesdayStart.getTimeInMillis() + 4L * 24L * 60L * 60L * 1000L,
                1,
                0,
                8 * 60,
                30,
                null,
                wednesdayStart.getTimeInMillis() - 1L
        );
        check(clipped.length == 0,
                "course days after the configured term end must not create reminders");
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
