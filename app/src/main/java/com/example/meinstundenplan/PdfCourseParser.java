package com.example.meinstundenplan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

final class PdfCourseParser {
    static final class ParseResult {
        final List<ParsedCourse> courses;
        final String debugLog;

        ParseResult(List<ParsedCourse> courses, String debugLog) {
            this.courses = courses;
            this.debugLog = debugLog;
        }
    }

    static final class ParsedCourse {
        int day;
        int period;
        int endPeriod = 1;
        String name = "";
        String weeks = "";
        String room = "";
        String teacher = "";
    }

    static final class PdfTextItem {
        final double x;
        final double y;
        final String text;

        PdfTextItem(double x, double y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }
    }

    private static final class PdfLiteral {
        final byte[] bytes;
        final int nextIndex;

        PdfLiteral(byte[] bytes, int nextIndex) {
            this.bytes = bytes;
            this.nextIndex = nextIndex;
        }
    }

    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final double DAY_FIRST_COLUMN_X = 99.08;
    private static final double DAY_COLUMN_WIDTH = 103.85;
    private static final int MAX_FIELD_LENGTH = 60;

    private PdfCourseParser() {
    }

    static ParseResult parseCourses(byte[] pdfBytes, int maxPeriods, int maxStreamBytes, int maxCourses)
            throws ImportException {
        StringBuilder debugLog = new StringBuilder();
        List<PdfTextItem> textItems = extractAllPdfText(pdfBytes, maxStreamBytes, debugLog);
        List<ParsedCourse> courses = coursesFromPdfItems(textItems, maxPeriods, maxCourses, debugLog);
        return new ParseResult(courses, debugLog.toString());
    }

    private static byte[] readWithLimit(InputStream inputStream, int maxBytes, String limitMessage)
            throws IOException, ImportException {
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

    private static String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_FIELD_LENGTH ? trimmed : trimmed.substring(0, MAX_FIELD_LENGTH);
    }


    private static List<PdfTextItem> extractAllPdfText(byte[] pdfBytes, int maxStreamBytes, StringBuilder debugLog) throws ImportException {
        if (pdfBytes.length < 8 || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            throw new ImportException("选择的文件不是有效 PDF");
        }

        List<PdfTextItem> textItems = new ArrayList<>();
        byte[] streamToken = "stream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] endStreamToken = "endstream".getBytes(StandardCharsets.ISO_8859_1);
        int searchFrom = 0;
        int parsedBytes = 0;
        int streamCount = 0;

        while (searchFrom < pdfBytes.length) {
            int streamStart = indexOf(pdfBytes, streamToken, searchFrom);
            if (streamStart < 0) {
                break;
            }
            int contentStart = skipPdfLineBreak(pdfBytes, streamStart + streamToken.length);
            int streamEnd = indexOf(pdfBytes, endStreamToken, contentStart);
            if (streamEnd < 0) {
                break;
            }
            // Do not trim binary bytes: a zlib checksum may itself end in CR/LF.
            // InflaterInputStream tolerates the delimiter EOL after the compressed stream.
            int contentEnd = streamEnd;
            if (contentEnd > contentStart) {
                byte[] rawStream = Arrays.copyOfRange(pdfBytes, contentStart, contentEnd);
                byte[] stream = inflateOrRaw(rawStream, maxStreamBytes);
                parsedBytes += stream.length;
                if (parsedBytes > maxStreamBytes) {
                    throw new ImportException("PDF 内容过大，已拒绝导入");
                }
                int beforeCount = textItems.size();
                parsePdfTextItems(stream, textItems);
                int addedCount = textItems.size() - beforeCount;
                if (addedCount > 0) {
                    streamCount++;
                    debugLog.append(String.format(
                            Locale.CHINA,
                            "Stream %d: 添加了 %d 个文本项",
                            streamCount,
                            addedCount
                    )).append('\n');
                }
            }
            searchFrom = streamEnd + endStreamToken.length;
        }

        debugLog.append(String.format(
                Locale.CHINA,
                "总计: 解析了 %d 个stream，提取了 %d 个文本项",
                streamCount,
                textItems.size()
        )).append('\n');

        if (textItems.isEmpty()) {
            throw new ImportException("PDF 中没有可解析的文字课程表");
        }

        return textItems;
    }

