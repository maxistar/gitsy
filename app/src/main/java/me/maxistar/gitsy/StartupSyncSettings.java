package me.maxistar.gitsy;

import java.util.Objects;

public final class StartupSyncSettings {
    public static final StartupSyncSettings DEFAULT =
            new StartupSyncSettings(StartupSyncMode.IF_STALE, StartupSyncInterval.ONE_HOUR);

    private final StartupSyncMode mode;
    private final StartupSyncInterval interval;

    public StartupSyncSettings(StartupSyncMode mode, StartupSyncInterval interval) {
        this.mode = Objects.requireNonNull(mode);
        this.interval = Objects.requireNonNull(interval);
    }

    public StartupSyncMode getMode() {
        return mode;
    }

    public StartupSyncInterval getInterval() {
        return interval;
    }
}
