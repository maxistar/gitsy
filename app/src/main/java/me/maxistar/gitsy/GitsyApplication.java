package me.maxistar.gitsy;

import android.app.Application;

import java.time.Clock;
import java.time.ZoneId;

public final class GitsyApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        scheduler().reconcile();
    }

    ScheduledSyncScheduler scheduler() {
        return new ScheduledSyncScheduler(
                new SharedPreferencesScheduledSyncSettings(this),
                new AndroidWorkManagerGateway(this), Clock.systemUTC(), ZoneId.systemDefault());
    }
}
