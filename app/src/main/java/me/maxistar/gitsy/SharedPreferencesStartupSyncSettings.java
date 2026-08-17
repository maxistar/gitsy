package me.maxistar.gitsy;

import android.content.Context;
import android.content.SharedPreferences;

public final class SharedPreferencesStartupSyncSettings implements StartupSyncSettingsRepository {
    static final String PREFERENCES_NAME = "startup_sync";
    static final String KEY_MODE = "mode";
    static final String KEY_INTERVAL = "interval";

    interface Store {
        String getString(String key, String defaultValue);
        void putStrings(String firstKey, String firstValue, String secondKey, String secondValue);
    }

    private final Store store;

    public SharedPreferencesStartupSyncSettings(Context context) {
        this(new SharedPreferencesStore(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)));
    }

    SharedPreferencesStartupSyncSettings(Store store) {
        this.store = store;
    }

    @Override
    public StartupSyncSettings load() {
        StartupSyncMode mode = StartupSyncMode.fromPreferenceValue(
                store.getString(KEY_MODE, StartupSyncMode.IF_STALE.getPreferenceValue()));
        StartupSyncInterval interval = StartupSyncInterval.fromPreferenceValue(
                store.getString(KEY_INTERVAL, StartupSyncInterval.ONE_HOUR.getPreferenceValue()));
        return new StartupSyncSettings(mode, interval);
    }

    @Override
    public void save(StartupSyncSettings settings) {
        store.putStrings(
                KEY_MODE, settings.getMode().getPreferenceValue(),
                KEY_INTERVAL, settings.getInterval().getPreferenceValue());
    }

    private static final class SharedPreferencesStore implements Store {
        private final SharedPreferences preferences;

        SharedPreferencesStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String getString(String key, String defaultValue) {
            try {
                return preferences.getString(key, defaultValue);
            } catch (ClassCastException ignored) {
                return defaultValue;
            }
        }

        @Override
        public void putStrings(String firstKey, String firstValue, String secondKey, String secondValue) {
            preferences.edit()
                    .putString(firstKey, firstValue)
                    .putString(secondKey, secondValue)
                    .apply();
        }
    }
}
