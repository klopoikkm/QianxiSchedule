package com.qianxi.schedule.data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ScheduleTime {
    private ScheduleTime() {}

    /**
     * Maps a course's start/end minutes onto the period grid defined by the user's class times.
     * Returns {startNode, endNode} (1-based, inclusive). The start node is the period whose window
     * best contains startMinute; the end node likewise for endMinute. Falls back to the nearest
     * period by minute distance when a course spans outside all configured periods.
     */
    public static int[] nodeRange(List<AppSettings.ClassTime> classTimes, int startMinute, int endMinute) {
        if (classTimes == null || classTimes.isEmpty()) return new int[]{1, 1};
        int startNode = nodeForMinute(classTimes, startMinute, true);
        int endNode = nodeForMinute(classTimes, endMinute, false);
        if (endNode < startNode) endNode = startNode;
        return new int[]{startNode, endNode};
    }

    /**
     * Finds the 1-based period index best matching a minute-of-day. When atStart is true the minute
     * marks a course's beginning (prefer the period it falls within or the next one); otherwise it
     * marks the end (prefer the period it falls within or the previous one).
     */
    private static int nodeForMinute(List<AppSettings.ClassTime> classTimes, int minute, boolean atStart) {
        for (int i = 0; i < classTimes.size(); i++) {
            AppSettings.ClassTime slot = classTimes.get(i);
            if (minute >= slot.startMinute && minute <= slot.endMinute) return i + 1;
        }
        // Not inside any slot: pick the nearest by distance to the relevant edge.
        int best = 1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < classTimes.size(); i++) {
            AppSettings.ClassTime slot = classTimes.get(i);
            int edge = atStart ? slot.startMinute : slot.endMinute;
            int dist = Math.abs(minute - edge);
            if (dist < bestDist) {
                bestDist = dist;
                best = i + 1;
            }
        }
        return best;
    }

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

    /** Start-of-day minute for a 1-based period, clamped to the configured class times. */
    public static int startMinuteForNode(List<AppSettings.ClassTime> classTimes, int node) {
        if (classTimes == null || classTimes.isEmpty()) return 8 * 60;
        int index = Math.max(1, Math.min(classTimes.size(), node)) - 1;
        return classTimes.get(index).startMinute;
    }

    /** End-of-day minute for a 1-based period, clamped to the configured class times. */
    public static int endMinuteForNode(List<AppSettings.ClassTime> classTimes, int node) {
        if (classTimes == null || classTimes.isEmpty()) return 8 * 60 + 45;
        int index = Math.max(1, Math.min(classTimes.size(), node)) - 1;
        return classTimes.get(index).endMinute;
    }
}
