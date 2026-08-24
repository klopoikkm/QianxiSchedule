package com.qianxi.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NextClassTest {
    private static final LocalDate SEMESTER = LocalDate.of(2026, 9, 7);

    @Test
    public void prefersTheClassInProgressToday() {
        Course morning = course("早课", 1, 8 * 60, 9 * 60 + 40);
        Course noon = course("午课", 1, 10 * 60, 11 * 60 + 40);
        NextClass found = NextClass.find(list(morning, noon), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 9 * 60);
        assertNotNull(found);
        assertEquals(NextClass.Kind.ONGOING, found.kind);
        assertEquals("早课", found.course.name);
        assertEquals(LocalDate.of(2026, 9, 7), found.date);
    }

    @Test
    public void picksTheLaterClassOnTheSameCalendarDay() {
        Course morning = course("早课", 1, 8 * 60, 9 * 60 + 40);
        Course noon = course("午课", 1, 10 * 60, 11 * 60 + 40);
        NextClass found = NextClass.find(list(morning, noon), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 9 * 60 + 50);
        assertNotNull(found);
        assertEquals(NextClass.Kind.UPCOMING, found.kind);
        assertEquals("午课", found.course.name);
        assertEquals(LocalDate.of(2026, 9, 7), found.date);
    }

    @Test
    public void jumpsToTheNextRealDateInsteadOfThisWeeksWeekday() {
        Course monday = course("周一早课", 1, 8 * 60, 9 * 60 + 40);
        NextClass found = NextClass.find(list(monday), SEMESTER, 20,
                LocalDate.of(2026, 9, 11), 20 * 60);
        assertNotNull(found);
        assertEquals(NextClass.Kind.UPCOMING, found.kind);
        assertEquals("周一早课", found.course.name);
        assertEquals(LocalDate.of(2026, 9, 14), found.date);
    }

    @Test
    public void skipsWeeksWhereTheCourseDoesNotOccur() {
        Course oddMonday = course("单周课", 1, 8 * 60, 9 * 60 + 40);
        oddMonday.parity = 1;
        NextClass found = NextClass.find(list(oddMonday), SEMESTER, 20,
                LocalDate.of(2026, 9, 11), 20 * 60);
        assertNotNull(found);
        assertEquals(LocalDate.of(2026, 9, 21), found.date);
    }

    @Test
    public void walksPastAnEmptyCurrentWeek() {
        Course later = course("第五周", 3, 14 * 60, 15 * 60 + 40);
        later.startWeek = 5;
        later.endWeek = 10;
        NextClass found = NextClass.find(list(later), SEMESTER, 20,
                LocalDate.of(2026, 9, 8), 8 * 60);
        assertNotNull(found);
        assertEquals("第五周", found.course.name);
        assertEquals(LocalDate.of(2026, 10, 7), found.date);
    }

    @Test
    public void waitsUntilSemesterStartWhenTodayIsEarlier() {
        Course monday = course("开学第一课", 1, 8 * 60, 9 * 60 + 40);
        NextClass found = NextClass.find(list(monday), SEMESTER, 20,
                LocalDate.of(2026, 8, 30), 21 * 60);
        assertNotNull(found);
        assertEquals(LocalDate.of(2026, 9, 7), found.date);
    }

    @Test
    public void returnsNullAfterTheConfiguredSemesterEnds() {
        Course monday = course("周一早课", 1, 8 * 60, 9 * 60 + 40);
        assertNull(NextClass.find(list(monday), SEMESTER, 2,
                LocalDate.of(2026, 9, 21), 8 * 60));
    }

    @Test
    public void returnsNullWhenNoCoursesRemain() {
        assertNull(NextClass.find(Collections.emptyList(), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 8 * 60));
    }

    @Test
    public void picksTheEarliestDateWhenSeveralDaysRemain() {
        Course thursday = course("周四课", 4, 8 * 60, 9 * 60 + 40);
        Course wednesday = course("周三课", 3, 14 * 60, 15 * 60 + 40);
        NextClass found = NextClass.find(list(thursday, wednesday), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 20 * 60);
        assertNotNull(found);
        assertEquals("周三课", found.course.name);
        assertEquals(LocalDate.of(2026, 9, 9), found.date);
    }

    @Test
    public void reportsTheEarlierClassWhenTwoOverlapRightNow() {
        Course early = course("先开始", 1, 8 * 60, 10 * 60);
        Course late = course("后开始", 1, 9 * 60, 11 * 60);
        NextClass found = NextClass.find(list(late, early), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 9 * 60 + 30);
        assertNotNull(found);
        assertEquals(NextClass.Kind.ONGOING, found.kind);
        assertEquals("先开始", found.course.name);
    }

    @Test
    public void ignoresWeeksBeyondTheConfiguredSemesterLength() {
        Course late = course("第十八周", 1, 8 * 60, 9 * 60 + 40);
        late.startWeek = 18;
        late.endWeek = 18;
        assertNull(NextClass.find(list(late), SEMESTER, 16,
                LocalDate.of(2026, 9, 7), 8 * 60));
    }

    @Test
    public void followsAnIrregularWeekMask() {
        Course sparse = course("不连续周", 2, 8 * 60, 9 * 60 + 40);
        sparse.weekMask = (1L << 3) | (1L << 7); // weeks 4 and 8
        NextClass found = NextClass.find(list(sparse), SEMESTER, 20,
                LocalDate.of(2026, 9, 7), 8 * 60);
        assertNotNull(found);
        assertEquals(LocalDate.of(2026, 9, 29), found.date);
    }

    private static List<Course> list(Course... courses) {
        return Arrays.asList(courses);
    }

    private static Course course(String name, int day, int start, int end) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = day;
        course.startMinute = start;
        course.endMinute = end;
        course.startWeek = 1;
        course.endWeek = 20;
        return course;
    }
}
