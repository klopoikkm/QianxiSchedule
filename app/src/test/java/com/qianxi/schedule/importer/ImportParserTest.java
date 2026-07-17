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
}
