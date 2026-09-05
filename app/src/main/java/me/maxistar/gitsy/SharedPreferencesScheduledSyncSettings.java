package me.maxistar.gitsy;

import android.content.Context;
import android.content.SharedPreferences;

public final class SharedPreferencesScheduledSyncSettings implements ScheduledSyncSettingsRepository {
    static final String PREFERENCES_NAME = "scheduled_sync";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_HOUR = "hour";
    static final String KEY_MINUTE = "minute";

    interface Store {
        boolean getBoolean(String key, boolean fallback);
        int getInt(String key, int fallback);
        void put(boolean enabled, int hour, int minute);
    }

    private final Store store;

    public SharedPreferencesScheduledSyncSettings(Context context) {
        this(new PreferencesStore(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)));
    }

    SharedPreferencesScheduledSyncSettings(Store store) { this.store = store; }

    @Override public ScheduledSyncSettings load() {
        return new ScheduledSyncSettings(
                store.getBoolean(KEY_ENABLED, false),
                store.getInt(KEY_HOUR, 2),
                store.getInt(KEY_MINUTE, 0));
    }

    @Override public void save(ScheduledSyncSettings settings) {
        store.put(settings.isEnabled(), settings.getHour(), settings.getMinute());
    }

    private static final class PreferencesStore implements Store {
        private final SharedPreferences preferences;
        PreferencesStore(SharedPreferences preferences) { this.preferences = preferences; }

        @Override public boolean getBoolean(String key, boolean fallback) {
            try { return preferences.getBoolean(key, fallback); }
            catch (ClassCastException ignored) { return fallback; }
        }

        @Override public int getInt(String key, int fallback) {
            try { return preferences.getInt(key, fallback); }
            catch (ClassCastException ignored) { return fallback; }
        }

        @Override public void put(boolean enabled, int hour, int minute) {
            preferences.edit().putBoolean(KEY_ENABLED, enabled)
                    .putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply();
        }
    }
}
