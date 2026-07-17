package com.qianxi.schedule.silence;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.data.ScheduleTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AlarmScheduler {
    public static final String ACTION_START = "com.qianxi.schedule.SILENCE_START";
    public static final String ACTION_END = "com.qianxi.schedule.SILENCE_END";
    public static final String EXTRA_TOKEN = "token";
    private static final String PREFS = "qianxi_scheduled_alarms";
    private static final String KEYS = "alarm_keys";
    private static final int DAYS_AHEAD = 35;
    private static final int REFRESH_REQUEST = 9481;

    private AlarmScheduler() {}

    public static synchronized void reschedule(Context rawContext) {
        Context context = rawContext.getApplicationContext();
        cancelCourseAlarms(context);
        AppSettings settings = new AppSettings(context);
        if (!settings.autoSilentEnabled()) {
            SilenceState.reconcile(context, Collections.emptySet());
            return;
        }

        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        LocalDateTime now = LocalDateTime.now();
        LocalDate lastDate = now.toLocalDate().plusDays(DAYS_AHEAD);
        List<Course> courses = CourseDatabase.get(context).all();
        Set<String> storedKeys = new HashSet<>();
        Set<String> currentlyActive = new HashSet<>();

        for (Course course : courses) {
            for (int week = course.startWeek; week <= Math.min(60, course.endWeek); week++) {
                if (!course.occursInWeek(week)) continue;
                LocalDate date = ScheduleTime.dateOf(settings.semesterStart(), week, course.dayOfWeek);
                if (date.isAfter(lastDate)) break;
                if (date.isBefore(now.toLocalDate().minusDays(1))) continue;
                LocalDateTime start = date.atTime(LocalTime.of(course.startMinute / 60, course.startMinute % 60));
                LocalDateTime end = date.atTime(LocalTime.of(course.endMinute / 60, course.endMinute % 60));
                String token = course.id + "@" + date;
                if (!now.isBefore(start) && now.isBefore(end)) currentlyActive.add(token);
                if (start.isAfter(now)) schedule(context, alarms, ACTION_START, token, start, storedKeys);
                if (end.isAfter(now)) schedule(context, alarms, ACTION_END, token, end, storedKeys);
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEYS, storedKeys).apply();
        SilenceState.reconcile(context, currentlyActive);
        ensureDailyRefresh(context);
    }

    public static void ensureDailyRefresh(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, RefreshReceiver.class).setAction("com.qianxi.schedule.REFRESH");
        PendingIntent pending = PendingIntent.getBroadcast(context, REFRESH_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        LocalDateTime next = LocalDate.now().plusDays(1).atTime(2, 15);
        long first = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, pending);
    }

    public static boolean canScheduleExact(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || manager.canScheduleExactAlarms());
    }

    private static void schedule(Context context, AlarmManager alarms, String action, String token,
                                 LocalDateTime time, Set<String> storedKeys) {
        int requestCode = requestCode(action, token);
        Intent intent = new Intent(context, SilenceReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_TOKEN, token);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            }
            storedKeys.add(action + "|" + requestCode);
        } catch (SecurityException ignored) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            storedKeys.add(action + "|" + requestCode);
        }
    }

    private static void cancelCourseAlarms(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> keys = prefs.getStringSet(KEYS, Collections.emptySet());
        if (alarms != null && keys != null) {
            for (String key : new HashSet<>(keys)) {
                int split = key.lastIndexOf('|');
                if (split < 0) continue;
                String action = key.substring(0, split);
                int requestCode;
                try { requestCode = Integer.parseInt(key.substring(split + 1)); }
                catch (NumberFormatException exception) { continue; }
                Intent intent = new Intent(context, SilenceReceiver.class).setAction(action);
                PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarms.cancel(pending);
                    pending.cancel();
                }
            }
        }
        prefs.edit().remove(KEYS).apply();
    }

    private static int requestCode(String action, String token) {
        return (action + '|' + token).hashCode() & 0x7FFFFFFF;
    }
}
