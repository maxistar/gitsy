package me.maxistar.gitsy;

public enum StartupSyncInterval {
    FIFTEEN_MINUTES("15_minutes", 15L * 60L * 1000L),
    ONE_HOUR("1_hour", 60L * 60L * 1000L),
    SIX_HOURS("6_hours", 6L * 60L * 60L * 1000L),
    TWENTY_FOUR_HOURS("24_hours", 24L * 60L * 60L * 1000L);

    private final String preferenceValue;
    private final long milliseconds;

    StartupSyncInterval(String preferenceValue, long milliseconds) {
        this.preferenceValue = preferenceValue;
        this.milliseconds = milliseconds;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public long getMilliseconds() {
        return milliseconds;
    }

    public static StartupSyncInterval fromPreferenceValue(String value) {
        for (StartupSyncInterval interval : values()) {
            if (interval.preferenceValue.equals(value)) return interval;
        }
        return ONE_HOUR;
    }
}
