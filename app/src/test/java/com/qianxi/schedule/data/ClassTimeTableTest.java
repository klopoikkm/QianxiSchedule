package com.qianxi.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialization of a 作息 table. This is the format an upgrade migrates the old single class-time
 * list into, so a round-trip defect would silently drop a user's schedule.
 */
public final class ClassTimeTableTest {
    @Test
    public void roundTripsPeriodsAndName() {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60 + 20, 9 * 60 + 5));
        times.add(new AppSettings.ClassTime(2, 9 * 60 + 15, 10 * 60));

        AppSettings.ClassTimeTable restored = AppSettings.ClassTimeTable.fromJson(
                new AppSettings.ClassTimeTable("summer", "夏季作息", times).toJson());

        assertEquals("summer", restored.id);
        assertEquals("夏季作息", restored.name);
        assertEquals(2, restored.times.size());
        assertEquals(1, restored.times.get(0).period);
        assertEquals(8 * 60 + 20, restored.times.get(0).startMinute);
        assertEquals(9 * 60 + 5, restored.times.get(0).endMinute);
        assertEquals(2, restored.times.get(1).period);
        assertEquals(10 * 60, restored.times.get(1).endMinute);
    }

    @Test
    public void dropsPeriodsWithAnEndBeforeTheirStart() {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60, 8 * 60 + 45));
        times.add(new AppSettings.ClassTime(2, 10 * 60, 9 * 60));   // invalid
        times.add(new AppSettings.ClassTime(3, -5, 60));            // invalid

        JSONObject json = new AppSettings.ClassTimeTable("t", "作息", times).toJson();
        JSONArray stored = json.optJSONArray("times");

        assertEquals(1, stored.length());
        assertEquals(1, AppSettings.ClassTimeTable.fromJson(json).times.size());
    }

    @Test
    public void rejectsTablesWithoutIdOrPeriods() {
        assertNull(AppSettings.ClassTimeTable.fromJson(null));
        // No id: cannot be addressed or activated, so it must not become a phantom table.
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60, 8 * 60 + 45));
        assertNull(AppSettings.ClassTimeTable.fromJson(
                new AppSettings.ClassTimeTable("", "作息", times).toJson()));
        // An id but no usable periods would render an empty grid.
        assertNull(AppSettings.ClassTimeTable.fromJson(
                new AppSettings.ClassTimeTable("t", "作息", new ArrayList<>()).toJson()));
    }

    @Test
    public void namesAnUnnamedTableRatherThanShowingBlank() {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60, 8 * 60 + 45));

        assertEquals("作息", AppSettings.ClassTimeTable.fromJson(
                new AppSettings.ClassTimeTable("t", "", times).toJson()).name);
    }
}
