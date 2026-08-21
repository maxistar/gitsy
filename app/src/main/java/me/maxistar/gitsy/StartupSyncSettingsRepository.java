package me.maxistar.gitsy;

public interface StartupSyncSettingsRepository {
    StartupSyncSettings load();
    void save(StartupSyncSettings settings);
}
