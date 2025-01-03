package me.maxistar.gitsync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import java.util.List;

public class ProjectService extends Service {
    private static final int NOTIFICATION_ID = 100;
    private NotificationManager notificationManager;
    private Notification.Builder notificationBuilder;

    FileStorageService fileStorageService;

    GitService gitService;


    static final String REGISTRY_JSON = "registry.json";

    @Override
    public void onCreate() {
        super.onCreate();
        fileStorageService = new FileStorageService(REGISTRY_JSON);
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
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_LONG).show();
        startForeground(NOTIFICATION_ID, getNotification("Starting Service..."));
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
            // for (int i = 0; i <= 100; i += 10) {
            //     try {
            //         Thread.sleep(1000);
            //     } catch (InterruptedException e) {
            //         e.printStackTrace();
            //     }
            //     updateNotification("Progress: " + i + "%");
            // }

            ProjectRepository repository = ProjectRepository.getInstance();
            List<ProjectModel> projects = repository.getProjects();

            for (ProjectModel project : projects) {
                if (project.getStatus() == ProjectModel.STATUS_TO_CLONE) {
                    cloneProject(project);
                }
            }


            stopSelf();
        }).start();
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

            fileStorageService.copyToSaf(
                    getApplicationContext(),
                    project.getFolderName(),
                    Uri.parse(project.getFolderUri())
            );

            project.setStatus(ProjectModel.STATUS_READY);
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));
        } catch (Exception e) {
            project.setStatus(ProjectModel.STATUS_CLONING_ERROR);
            EventBus.getInstance().post(new UpdateListEvent("Hello, EventBus!"));
            // Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
