package com.qianxi.schedule.importer;

import com.qianxi.schedule.data.AppSettings;
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
    private static final int[] COLORS = {
            0xFF087F5B, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
            0xFFE8590C, 0xFF5F6F52, 0xFF0B7285, 0xFF495057
    };

    public static final class ImportOutcome {
        public final List<Course> courses;
        public final String adapterId;
        public final String layout;
        public final String source;
        public final String sourceUrl;
        public final String pageTitle;
        public final String term;
        public final String campus;
        public final int rawItems;
        public final int skippedItems;
        public final int frames;
        public final int shadowRoots;
        public final int tables;
        public final int candidates;
        public final int missingWeekCandidates;
        public final int runtimeActivities;
        public final int recordRows;
        public final int visualCandidates;
        public final int visualDays;
        public final int visualSections;
        public final int visualNodes;
        public final List<String> candidateSamples;
        public final List<String> fingerprints;
        /**
         * The period structure stated by the page's own label column, when it provides one.
         * Empty when the page does not spell out per-period clocks.
         */
        public final List<AppSettings.ClassTime> pagePeriods;

        ImportOutcome(List<Course> courses, String adapterId, String layout, String source, String sourceUrl,
                      String pageTitle, String term, String campus, int rawItems, int skippedItems,
                      int frames, int shadowRoots, int tables, int candidates,
                      int missingWeekCandidates, int runtimeActivities,
                      int recordRows, int visualCandidates, int visualDays, int visualSections, int visualNodes,
                      List<String> candidateSamples, List<String> fingerprints,
                      List<AppSettings.ClassTime> pagePeriods) {
            this.courses = courses;
            this.adapterId = adapterId;
            this.layout = layout;
            this.source = source;
            this.sourceUrl = sourceUrl;
            this.pageTitle = pageTitle;
            this.term = term;
            this.campus = campus;
            this.rawItems = rawItems;
            this.skippedItems = skippedItems;
            this.frames = frames;
            this.shadowRoots = shadowRoots;
            this.tables = tables;
            this.candidates = candidates;
            this.missingWeekCandidates = missingWeekCandidates;
            this.runtimeActivities = runtimeActivities;
            this.recordRows = recordRows;
            this.visualCandidates = visualCandidates;
            this.visualDays = visualDays;
            this.visualSections = visualSections;
            this.visualNodes = visualNodes;
            this.candidateSamples = candidateSamples;
            this.fingerprints = fingerprints;
            this.pagePeriods = pagePeriods;
        }
    }

    private ImportParser() {}

    public static List<Course> parseJavascriptResult(String callbackValue, String selectedAdapter) throws Exception {
        return parseOutcome(callbackValue, selectedAdapter, null).courses;
    }

    public static ImportOutcome parseOutcome(String callbackValue, String selectedAdapter) throws Exception {
        return parseOutcome(callbackValue, selectedAdapter, null);
    }

    /**
     * Parses the scanner payload into courses. When {@code classTimes} is supplied (the user's
     * configured 作息), section-based courses derive their start/end minutes from that table so the
     * imported alarm time matches exactly where the grid draws the course. An explicit clock in
     * the page always wins. A null table falls back to the
     * built-in default schedule (used by unit tests and the pre-作息 import path).
     */
    public static ImportOutcome parseOutcome(String callbackValue, String selectedAdapter,
                                             List<AppSettings.ClassTime> classTimes) throws Exception {
        Object decoded = new JSONTokener(callbackValue).nextValue();
        String payload = decoded instanceof String ? (String) decoded : callbackValue;
        JSONObject root = new JSONObject(payload);
        JSONArray items = root.optJSONArray("items");
        if (items == null) throw new IllegalArgumentException("课表页面没有返回可解析的数据");

        String reportedAdapter = ImportAdapter.migrateLegacyId(root.optString("adapter", ""));
        selectedAdapter = ImportAdapter.migrateLegacyId(selectedAdapter);
        String adapter = reportedAdapter.isEmpty() || ImportAdapter.AUTO.equals(reportedAdapter)
                ? selectedAdapter : reportedAdapter;
        if (adapter == null || adapter.isEmpty() || ImportAdapter.AUTO.equals(adapter)) {
            adapter = ImportAdapter.GENERIC;
        }
        Map<String, Course> merged = new LinkedHashMap<>();
        int skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Course course = item == null ? null : parseItem(item, adapter, classTimes);
            if (course == null) {
                skipped++;
                continue;
            }
            mergeCourse(merged, course);
        }
        JSONObject diagnostics = root.optJSONObject("diagnostics");
        List<String> samples = new ArrayList<>();
        JSONArray sampleArray = diagnostics == null ? null : diagnostics.optJSONArray("samples");
        if (sampleArray != null) {
            for (int i = 0; i < sampleArray.length() && samples.size() < 3; i++) {
                String sample = normalize(sampleArray.optString(i, ""));
                if (!sample.isEmpty()) samples.add(sample);
            }
        }
        List<String> fingerprints = new ArrayList<>();
        JSONArray fingerprintArray = diagnostics == null ? null : diagnostics.optJSONArray("fingerprints");
        if (fingerprintArray != null) {
            for (int i = 0; i < fingerprintArray.length() && fingerprints.size() < 5; i++) {
                JSONObject item = fingerprintArray.optJSONObject(i);
                if (item == null) continue;
                String id = normalize(item.optString("id", ""));
                if (id.isEmpty()) continue;
                StringBuilder summary = new StringBuilder(id)
                        .append(" (").append(item.optInt("score", 0)).append(')');
                JSONArray evidence = item.optJSONArray("evidence");
                if (evidence != null && evidence.length() > 0) {
                    summary.append(": ").append(normalize(evidence.optString(0, "")));
                }
                fingerprints.add(summary.toString());
            }
        }
        List<AppSettings.ClassTime> pagePeriods = new ArrayList<>();
        JSONArray periodArray = root.optJSONArray("periods");
        for (int i = 0; periodArray != null && i < periodArray.length(); i++) {
            JSONObject item = periodArray.optJSONObject(i);
            if (item == null) continue;
            int period = item.optInt("period", 0);
            int start = parseClock(item.optString("startTime", ""));
            int end = parseClock(item.optString("endTime", ""));
            if (period < 1 || period > 30 || start < 0 || end <= start) continue;
            pagePeriods.add(new AppSettings.ClassTime(period, start, end));
        }
        return new ImportOutcome(new ArrayList<>(merged.values()), adapter,
                root.optString("layout", "generic"),
                root.optString("source", "page-dom"), root.optString("sourceUrl", ""),
                root.optString("pageTitle", ""), root.optString("term", ""),
                root.optString("campus", ""), items.length(), skipped,
                diagnostics == null ? 0 : diagnostics.optInt("frames", 0),
                diagnostics == null ? 0 : diagnostics.optInt("shadowRoots", 0),
                diagnostics == null ? 0 : diagnostics.optInt("tables", 0),
                diagnostics == null ? items.length() : diagnostics.optInt("candidates", items.length()),
                diagnostics == null ? 0 : diagnostics.optInt("missingWeekCandidates", 0),
                diagnostics == null ? 0 : diagnostics.optInt("runtimeActivities", 0),
                diagnostics == null ? 0 : diagnostics.optInt("recordRows", 0),
                diagnostics == null ? 0 : diagnostics.optInt("visualCandidates", 0),
                diagnostics == null ? 0 : diagnostics.optInt("visualDays", 0),
                diagnostics == null ? 0 : diagnostics.optInt("visualSections", 0),
                diagnostics == null ? 0 : diagnostics.optInt("visualNodes", 0),
                samples, fingerprints, pagePeriods);
    }

    private static Course parseItem(JSONObject item, String adapter,
                                    List<AppSettings.ClassTime> classTimes) {
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
        // A structured "name" field comes from the page's own data model, so it is trusted as a
        // course title; a name recovered from cell text is a guess and gets the stricter checks.
        boolean nameWasExplicit = !course.name.isEmpty();
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
        if (course.name.isEmpty() || isNoise(course.name, nameWasExplicit)) return null;

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
            course.startMinute = startForSection(sectionRange[0], classTimes);
            course.endMinute = endForSection(sectionRange[1], classTimes);
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
            // Missing weeks do not mean "the whole semester". Silently turning them into 1-20
            // creates classes on weeks where they never occur.
            return null;
        }
        course.color = COLORS[Math.floorMod(course.name.hashCode(), COLORS.length)];
        return course;
    }

    private static int[] explicitTimes(JSONObject item, String text) {
        int start = parseClock(item.optString("startTime", ""));
        int end = parseClock(item.optString("endTime", ""));
        if (start >= 0 && end > start) return new int[]{start, end};
        Matcher matcher = Pattern.compile("([01]?\\d|2[0-3])[:：](\\d{2})\\s*[-~～－–至—]\\s*([01]?\\d|2[0-3])[:：](\\d{2})")
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
        // A timetable cell often contains "2节/周" or "3节/周", meaning weekly workload rather
        // than period number. The DOM scanner already knows the row, so only explicit "第x节" or
        // an actual x-y range may override it.
        // "第2大节" (强智/青果 style): one 大节 is two consecutive periods, so 大节 n covers 2n-1..2n.
        Matcher big = Pattern.compile("第\\s*(\\d{1,2})\\s*大节").matcher(text);
        if (big.find()) {
            int index = integer(big.group(1), 0);
            if (index >= 1) return new int[]{index * 2 - 1, index * 2};
        }
        // "第1,2节" / "第3、4节": an explicit list, so take its span rather than only the first value.
        Matcher list = Pattern.compile("第\\s*((?:\\d{1,2}\\s*[,，、]\\s*)+\\d{1,2})\\s*节(?!\\s*/\\s*周)")
                .matcher(text);
        if (list.find()) {
            int low = Integer.MAX_VALUE;
            int high = 0;
            Matcher values = Pattern.compile("\\d{1,2}").matcher(list.group(1));
            while (values.find()) {
                int value = integer(values.group(), 0);
                if (value < 1) continue;
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            if (high >= 1) return new int[]{low, high};
        }
        Matcher matcher = Pattern.compile("第\\s*(\\d{1,2})\\s*(?:[-~～－–至—]\\s*(\\d{1,2}))?\\s*节(?!\\s*/\\s*周)")
                .matcher(text);
        if (!matcher.find()) {
            matcher = Pattern.compile("(?:^|[\\s（(])\\s*(\\d{1,2})\\s*[-~～－–至—]\\s*(\\d{1,2})\\s*节(?!\\s*/\\s*周)")
                    .matcher(text);
            if (!matcher.find()) return new int[]{fallbackStart, fallbackEnd};
        }
        int first = integer(matcher.group(1), fallbackStart);
        int last = matcher.group(2) == null || matcher.group(2).isEmpty()
                ? first : integer(matcher.group(2), first);
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
        Matcher area = Pattern.compile("((?:\\d{1,2}\\s*(?:[-~～－–至—]\\s*\\d{1,2})?\\s*[,，、]?\\s*)+)周(?:\\s*[（(]?(单|双)周?[）)]?)?")
                .matcher(text);
        String values;
        String parity;
        int matchStart;
        if (area.find()) {
            values = area.group(1);
            parity = area.group(2) == null ? "" : area.group(2);
            matchStart = area.start();
        } else {
            // Classic EAMS abbreviates weeks as "(4-15,工学馆307)" without a 周 suffix.
            // Requiring a comma after 1-2 digit ranges avoids matching course codes.
            Matcher compact = Pattern.compile(
                    "[（(]\\s*((?:\\d{1,2}\\s*(?:[-~～－–至—]\\s*\\d{1,2})?)"
                            + "(?:\\s*[,，、]\\s*(?:\\d{1,2}\\s*(?:[-~～－–至—]\\s*\\d{1,2})?))*)"
                            + "\\s*(单|双)?\\s*[,，]")
                    .matcher(text == null ? "" : text);
            if (!compact.find()) return 0L;
            values = compact.group(1);
            parity = compact.group(2) == null ? "" : compact.group(2);
            matchStart = compact.start();
        }
        if (parity.isEmpty()) parity = leadingParity(text, matchStart);
        long mask = 0L;
        Matcher numbers = Pattern.compile("(\\d{1,2})(?:\\s*[-~～－–至—]\\s*(\\d{1,2}))?").matcher(values);
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

    /**
     * Some systems put the parity before the range instead of after it — "单周1-16", "双周 2-16周",
     * "单 3-15周". Looks at the text immediately preceding the matched week list for that marker.
     */
    private static String leadingParity(String text, int weekStart) {
        String prefix = text.substring(0, Math.max(0, weekStart));
        Matcher matcher = Pattern.compile("(单|双)\\s*周?\\s*[（(]?\\s*$").matcher(prefix);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String ensureWeekSuffix(String value) {
        // A binary week string is parsed as-is by extractWeeks; don't append 周 (it would break it).
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 4 && trimmed.matches("[01]+")) return trimmed;
        return value.contains("周") ? value : value + "周";
    }

    /**
     * Start minute of a 1-based period. The user's own class-time table wins for every school.
     * Missing slots retain the built-in fallback so partial settings never hide a course.
     */
    private static int startForSection(int section, List<AppSettings.ClassTime> classTimes) {
        AppSettings.ClassTime slot = slotForSection(classTimes, section);
        if (slot != null) return slot.startMinute;
        if (section >= 1 && section <= DEFAULT_START_TIMES.length) {
            return DEFAULT_START_TIMES[section - 1];
        }
        return 8 * 60 + (section - 1) * 50;
    }

    private static int endForSection(int section, List<AppSettings.ClassTime> classTimes) {
        AppSettings.ClassTime slot = slotForSection(classTimes, section);
        if (slot != null) return slot.endMinute;
        return startForSection(section, classTimes) + 45;
    }

    /**
     * Looks up a period in the user's 作息 by its declared period number, falling back to list
     * position. A hand-edited table may skip or renumber periods, so matching on {@code period}
     * first keeps "第 6 节" pointing at the slot the user labelled 6.
     */
    private static AppSettings.ClassTime slotForSection(List<AppSettings.ClassTime> classTimes, int section) {
        if (classTimes == null || classTimes.isEmpty() || section < 1) return null;
        for (AppSettings.ClassTime slot : classTimes) {
            if (slot != null && slot.period == section
                    && slot.endMinute > slot.startMinute) {
                return slot;
            }
        }
        if (section <= classTimes.size()) {
            AppSettings.ClassTime slot = classTimes.get(section - 1);
            if (slot != null && slot.endMinute > slot.startMinute) return slot;
        }
        return null;
    }

    private static void mergeCourse(Map<String, Course> merged, Course course) {
        String key = course.name + '\u0000' + course.teacher + '\u0000' + course.location + '\u0000'
                + course.dayOfWeek + '\u0000' + course.startMinute + '\u0000' + course.endMinute;
        Course existing = merged.get(key);
        if (existing == null) {
            // The same timetable is frequently exposed twice: once through the vendor's runtime
            // activity model and once through the visible DOM. Those representations may differ
            // only by optional metadata, for example "工学馆204" versus
            // "工学馆204(学校本部)". Treating location as an exact key leaves two overlapping
            // cards in the database, so also look for a semantically equivalent session.
            for (Course candidate : merged.values()) {
                if (sameLogicalSession(candidate, course)) {
                    existing = candidate;
                    break;
                }
            }
        }
        if (existing == null) {
            merged.put(key, course);
            return;
        }
        existing.teacher = richerMetadata(existing.teacher, course.teacher, false);
        existing.location = richerMetadata(existing.location, course.location, true);
        long existingMask = existing.weekMask != 0L ? existing.weekMask
                : rangeMask(existing.startWeek, existing.endWeek, existing.parity);
        long incomingMask = course.weekMask != 0L ? course.weekMask
                : rangeMask(course.startWeek, course.endWeek, course.parity);
        existing.weekMask = existingMask | incomingMask;
        existing.startWeek = firstWeek(existing.weekMask);
        existing.endWeek = lastWeek(existing.weekMask);
        existing.parity = 0;
    }

    private static boolean sameLogicalSession(Course first, Course second) {
        return first.name.equalsIgnoreCase(second.name)
                && first.dayOfWeek == second.dayOfWeek
                // WakeUp identifies timetable placement with startNode/step. Clock minutes are
                // alarm metadata and may differ between a vendor model and rendered page even
                // though both describe the same period grid slot.
                && first.startNode == second.startNode
                && first.step == second.step
                && compatibleMetadata(first.teacher, second.teacher, false)
                && compatibleMetadata(first.location, second.location, true);
    }

    private static boolean compatibleMetadata(String first, String second, boolean location) {
        String left = metadataKey(first, location);
        String right = metadataKey(second, location);
        return left.isEmpty() || right.isEmpty() || left.equals(right);
    }

    private static String richerMetadata(String current, String incoming, boolean location) {
        String left = normalize(current == null ? "" : current);
        String right = normalize(incoming == null ? "" : incoming);
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        if (!metadataKey(left, location).equals(metadataKey(right, location))) return left;
        return right.length() > left.length() ? right : left;
    }

    private static String metadataKey(String value, boolean location) {
        String compact = normalize(value == null ? "" : value)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        if (!location || compact.isEmpty()) return compact;
        // Campus qualifiers describe the same room and vary between the runtime model and DOM.
        // Only strip a bracketed trailing qualifier, never a real building/room component.
        String withoutCampus = compact.replaceFirst(
                "[（(\\[【](?:学校本部|[^()（）\\[\\]【】]{1,20}校区)[）)\\]】]$", "");
        return withoutCampus.isEmpty() ? compact : withoutCampus;
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
                if (candidate.matches(".*(馆|楼|室|房|区|中心|校区|操场|球场|机房)$")) continue;
                if (candidate.matches("^(实验辅导?老师|辅导教师|助教|监考老师|教师|老师|实验员|带教|外聘|待定|未安排)$")) continue;
                // Week/parity and workload fragments that survive into a parenthesis group.
                if (candidate.matches("^(单周|双周|全周|连上|待安排|不排课)$")) continue;
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
        // A separator or wing may sit between building and number: 综合楼-234, 教学楼 A305,
        // 信息楼#201, 综合楼B区201.
        Matcher matcher = Pattern.compile(
                "([\\u4e00-\\u9fa5A-Za-z0-9]{1,10}?(?:馆|楼|室|房|中心|教室|机房|操场|球场)"
                        + "[-—#\\s]?[A-Za-z]?[-—#区]?\\d{1,4})")
                .matcher(text);
        if (matcher.find()) return tidyRoom(matcher.group(1));
        // Standalone place ending in a place word without a number, e.g. 乒乓球室, 体育馆, 实验中心.
        matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}(?:室|馆|中心|操场|球场|机房))").matcher(text);
        if (matcher.find()) return tidyRoom(matcher.group(1));
        // A bare room code with no place word, delimited by a comma or wrapped in parentheses:
        // ",A303)", ",信息楼201", "(4#201)", "(A-303)". The code must start with a letter/汉字 or
        // use a "#" wing separator, otherwise a week range like "(1-16)" would look like a room.
        String code = "(?:[A-Za-z\\u4e00-\\u9fa5][A-Za-z0-9\\u4e00-\\u9fa5]{0,7}[-—#区]?\\d{2,4}"
                + "|\\d{1,3}#\\d{2,4})";
        matcher = Pattern.compile("[,，]\\s*(" + code + ")\\s*[\\)）]?").matcher(text);
        if (matcher.find()) return tidyRoom(matcher.group(1));
        matcher = Pattern.compile("[\\(（]\\s*(" + code + ")\\s*[\\)）]").matcher(text);
        if (matcher.find()) return tidyRoom(matcher.group(1));
        return "";
    }

    /** Collapses the separators a room token may carry, so "综合楼 - 234" becomes "综合楼-234". */
    private static String tidyRoom(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private static String cleanupName(String value) {
        // Remove course code patterns like (3030113067.01), [3030113067.01], 【3030113067.01】
        value = value.replaceAll("[\\(（\\[【]\\d{8,}[^)）\\]】]*[\\)）\\]】]", "");
        // A semicolon-separated group is the page's metadata bundle, e.g. "(李铁夫；限选；全周)"
        // or "(陈志勇；限选；全周；六教6A112)". Without a room it carries neither comma nor digit,
        // so the rules below would leave it inside the course title.
        value = value.replaceAll("[\\(（][^)）]*[;；][^)）]*[\\)）]", "");
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
        // A week/section descriptor is a line made only of numbers, separators and 周/节 wording.
        // Merely *ending* in 周 is not enough: a single-cell course string such as
        // "科技创新与挑战3D(李铁夫；限选；全周)" ends that way and is a real course title.
        return value.matches("^(教师|老师|任课教师|地点|上课地点|教室|校区|周次|周数|节次|时间|学分|课程号|教学班).*?")
                || value.matches("^[\\d\\s,，、()（）单双全周节第\\-~～－–至—]+$")
                || value.matches("^\\d{1,2}[:：]\\d{2}.*");
    }

    private static boolean isNoise(String value, boolean nameWasExplicit) {
        String compact = value.replaceAll("\\s", "");
        if (compact.isEmpty() || compact.equals("课表") || compact.equals("课程表")) return true;
        if (compact.matches(".*(?:学年|学期).*(?:的)?课表$")) return true;
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
        // Very short text without course-related keywords is usually a teacher name or a stray
        // label. Plenty of real courses are this short though (体育, 线性代数, 高等代数), so only
        // apply the guard to names we had to guess out of cell text, and let well-known short
        // subjects through either way.
        if (nameWasExplicit || isCommonShortSubject(compact)) return false;
        if (compact.length() <= 4 && !compact.matches(".*[课程学实验设计理论基础导论概论原理技术方法].*")) return true;
        return false;
    }

    /**
     * Short course names that turn up across schools and would otherwise be mistaken for a teacher
     * name by the length guard in {@link #isNoise}.
     */
    private static boolean isCommonShortSubject(String compact) {
        return compact.matches(".*(体育|代数|几何|英语|日语|俄语|法语|德语|语文|数学|物理|化学|生物"
                + "|政治|历史|地理|音乐|美术|军训|概率|统计|会计|法学|哲学|写作|阅读|听力|口语"
                + "|素描|色彩|声乐|钢琴|舞蹈|游泳|篮球|足球|排球|网球|羽毛球|乒乓球|武术|健美操).*");
    }
}
