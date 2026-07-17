package com.qianxi.schedule.silence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.qianxi.schedule.data.AppSettings;

public final class SilenceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra(AlarmScheduler.EXTRA_TOKEN);
        if (token == null || token.isEmpty()) return;
        AppSettings settings = new AppSettings(context);
        if (AlarmScheduler.ACTION_START.equals(intent.getAction())) {
            if (settings.autoSilentEnabled()) SilenceState.enter(context, token);
        } else if (AlarmScheduler.ACTION_END.equals(intent.getAction())) {
            SilenceState.exit(context, token);
        }
    }
}