    private static byte[] inflateOrRaw(byte[] rawStream, int maxStreamBytes) throws ImportException {
        try {
            return readWithLimit(new InflaterInputStream(new ByteArrayInputStream(rawStream)), maxStreamBytes, "PDF 压缩内容过大，已拒绝导入");
        } catch (ImportException error) {
            throw error;
        } catch (Exception ignored) {
            return rawStream;
        }
    }

    private static void parsePdfTextItems(byte[] stream, List<PdfTextItem> textItems) {
        double currentX = 0;
        double currentY = 0;
        int index = 0;
        while (index < stream.length) {
            if (index + 1 < stream.length && stream[index] == 'T' && stream[index + 1] == 'm') {
                double[] matrix = readLastNumbersBefore(stream, index, 6);
                if (matrix != null) {
                    currentX = matrix[4];
                    currentY = matrix[5];
                }
                index += 2;
                continue;
            }

            if (stream[index] == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                int afterLiteral = skipWhitespace(stream, literal.nextIndex);
                if (afterLiteral + 1 < stream.length && stream[afterLiteral] == 'T' && stream[afterLiteral + 1] == 'j') {
                    String text = decodePdfText(literal.bytes).trim();
                    if (!text.isEmpty()) {
                        textItems.add(new PdfTextItem(currentX, currentY, text));
                    }
                    index = afterLiteral + 2;
                    continue;
                }
                index = literal.nextIndex;
                continue;
            }

            if (stream[index] == '[') {
                PdfLiteral arrayText = readPdfTextArray(stream, index);
                int afterArray = skipWhitespace(stream, arrayText.nextIndex);
                if (afterArray + 1 < stream.length && stream[afterArray] == 'T' && stream[afterArray + 1] == 'J') {
                    String text = decodePdfText(arrayText.bytes).trim();
                    if (!text.isEmpty()) {
                        textItems.add(new PdfTextItem(currentX, currentY, text));
                    }
                    index = afterArray + 2;
                    continue;
                }
                index = arrayText.nextIndex;
                continue;
            }

            if (stream[index] == '<' && index + 1 < stream.length && stream[index + 1] != '<') {
                PdfLiteral hex = readPdfHexString(stream, index);
                int afterHex = skipWhitespace(stream, hex.nextIndex);
                if (afterHex + 1 < stream.length && stream[afterHex] == 'T' && stream[afterHex + 1] == 'j') {
                    String text = decodePdfText(hex.bytes).trim();
                    if (!text.isEmpty()) {
                        textItems.add(new PdfTextItem(currentX, currentY, text));
                    }
                    index = afterHex + 2;
                    continue;
                }
                index = hex.nextIndex;
                continue;
            }

            index++;
        }
    }

