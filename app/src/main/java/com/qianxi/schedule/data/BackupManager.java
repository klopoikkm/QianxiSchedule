package com.qianxi.schedule.data;

import com.qianxi.schedule.importer.SchoolProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Serializes user-owned schedule data without including WebView credentials. */
public final class BackupManager {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_COURSES = 2000;

    public static final class Backup {
        public final LocalDate semesterStart;
        public final boolean autoSilent;
        public final String schoolUrl;
        public final String adapterId;
        public final String selectedProfileId;
        public final List<SchoolProfile> profiles;
        public final List<Course> courses;

        private Backup(LocalDate semesterStart, boolean autoSilent, String schoolUrl,
                       String adapterId, String selectedProfileId, List<SchoolProfile> profiles,
                       List<Course> courses) {
            this.semesterStart = semesterStart;
            this.autoSilent = autoSilent;
            this.schoolUrl = schoolUrl;
            this.adapterId = adapterId;
            this.selectedProfileId = selectedProfileId;
            this.profiles = Collections.unmodifiableList(profiles);
            this.courses = Collections.unmodifiableList(courses);
        }
    }

    private BackupManager() {}

    public static String encode(AppSettings settings, List<Course> courses) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", SCHEMA_VERSION);
        root.put("app", "QianxiSchedule");
        root.put("semesterStart", settings.semesterStart().toString());
        root.put("autoSilent", settings.autoSilentEnabled());
        root.put("schoolUrl", settings.schoolUrl());
        root.put("adapterId", settings.selectedAdapterId());
        root.put("selectedProfileId", settings.selectedSchoolProfileId());

        JSONArray profiles = new JSONArray();
        for (SchoolProfile profile : settings.customSchoolProfiles()) profiles.put(profile.toJson());
        root.put("profiles", profiles);

        JSONArray values = new JSONArray();
        for (Course course : courses) values.put(courseToJson(course));
        root.put("courses", values);
        return root.toString(2);
    }

    public static Backup decode(String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) throw new JSONException("备份文件为空");
        JSONObject root = new JSONObject(raw);
        if (root.optInt("schema", -1) != SCHEMA_VERSION
                || !"QianxiSchedule".equals(root.optString("app", ""))) {
            throw new JSONException("备份文件版本不受支持");
        }

        LocalDate semesterStart;
        try {
            semesterStart = LocalDate.parse(root.getString("semesterStart"));
        } catch (Exception exception) {
            throw new JSONException("学期开始日期无效");
        }
        JSONArray values = root.optJSONArray("courses");
        if (values == null) throw new JSONException("备份文件缺少课程数据");
        if (values.length() > MAX_COURSES) throw new JSONException("课程数量超过上限");

        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            Course course = courseFromJson(values.optJSONObject(i));
            if (course != null) courses.add(course);
        }
        if (courses.size() != values.length()) throw new JSONException("备份中包含无效课程记录");

        List<SchoolProfile> profiles = new ArrayList<>();
        JSONArray profileValues = root.optJSONArray("profiles");
        if (profileValues != null) {
            if (profileValues.length() > 20) throw new JSONException("教务入口数量超过上限");
            for (int i = 0; i < profileValues.length(); i++) {
                SchoolProfile profile = SchoolProfile.fromJson(profileValues.optJSONObject(i));
                if (profile == null || !profile.custom) throw new JSONException("备份中包含无效教务入口");
                profiles.add(profile);
            }
        }
        return new Backup(semesterStart, root.optBoolean("autoSilent", false),
                root.optString("schoolUrl", ""), root.optString("adapterId", "auto"),
                root.optString("selectedProfileId", SchoolProfile.CUSTOM_ENTRY_ID), profiles, courses);
    }

    private static JSONObject courseToJson(Course course) throws JSONException {
        return new JSONObject()
                .put("name", course.name)
                .put("teacher", course.teacher)
                .put("location", course.location)
                .put("dayOfWeek", course.dayOfWeek)
                .put("startMinute", course.startMinute)
                .put("endMinute", course.endMinute)
                .put("startWeek", course.startWeek)
                .put("endWeek", course.endWeek)
                .put("parity", course.parity)
                .put("weekMask", course.weekMask)
                .put("color", course.color);
    }

    private static Course courseFromJson(JSONObject value) {
        if (value == null) return null;
        String name = value.optString("name", "").trim();
        int day = value.optInt("dayOfWeek", 0);
        int start = value.optInt("startMinute", -1);
        int end = value.optInt("endMinute", -1);
        int firstWeek = value.optInt("startWeek", 0);
        int lastWeek = value.optInt("endWeek", 0);
        int parity = value.optInt("parity", -1);
        long mask = value.optLong("weekMask", 0L);
        if (name.isEmpty() || day < 1 || day > 7 || start < 0 || end <= start || end > 24 * 60
                || firstWeek < 1 || lastWeek < firstWeek || lastWeek > 60
                || parity < 0 || parity > 2 || mask < 0) return null;
        Course course = new Course();
        course.name = name;
        course.teacher = value.optString("teacher", "").trim();
        course.location = value.optString("location", "").trim();
        course.dayOfWeek = day;
        course.startMinute = start;
        course.endMinute = end;
        course.startWeek = firstWeek;
        course.endWeek = lastWeek;
        course.parity = parity;
        course.weekMask = mask;
        course.color = value.optInt("color", course.color);
        return course;
    }
}
