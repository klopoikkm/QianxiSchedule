package com.qianxi.schedule.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;

public final class ScheduleTimeTest {
    @Test
    public void calculatesWeekAndCourseDate() {
        LocalDate semesterStart = LocalDate.of(2026, 9, 7);
        assertEquals(1, ScheduleTime.weekOf(semesterStart, LocalDate.of(2026, 9, 7)));
        assertEquals(3, ScheduleTime.weekOf(semesterStart, LocalDate.of(2026, 9, 21)));
        assertEquals(LocalDate.of(2026, 9, 23), ScheduleTime.dateOf(semesterStart, 3, 3));
    }
}
