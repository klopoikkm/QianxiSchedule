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
}
