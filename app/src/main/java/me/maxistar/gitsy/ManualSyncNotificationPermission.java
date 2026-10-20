package me.maxistar.gitsy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class ManualSyncNotificationPermission {
    public static final int REQUEST_CODE = 43;

    private static final String PREFERENCES_NAME = "manual_sync_notifications";
    private static final String REQUESTED_KEY = "permission_requested";
    private static final String PENDING_KEY = "permission_pending";

    public interface Continuation {
        void run(boolean notificationsVisible);
    }

    private final ManualSyncOperationGate pendingOperation = new ManualSyncOperationGate();

    public void restoreInstanceState(Bundle state) {
        pendingOperation.restore(state != null && state.getBoolean(PENDING_KEY, false));
    }

    public void saveInstanceState(Bundle state) {
        state.putBoolean(PENDING_KEY, pendingOperation.isPending());
    }

    public void runOrRequest(Activity activity, Continuation continuation) {
        if (Build.VERSION.SDK_INT < 33) {
            continuation.run(true);
            return;
        }

        if (notificationsVisible(activity)) {
            continuation.run(true);
            return;
        }

        boolean requestedBefore = activity.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(REQUESTED_KEY, false);
        NotificationPermissionDecision.Result decision = NotificationPermissionDecision.evaluate(
                Build.VERSION.SDK_INT,
                notificationPermissionGranted(activity),
                notificationsEnabled(activity),
                requestedBefore);
        if (decision == NotificationPermissionDecision.Result.REQUEST) {
            if (!pendingOperation.begin()) return;
            activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(REQUESTED_KEY, true).apply();
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE);
            return;
        }

        if (decision == NotificationPermissionDecision.Result.UNAVAILABLE) {
            showGuidance(activity);
            continuation.run(false);
            return;
        }

        continuation.run(true);
    }

    public boolean onRequestPermissionsResult(Activity activity, int requestCode,
                                              Continuation continuation) {
        if (requestCode != REQUEST_CODE || !pendingOperation.complete()) return false;
        boolean visible = notificationsVisible(activity);
        if (!visible) showGuidance(activity);
        continuation.run(visible);
        return true;
    }

    static boolean notificationsVisible(Context context) {
        return notificationPermissionGranted(context) && notificationsEnabled(context);
    }

    static boolean notificationPermissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean notificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private void showGuidance(Activity activity) {
        if (activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.sync_notification_permission_title)
                .setMessage(R.string.sync_notification_permission_message)
                .setPositiveButton(R.string.sync_notification_permission_settings,
                        (dialog, which) -> NotificationSettingsLauncher.open(activity))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