    private static List<ParsedCourse> coursesFromPdfItems(List<PdfTextItem> items, int maxPeriods, int maxCourses, StringBuilder debugLog) {
        List<ParsedCourse> parsed = new ArrayList<>();
        int matchCount = 0;
        int skipCount = 0;

        for (int i = 0; i < items.size() && parsed.size() < maxCourses; i++) {
            PdfTextItem item = items.get(i);
            TimetableRules.PeriodMatch periodMatch =
                    TimetableRules.matchPeriodWeeks(item.text, maxPeriods);
            if (!periodMatch.matched) {
                continue;
            }

            matchCount++;
            boolean rowAxis = usesRowAxis(items, i);
            int day = rowAxis ? dayFromX(item.y) : dayFromX(item.x);
            debugLog.append(String.format(Locale.CHINA, "\n[匹配%d] 文本: %s\n", matchCount, item.text));
            debugLog.append(String.format(Locale.ROOT, "  位置: x=%.2f, y=%.2f\n", item.x, item.y));
            debugLog.append(String.format(Locale.CHINA, "  解析: 星期=%d, 节次=%s-%s, 周次=%s\n",
                day, periodMatch.startPeriod, periodMatch.endPeriod, periodMatch.trailingText));

            if (!periodMatch.valid) {
                debugLog.append("  结果: 节次超出当前设置或范围倒置，跳过\n");
                skipCount++;
                continue;
            }

            if (day < 0) {
                debugLog.append("  结果: X坐标不匹配任何星期，跳过\n");
                skipCount++;
                continue;
            }

            String name = courseNameBefore(items, i, rowAxis);
            debugLog.append(String.format(Locale.CHINA, "  课程名: %s\n", name));

            if (name.isEmpty()) {
                debugLog.append("  结果: 课程名称为空，跳过\n");
                skipCount++;
                continue;
            }

            int period = periodMatch.startPeriod;
            int endPeriod = periodMatch.endPeriod;
            String block = courseBlockAfter(items, i, rowAxis);

            ParsedCourse course = new ParsedCourse();
            course.day = day;
            course.period = period;
            course.endPeriod = endPeriod;
            course.name = cleanField(name);

            // 清理weeks字段：移除时间信息（如"及18:30-20:10"）
            String rawWeeks = periodMatch.trailingText;
            if (rawWeeks != null) {
                // 移除附带的时间格式（如“及18:30-20:10”），保留“1-6周及9-17周”。
                rawWeeks = rawWeeks.replaceAll(
                        "\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}",
                        ""
                ).trim();
            }
            course.weeks = cleanField(cleanPdfWeeks(rawWeeks));
            TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(course.weeks);
            if (!weekResult.valid) {
                debugLog.append("  结果: 上课周格式无法识别，跳过\n");
                skipCount++;
                continue;
            }

            course.room = cleanField(extractPdfField(block, "/场地:"));
            course.teacher = cleanField(extractPdfField(block, "/教师:"));

            if (!course.name.isEmpty()) {
                parsed.add(course);
                debugLog.append(String.format(Locale.CHINA, "  结果: 成功添加 - %s (%s 第%d-%d节 %s)\n",
                    course.name, DAYS[course.day], course.period, course.endPeriod, course.weeks));
            }
        }

        debugLog.append(String.format(Locale.CHINA, "\n总结: 匹配到%d个，成功%d个，跳过%d个\n", matchCount, parsed.size(), skipCount));

        return parsed;
    }

    private static String cleanPdfWeeks(String rawWeeks) {
        if (rawWeeks == null) {
            return "";
        }
        String cleaned = rawWeeks.replaceFirst("^[\\uff09)\\s:\\uff1a]+", "").trim();
        return cleaned
                .replaceAll("\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}", "")
                .trim();
    }

    private static String courseNameBefore(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        List<String> parts = new ArrayList<>();
        for (int i = metadataIndex - 1; i >= 0; i--) {
            PdfTextItem previous = items.get(i);
            if (!sameCourseAxis(previous, metadata, rowAxis)) {
                // 课程可能跨 PDF 分页：课程名留在上一页底部，节次信息出现在下一页顶部，
                // 中间会夹杂其他列的文本，跳过它们继续沿同一列/行回溯。
                continue;
            }
            if (!looksLikeCourseNameLine(previous.text)) {
                break;
            }
            parts.add(0, previous.text);
        }
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(part);
        }
        String result = name.toString()
                .replace("★", "")
                .replace("○", "")
                .replace("●", "")
                .replace("◇", "")
                .replace("◆", "")
                .trim();

        // 移除结尾可能粘连的星期和节次信息
        result = result.replaceAll("周[一二三四五六日]第\\d{1,2}-?\\d{0,2}节$", "");
        result = result.replaceAll("第\\d{1,2}-?\\d{0,2}节$", "");

