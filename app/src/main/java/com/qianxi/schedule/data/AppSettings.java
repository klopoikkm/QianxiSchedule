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
     * Returns class time slots. If none saved, returns default schedule (12 periods).
     */
    public List<ClassTime> classTimes() {
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

    /**
     * Saves class time slots. Filters out invalid entries (negative times, end <= start).
     */
    public void setClassTimes(List<ClassTime> times) {
        JSONArray array = new JSONArray();
        for (ClassTime time : times) {
            if (time.startMinute < 0 || time.endMinute <= time.startMinute) continue;
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
