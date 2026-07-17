package com.qianxi.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

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
}
