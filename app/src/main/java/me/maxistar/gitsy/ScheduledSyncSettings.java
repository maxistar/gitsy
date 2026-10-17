package me.maxistar.gitsy;

import java.util.Objects;

public final class ScheduledSyncSettings {
    private static final int DEFAULT_HOUR = 2;
    private static final int DEFAULT_MINUTE = 0;
    public static final ScheduledSyncSettings DEFAULT =
            new ScheduledSyncSettings(false, DEFAULT_HOUR, DEFAULT_MINUTE);

    private final boolean enabled;
    private final int hour;
    private final int minute;

    public ScheduledSyncSettings(boolean enabled, int hour, int minute) {
        this.enabled = enabled;
        this.hour = normalize(hour, 0, 23, DEFAULT_HOUR);
        this.minute = normalize(minute, 0, 59, DEFAULT_MINUTE);
    }

    private static int normalize(int value, int minimum, int maximum, int fallback) {
        return value >= minimum && value <= maximum ? value : fallback;
    }

    public boolean isEnabled() { return enabled; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScheduledSyncSettings)) return false;
        ScheduledSyncSettings value = (ScheduledSyncSettings) other;
        return enabled == value.enabled && hour == value.hour && minute == value.minute;
    }

    @Override public int hashCode() { return Objects.hash(enabled, hour, minute); }
}
