package me.maxistar.gitsy;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ScheduledProjectSyncWorker extends Worker {
    private final NotificationManager notifications;
    private int notificationId;

    public ScheduledProjectSyncWorker(@NonNull Context context,
                                      @NonNull WorkerParameters parameters) {
        super(context, parameters);
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    @NonNull @Override public Result doWork() {
        String projectId = getInputData().getString(ScheduledSyncConstants.KEY_PROJECT_ID);
        long triggerTime = getInputData().getLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, 0L);
        if (projectId == null || projectId.isEmpty()) return Result.failure();
        ProjectRepository repository = ProjectRepository.getInstance();
        repository.ensureProjectsLoaded(getApplicationContext());
        ProjectModel project = repository.findProject(projectId);
        if (!new ScheduledProjectDecision(new ScheduledProjectEligibility())
                .shouldRun(project, triggerTime)) return Result.success();

        ProjectExecutionGate gate = ServiceLocator.getInstance().getProjectExecutionGate();
        if (!gate.tryAcquire(projectId)) return Result.success();
        try {
            createChannel();
            notificationId = ScheduledSyncConstants.NOTIFICATION_ID_BASE
                    + (projectId.hashCode() & 0x3ff);
            try {
                setForegroundAsync(foregroundInfo(getApplicationContext()
                        .getString(R.string.scheduled_sync_running))).get();
            } catch (Exception error) {
                project.setSyncFailureCategory(SyncFailureCategory.UNKNOWN);
                repository.saveProjects(getApplicationContext());
                return Result.success();
            }
            new ProjectSyncRunner(getApplicationContext())
                    .synchronize(project, phase -> {
                        if (isStopped()) throw new WorkStoppedException();
                        updateForeground(phase);
                    });
            // Every attempted synchronization is terminal for this daily trigger.
            return Result.success();
        } finally {
            gate.release(projectId);
        }
    }

    private static final class WorkStoppedException extends RuntimeException { }

    private void updateForeground(ProjectSyncRunner.Phase phase) {
        int text = phase == ProjectSyncRunner.Phase.IMPORT
                ? R.string.scheduled_sync_importing
                : phase == ProjectSyncRunner.Phase.GIT
                ? R.string.scheduled_sync_synchronizing : R.string.scheduled_sync_exporting;
        setForegroundAsync(foregroundInfo(getApplicationContext().getString(text)));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(new NotificationChannel(
                    ScheduledSyncConstants.NOTIFICATION_CHANNEL_ID,
                    getApplicationContext().getString(R.string.scheduled_sync_channel),
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    private ForegroundInfo foregroundInfo(String text) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(),
                ScheduledSyncConstants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getApplicationContext().getString(R.string.scheduled_sync_title))
                .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(notificationId, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(notificationId, notification);
    }
}
