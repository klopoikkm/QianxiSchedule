package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.qianxi.schedule.data.Course;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public final class ImportParserTest {
    @Test
    public void parsesOddWeekRange() {
        long mask = ImportParser.extractWeeks("1-16周(单)");
        assertTrue((mask & 1L) != 0);
        assertTrue((mask & (1L << 14)) != 0);
        assertEquals(0L, mask & (1L << 1));
    }

    @Test
    public void parsesDiscontinuousWeeks() {
        long mask = ImportParser.extractWeeks("1-4,7,9-10周");
        assertTrue((mask & (1L << 0)) != 0);
        assertTrue((mask & (1L << 6)) != 0);
        assertTrue((mask & (1L << 9)) != 0);
        assertEquals(0L, mask & (1L << 7));
    }

    @Test
    public void parsesCapturedZhengfangCourse() throws Exception {
        String payload = "{\"items\":[{\"day\":2,\"section\":3,\"text\":"
                + "\"课程名称：高等数学\\n教师：张老师\\n上课地点：A101\\n1-16周(单)\\n第3-4节\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), "正方教务");

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("高等数学", course.name);
        assertEquals("张老师", course.teacher);
        assertEquals("A101", course.location);
        assertEquals(2, course.dayOfWeek);
        assertEquals(10 * 60, course.startMinute);
        assertEquals(11 * 60 + 40, course.endMinute);
        assertTrue(course.occursInWeek(15));
        assertTrue(!course.occursInWeek(16));
    }

    @Test
    public void parsesNortheasternUniversityStructuredCourse() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"source\":\"neu-api\","
                + "\"term\":\"2026-2027-1\",\"campus\":\"浑南校区\",\"items\":[{"
                + "\"name\":\"编译原理\",\"teacher\":\"李老师\",\"location\":\"信息楼B201\","
                + "\"weeks\":\"1-16周(双)\",\"day\":3,\"section\":1,\"endSection\":2,"
                + "\"text\":\"编译原理\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals("2026-2027-1", outcome.term);
        assertEquals("浑南校区", outcome.campus);
        assertEquals(1, outcome.courses.size());
        Course course = outcome.courses.get(0);
        assertEquals(8 * 60 + 30, course.startMinute);
        assertEquals(10 * 60 + 10, course.endMinute);
        assertTrue(course.occursInWeek(2));
        assertTrue(!course.occursInWeek(3));
    }

    @Test
    public void mergesEquivalentSessionsAcrossWeekRanges() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"items\":["
                + "{\"name\":\"大学物理\",\"day\":1,\"section\":3,\"endSection\":4,\"weeks\":\"1-4周\"},"
                + "{\"name\":\"大学物理\",\"day\":1,\"section\":3,\"endSection\":4,\"weeks\":\"5-8周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(1, courses.size());
        assertTrue(courses.get(0).occursInWeek(1));
        assertTrue(courses.get(0).occursInWeek(8));
    }

    @Test
    public void parsesCompactApiClock() {
        assertEquals(8 * 60 + 30, ImportParser.parseClock("083000"));
        assertEquals(21 * 60 + 25, ImportParser.parseClock("21:25"));
        assertEquals(-1, ImportParser.parseClock("25:90"));
    }
}
