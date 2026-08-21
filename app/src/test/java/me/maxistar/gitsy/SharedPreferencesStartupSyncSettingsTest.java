package me.maxistar.gitsy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SharedPreferencesStartupSyncSettingsTest {
    @Test
    public void missingValuesUseAskIfStaleDefaults() {
        StartupSyncSettings settings = repository(new HashMap<>()).load();
        assertEquals(StartupSyncMode.ASK_IF_STALE, settings.getMode());
        assertEquals(StartupSyncInterval.ONE_HOUR, settings.getInterval());
        assertEquals(StartupSyncSettings.DEFAULT.getMode(), settings.getMode());
        assertEquals(StartupSyncSettings.DEFAULT.getInterval(), settings.getInterval());
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
    public void askIfStaleIsPersistedWithItsInterval() {
        Map<String, String> values = new HashMap<>();
        SharedPreferencesStartupSyncSettings repository = repository(values);
        repository.save(new StartupSyncSettings(
                StartupSyncMode.ASK_IF_STALE, StartupSyncInterval.SIX_HOURS));

        StartupSyncSettings loaded = repository.load();
        assertEquals(StartupSyncMode.ASK_IF_STALE, loaded.getMode());
        assertEquals(StartupSyncInterval.SIX_HOURS, loaded.getInterval());
        assertEquals("ask_if_stale", values.get(SharedPreferencesStartupSyncSettings.KEY_MODE));
    }

    @Test
    public void everySerializedModeRemainsCompatible() {
        for (StartupSyncMode mode : StartupSyncMode.values()) {
            Map<String, String> values = new HashMap<>();
            values.put(SharedPreferencesStartupSyncSettings.KEY_MODE, mode.getPreferenceValue());
            assertEquals(mode, repository(values).load().getMode());
        }
    }

    @Test
    public void changingModeRetainsSelectedInterval() {
        Map<String, String> values = new HashMap<>();
        SharedPreferencesStartupSyncSettings repository = repository(values);
        repository.save(new StartupSyncSettings(
                StartupSyncMode.ASK_IF_STALE, StartupSyncInterval.TWENTY_FOUR_HOURS));
        repository.save(new StartupSyncSettings(
                StartupSyncMode.NEVER, repository.load().getInterval()));
        assertEquals(StartupSyncInterval.TWENTY_FOUR_HOURS, repository.load().getInterval());
    }

    @Test
    public void staleIntervalApplicabilityIsTyped() {
        assertEquals(false, StartupSyncMode.NEVER.usesStaleInterval());
        assertEquals(true, StartupSyncMode.ASK_IF_STALE.usesStaleInterval());
        assertEquals(true, StartupSyncMode.IF_STALE.usesStaleInterval());
        assertEquals(false, StartupSyncMode.ALWAYS.usesStaleInterval());
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
