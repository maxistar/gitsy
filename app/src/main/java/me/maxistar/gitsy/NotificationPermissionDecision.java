package me.maxistar.gitsy;

public final class NotificationPermissionDecision {
    public enum Result {
        NOT_REQUIRED,
        VISIBLE,
        REQUEST,
        UNAVAILABLE
    }

    private NotificationPermissionDecision() { }

    public static Result evaluate(int apiLevel, boolean permissionGranted,
                                  boolean notificationsEnabled, boolean requestedBefore) {
        if (apiLevel < 33) return Result.NOT_REQUIRED;
        if (permissionGranted && notificationsEnabled) return Result.VISIBLE;
        if (!permissionGranted && !requestedBefore) return Result.REQUEST;
        return Result.UNAVAILABLE;
    }
}
