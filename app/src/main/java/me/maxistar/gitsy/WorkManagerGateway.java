package me.maxistar.gitsy;

import java.time.Instant;
import java.util.List;

import androidx.work.WorkInfo;

import com.google.common.util.concurrent.ListenableFuture;

interface WorkManagerGateway {
    void replaceDailyTrigger(Instant runAt);
    void cancelDailyTrigger();
    void enqueueProject(String projectId, long triggerTimeMillis);
    ListenableFuture<List<WorkInfo>> dailyWorkState();
    ListenableFuture<List<WorkInfo>> projectWorkState(String projectId);
}
