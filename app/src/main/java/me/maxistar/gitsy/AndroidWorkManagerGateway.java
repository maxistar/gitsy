package me.maxistar.gitsy;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.WorkInfo;
import com.google.common.util.concurrent.ListenableFuture;

final class AndroidWorkManagerGateway implements WorkManagerGateway {
    private final WorkManager workManager;

    AndroidWorkManagerGateway(Context context) {
        workManager = WorkManager.getInstance(context.getApplicationContext());
    }

    @Override public void replaceDailyTrigger(Instant runAt) {
        OneTimeWorkRequest request = dailyRequest(runAt, Instant.now());
        workManager.enqueueUniqueWork(ScheduledSyncConstants.DAILY_WORK_NAME,
                ExistingWorkPolicy.REPLACE, request);
    }

    @Override public void cancelDailyTrigger() {
        workManager.cancelUniqueWork(ScheduledSyncConstants.DAILY_WORK_NAME);
    }

    @Override public void enqueueProject(String projectId, long triggerTimeMillis) {
        OneTimeWorkRequest request = projectRequest(projectId, triggerTimeMillis);
        workManager.enqueueUniqueWork(ScheduledSyncConstants.PROJECT_WORK_PREFIX + projectId,
                ExistingWorkPolicy.KEEP, request);
    }

    static OneTimeWorkRequest dailyRequest(Instant runAt, Instant now) {
        long delay = Math.max(0, Duration.between(now, runAt).toMillis());
        return new OneTimeWorkRequest.Builder(DailySyncTriggerWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
    }

    static OneTimeWorkRequest projectRequest(String projectId, long triggerTimeMillis) {
        Data input = new Data.Builder().putString(ScheduledSyncConstants.KEY_PROJECT_ID, projectId)
                .putLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, triggerTimeMillis).build();
        return new OneTimeWorkRequest.Builder(ScheduledProjectSyncWorker.class)
                .setInputData(input).setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
    }

    @Override public ListenableFuture<List<WorkInfo>> dailyWorkState() {
        return workManager.getWorkInfosForUniqueWork(ScheduledSyncConstants.DAILY_WORK_NAME);
    }

    @Override public ListenableFuture<List<WorkInfo>> projectWorkState(String projectId) {
        return workManager.getWorkInfosForUniqueWork(
                ScheduledSyncConstants.PROJECT_WORK_PREFIX + projectId);
    }
}
