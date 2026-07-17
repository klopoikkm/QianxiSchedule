package com.qianxi.schedule.importer;

import com.qianxi.schedule.data.Course;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportParser {
    private static final int[] START_TIMES = {
            8 * 60, 8 * 60 + 55, 10 * 60, 10 * 60 + 55,
            14 * 60, 14 * 60 + 55, 16 * 60, 16 * 60 + 55,
            19 * 60, 19 * 60 + 55, 20 * 60 + 50, 21 * 60 + 45
    };
    private static final int[] COLORS = {
            0xFF087F5B, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
            0xFFE8590C, 0xFF5F6F52, 0xFF0B7285, 0xFF495057
    };

    private ImportParser() {}

    public static List<Course> parseJavascriptResult(String callbackValue, String system) throws Exception {
        Object decoded = new JSONTokener(callbackValue).nextValue();
        String payload = decoded instanceof String ? (String) decoded : callbackValue;
        JSONObject root = new JSONObject(payload);
        JSONArray items = root.optJSONArray("items");
        if (items == null) throw new IllegalArgumentException("课表页面没有返回可解析的数据");

        Set<Course> unique = new LinkedHashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            Course course = parseItem(item, system);
            if (course != null) unique.add(course);
        }
        return new ArrayList<>(unique);
    }

    private static Course parseItem(JSONObject item, String system) {
        String text = normalize(item.optString("text", ""));
        String title = normalize(item.optString("title", ""));
        if (text.isEmpty()) text = title;
        if (text.isEmpty() || isNoise(text)) return null;

        int day = item.optInt("day", 0);
        int section = item.optInt("section", 1);
        if (day < 1 || day > 7) return null;

        Course course = new Course();
        course.dayOfWeek = day;
        course.name = extractName(text, system);
        if (course.name.isEmpty() || isNoise(course.name)) return null;
        course.teacher = extractLabeled(text, "(?:任课)?教师|老师|授课教师");
        course.location = extractLabeled(text, "上课地点|地点|教室|校区");

        int[] sectionRange = extractSections(text, section);
        int[] explicitTime = extractTimeRange(text);
        if (explicitTime != null) {
            course.startMinute = explicitTime[0];
            course.endMinute = explicitTime[1];
        } else {
            course.startMinute = timeForSection(sectionRange[0]);
            course.endMinute = timeForSection(sectionRange[1]) + 45;
        }

        long weekMask = extractWeeks(text);
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

    private static String extractName(String text, String system) {
        String labeled = extractLabeled(text, "课程名称|课程名|科目名称|科目");
        if (!labeled.isEmpty()) return cleanupName(labeled);
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

    private static int[] extractSections(String text, int fallback) {
        Matcher matcher = Pattern.compile("(?:第)?(\\d{1,2})\\s*[-~至—]?\\s*(\\d{0,2})\\s*节").matcher(text);
        if (!matcher.find()) return new int[]{fallback, fallback};
        int first = integer(matcher.group(1), fallback);
        int last = matcher.group(2).isEmpty() ? first : integer(matcher.group(2), first);
        return new int[]{Math.max(1, first), Math.max(first, last)};
    }

    private static int[] extractTimeRange(String text) {
        Matcher matcher = Pattern.compile("([01]?\\d|2[0-3])[:：](\\d{2})\\s*[-~至—]\\s*([01]?\\d|2[0-3])[:：](\\d{2})")
                .matcher(text);
        if (!matcher.find()) return null;
        int start = integer(matcher.group(1), 8) * 60 + integer(matcher.group(2), 0);
        int end = integer(matcher.group(3), 9) * 60 + integer(matcher.group(4), 0);
        return end > start ? new int[]{start, end} : null;
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

    private static int timeForSection(int section) {
        if (section >= 1 && section <= START_TIMES.length) return START_TIMES[section - 1];
        return 8 * 60 + (section - 1) * 50;
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
