package com.qianxi.schedule.data;

import java.time.LocalDate;
import java.util.List;

/**
 * Resolves the class happening now, or the next class after now, on real calendar dates. Course
 * rows only store a weekday plus a week mask, so searching by weekday inside the current week
 * wraps back to this Monday and reports a date that has already passed.
 */
public final class NextClass {
    public enum Kind { ONGOING, UPCOMING }

    public final Kind kind;
    public final Course course;
    public final LocalDate date;

    private NextClass(Kind kind, Course course, LocalDate date) {
        this.kind = kind;
        this.course = course;
        this.date = date;
    }

    /**
     * @param courses       all stored course rows, not just the current teaching week
     * @param semesterStart Monday that week 1 starts on
     * @param totalWeeks    last teaching week the user configured
     * @param today         calendar date to resolve against
     * @param nowMinute     minute-of-day on {@code today}
     * @return the class in progress, else the soonest later one, else null
     */
    public static NextClass find(List<Course> courses, LocalDate semesterStart, int totalWeeks,
                                 LocalDate today, int nowMinute) {
        if (courses == null || courses.isEmpty() || semesterStart == null || today == null) {
            return null;
        }
        int fromWeek = ScheduleTime.weekOf(semesterStart, today);
        if (fromWeek > totalWeeks) return null;

        Course ongoing = null;
        Course upcoming = null;
        LocalDate upcomingDate = null;
        // Each course is walked forward from the current week to its own first remaining date, so
        // the cost stays proportional to the course count rather than to the days left in term.
        for (Course course : courses) {
            LocalDate date = firstDateFrom(course, semesterStart, totalWeeks, fromWeek, today, nowMinute);
            if (date == null) continue;
            if (date.equals(today) && course.startMinute <= nowMinute) {
                if (ongoing == null || course.startMinute < ongoing.startMinute) ongoing = course;
                continue;
            }
            if (upcomingDate == null || date.isBefore(upcomingDate)
                    || (date.equals(upcomingDate) && course.startMinute < upcoming.startMinute)) {
                upcoming = course;
                upcomingDate = date;
            }
        }
        if (ongoing != null) return new NextClass(Kind.ONGOING, ongoing, today);
        if (upcoming != null) return new NextClass(Kind.UPCOMING, upcoming, upcomingDate);
        return null;
    }

    /**
     * Earliest date this course still runs on: today when it is in progress or yet to start,
     * otherwise the next week it occurs in. Null once it is over for the term.
     */
    private static LocalDate firstDateFrom(Course course, LocalDate semesterStart, int totalWeeks,
                                           int fromWeek, LocalDate today, int nowMinute) {
        // startWeek tracks the first set bit of weekMask everywhere it is written, so a course that
        // only runs late in the term does not get its earlier weeks probed one by one.
        for (int week = Math.max(Math.max(1, fromWeek), course.startWeek); week <= totalWeeks; week++) {
            if (!course.occursInWeek(week)) continue;
            LocalDate date = ScheduleTime.dateOf(semesterStart, week, course.dayOfWeek);
            if (date.isBefore(today)) continue;
            // Today's slot only counts while the class is still running or has not begun.
            if (date.equals(today) && course.endMinute <= nowMinute) continue;
            return date;
        }
        return null;
    }
}
