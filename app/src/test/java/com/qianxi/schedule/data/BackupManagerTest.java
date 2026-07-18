package com.qianxi.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackupManagerTest {
    @Test
    public void decodesCoursesAndSettings() throws Exception {
        String raw = "{\"schema\":1,\"app\":\"QianxiSchedule\","
                + "\"semesterStart\":\"2026-09-07\",\"autoSilent\":true,"
                + "\"schoolUrl\":\"https://jwxt.example.edu/\",\"adapterId\":\"generic\","
                + "\"courses\":[{\"name\":\"数据结构\",\"teacher\":\"李老师\","
                + "\"location\":\"A101\",\"dayOfWeek\":2,\"startMinute\":510,"
                + "\"endMinute\":605,\"startWeek\":1,\"endWeek\":16,"
                + "\"parity\":0,\"weekMask\":0,\"color\":-16711936}],\"profiles\":[]}";

        BackupManager.Backup backup = BackupManager.decode(raw);

        assertEquals("2026-09-07", backup.semesterStart.toString());
        assertTrue(backup.autoSilent);
        assertEquals("generic", backup.adapterId);
        assertEquals(1, backup.courses.size());
        assertEquals("数据结构", backup.courses.get(0).name);
        assertEquals(510, backup.courses.get(0).startMinute);
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsUnsupportedSchema() throws Exception {
        BackupManager.decode("{\"schema\":99,\"app\":\"QianxiSchedule\",\"courses\":[]}");
    }

    @Test(expected = org.json.JSONException.class)
    public void rejectsInvalidCourseRange() throws Exception {
        BackupManager.decode("{\"schema\":1,\"app\":\"QianxiSchedule\","
                + "\"semesterStart\":\"2026-09-07\",\"courses\":[{"
                + "\"name\":\"坏数据\",\"dayOfWeek\":8,\"startMinute\":600,"
                + "\"endMinute\":700,\"startWeek\":1,\"endWeek\":16,\"parity\":0}]}" );
    }
}
