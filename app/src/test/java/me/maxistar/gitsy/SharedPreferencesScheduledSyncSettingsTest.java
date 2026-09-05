package me.maxistar.gitsy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SharedPreferencesScheduledSyncSettingsTest {
    @Test public void missingValuesDefaultToDisabledAtTwo() {
        assertEquals(ScheduledSyncSettings.DEFAULT, repository(new FakeStore()).load());
    }

    @Test public void validValuesRoundTrip() {
        FakeStore store = new FakeStore();
        SharedPreferencesScheduledSyncSettings repository = repository(store);
        repository.save(new ScheduledSyncSettings(true, 23, 45));
        assertEquals(new ScheduledSyncSettings(true, 23, 45), repository.load());
    }

    @Test public void invalidStoredTimeNormalizesToTwo() {
        FakeStore store = new FakeStore();
        store.enabled = true; store.hour = 50; store.minute = -8;
        assertEquals(new ScheduledSyncSettings(true, 2, 0), repository(store).load());
    }

    private SharedPreferencesScheduledSyncSettings repository(FakeStore store) {
        return new SharedPreferencesScheduledSyncSettings(store);
    }

    private static final class FakeStore implements SharedPreferencesScheduledSyncSettings.Store {
        boolean enabled; int hour = 2; int minute;
        @Override public boolean getBoolean(String key, boolean fallback) { return enabled; }
        @Override public int getInt(String key, int fallback) {
            return SharedPreferencesScheduledSyncSettings.KEY_HOUR.equals(key) ? hour : minute;
        }
        @Override public void put(boolean enabled, int hour, int minute) {
            this.enabled = enabled; this.hour = hour; this.minute = minute;
        }
    }
}
