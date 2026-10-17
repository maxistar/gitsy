package me.maxistar.gitsy;

final class ScheduledSyncConstants {
    static final String DAILY_WORK_NAME = "gitsy-daily-sync-trigger";
    static final String PROJECT_WORK_PREFIX = "gitsy-scheduled-project-";
    static final String KEY_PROJECT_ID = "project_id";
    static final String KEY_TRIGGER_TIME = "trigger_time";
    static final String NOTIFICATION_CHANNEL_ID = "scheduled_sync_channel";
    static final int NOTIFICATION_ID_BASE = 2000;

    private ScheduledSyncConstants() {}
}
