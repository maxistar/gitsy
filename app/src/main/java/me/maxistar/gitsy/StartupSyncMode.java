package me.maxistar.gitsy;

public enum StartupSyncMode {
    NEVER("never"),
    ASK_IF_STALE("ask_if_stale"),
    IF_STALE("if_stale"),
    ALWAYS("always");

    private final String preferenceValue;

    StartupSyncMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public boolean usesStaleInterval() {
        return this == ASK_IF_STALE || this == IF_STALE;
    }

    public static StartupSyncMode fromPreferenceValue(String value) {
        for (StartupSyncMode mode : values()) {
            if (mode.preferenceValue.equals(value)) return mode;
        }
        return IF_STALE;
    }
}
