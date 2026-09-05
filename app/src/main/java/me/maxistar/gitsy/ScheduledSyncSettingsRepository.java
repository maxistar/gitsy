package me.maxistar.gitsy;

public interface ScheduledSyncSettingsRepository {
    ScheduledSyncSettings load();
    void save(ScheduledSyncSettings settings);
}
