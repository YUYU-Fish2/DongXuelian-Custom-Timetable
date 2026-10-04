package com.example.meinstundenplan;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

/** Reproductions for bugs found during the October 2026 review. */
public final class BugRegressionTest {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "ascii": testAsciiName(); break;
            case "hex": testStandaloneHexName(); break;
            case "period": testThreeDigitPeriod(); break;
            case "compressed": testCompressedStreamEndingInNewline(); break;
            case "encoding": testEncodingCompatibility(); break;
            default: throw new AssertionError("Unknown test: " + args[0]);
        }
        System.out.println("BugRegressionTest: " + args[0] + " passed");
    }

    private static void testAsciiName() throws Exception {
        PdfCourseParser.ParseResult result = parse(content("(Math) Tj"));
        check(result.courses.size() == 1, "Expected one Math course");
        check("Math".equals(result.courses.get(0).name),
                "ASCII name corrupted: " + result.courses.get(0).name);
    }

    private static void testStandaloneHexName() throws Exception {
        PdfCourseParser.ParseResult result = parse(content("<4D617468> Tj"));
        check(result.courses.size() == 1, "Standalone hex Tj text was lost");
        check("Math".equals(result.courses.get(0).name), "Wrong hex name");
    }

    private static void testThreeDigitPeriod() {
        for (String invalid : new String[]{"第101节 1-16周", "101节", "第0节", "第01节",
                "第1-101节", "第0 - 1节", "第1 - 0节", "第999999999999节", "-1节"}) {
            TimetableRules.PeriodMatch result = TimetableRules.matchPeriodWeeks(invalid, 11);
            check(!result.matched || !result.valid,
                    "Invalid period accepted as " + result.startPeriod + ": " + invalid);
        }
    }

    private static void testCompressedStreamEndingInNewline() throws Exception {
        byte[] compressed = null;
        // Find a valid zlib stream whose checksum ends with LF; that LF is binary data.
        for (int i = 0; i < 1000; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(out)) {
                deflater.write(content("(History) Tj"));
                deflater.write(("\n% padding " + i).getBytes(StandardCharsets.US_ASCII));
            }
            byte[] candidate = out.toByteArray();
            if (candidate[candidate.length - 1] == '\n') {
                compressed = candidate;
                break;
            }
        }
        check(compressed != null, "Could not construct zlib fixture");
        PdfCourseParser.ParseResult result = parse(compressed);
        check(result.courses.size() == 1, "Valid compressed PDF was not imported");
    }

    private static void testEncodingCompatibility() throws Exception {
        for (String name : new String[]{"高数", "Data Structures", "History"}) {
            PdfCourseParser.ParseResult result = parse(content("(" + name + ") Tj"));
            check(result.courses.size() == 1 && name.equals(result.courses.get(0).name),
                    "UTF-8 name corrupted: " + name);
        }
        for (String hex : new String[]{"FEFF004D006100740068", "FFFE4D00610074006800", "004D006100740068"}) {
            PdfCourseParser.ParseResult result = parse(content("<" + hex + "> Tj"));
            check(result.courses.size() == 1 && "Math".equals(result.courses.get(0).name),
                    "UTF-16 name corrupted: " + hex);
        }
        PdfCourseParser.ParseResult rawCjk = parse(content("<6570636E7ED36784> Tj"));
        check(rawCjk.courses.size() == 1 && "数据结构".equals(rawCjk.courses.get(0).name),
                "Unmarked UTF-16BE CJK compatibility lost");
    }

    private static byte[] content(String nameOperator) {
        return ("BT 1 0 0 1 99.08 99.08 Tm " + nameOperator
                + " 1 0 0 1 99.08 99.08 Tm (第1节 1-16周) Tj ET")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static PdfCourseParser.ParseResult parse(byte[] stream) throws Exception {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.write("%PDF-1.4\nstream\n".getBytes(StandardCharsets.US_ASCII));
        pdf.write(stream);
        pdf.write("\nendstream\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
        return PdfCourseParser.parseCourses(pdf.toByteArray(), 11, 2 * 1024 * 1024, 200);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
