package com.example.meinstundenplan;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class PdfCourseParserTest {
    private static final int MAX_PERIODS = 11;
    private static final int MAX_STREAM = 2 * 1024 * 1024;
    private static final int MAX_COURSES = 200;

    public static void main(String[] args) throws Exception {
        testInvalidHeaderRejected();
        testEmptyPdfRejected();
        testSingleCourseParsed();
        testRowAxisCourseParsed();
        testWeekTimeSuffixCleaned();
        testInvalidWeeksSkipped();
        testPeriodOutOfRangeSkipped();
        testMultiStreamParsed();
        testCrossPageNameLookup();
        testStreamSizeLimitRejected();
        testCourseCountLimit();
        testLiteralEscapeInName();
        testHexTextInName();
        testDayMappingRejected();
        testDebugLogProduced();
        System.out.println("PdfCourseParserTest: all checks passed");
    }

    // U+7B2C=? U+8282=? U+5468=? U+5355=? U+4E0B=? U+5B66=? U+671F=?
    // U+53CA=? U+573A=? U+5730=? U+6559=? U+5E08=? U+5F20=? U+4E09=?
    private static final String METADATA_SINGLE = "(\u7b2c1\u8282 1-16\u5468) Tj";            // ?1? 1-16?
    private static final String METADATA_ROW = "(\u7b2c5\u8282 9-17\u5468) Tj";              // ?5? 9-17?

    private static void testInvalidHeaderRejected() {
        byte[] notPdf = "hello world".getBytes(StandardCharsets.ISO_8859_1);
        expectThrows(() -> PdfCourseParser.parseCourses(notPdf, MAX_PERIODS, MAX_STREAM, MAX_COURSES));
    }

    private static void testEmptyPdfRejected() {
        byte[] emptyPdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
        expectThrows(() -> PdfCourseParser.parseCourses(emptyPdf, MAX_PERIODS, MAX_STREAM, MAX_COURSES));
    }

    private static void testSingleCourseParsed() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 99.08 99.08 Tm (/\u573a\u5730:A101/\u6559\u5e08:\u5f20\u4e09 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "single course expected, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.day == 0, "course should map to Monday, got " + c.day);
        check(c.period == 1 && c.endPeriod == 1, "period 1-1 expected, got " + c.period + "-" + c.endPeriod);
        check(c.name.equals("Data Structures"), "name mismatch: " + c.name);
        check(c.weeks.equals("1-16\u5468"), "weeks mismatch: " + c.weeks);
        check(c.room.equals("A101"), "room mismatch: " + c.room);
        check(c.teacher.equals("\u5f20\u4e09"), "teacher mismatch: " + c.teacher);
    }

    private static void testRowAxisCourseParsed() throws Exception {
        String content = "BT 1 0 0 1 50 99.08 Tm (History) Tj "
                + "1 0 0 1 203.78 99.08 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "row-axis course expected, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.day == 0, "row-axis day mismatch: " + c.day);
        check(c.period == 5 && c.endPeriod == 5, "row-axis period mismatch: " + c.period + "-" + c.endPeriod);
        check(c.name.equals("History"), "row-axis name mismatch: " + c.name);
        check(c.weeks.equals("9-17\u5468"), "row-axis weeks mismatch: " + c.weeks);
    }

    private static void testWeekTimeSuffixCleaned() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Physics) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c2\u8282 1-16\u5468\u53ca18:30-20:10) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "week-suffix course expected, got " + result.courses.size());
        check(result.courses.get(0).weeks.equals("1-16\u5468"),
                "time suffix should be stripped from weeks, got: " + result.courses.get(0).weeks);
    }

    private static void testInvalidWeeksSkipped() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c3\u8282 \u4e0b\u5b66\u671f) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "unrecognizable weeks should be skipped");
    }

    private static void testPeriodOutOfRangeSkipped() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Physics) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c99\u8282 1-16\u5468\u5355\u5468 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "out-of-range period should be skipped");
    }

    private static void testMultiStreamParsed() throws Exception {
        String s1 = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        String s2 = "BT 1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(s1), stream(s2)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2, "two streams should yield two courses, got " + result.courses.size());
    }

    // 课程跨 PDF 分页：课程名留在第 1 页底部（如 x=415.62, y=22.5），
    // 节次信息出现在第 2 页顶部（y=567），中间隔着其他列的文本（如节次标签"8"）。
    // 回溯查找课程名时必须跳过其他列的文本，而不是在它们处中断。
    private static void testCrossPageNameLookup() throws Exception {
        String page1Tail = "BT 1 0 0 1 415.62 22.5 Tm (Comp Organization) Tj ET";
        String page2Head = "BT 1 0 0 1 78.13 524.5 Tm (8) Tj "
                + "1 0 0 1 415.62 567 Tm " + METADATA_ROW + " "
                + "1 0 0 1 415.62 555 Tm (/\u573a\u5730:A101/\u6559\u5e08:\u5f20\u4e09 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(page1Tail), stream(page2Head)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1,
                "cross-page course should be parsed, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.name.equals("Comp Organization"), "name mismatch: " + c.name);
        check(c.day == 3, "course should map to Thursday, got " + c.day);
        check(c.room.equals("A101"), "room mismatch: " + c.room);
        check(c.teacher.equals("\u5f20\u4e09"), "teacher mismatch: " + c.teacher);
    }

    private static void testStreamSizeLimitRejected() {
        byte[] big = new byte[100];
        Arrays.fill(big, (byte) 'x');
        expectThrows(() -> PdfCourseParser.parseCourses(pdfWithStreams(big), MAX_PERIODS, 50, MAX_COURSES));
    }

    private static void testCourseCountLimit() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, 1);
        check(result.courses.size() == 1, "course count limit should cap results, got " + result.courses.size());
    }

    private static void testLiteralEscapeInName() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data\\)Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "escaped-literal course expected, got " + result.courses.size());
        check(result.courses.get(0).name.equals("Data)Structures"),
                "escaped paren should be decoded, got: " + result.courses.get(0).name);
    }

    private static void testHexTextInName() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm [<48656C6C6F>] TJ "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "hex-text course expected, got " + result.courses.size());
        check(result.courses.get(0).name.equals("Hello"), "hex string should decode to Hello, got: " + result.courses.get(0).name);
    }

    private static void testDayMappingRejected() throws Exception {
        String content = "BT 1 0 0 1 50 99.08 Tm (Physics) Tj "
                + "1 0 0 1 50 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "off-grid x should be rejected");
    }

    private static void testDebugLogProduced() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.debugLog != null && result.debugLog.length() > 0, "debug log should not be empty");
    }

    private static byte[] pdfWithStreams(byte[]... streamContents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        for (byte[] s : streamContents) {
            write(out, "stream\n");
            out.write(s, 0, s.length);
            write(out, "\nendstream\n");
        }
        write(out, "%%EOF\n");
        return out.toByteArray();
    }

    private static byte[] stream(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void write(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void expectThrows(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected ImportException but nothing was thrown");
        } catch (ImportException expected) {
            // ok
        } catch (Exception error) {
            throw new AssertionError("unexpected exception: " + error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
