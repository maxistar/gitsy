package me.maxistar.gitsy;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.Clock;
import java.time.ZoneId;

public final class DailySyncTriggerWorker extends Worker {
    public DailySyncTriggerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferencesScheduledSyncSettings settings =
                new SharedPreferencesScheduledSyncSettings(context);
        AndroidWorkManagerGateway gateway = new AndroidWorkManagerGateway(context);
        Clock clock = Clock.systemUTC();
        ScheduledSyncScheduler scheduler = new ScheduledSyncScheduler(
                settings, gateway, clock, ZoneId.systemDefault());
        new DailySyncTrigger(settings, gateway, scheduler,
                () -> ProjectRepository.getInstance().ensureProjectsLoaded(context), clock).run();
        return Result.success();
    }
}
