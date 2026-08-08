package com.qianxi.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.qianxi.schedule.importer.ImportAdapter;
import com.qianxi.schedule.importer.SchoolProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppSettings {
    private static final String PREFS = "qianxi_settings";
    private final SharedPreferences preferences;

    /**
     * Class time slot: maps period index to start/end minutes.
     */
    public static final class ClassTime {
        public final int period;
        public final int startMinute;
        public final int endMinute;

        public ClassTime(int period, int startMinute, int endMinute) {
            this.period = period;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("period", period);
                obj.put("start", startMinute);
                obj.put("end", endMinute);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static ClassTime fromJson(JSONObject obj) {
            if (obj == null) return null;
            try {
                return new ClassTime(
                    obj.getInt("period"),
                    obj.getInt("start"),
                    obj.getInt("end")
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    public AppSettings(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public LocalDate semesterStart() {
        long fallback = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
        return LocalDate.ofEpochDay(preferences.getLong("semester_start", fallback));
    }

    public void setSemesterStart(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        preferences.edit().putLong("semester_start", monday.toEpochDay()).apply();
    }

    /** Total weeks in the semester (10–30, default 20). */
    public int totalWeeks() {
        return Math.max(10, Math.min(30, preferences.getInt("total_weeks", 20)));
    }

    public void setTotalWeeks(int weeks) {
        preferences.edit().putInt("total_weeks", Math.max(10, Math.min(30, weeks))).apply();
    }

    public boolean autoSilentEnabled() {
        return preferences.getBoolean("auto_silent", false);
    }

    public void setAutoSilentEnabled(boolean enabled) {
        preferences.edit().putBoolean("auto_silent", enabled).apply();
    }

    public String schoolUrl() {
        return preferences.getString("school_url", "");
    }

    public void setSchoolUrl(String url) {
        preferences.edit().putString("school_url", url).apply();
    }

    /** Height in dp of a single period row in the schedule grid (user-adjustable, 40–80). */
    public int itemHeightDp() {
        return Math.max(40, Math.min(80, preferences.getInt("item_height_dp", 56)));
    }

    public void setItemHeightDp(int value) {
        preferences.edit().putInt("item_height_dp", Math.max(40, Math.min(80, value))).apply();
    }

    /** Absolute path to a copied-in background image, or empty for the default gradient. */
    public String backgroundPath() {
        return preferences.getString("background_path", "");
    }

    public void setBackgroundPath(String path) {
        preferences.edit().putString("background_path", path == null ? "" : path).apply();
    }

    /**
     * Text enhancement mode for better readability over background images.
     * 0 = none (default), 1 = light overlay, 2 = dark overlay
     */
    public int textEnhancementMode() {
        return Math.max(0, Math.min(2, preferences.getInt("text_enhancement_mode", 0)));
    }

    public void setTextEnhancementMode(int mode) {
        preferences.edit().putInt("text_enhancement_mode", Math.max(0, Math.min(2, mode))).apply();
    }

    /**
     * Per-slot preferred course: when several fully-overlapping courses share a slot, this records
     * which course id the user chose to show on top. Keyed by "day-startNode".
     */
    public long preferredCourseForSlot(int dayOfWeek, int startNode) {
        return preferences.getLong("slot_pref_" + dayOfWeek + "_" + startNode, 0L);
    }

    public void setPreferredCourseForSlot(int dayOfWeek, int startNode, long courseId) {
        preferences.edit().putLong("slot_pref_" + dayOfWeek + "_" + startNode, courseId).apply();
    }

    public String selectedSchoolProfileId() {
        return preferences.getString("selected_school_profile", SchoolProfile.CUSTOM_ENTRY_ID);
    }

    public void setSelectedSchoolProfileId(String id) {
        preferences.edit().putString("selected_school_profile", id).apply();
    }

    public String selectedAdapterId() {
        return preferences.getString("selected_adapter", ImportAdapter.AUTO);
    }

    public void setSelectedAdapterId(String id) {
        preferences.edit().putString("selected_adapter", id).apply();
    }

    public List<SchoolProfile> customSchoolProfiles() {
        List<SchoolProfile> profiles = new ArrayList<>();
        String raw = preferences.getString("custom_school_profiles", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                SchoolProfile profile = SchoolProfile.fromJson(array.optJSONObject(i));
                if (profile != null) profiles.add(profile);
            }
        } catch (Exception ignored) {
        }
        return profiles;
    }

    public SchoolProfile saveCustomSchoolProfile(String name, String url, String adapterId) {
        List<SchoolProfile> profiles = customSchoolProfiles();
        String normalizedUrl = url.trim();
        String existingId = null;
        for (SchoolProfile profile : profiles) {
            if (profile.url.equalsIgnoreCase(normalizedUrl)) {
                existingId = profile.id;
                break;
            }
        }
        if (existingId != null) {
            final String replaceId = existingId;
            profiles.removeIf(profile -> replaceId.equals(profile.id));
        }
        SchoolProfile saved = new SchoolProfile(
                existingId == null ? "custom-" + UUID.randomUUID() : existingId,
                name.trim(), normalizedUrl, adapterId, true);
        profiles.add(0, saved);
        while (profiles.size() > 20) profiles.remove(profiles.size() - 1);
        writeProfiles(profiles);
        setSelectedSchoolProfileId(saved.id);
        return saved;
    }

    public void deleteCustomSchoolProfile(String id) {
        List<SchoolProfile> profiles = customSchoolProfiles();
        profiles.removeIf(profile -> profile.id.equals(id));
        writeProfiles(profiles);
        if (id.equals(selectedSchoolProfileId())) {
            setSelectedSchoolProfileId(SchoolProfile.CUSTOM_ENTRY_ID);
        }
    }

    public void replaceCustomSchoolProfiles(List<SchoolProfile> profiles) {
        List<SchoolProfile> valid = new ArrayList<>();
        for (SchoolProfile profile : profiles) {
            if (profile != null && profile.custom && !profile.url.trim().isEmpty()) {
                valid.add(profile);
                if (valid.size() == 20) break;
            }
        }
        writeProfiles(valid);
    }

    private void writeProfiles(List<SchoolProfile> profiles) {
        JSONArray array = new JSONArray();
        for (SchoolProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (Exception ignored) {}
        }
        preferences.edit().putString("custom_school_profiles", array.toString()).apply();
    }

    /**
     * A named set of class times. Schools differ in period count and timing, and a single student
     * may need more than one (summer vs winter schedule, or a second campus), so 作息 is stored as a
     * list of tables with one marked active — the model WakeUp uses for its TimeTable/TimeDetail
     * pair, kept flat here because periods are the only detail we need.
     */
    public static final class ClassTimeTable {
        public final String id;
        public final String name;
        public final List<ClassTime> times;

        public ClassTimeTable(String id, String name, List<ClassTime> times) {
            this.id = id;
            this.name = name;
            this.times = times == null ? new ArrayList<>() : times;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("name", name);
                JSONArray array = new JSONArray();
                for (ClassTime time : times) {
                    if (time == null || time.startMinute < 0 || time.endMinute <= time.startMinute) continue;
                    array.put(time.toJson());
                }
                obj.put("times", array);
            } catch (Exception ignored) {
            }
            return obj;
        }

        public static ClassTimeTable fromJson(JSONObject obj) {
            if (obj == null) return null;
            String id = obj.optString("id", "");
            if (id.isEmpty()) return null;
            List<ClassTime> times = new ArrayList<>();
            JSONArray array = obj.optJSONArray("times");
            for (int i = 0; array != null && i < array.length(); i++) {
                ClassTime time = ClassTime.fromJson(array.optJSONObject(i));
                if (time != null) times.add(time);
            }
            if (times.isEmpty()) return null;
            String name = obj.optString("name", "");
            return new ClassTimeTable(id, name.isEmpty() ? "作息" : name, times);
        }
    }

    private static final String DEFAULT_TABLE_ID = "default";

    /**
     * All saved 作息 tables, always at least one. On first run (or straight after an upgrade) the
     * legacy single {@code class_times} list is migrated into a table named 默认作息 so existing
     * users keep the schedule they had.
     */
    public List<ClassTimeTable> classTimeTables() {
        List<ClassTimeTable> tables = new ArrayList<>();
        String raw = preferences.getString("class_time_tables", "");
        if (!raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    ClassTimeTable table = ClassTimeTable.fromJson(array.optJSONObject(i));
                    if (table != null) tables.add(table);
                }
            } catch (Exception ignored) {
            }
        }
        if (tables.isEmpty()) {
            tables.add(new ClassTimeTable(DEFAULT_TABLE_ID, "默认作息", legacyClassTimes()));
        }
        return tables;
    }

    /** Reads the pre-multi-table list, falling back to the built-in default schedule. */
    private List<ClassTime> legacyClassTimes() {
        String raw = preferences.getString("class_times", "");
        if (raw.isEmpty()) return defaultClassTimes();
        try {
            List<ClassTime> times = new ArrayList<>();
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                ClassTime time = ClassTime.fromJson(array.optJSONObject(i));
                if (time != null) times.add(time);
            }
            return times.isEmpty() ? defaultClassTimes() : times;
        } catch (Exception e) {
            return defaultClassTimes();
        }
    }

    /** Id of the active 作息 table, coerced to an existing table. */
    public String currentClassTimeTableId() {
        String id = preferences.getString("current_class_time_table", DEFAULT_TABLE_ID);
        for (ClassTimeTable table : classTimeTables()) {
            if (table.id.equals(id)) return id;
        }
        return classTimeTables().get(0).id;
    }

    public void setCurrentClassTimeTableId(String id) {
        if (id == null || id.isEmpty()) return;
        preferences.edit().putString("current_class_time_table", id).apply();
    }

    /** Creates a table seeded from the default schedule and makes it active. */
    public ClassTimeTable createClassTimeTable(String name) {
        List<ClassTimeTable> tables = classTimeTables();
        ClassTimeTable created = new ClassTimeTable("table-" + UUID.randomUUID(),
                name == null || name.trim().isEmpty() ? "新作息" : name.trim(), defaultClassTimes());
        tables.add(created);
        writeClassTimeTables(tables);
        setCurrentClassTimeTableId(created.id);
        return created;
    }

    public void renameClassTimeTable(String id, String name) {
        if (name == null || name.trim().isEmpty()) return;
        List<ClassTimeTable> tables = classTimeTables();
        List<ClassTimeTable> updated = new ArrayList<>();
        for (ClassTimeTable table : tables) {
            updated.add(table.id.equals(id)
                    ? new ClassTimeTable(table.id, name.trim(), table.times) : table);
        }
        writeClassTimeTables(updated);
    }

    /** Deletes a table. The last remaining table is kept so 作息 is never empty. */
    public void deleteClassTimeTable(String id) {
        List<ClassTimeTable> tables = classTimeTables();
        if (tables.size() <= 1) return;
        tables.removeIf(table -> table.id.equals(id));
        writeClassTimeTables(tables);
        if (id.equals(preferences.getString("current_class_time_table", DEFAULT_TABLE_ID))) {
            setCurrentClassTimeTableId(tables.get(0).id);
        }
    }

    private void writeClassTimeTables(List<ClassTimeTable> tables) {
        JSONArray array = new JSONArray();
        for (ClassTimeTable table : tables) {
            if (table == null || table.times.isEmpty()) continue;
            array.put(table.toJson());
        }
        preferences.edit().putString("class_time_tables", array.toString()).apply();
    }

    /**
     * Class times of the active 作息 table. Never empty: falls back to the default schedule.
     */
    public List<ClassTime> classTimes() {
        String current = currentClassTimeTableId();
        for (ClassTimeTable table : classTimeTables()) {
            if (table.id.equals(current)) return table.times;
        }
        return defaultClassTimes();
    }

    /**
     * Saves class time slots into the active 作息 table. Filters out invalid entries (negative
     * times, end <= start); an all-invalid list is ignored rather than emptying the table.
     */
    public void setClassTimes(List<ClassTime> times) {
        List<ClassTime> valid = new ArrayList<>();
        for (ClassTime time : times == null ? new ArrayList<ClassTime>() : times) {
            if (time == null || time.startMinute < 0 || time.endMinute <= time.startMinute) continue;
            valid.add(time);
        }
        if (valid.isEmpty()) valid = defaultClassTimes();
        String current = currentClassTimeTableId();
        List<ClassTimeTable> tables = classTimeTables();
        List<ClassTimeTable> updated = new ArrayList<>();
        for (ClassTimeTable table : tables) {
            updated.add(table.id.equals(current)
                    ? new ClassTimeTable(table.id, table.name, valid) : table);
        }
        writeClassTimeTables(updated);
        // Keep the legacy key in step so a downgrade still finds the active schedule.
        JSONArray array = new JSONArray();
        for (ClassTime time : valid) {
            try { array.put(time.toJson()); } catch (Exception ignored) {}
        }
        preferences.edit().putString("class_times", array.toString()).apply();
    }

    /**
     * Default class schedule: 12 periods, 08:00-22:00, 45min per class, 5-15min breaks.
     */
    private List<ClassTime> defaultClassTimes() {
        List<ClassTime> times = new ArrayList<>();
        times.add(new ClassTime(1, 8 * 60, 8 * 60 + 45));          // 08:00-08:45
        times.add(new ClassTime(2, 8 * 60 + 50, 9 * 60 + 35));     // 08:50-09:35
        times.add(new ClassTime(3, 9 * 60 + 55, 10 * 60 + 40));    // 09:55-10:40
        times.add(new ClassTime(4, 10 * 60 + 45, 11 * 60 + 30));   // 10:45-11:30
        times.add(new ClassTime(5, 11 * 60 + 35, 12 * 60 + 20));   // 11:35-12:20
        times.add(new ClassTime(6, 14 * 60, 14 * 60 + 45));        // 14:00-14:45
        times.add(new ClassTime(7, 14 * 60 + 50, 15 * 60 + 35));   // 14:50-15:35
        times.add(new ClassTime(8, 15 * 60 + 55, 16 * 60 + 40));   // 15:55-16:40
        times.add(new ClassTime(9, 16 * 60 + 45, 17 * 60 + 30));   // 16:45-17:30
        times.add(new ClassTime(10, 17 * 60 + 35, 18 * 60 + 20));  // 17:35-18:20
        times.add(new ClassTime(11, 19 * 60, 19 * 60 + 45));       // 19:00-19:45
        times.add(new ClassTime(12, 19 * 60 + 50, 20 * 60 + 35));  // 19:50-20:35
        return times;
    }
}
