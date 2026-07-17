package com.qianxi.schedule.data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class ScheduleTime {
    private ScheduleTime() {}

    public static int weekOf(LocalDate semesterStart, LocalDate date) {
        long days = ChronoUnit.DAYS.between(semesterStart, date);
        return Math.max(1, (int) Math.floorDiv(days, 7) + 1);
    }

    public static LocalDate weekStart(LocalDate semesterStart, int week) {
        return semesterStart.plusWeeks(Math.max(0, week - 1L));
    }

    public static LocalDate dateOf(LocalDate semesterStart, int week, int dayOfWeek) {
        return weekStart(semesterStart, week).plusDays(Math.max(0, Math.min(6, dayOfWeek - 1)));
    }

    public static String formatMinutes(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
