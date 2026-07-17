package com.qianxi.schedule.data;

import java.util.Objects;

public final class Course {
    public long id;
    public String name = "";
    public String teacher = "";
    public String location = "";
    public int dayOfWeek = 1;
    public int startMinute = 8 * 60;
    public int endMinute = 9 * 60 + 40;
    public int startWeek = 1;
    public int endWeek = 20;
    public int parity = 0; // 0: every week, 1: odd, 2: even
    public long weekMask = 0L; // Imported irregular week sets; bit 0 means week 1.
    public int color = 0xFF2F9E6F;

    public boolean occursInWeek(int week) {
        if (weekMask != 0L && week >= 1 && week <= 60) {
            return (weekMask & (1L << (week - 1))) != 0;
        }
        if (week < startWeek || week > endWeek) return false;
        return parity == 0 || (parity == 1 && week % 2 == 1) || (parity == 2 && week % 2 == 0);
    }

    public Course copy() {
        Course value = new Course();
        value.id = id;
        value.name = name;
        value.teacher = teacher;
        value.location = location;
        value.dayOfWeek = dayOfWeek;
        value.startMinute = startMinute;
        value.endMinute = endMinute;
        value.startWeek = startWeek;
        value.endWeek = endWeek;
        value.parity = parity;
        value.weekMask = weekMask;
        value.color = color;
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Course)) return false;
        Course course = (Course) other;
        return dayOfWeek == course.dayOfWeek
                && startMinute == course.startMinute
                && endMinute == course.endMinute
                && startWeek == course.startWeek
                && endWeek == course.endWeek
                && parity == course.parity
                && weekMask == course.weekMask
                && Objects.equals(name, course.name)
                && Objects.equals(teacher, course.teacher)
                && Objects.equals(location, course.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, teacher, location, dayOfWeek, startMinute, endMinute,
                startWeek, endWeek, parity, weekMask);
    }
}
