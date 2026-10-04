package me.maxistar.gitsy;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScheduledSyncTimeChangeReceiverTest {
    @Test public void onlyClockAndZoneChangesReconcile() {
        assertTrue(ScheduledSyncTimeChangeReceiver.shouldReconcile(Intent.ACTION_TIME_CHANGED));
        assertTrue(ScheduledSyncTimeChangeReceiver.shouldReconcile(Intent.ACTION_TIMEZONE_CHANGED));
        assertFalse(ScheduledSyncTimeChangeReceiver.shouldReconcile(Intent.ACTION_BOOT_COMPLETED));
        assertFalse(ScheduledSyncTimeChangeReceiver.shouldReconcile(null));
    }
}
