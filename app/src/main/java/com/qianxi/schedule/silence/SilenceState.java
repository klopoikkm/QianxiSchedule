package com.qianxi.schedule.silence;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SilenceState {
    private static final String PREFS = "qianxi_silence_state";
    private static final String ACTIVE = "active_tokens";
    private static final String ORIGINAL_RINGER = "original_ringer";

    private SilenceState() {}

    public static boolean hasPolicyAccess(Context context) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationPolicyAccessGranted();
    }

    public static synchronized void enter(Context context, String token) {
        if (!hasPolicyAccess(context)) return;
        SharedPreferences prefs = prefs(context);
        Set<String> active = copySet(prefs.getStringSet(ACTIVE, Collections.emptySet()));
        if (active.isEmpty()) saveOriginalMode(context, prefs);
        active.add(token);
        prefs.edit().putStringSet(ACTIVE, active).apply();
        applySilent(context);
    }

    public static synchronized void exit(Context context, String token) {
        SharedPreferences prefs = prefs(context);
        Set<String> active = copySet(prefs.getStringSet(ACTIVE, Collections.emptySet()));
        active.remove(token);
        prefs.edit().putStringSet(ACTIVE, active).apply();
        if (active.isEmpty()) restoreOriginalMode(context, prefs);
    }

    public static synchronized void reconcile(Context context, Set<String> desiredTokens) {
        SharedPreferences prefs = prefs(context);
        Set<String> current = copySet(prefs.getStringSet(ACTIVE, Collections.emptySet()));
        if (desiredTokens.isEmpty()) {
            prefs.edit().putStringSet(ACTIVE, Collections.emptySet()).apply();
            if (!current.isEmpty()) restoreOriginalMode(context, prefs);
            return;
        }
        if (!hasPolicyAccess(context)) return;
        if (current.isEmpty()) saveOriginalMode(context, prefs);
        prefs.edit().putStringSet(ACTIVE, new HashSet<>(desiredTokens)).apply();
        applySilent(context);
    }

    private static void saveOriginalMode(Context context, SharedPreferences prefs) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) prefs.edit().putInt(ORIGINAL_RINGER, audio.getRingerMode()).apply();
    }

    private static void applySilent(Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        try {
            if (audio.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Some OEM builds deny mode changes even after policy access is granted.
        }
    }

    private static void restoreOriginalMode(Context context, SharedPreferences prefs) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null || !hasPolicyAccess(context)) return;
        int original = prefs.getInt(ORIGINAL_RINGER, AudioManager.RINGER_MODE_NORMAL);
        try {
            // Respect a manual user override made while class was in progress.
            if (audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                audio.setRingerMode(original);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        prefs.edit().remove(ORIGINAL_RINGER).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> copySet(Set<String> source) {
        return source == null ? new HashSet<>() : new HashSet<>(source);
    }
}
