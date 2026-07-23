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
    private static final int DB_VERSION = 2;
    private static volatile CourseDatabase instance;
    private final Context context;

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
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE courses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,teacher TEXT NOT NULL,location TEXT NOT NULL,"
                + "day_of_week INTEGER NOT NULL,start_minute INTEGER NOT NULL,end_minute INTEGER NOT NULL,"
                + "start_node INTEGER NOT NULL DEFAULT 1,step INTEGER NOT NULL DEFAULT 1,"
                + "start_week INTEGER NOT NULL,end_week INTEGER NOT NULL,parity INTEGER NOT NULL,"
                + "week_mask INTEGER NOT NULL DEFAULT 0,color INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_courses_day_time ON courses(day_of_week,start_minute)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 -> v2: add period-grid columns and back-fill them once from the minute fields.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE courses ADD COLUMN start_node INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE courses ADD COLUMN step INTEGER NOT NULL DEFAULT 1");
            backfillNodes(db);
        }
    }

    /**
     * Maps existing courses onto the period grid once, using the current class-time layout, so
     * pre-v2 rows render correctly without waiting for the user to re-save each course.
     */
    private void backfillNodes(SQLiteDatabase db) {
        java.util.List<AppSettings.ClassTime> times = new AppSettings(context).classTimes();
        try (Cursor cursor = db.query("courses", new String[]{"id", "start_minute", "end_minute"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                int start = cursor.getInt(1);
                int end = cursor.getInt(2);
                int[] range = ScheduleTime.nodeRange(times, start, end);
                ContentValues values = new ContentValues();
                values.put("start_node", range[0]);
                values.put("step", Math.max(1, range[1] - range[0] + 1));
                db.update("courses", values, "id=?", new String[]{String.valueOf(id)});
            }
        }
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

    /**
     * Returns every row belonging to the same logical course as {@code seed}: same name, teacher
     * and location. A logical course may span several rows (one per weekday/period/week segment),
     * mirroring WakeUp's one base + many detail rows. Ordered by day then start node so the editor
     * lists segments predictably.
     */
    public synchronized List<Course> courseGroup(String name, String teacher, String location) {
        List<Course> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("courses", null,
                "name=? AND teacher=? AND location=?",
                new String[]{name.trim(), teacher.trim(), location.trim()},
                null, null, "day_of_week,start_node")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    /**
     * Replaces a logical course: deletes the rows in {@code removeIds} and inserts every segment in
     * {@code segments} in one transaction. Used by the editor's multi-segment save so old segments
     * never linger when the user removes or changes a time slot.
     */
    public synchronized void replaceGroup(List<Long> removeIds, List<Course> segments) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (removeIds != null) {
                for (Long id : removeIds) {
                    if (id != null && id > 0) {
                        db.delete("courses", "id=?", new String[]{String.valueOf(id)});
                    }
                }
            }
            for (Course segment : segments) {
                segment.id = db.insertOrThrow("courses", null, valuesOf(segment));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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

    public synchronized List<Course> conflicts(Course candidate) {
        return conflictsExcluding(candidate, java.util.Collections.emptySet());
    }

    /**
     * Conflicts ignoring the candidate itself and any ids in {@code excludeIds} — used when
     * editing a multi-segment course so its own other segments do not count as conflicts.
     */
    public synchronized List<Course> conflictsExcluding(Course candidate, Set<Long> excludeIds) {
        List<Course> result = new ArrayList<>();
        for (Course course : all()) {
            if (course.id == candidate.id) continue;
            if (excludeIds != null && excludeIds.contains(course.id)) continue;
            if (candidate.conflictsWith(course)) result.add(course);
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
        values.put("start_node", course.startNode);
        values.put("step", Math.max(1, course.step));
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
        course.startNode = cursor.getInt(cursor.getColumnIndexOrThrow("start_node"));
        course.step = Math.max(1, cursor.getInt(cursor.getColumnIndexOrThrow("step")));
        course.startWeek = cursor.getInt(cursor.getColumnIndexOrThrow("start_week"));
        course.endWeek = cursor.getInt(cursor.getColumnIndexOrThrow("end_week"));
        course.parity = cursor.getInt(cursor.getColumnIndexOrThrow("parity"));
        course.weekMask = cursor.getLong(cursor.getColumnIndexOrThrow("week_mask"));
        course.color = cursor.getInt(cursor.getColumnIndexOrThrow("color"));
        return course;
    }
}
