package com.qianxi.schedule.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CourseDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "qianxi.db";
    private static final int DB_VERSION = 1;
    private static volatile CourseDatabase instance;

    public static CourseDatabase get(Context context) {
        if (instance == null) {
            synchronized (CourseDatabase.class) {
                if (instance == null) {
                    instance = new CourseDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CourseDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE courses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,teacher TEXT NOT NULL,location TEXT NOT NULL,"
                + "day_of_week INTEGER NOT NULL,start_minute INTEGER NOT NULL,end_minute INTEGER NOT NULL,"
                + "start_week INTEGER NOT NULL,end_week INTEGER NOT NULL,parity INTEGER NOT NULL,"
                + "week_mask INTEGER NOT NULL DEFAULT 0,color INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_courses_day_time ON courses(day_of_week,start_minute)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema version 1 has no migrations yet.
    }

    public synchronized long save(Course course) {
        ContentValues values = valuesOf(course);
        SQLiteDatabase db = getWritableDatabase();
        if (course.id > 0) {
            db.update("courses", values, "id=?", new String[]{String.valueOf(course.id)});
            return course.id;
        }
        course.id = db.insertOrThrow("courses", null, values);
        return course.id;
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("courses", "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized Course find(long id) {
        try (Cursor cursor = getReadableDatabase().query("courses", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public synchronized List<Course> all() {
        List<Course> courses = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("courses", null, null, null,
                null, null, "day_of_week,start_minute,name")) {
            while (cursor.moveToNext()) courses.add(fromCursor(cursor));
        }
        return courses;
    }

    public synchronized List<Course> forWeek(int week) {
        List<Course> result = new ArrayList<>();
        for (Course course : all()) {
            if (course.occursInWeek(week)) result.add(course);
        }
        return result;
    }

    public synchronized void importCourses(List<Course> courses, boolean replace) {
        Set<Course> known = replace ? new HashSet<>() : new HashSet<>(all());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (replace) db.delete("courses", null, null);
            for (Course course : courses) {
                if (!known.add(course)) continue;
                course.id = db.insertOrThrow("courses", null, valuesOf(course));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static ContentValues valuesOf(Course course) {
        ContentValues values = new ContentValues();
        values.put("name", course.name.trim());
        values.put("teacher", course.teacher.trim());
        values.put("location", course.location.trim());
        values.put("day_of_week", course.dayOfWeek);
        values.put("start_minute", course.startMinute);
        values.put("end_minute", course.endMinute);
        values.put("start_week", course.startWeek);
        values.put("end_week", course.endWeek);
        values.put("parity", course.parity);
        values.put("week_mask", course.weekMask);
        values.put("color", course.color);
        return values;
    }

    private static Course fromCursor(Cursor cursor) {
        Course course = new Course();
        course.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        course.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        course.teacher = cursor.getString(cursor.getColumnIndexOrThrow("teacher"));
        course.location = cursor.getString(cursor.getColumnIndexOrThrow("location"));
        course.dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow("day_of_week"));
        course.startMinute = cursor.getInt(cursor.getColumnIndexOrThrow("start_minute"));
        course.endMinute = cursor.getInt(cursor.getColumnIndexOrThrow("end_minute"));
        course.startWeek = cursor.getInt(cursor.getColumnIndexOrThrow("start_week"));
        course.endWeek = cursor.getInt(cursor.getColumnIndexOrThrow("end_week"));
        course.parity = cursor.getInt(cursor.getColumnIndexOrThrow("parity"));
        course.weekMask = cursor.getLong(cursor.getColumnIndexOrThrow("week_mask"));
        course.color = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        return course;
    }
}
