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
    public void extractsMultipleTeachersAndRoomFromEamsString() throws Exception {
        String payload = "{\"items\":[{\"day\":4,\"section\":5,\"endSection\":6,\"text\":"
                + "\"人工智能导论(3030113067.01)(吕宪伟,王强)(4-15,工学馆307)\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("人工智能导论", course.name);
        assertEquals("吕宪伟,王强", course.teacher);
        assertEquals("工学馆307", course.location);
    }

    @Test
    public void populatesStartNodeAndStepFromSections() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"items\":[{\"name\":\"大学物理\",\"day\":2,"
                + "\"section\":3,\"endSection\":5,\"weeks\":\"1-8周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals(3, course.startNode);
        assertEquals(3, course.step);
        assertEquals(5, course.endNode());
    }

    @Test
    public void parsesBinaryWeekString() {
        // "0111111110" → weeks 2..9 (bit n = week n+1).
        long mask = ImportParser.extractWeeks("0111111110");
        assertEquals(0L, mask & 1L);              // week 1 off
        assertTrue((mask & (1L << 1)) != 0);      // week 2 on
        assertTrue((mask & (1L << 8)) != 0);      // week 9 on
        assertEquals(0L, mask & (1L << 9));       // week 10 off
    }

    @Test
    public void importsCourseWithBinaryWeeksField() throws Exception {
        String payload = "{\"items\":[{\"name\":\"汇编语言程序设计\",\"teacher\":\"张旭\","
                + "\"location\":\"综合楼1208\",\"day\":3,\"section\":3,\"endSection\":4,"
                + "\"weeks\":\"0111111111000000000\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEUQ_EAMS);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("汇编语言程序设计", course.name);
        assertEquals("综合楼1208", course.location);
        assertEquals(2, course.startWeek);
        assertEquals(10, course.endWeek);
        assertTrue(!course.occursInWeek(1));
        assertTrue(course.occursInWeek(2));
        assertTrue(course.occursInWeek(10));
        assertTrue(!course.occursInWeek(11));
    }

    @Test
    public void parsesCompactApiClock() {
        assertEquals(8 * 60 + 30, ImportParser.parseClock("083000"));
        assertEquals(21 * 60 + 25, ImportParser.parseClock("21:25"));
        assertEquals(-1, ImportParser.parseClock("25:90"));
    }

    @Test
    public void preservesPageDiagnosticsForUnsupportedLayouts() throws Exception {
        String payload = "{\"adapter\":\"zhengfang\",\"source\":\"page-dom\","
                + "\"sourceUrl\":\"https://jwgl.example.edu/kb\",\"pageTitle\":\"个人课表\","
                + "\"diagnostics\":{\"frames\":1,\"tables\":3,\"candidates\":2},\"items\":[]}";

        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.AUTO);

        assertEquals("个人课表", outcome.pageTitle);
        assertEquals("https://jwgl.example.edu/kb", outcome.sourceUrl);
        assertEquals(1, outcome.frames);
        assertEquals(3, outcome.tables);
        assertEquals(2, outcome.candidates);
    }
}
