package me.maxistar.gitsy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.Clock;
import java.time.ZoneId;

public final class ScheduledSyncTimeChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!shouldReconcile(action)) return;
        SharedPreferencesScheduledSyncSettings settings =
                new SharedPreferencesScheduledSyncSettings(context);
        if (!settings.load().isEnabled()) return;
        new ScheduledSyncScheduler(
                settings,
                new AndroidWorkManagerGateway(context), Clock.systemUTC(), ZoneId.systemDefault())
                .reconcile();
    }

    static boolean shouldReconcile(String action) {
        return Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action);
    }
}
