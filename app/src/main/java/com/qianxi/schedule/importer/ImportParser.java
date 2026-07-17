package com.qianxi.schedule.importer;

import com.qianxi.schedule.data.Course;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportParser {
    private static final int[] DEFAULT_START_TIMES = {
            8 * 60, 8 * 60 + 55, 10 * 60, 10 * 60 + 55,
            14 * 60, 14 * 60 + 55, 16 * 60, 16 * 60 + 55,
            19 * 60, 19 * 60 + 55, 20 * 60 + 50, 21 * 60 + 45
    };
    private static final int[] NEU_START_TIMES = {
            8 * 60 + 30, 9 * 60 + 25, 10 * 60 + 30, 11 * 60 + 25,
            14 * 60, 14 * 60 + 55, 16 * 60, 16 * 60 + 55,
            18 * 60 + 30, 19 * 60 + 25, 20 * 60 + 30, 21 * 60 + 25
    };
    private static final int[] NEU_END_TIMES = {
            9 * 60 + 15, 10 * 60 + 10, 11 * 60 + 15, 12 * 60 + 10,
            14 * 60 + 45, 15 * 60 + 40, 16 * 60 + 45, 17 * 60 + 40,
            19 * 60 + 15, 20 * 60 + 10, 21 * 60 + 15, 22 * 60 + 10
    };
    private static final int[] COLORS = {
            0xFF087F5B, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
            0xFFE8590C, 0xFF5F6F52, 0xFF0B7285, 0xFF495057
    };

    public static final class ImportOutcome {
        public final List<Course> courses;
        public final String adapterId;
        public final String source;
        public final String term;
        public final String campus;
        public final int rawItems;
        public final int skippedItems;

        ImportOutcome(List<Course> courses, String adapterId, String source, String term,
                      String campus, int rawItems, int skippedItems) {
            this.courses = courses;
            this.adapterId = adapterId;
            this.source = source;
            this.term = term;
            this.campus = campus;
            this.rawItems = rawItems;
            this.skippedItems = skippedItems;
        }
    }

    private ImportParser() {}

    public static List<Course> parseJavascriptResult(String callbackValue, String selectedAdapter) throws Exception {
        return parseOutcome(callbackValue, selectedAdapter).courses;
    }

    public static ImportOutcome parseOutcome(String callbackValue, String selectedAdapter) throws Exception {
        Object decoded = new JSONTokener(callbackValue).nextValue();
        String payload = decoded instanceof String ? (String) decoded : callbackValue;
        JSONObject root = new JSONObject(payload);
        JSONArray items = root.optJSONArray("items");
        if (items == null) throw new IllegalArgumentException("课表页面没有返回可解析的数据");

        String reportedAdapter = root.optString("adapter", selectedAdapter);
        String adapter = ImportAdapter.GENERIC.equals(reportedAdapter)
                && !ImportAdapter.AUTO.equals(selectedAdapter) ? selectedAdapter : reportedAdapter;
        Map<String, Course> merged = new LinkedHashMap<>();
        int skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Course course = item == null ? null : parseItem(item, adapter);
            if (course == null) {
                skipped++;
                continue;
            }
            mergeCourse(merged, course);
        }
        return new ImportOutcome(new ArrayList<>(merged.values()), adapter,
                root.optString("source", "page-dom"), root.optString("term", ""),
                root.optString("campus", ""), items.length(), skipped);
    }

    private static Course parseItem(JSONObject item, String adapter) {
        String text = normalize(item.optString("text", ""));
        String title = normalize(item.optString("title", ""));
        if (text.isEmpty()) text = title;

        int day = item.optInt("day", 0);
        int section = item.optInt("section", 1);
        int endSection = Math.max(section, item.optInt("endSection", section));
        if (day < 1 || day > 7 || section < 1 || section > 30) return null;

        Course course = new Course();
        course.dayOfWeek = day;
        course.name = normalize(item.optString("name", ""));
        if (course.name.isEmpty()) course.name = extractName(text);
        course.name = cleanupName(course.name);
        if (course.name.isEmpty() || isNoise(course.name)) return null;

        course.teacher = normalize(item.optString("teacher", ""));
        if (course.teacher.isEmpty()) {
            course.teacher = extractLabeled(text, "(?:任课)?教师|老师|授课教师|主讲教师");
        }
        course.location = normalize(item.optString("location", ""));
        if (course.location.isEmpty()) {
            course.location = extractLabeled(text, "上课地点|地点|教室|校区");
        }

        int[] explicitRange = explicitTimes(item, text);
        int[] sectionRange = extractSections(text, section, endSection);
        if (explicitRange != null) {
            course.startMinute = explicitRange[0];
            course.endMinute = explicitRange[1];
        } else {
            course.startMinute = startForSection(sectionRange[0], adapter);
            course.endMinute = endForSection(sectionRange[1], adapter);
        }
        if (course.endMinute <= course.startMinute) return null;

        String weekText = normalize(item.optString("weeks", ""));
        long weekMask = weekText.isEmpty() ? extractWeeks(text) : extractWeeks(ensureWeekSuffix(weekText));
        course.weekMask = weekMask;
        if (weekMask != 0L) {
            course.startWeek = firstWeek(weekMask);
            course.endWeek = lastWeek(weekMask);
        } else {
            course.startWeek = 1;
            course.endWeek = 20;
        }
        course.color = COLORS[Math.floorMod(course.name.hashCode(), COLORS.length)];
        return course;
    }

    private static int[] explicitTimes(JSONObject item, String text) {
        int start = parseClock(item.optString("startTime", ""));
        int end = parseClock(item.optString("endTime", ""));
        if (start >= 0 && end > start) return new int[]{start, end};
        Matcher matcher = Pattern.compile("([01]?\\d|2[0-3])[:：](\\d{2})\\s*[-~至—]\\s*([01]?\\d|2[0-3])[:：](\\d{2})")
                .matcher(text);
        if (!matcher.find()) return null;
        start = integer(matcher.group(1), 8) * 60 + integer(matcher.group(2), 0);
        end = integer(matcher.group(3), 9) * 60 + integer(matcher.group(4), 0);
        return end > start ? new int[]{start, end} : null;
    }

    static int parseClock(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 6) digits = digits.substring(0, 4);
        if (digits.length() == 3) digits = "0" + digits;
        if (digits.length() != 4) return -1;
        int hour = integer(digits.substring(0, 2), -1);
        int minute = integer(digits.substring(2, 4), -1);
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 ? hour * 60 + minute : -1;
    }

    private static String extractName(String text) {
        String labeled = extractLabeled(text, "课程名称|课程名|科目名称|科目");
        if (!labeled.isEmpty()) return labeled;
        for (String line : text.split("\\n")) {
            String value = cleanupName(line);
            if (value.length() < 2 || isMetadata(value) || value.matches("^[\\d\\W_]+$")) continue;
            return value;
        }
        return "";
    }

    private static String extractLabeled(String text, String labelPattern) {
        Pattern pattern = Pattern.compile("(?:^|\\n|[;；])\\s*(?:" + labelPattern
                + ")\\s*[:：]?\\s*([^\\n;；|]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return "";
        String value = matcher.group(1).trim();
        value = value.replaceFirst("(?i)\\s*(?:周次|周数|节次|上课时间|地点|教室|教师)\\s*[:：].*$", "");
        return value.trim();
    }

    private static int[] extractSections(String text, int fallbackStart, int fallbackEnd) {
        Matcher matcher = Pattern.compile("(?:第)?(\\d{1,2})\\s*[-~至—]?\\s*(\\d{0,2})\\s*节").matcher(text);
        if (!matcher.find()) return new int[]{fallbackStart, fallbackEnd};
        int first = integer(matcher.group(1), fallbackStart);
        int last = matcher.group(2).isEmpty() ? first : integer(matcher.group(2), first);
        return new int[]{Math.max(1, first), Math.max(first, last)};
    }

    static long extractWeeks(String text) {
        Matcher area = Pattern.compile("((?:\\d{1,2}\\s*(?:[-~至—]\\s*\\d{1,2})?\\s*[,，、]?\\s*)+)周(?:\\s*[（(]?(单|双)周?[）)]?)?")
                .matcher(text);
        if (!area.find()) return 0L;
        String values = area.group(1);
        String parity = area.group(2) == null ? "" : area.group(2);
        long mask = 0L;
        Matcher numbers = Pattern.compile("(\\d{1,2})(?:\\s*[-~至—]\\s*(\\d{1,2}))?").matcher(values);
        while (numbers.find()) {
            int first = integer(numbers.group(1), 1);
            int last = numbers.group(2) == null ? first : integer(numbers.group(2), first);
            for (int week = Math.max(1, first); week <= Math.min(60, last); week++) {
                if (("单".equals(parity) && week % 2 == 0) || ("双".equals(parity) && week % 2 == 1)) continue;
                mask |= 1L << (week - 1);
            }
        }
        return mask;
    }

    private static String ensureWeekSuffix(String value) {
        return value.contains("周") ? value : value + "周";
    }

    private static int startForSection(int section, String adapter) {
        int[] times = ImportAdapter.NEU.equals(adapter) ? NEU_START_TIMES : DEFAULT_START_TIMES;
        if (section >= 1 && section <= times.length) return times[section - 1];
        return 8 * 60 + (section - 1) * 50;
    }

    private static int endForSection(int section, String adapter) {
        if (ImportAdapter.NEU.equals(adapter) && section >= 1 && section <= NEU_END_TIMES.length) {
            return NEU_END_TIMES[section - 1];
        }
        return startForSection(section, adapter) + 45;
    }

    private static void mergeCourse(Map<String, Course> merged, Course course) {
        String key = course.name + '\u0000' + course.teacher + '\u0000' + course.location + '\u0000'
                + course.dayOfWeek + '\u0000' + course.startMinute + '\u0000' + course.endMinute;
        Course existing = merged.get(key);
        if (existing == null) {
            merged.put(key, course);
            return;
        }
        long existingMask = existing.weekMask != 0L ? existing.weekMask
                : rangeMask(existing.startWeek, existing.endWeek, existing.parity);
        long incomingMask = course.weekMask != 0L ? course.weekMask
                : rangeMask(course.startWeek, course.endWeek, course.parity);
        existing.weekMask = existingMask | incomingMask;
        existing.startWeek = firstWeek(existing.weekMask);
        existing.endWeek = lastWeek(existing.weekMask);
        existing.parity = 0;
    }

    private static long rangeMask(int first, int last, int parity) {
        long mask = 0L;
        for (int week = Math.max(1, first); week <= Math.min(60, last); week++) {
            if ((parity == 1 && week % 2 == 0) || (parity == 2 && week % 2 == 1)) continue;
            mask |= 1L << (week - 1);
        }
        return mask;
    }

    private static int firstWeek(long mask) {
        return Long.numberOfTrailingZeros(mask) + 1;
    }

    private static int lastWeek(long mask) {
        return 64 - Long.numberOfLeadingZeros(mask);
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private static String normalize(String value) {
        return value.replace('\u00A0', ' ').replace('\r', '\n')
                .replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\\n").trim();
    }

    private static String cleanupName(String value) {
        return value.replaceFirst("^(课程名称|课程名|课程|科目)\\s*[:：]?\\s*", "")
                .replaceAll("^[·•\\-—\\s]+|[·•\\-—\\s]+$", "").trim();
    }

    private static boolean isMetadata(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        return value.matches("^(教师|老师|任课教师|地点|上课地点|教室|校区|周次|周数|节次|时间|学分|课程号|教学班).*?")
                || value.matches(".*(?:周|节)\\s*[（(]?(?:单|双)?[）)]?$")
                || value.matches("^\\d{1,2}[:：]\\d{2}.*");
    }

    private static boolean isNoise(String value) {
        String compact = value.replaceAll("\\s", "");
        return compact.isEmpty() || compact.equals("课表") || compact.equals("课程表")
                || compact.equals("上午") || compact.equals("下午") || compact.equals("晚上")
                || compact.matches("^(星期|周)[一二三四五六日]$");
    }
}
