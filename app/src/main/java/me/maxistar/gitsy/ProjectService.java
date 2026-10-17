package me.maxistar.gitsy;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;

public class ProjectService extends Service {
    private static final int NOTIFICATION_ID = 100;
    private NotificationManager notificationManager;
    private Notification.Builder notificationBuilder;

    FileStorageService fileStorageService;

    GitService gitService;

    public static boolean started = false;


    @Override
    public void onCreate() {
        super.onCreate();
        fileStorageService = new FileStorageService();
        gitService = new GitService();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("service_channel",
                    getString(R.string.foreground_service_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_LONG).show();
        startForeground(NOTIFICATION_ID, getNotification(getString(R.string.starting_service)));
        ServiceLocator.getInstance().getWakeLockService().acquireLock(getApplicationContext());
        // Perform long-running task in a background thread and update notification text
        performTaskAndUpdateNotification();
        return START_STICKY;
    }

    private Notification getNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, "service_channel")
                    .setContentTitle(getString(R.string.foreground_service_title))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId("service_channel");
        }

        return notificationBuilder.build();
    }

    private void performTaskAndUpdateNotification() {
        started = true;
        new Thread(() -> {
            try {
                ProjectRepository repository = ProjectRepository.getInstance();
                List<ProjectModel> projects = repository.getProjects();
                ProjectExecutionGate gate = ServiceLocator.getInstance().getProjectExecutionGate();

                for (ProjectModel project : projects) {
                    if (!gate.tryAcquire(project.getFolderName())) continue;
                    try {
                        if (project.getStatus() == ProjectModel.STATUS_TO_CLONE) cloneProject(project);
                        if (project.getStatus() == ProjectModel.STATUS_TO_SYNC) syncProject(project);
                    } finally {
                        gate.release(project.getFolderName());
                    }
                }
            } finally {
                stopSelf();
                ServiceLocator.getInstance().getWakeLockService().releaseLock();
                started = false;
            }
        }).start();
    }

    private void syncProject(ProjectModel project) {
        ProjectSyncRunner.Result result = new ProjectSyncRunner(getApplicationContext())
                .synchronize(project, phase -> {
                    if (phase == ProjectSyncRunner.Phase.IMPORT) updateNotification("Copy changes to git");
                    else if (phase == ProjectSyncRunner.Phase.GIT) updateNotification("Synchronization");
                    else updateNotification("Copy changes to local folder");
                });
        if (!result.isSuccess()) {
            Exception error = result.getError();
            if (postSshAttention(project, error, ProjectModel.STATUS_TO_SYNC)) return;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void cloneProject(ProjectModel project) {
        CloneCoordinator coordinator = new CloneCoordinator(
                current -> {
                    updateNotification("Cloning Repo");
                    gitService.cloneRepository(getApplicationContext(), current);
                },
                current -> {
                    updateNotification("Moving Files to SAF");
                    return fileStorageService.copyToSaf(getApplicationContext(),
                            current.getFolderName(), Uri.parse(current.getFolderUri()));
                },
                System::currentTimeMillis,
                current -> persistAndNotify());
        CloneCoordinator.Result result = coordinator.clone(project);
        if (!result.isSuccess()) {
            Exception e = result.getError();
            if (postSshAttention(project, e, ProjectModel.STATUS_TO_CLONE)) return;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    int message = e instanceof HttpsCloneException
                            && ((HttpsCloneException) e).getCategory()
                            == CloneFailureCategory.AUTHENTICATION
                            ? R.string.https_clone_authentication_error
                            : R.string.clone_failed_sanitized;
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void persistAndNotify() {
        ProjectRepository.getInstance().saveProjects(getApplicationContext());
        EventBus.getInstance().post(new UpdateListEvent("project state changed"));
    }

    private boolean postSshAttention(ProjectModel project, Exception error, int retryStatus) {
        if (error instanceof SshHostTrustRequiredException) {
            ServiceLocator.getInstance().publishSshAttention(SshOperationAttentionEvent.trust(project,
                    ((SshHostTrustRequiredException) error).getRequest(), retryStatus));
            return true;
        }
        if (error instanceof SshHostKeyChangedException) {
            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.changed(project, retryStatus));
            return true;
        }
        if (error instanceof SshIdentityAccessException) {
            ServiceLocator.getInstance().publishSshAttention(
                    SshOperationAttentionEvent.missing(project, retryStatus));
            return true;
        }
        return false;
    }

    @SuppressLint("NotificationPermission") // Foreground-service notifications remain visible in Task Manager when notification permission is denied.
    private void updateNotification(String text) {
        notificationBuilder.setContentText(text);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
