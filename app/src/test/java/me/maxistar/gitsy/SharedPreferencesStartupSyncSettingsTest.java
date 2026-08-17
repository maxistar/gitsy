package me.maxistar.gitsy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SharedPreferencesStartupSyncSettingsTest {
    @Test
    public void missingValuesUseExistingBehaviorDefaults() {
        StartupSyncSettings settings = repository(new HashMap<>()).load();
        assertEquals(StartupSyncMode.IF_STALE, settings.getMode());
        assertEquals(StartupSyncInterval.ONE_HOUR, settings.getInterval());
    }

    @Test
    public void persistedValuesAreLoaded() {
        Map<String, String> values = new HashMap<>();
        values.put(SharedPreferencesStartupSyncSettings.KEY_MODE, "always");
        values.put(SharedPreferencesStartupSyncSettings.KEY_INTERVAL, "24_hours");
        StartupSyncSettings settings = repository(values).load();
        assertEquals(StartupSyncMode.ALWAYS, settings.getMode());
        assertEquals(StartupSyncInterval.TWENTY_FOUR_HOURS, settings.getInterval());
    }

    @Test
    public void changedValuesAreSavedTogether() {
        Map<String, String> values = new HashMap<>();
        SharedPreferencesStartupSyncSettings repository = repository(values);
        repository.save(new StartupSyncSettings(
                StartupSyncMode.NEVER, StartupSyncInterval.FIFTEEN_MINUTES));
        assertEquals("never", values.get(SharedPreferencesStartupSyncSettings.KEY_MODE));
        assertEquals("15_minutes", values.get(SharedPreferencesStartupSyncSettings.KEY_INTERVAL));
    }

    @Test
    public void corruptValuesFallBackIndependently() {
        Map<String, String> values = new HashMap<>();
        values.put(SharedPreferencesStartupSyncSettings.KEY_MODE, "sometimes");
        values.put(SharedPreferencesStartupSyncSettings.KEY_INTERVAL, "soon");
        StartupSyncSettings settings = repository(values).load();
        assertEquals(StartupSyncMode.IF_STALE, settings.getMode());
        assertEquals(StartupSyncInterval.ONE_HOUR, settings.getInterval());
    }

    private SharedPreferencesStartupSyncSettings repository(Map<String, String> values) {
        return new SharedPreferencesStartupSyncSettings(new SharedPreferencesStartupSyncSettings.Store() {
            @Override
            public String getString(String key, String defaultValue) {
                return values.containsKey(key) ? values.get(key) : defaultValue;
            }

            @Override
            public void putStrings(String firstKey, String firstValue, String secondKey, String secondValue) {
                values.put(firstKey, firstValue);
                values.put(secondKey, secondValue);
            }
        });
    }
}