        return result.trim();
    }

    private static String courseBlockAfter(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        StringBuilder block = new StringBuilder();
        for (int i = metadataIndex; i < items.size(); i++) {
            PdfTextItem current = items.get(i);
            if (!sameCourseAxis(current, metadata, rowAxis)) {
                break;
            }
            if (i > metadataIndex && startsNewCourseTitle(current.text)) {
                break;
            }
            block.append(current.text);
        }
        return block.toString();
    }

    private static boolean looksLikeCourseNameLine(String text) {
        String value = text.trim();
        return !value.isEmpty()
                && !value.contains("/")
                && !value.startsWith(":")
                && !value.startsWith("：")
                && !value.contains("节)")
                && !value.contains("节）")
                && !value.matches(".*第\\d{1,2}-?\\d{0,2}节.*") // 排除包含"第X节"的行
                && !value.matches("周[一二三四五六日].*") // 排除以"周X"开头的行
                && !value.matches("\\d+")
                && !value.startsWith("打印时间")
                && !value.startsWith(": 体育课");
    }

    private static boolean startsNewCourseTitle(String text) {
        return (text.contains("★")
                || text.contains("○")
                || text.contains("●")
                || text.contains("◇")
                || text.contains("◆"))
                && !text.contains("教学班");
    }

    private static boolean sameColumn(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.x - right.x) < 3.0;
    }

    private static boolean sameRow(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.y - right.y) < 3.0;
    }

    private static boolean sameCourseAxis(PdfTextItem left, PdfTextItem right, boolean rowAxis) {
        return rowAxis ? sameRow(left, right) : sameColumn(left, right);
    }

    private static boolean usesRowAxis(List<PdfTextItem> items, int metadataIndex) {
        PdfTextItem metadata = items.get(metadataIndex);
        if (metadataIndex > 0) {
            PdfTextItem previous = items.get(metadataIndex - 1);
            if (looksLikeCourseNameLine(previous.text)
                    && sameRow(previous, metadata) && !sameColumn(previous, metadata)) {
                return true;
            }
        }
        if (metadataIndex + 1 < items.size()) {
            PdfTextItem next = items.get(metadataIndex + 1);
            if (looksLikeCourseNameLine(next.text)
                    && sameRow(next, metadata) && !sameColumn(next, metadata)) {
                return true;
            }
        }
        return false;
    }

    private static int dayFromX(double x) {
        int day = (int) Math.round((x - DAY_FIRST_COLUMN_X) / DAY_COLUMN_WIDTH);
        if (day < 0 || day >= DAYS.length) {
            return -1;
        }
        double expectedX = DAY_FIRST_COLUMN_X + day * DAY_COLUMN_WIDTH;
        return Math.abs(x - expectedX) <= 35.0 ? day : -1;
    }

    private static String extractPdfField(String block, String marker) {
        int start = block.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = block.indexOf("/", start);
        if (end < 0) {
            end = block.length();
        }
        return block.substring(start, end).trim();
    }

    private static PdfLiteral readPdfLiteral(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int depth = 1;
        int index = startIndex + 1;
        while (index < stream.length && depth > 0) {
            int value = stream[index] & 0xff;
            if (value == '\\') {
                index++;
                if (index >= stream.length) {
                    break;
                }
                int escaped = stream[index] & 0xff;
                switch (escaped) {
                    case 'n':
                        output.write('\n');
                        index++;
                        break;
                    case 'r':
                        output.write('\r');
                        index++;
                        break;
                    case 't':
                        output.write('\t');
                        index++;
                        break;
                    case 'b':
                        output.write('\b');
                        index++;
                        break;
                    case 'f':
                        output.write('\f');
                        index++;
                        break;
                    case '(':
                    case ')':
                    case '\\':
                        output.write(escaped);
                        index++;
                        break;
                    case '\r':
                        index++;
                        if (index < stream.length && stream[index] == '\n') {
                            index++;
                        }
                        break;
                    case '\n':
                        index++;
                        break;
                    default:
                        if (escaped >= '0' && escaped <= '7') {
                            int octal = escaped - '0';
                            index++;
                            for (int i = 0; i < 2 && index < stream.length; i++) {
                                int digit = stream[index] & 0xff;
                                if (digit < '0' || digit > '7') {
                                    break;
                                }
                                octal = octal * 8 + digit - '0';
                                index++;
                            }
                            output.write(octal & 0xff);
                        } else {
                            output.write(escaped);
                            index++;
                        }
                        break;
                }
            } else if (value == '(') {
                depth++;
                output.write(value);
                index++;
            } else if (value == ')') {
                depth--;
                if (depth > 0) {
                    output.write(value);
                }
                index++;
            } else {
                output.write(value);
                index++;
            }
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static PdfLiteral readPdfTextArray(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == ']') {
                index++;
                break;
            }
            if (value == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                output.write(literal.bytes, 0, literal.bytes.length);
                index = literal.nextIndex;
                continue;
            }
            if (value == '<' && index + 1 < stream.length && stream[index + 1] != '<') {
                PdfLiteral hex = readPdfHexString(stream, index);
                output.write(hex.bytes, 0, hex.bytes.length);
                index = hex.nextIndex;
                continue;
            }
            index++;
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static PdfLiteral readPdfHexString(byte[] stream, int startIndex) {
        ByteArrayOutputStream hex = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == '>') {
                index++;
                break;
            }
            if (isHexByte(value)) {
                hex.write(value);
            }
            index++;
        }

        byte[] hexBytes = hex.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < hexBytes.length; i += 2) {
            int high = hexValue(hexBytes[i]);
            int low = i + 1 < hexBytes.length ? hexValue(hexBytes[i + 1]) : 0;
            output.write(((high << 4) | low) & 0xff);
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static String decodePdfText(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        // Even-length ASCII/UTF-8 can look like readable CJK when decoded as UTF-16.
        // Prefer valid UTF-8 unless NUL bytes indicate UTF-16 text without a BOM.
        boolean hasNul = false;
        for (byte value : bytes) {
            hasNul |= value == 0;
        }
        if (!hasNul) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ignored) {
                // The school's PDF exporter also emits UTF-16BE without a BOM.
            }
        }
        if (bytes.length >= 2 && bytes.length % 2 == 0) {
            String utf16 = new String(bytes, Charset.forName("UTF-16BE"));
            if (hasReadableText(utf16)) {
                return utf16;
            }
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (hasReadableText(utf8)) {
            return utf8;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static boolean hasReadableText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if ((value >= 0x4e00 && value <= 0x9fff) || Character.isLetterOrDigit(value)) {
                return true;
            }
        }
        return false;
    }

    private static double[] readLastNumbersBefore(byte[] data, int operatorIndex, int count) {
        double[] numbers = new double[count];
        int found = 0;
        int index = operatorIndex - 1;
        while (found < count && index >= 0) {
            while (index >= 0 && isWhitespace(data[index])) {
                index--;
            }
            int end = index + 1;
            while (index >= 0 && isNumberByte(data[index])) {
                index--;
            }
            int start = index + 1;
            if (start >= end) {
                return null;
            }
            try {
                String token = new String(data, start, end - start, StandardCharsets.ISO_8859_1);
                numbers[count - found - 1] = Double.parseDouble(token);
                found++;
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return found == count ? numbers : null;
    }

    private static boolean isNumberByte(byte value) {
        return (value >= '0' && value <= '9') || value == '-' || value == '+' || value == '.';
    }

    private static boolean isHexByte(int value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private static int hexValue(byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return 0;
    }

    private static boolean isWhitespace(byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t' || value == '\f' || value == 0;
    }

    private static int skipWhitespace(byte[] data, int index) {
        int cursor = index;
        while (cursor < data.length && isWhitespace(data[cursor])) {
            cursor++;
        }
        return cursor;
    }

    private static int skipPdfLineBreak(byte[] data, int index) {
        int cursor = index;
        if (cursor < data.length && data[cursor] == '\r') {
            cursor++;
            if (cursor < data.length && data[cursor] == '\n') {
                cursor++;
            }
        } else if (cursor < data.length && data[cursor] == '\n') {
            cursor++;
        }
        return cursor;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            boolean matched = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }
}
