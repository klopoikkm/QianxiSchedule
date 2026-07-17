package com.qianxi.schedule.silence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        new Thread(() -> {
            try { AlarmScheduler.reschedule(context); }
            finally { result.finish(); }
        }, "qianxi-alarm-refresh").start();
    }
}
