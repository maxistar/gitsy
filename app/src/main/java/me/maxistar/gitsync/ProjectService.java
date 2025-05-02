package me.maxistar.gitsync;

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

import java.io.File;
import java.util.List;

public class ProjectService extends Service {
    private static final int NOTIFICATION_ID = 100;
    private NotificationManager notificationManager;
    private Notification.Builder notificationBuilder;

    FileStorageService fileStorageService;

    GitService gitService;


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
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_LONG).show();
        startForeground(NOTIFICATION_ID, getNotification("Starting Service..."));
        ServiceLocator.getInstance().getWakeLockService().acquireLock(getApplicationContext());
        // Perform long-running task in a background thread and update notification text
        performTaskAndUpdateNotification();
        return START_STICKY;
    }

    private Notification getNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, "service_channel")
                    .setContentTitle("Foreground Service")
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
        new Thread(() -> {
            ProjectRepository repository = ProjectRepository.getInstance();
            List<ProjectModel> projects = repository.getProjects();

            for (ProjectModel project : projects) {
                if (project.getStatus() == ProjectModel.STATUS_TO_CLONE) {
                    cloneProject(project);
                }
                if (project.getStatus() == ProjectModel.STATUS_TO_SYNC) {
                    syncProject(project);
                }
            }


            stopSelf();
            ServiceLocator.getInstance().getWakeLockService().releaseLock();
        }).start();
    }

    private void syncProject(ProjectModel project) {
        project.setStatus(ProjectModel.STATUS_SYNC_IN_PROGRESS);
        EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));

        try {

            updateNotification("Copy changes to git");

            fileStorageService.copyFromSaf(
                    getApplicationContext(),
                    Uri.parse(project.getFolderUri()),
                    project.getFolderName()
            );

            updateNotification("Synchronization");

// todo auto fix when locked
//            File lockFile = new File("/data/user/0/me.maxistar.gitsync/files/project_1738597560466/.git/index.lock");
//            lockFile.delete();

            gitService.syncRepository(
                    getApplicationContext(),
                    project.getFolderName(),
                    project.getUserName(),
                    project.getPassword()
            );

            updateNotification("Copy changes to local folder");

            fileStorageService.copyToSaf(
                    getApplicationContext(),
                    project.getFolderName(),
                    Uri.parse(project.getFolderUri())
            );


            project.setStatus(ProjectModel.STATUS_READY);
            project.setLastSync(System.currentTimeMillis());
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));


        } catch (Exception e) {



            project.setStatus(ProjectModel.STATUS_SYNC_ERROR);



            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));


            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });


        }
    }

    private void cloneProject(ProjectModel project) {
        try {
            project.setStatus(ProjectModel.STATUS_CLONING);
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));

            updateNotification("Cloning Repo");

            gitService.cloneRepository(
                    getApplicationContext(),
                    project.getFolderName(),
                    project.getRepoUrl(),
                    project.getUserName(),
                    project.getPassword()
            );

            updateNotification("Moving Files to SAF");

            int totalFiles = fileStorageService.copyToSaf(
                    getApplicationContext(),
                    project.getFolderName(),
                    Uri.parse(project.getFolderUri())
            );

            project.setStatus(ProjectModel.STATUS_READY);
            project.setNumberFiles(totalFiles);
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));
        } catch (Exception e) {
            project.setStatus(ProjectModel.STATUS_CLONING_ERROR);
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();                }
            });

        }
    }

    private void updateNotification(String text) {
        notificationBuilder.setContentText(text);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
