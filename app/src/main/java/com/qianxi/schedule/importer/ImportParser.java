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
        public final String sourceUrl;
        public final String pageTitle;
        public final String term;
        public final String campus;
        public final int rawItems;
        public final int skippedItems;
        public final int frames;
        public final int tables;
        public final int candidates;

        ImportOutcome(List<Course> courses, String adapterId, String source, String sourceUrl,
                      String pageTitle, String term, String campus, int rawItems, int skippedItems,
                      int frames, int tables, int candidates) {
            this.courses = courses;
            this.adapterId = adapterId;
            this.source = source;
            this.sourceUrl = sourceUrl;
            this.pageTitle = pageTitle;
            this.term = term;
            this.campus = campus;
            this.rawItems = rawItems;
            this.skippedItems = skippedItems;
            this.frames = frames;
            this.tables = tables;
            this.candidates = candidates;
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
        JSONObject diagnostics = root.optJSONObject("diagnostics");
        return new ImportOutcome(new ArrayList<>(merged.values()), adapter,
                root.optString("source", "page-dom"), root.optString("sourceUrl", ""),
                root.optString("pageTitle", ""), root.optString("term", ""),
                root.optString("campus", ""), items.length(), skipped,
                diagnostics == null ? 0 : diagnostics.optInt("frames", 0),
                diagnostics == null ? 0 : diagnostics.optInt("tables", 0),
                diagnostics == null ? items.length() : diagnostics.optInt("candidates", items.length()));
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

        course.teacher = normalize(item.optString("teacher", ""));
        course.location = normalize(item.optString("location", ""));

        // EAMS/beangle cells embed teacher and room inside the course string, e.g.
        // "人工智能导论(3030113067.01)(吕宪伟)n(4-15,工学馆307)". Pull them out of whatever
        // text we have (name or full text) before cleaning the course name down to just the title.
        String source = course.name.isEmpty() ? text : course.name + "\n" + text;
        if (course.teacher.isEmpty()) course.teacher = extractTeacher(source);
        if (course.location.isEmpty()) course.location = extractRoom(source);

        course.name = cleanupName(course.name);
        if (course.name.isEmpty() || isNoise(course.name)) return null;

        if (course.teacher.isEmpty()) {
            course.teacher = extractLabeled(text, "(?:任课)?教师|老师|授课教师|主讲教师");
        }
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
        // Period-grid position comes straight from the section range so the grid does not have to
        // re-derive it from minutes (WakeUp's startNode/step model).
        course.startNode = Math.max(1, sectionRange[0]);
        course.step = Math.max(1, sectionRange[1] - sectionRange[0] + 1);

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
            // Require at least one CJK ideograph or ASCII letter; a line of only digits and
            // punctuation is metadata, not a course. (Java's \\W treats CJK as non-word, so the
            // old ^[\\d\\W_]+$ guard wrongly rejected every Chinese course name.)
            if (value.length() < 2 || isMetadata(value)
                    || !value.matches(".*[\\u4e00-\\u9fa5A-Za-z].*")) continue;
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
        // A pure binary week string (bit n = week n+1), as emitted by beangle EAMS TaskActivity.
        // Handle it directly so no week is lost in the range-text round trip.
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() >= 4 && trimmed.matches("[01]+")) {
            long binMask = 0L;
            for (int i = 0; i < trimmed.length() && i < 60; i++) {
                if (trimmed.charAt(i) == '1') binMask |= 1L << i;
            }
            if (binMask != 0L) return binMask;
        }
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
        // A binary week string is parsed as-is by extractWeeks; don't append 周 (it would break it).
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 4 && trimmed.matches("[01]+")) return trimmed;
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

    /**
     * Extracts a teacher name from an EAMS course string. The format is typically
     * "课程名(课程号)(教师名)周次信息", so the teacher is a 2–4 character Chinese name in
     * parentheses that is NOT a course code and NOT a building/room token.
     */
    private static String extractTeacher(String text) {
        // Multiple teachers can be co-listed in one parenthesis group ("张三,李四" / "张三、李四")
        // or in adjacent groups. Collect each valid Chinese name once, in order, and join with a
        // comma so the card shows the full teaching team rather than only the first name.
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[\\(（]([\\u4e00-\\u9fa5A-Za-z,，、/\\s]{2,40})[\\)）]").matcher(text);
        while (matcher.find()) {
            for (String part : matcher.group(1).split("[,，、/\\s]+")) {
                String candidate = part.trim();
                if (candidate.length() < 2 || candidate.length() > 5) continue;
                // Skip room/building tokens and non-teacher roles.
                if (candidate.matches(".*(馆|楼|室|房|区|中心|校区)$")) continue;
                if (candidate.matches("^(实验辅导?老师|辅导教师|助教|监考老师|教师|老师)$")) continue;
                if (!candidate.matches("[\\u4e00-\\u9fa5]{2,5}")) continue;
                names.add(candidate);
            }
        }
        return String.join(",", names);
    }

    /**
     * Extracts a classroom/building token from an EAMS course string, e.g. "工学馆307",
     * "工学馆216", "乒乓球室". Rooms usually appear after weeks like "(4-15,工学馆307)".
     */
    private static String extractRoom(String text) {
        // Building + room number, e.g. 工学馆307, A303, 第一教学楼216, 3号教学楼201, 科技楼B105.
        Matcher matcher = Pattern.compile(
                "([\\u4e00-\\u9fa5A-Za-z0-9]{1,10}?(?:馆|楼|室|房|中心|教室|机房|操场|球场)[A-Za-z]?\\d{1,4})")
                .matcher(text);
        if (matcher.find()) return matcher.group(1);
        // Standalone place ending in a place word without a number, e.g. 乒乓球室, 体育馆, 实验中心.
        matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}(?:室|馆|中心|操场|球场|机房))").matcher(text);
        if (matcher.find()) return matcher.group(1);
        // Bare building+number without a place word, e.g. after a comma: ",A303)" or ",信息楼201".
        matcher = Pattern.compile("[,，]\\s*([A-Za-z\\u4e00-\\u9fa5]{1,8}\\d{2,4})\\s*[\\)）]?").matcher(text);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private static String cleanupName(String value) {
        // Remove course code patterns like (3030113067.01), [3030113067.01], 【3030113067.01】
        value = value.replaceAll("[\\(（\\[【]\\d{8,}[^)）\\]】]*[\\)）\\]】]", "");
        // Remove multi-value groups: teacher lists "(吕宪伟,王强)" and week+room "(4-15,工学馆307)".
        // Any parenthesis group that contains a comma or a digit is metadata, never the course name.
        value = value.replaceAll("[\\(（][^)）]*[,，\\d][^)）]*[\\)）]", "");
        // Remove teacher names in parentheses like (张三), (吕宪伟)
        value = value.replaceAll("[\\(（][\\u4e00-\\u9fa5]{2,4}[\\)）]", "");
        // Remove trailing incomplete parentheses and garbage
        value = value.replaceAll("[\\(（\\[【][^)）\\]】]*$", "");
        value = value.replaceAll("[nrltf\\\\]+$", "");
        // Remove label prefixes
        value = value.replaceFirst("^(课程名称|课程名|课程|科目)\\s*[:：]?\\s*", "");
        // Trim decorators and whitespace
        value = value.replaceAll("^[·•\\-—\\s]+|[·•\\-—\\s]+$", "").trim();
        return value;
    }

    private static boolean isMetadata(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        return value.matches("^(教师|老师|任课教师|地点|上课地点|教室|校区|周次|周数|节次|时间|学分|课程号|教学班).*?")
                || value.matches(".*(?:周|节)\\s*[（(]?(?:单|双)?[）)]?$")
                || value.matches("^\\d{1,2}[:：]\\d{2}.*");
    }

    private static boolean isNoise(String value) {
        String compact = value.replaceAll("\\s", "");
        if (compact.isEmpty() || compact.equals("课表") || compact.equals("课程表")) return true;
        if (compact.equals("上午") || compact.equals("下午") || compact.equals("晚上")) return true;
        if (compact.matches("^(星期|周)[一二三四五六日]$")) return true;
        // Summary/notes/metadata keywords
        if (compact.contains("授课小结") || compact.contains("教学小结")) return true;
        if (compact.contains("课程小结") || compact.contains("备注")) return true;
        if (compact.contains("授课计划") || compact.contains("教学计划")) return true;
        if (compact.contains("教学进度") || compact.contains("授课安排")) return true;
        if (compact.equals("授课信息") || compact.equals("授课") || compact.equals("大课排课")) return true;
        if (compact.matches("^(实验辅导?老师|辅导教师|助教|监考老师)$")) return true;
        // UI action words
        if (compact.matches("^(操作|编辑|删除|查看|详情|添加|修改|保存|取消|确定|提交)$")) return true;
        // Metadata field labels
        if (compact.matches("^(代码|编号|序号|课程号|教学班号|上课时间|上课地点)$")) return true;
        // Empty/placeholder values
        if (compact.equals("无") || compact.equals("暂无")) return true;
        if (compact.matches("^[—\\-\\.。,，、]+$")) return true;
        // Very short text without course-related keywords (likely just names/labels)
        if (compact.length() <= 4 && !compact.matches(".*[课程学实验设计理论基础导论概论原理技术方法].*")) return true;
        return false;
    }
}
