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

    /** A per-course clock override only survives a backup round-trip if the node range does too. */
    @Test
    public void keepsGridPositionAndCustomMinutes() throws Exception {
        String raw = "{\"schema\":1,\"app\":\"QianxiSchedule\","
                + "\"semesterStart\":\"2026-09-07\",\"courses\":[{\"name\":\"物理实验\","
                + "\"teacher\":\"\",\"location\":\"B203\",\"dayOfWeek\":3,"
                + "\"startMinute\":840,\"endMinute\":1020,\"startNode\":6,\"step\":3,"
                + "\"startWeek\":1,\"endWeek\":16,\"parity\":0,\"weekMask\":0}],\"profiles\":[]}";

        Course course = BackupManager.decode(raw).courses.get(0);

        assertEquals(6, course.startNode);
        assertEquals(3, course.step);
        assertEquals(8, course.endNode());
        assertEquals(840, course.startMinute);
        assertEquals(1020, course.endMinute);
    }

    /** Backups written before the grid columns existed leave the node fields to be derived later. */
    @Test
    public void leavesLegacyGridPositionUnset() throws Exception {
        String raw = "{\"schema\":1,\"app\":\"QianxiSchedule\","
                + "\"semesterStart\":\"2026-09-07\",\"courses\":[{\"name\":\"高等数学\","
                + "\"teacher\":\"\",\"location\":\"\",\"dayOfWeek\":1,"
                + "\"startMinute\":600,\"endMinute\":690,"
                + "\"startWeek\":1,\"endWeek\":16,\"parity\":0,\"weekMask\":0}],\"profiles\":[]}";

        Course course = BackupManager.decode(raw).courses.get(0);

        assertEquals(0, course.startNode);
        assertEquals(0, course.step);
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
