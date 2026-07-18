package com.qianxi.schedule.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CourseTest {
    @Test
    public void detectsOverlappingTimeAndWeeks() {
        Course first = course(1, 8 * 60, 10 * 60, 0);
        Course second = course(1, 9 * 60, 11 * 60, 1);
        assertTrue(first.conflictsWith(second));
    }

    @Test
    public void oddAndEvenSchedulesDoNotConflict() {
        Course odd = course(2, 8 * 60, 10 * 60, 1);
        Course even = course(2, 9 * 60, 11 * 60, 2);
        assertFalse(odd.conflictsWith(even));
    }

    @Test
    public void adjacentCoursesDoNotConflict() {
        Course first = course(3, 8 * 60, 9 * 60, 0);
        Course second = course(3, 9 * 60, 10 * 60, 0);
        assertFalse(first.conflictsWith(second));
    }

    @Test
    public void disjointIrregularWeeksDoNotConflict() {
        Course first = course(4, 8 * 60, 10 * 60, 0);
        Course second = course(4, 9 * 60, 11 * 60, 0);
        first.weekMask = 1L << 2;
        second.weekMask = 1L << 3;
        assertFalse(first.conflictsWith(second));
    }

    private static Course course(int day, int start, int end, int parity) {
        Course course = new Course();
        course.name = "课程";
        course.dayOfWeek = day;
        course.startMinute = start;
        course.endMinute = end;
        course.startWeek = 1;
        course.endWeek = 20;
        course.parity = parity;
        return course;
    }
}
