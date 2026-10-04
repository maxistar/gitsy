package me.maxistar.gitsy;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import androidx.work.WorkInfo;
import com.google.common.util.concurrent.ListenableFuture;

import static org.junit.Assert.*;

public class ScheduledSyncSchedulerTest {
    @Test public void disabledScheduleCancels() {
        FakeGateway gateway = new FakeGateway();
        scheduler(ScheduledSyncSettings.DEFAULT, gateway).reconcile();
        assertTrue(gateway.cancelled); assertNull(gateway.runAt);
    }

    @Test public void enabledScheduleReplacesAtNextLocalOccurrence() {
        FakeGateway gateway = new FakeGateway();
        scheduler(new ScheduledSyncSettings(true, 2, 0), gateway).reconcile();
        assertEquals(Instant.parse("2026-09-06T00:00:00Z"), gateway.runAt);
        assertFalse(gateway.cancelled);
    }

    private ScheduledSyncScheduler scheduler(ScheduledSyncSettings value, FakeGateway gateway) {
        ScheduledSyncSettingsRepository repository = new ScheduledSyncSettingsRepository() {
            @Override public ScheduledSyncSettings load() { return value; }
            @Override public void save(ScheduledSyncSettings settings) { }
        };
        return new ScheduledSyncScheduler(repository, gateway,
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneId.of("UTC")),
                ZoneId.of("Europe/Berlin"));
    }

    private static final class FakeGateway implements WorkManagerGateway {
        Instant runAt; boolean cancelled;
        @Override public void replaceDailyTrigger(Instant runAt) { this.runAt = runAt; }
        @Override public void cancelDailyTrigger() { cancelled = true; }
        @Override public void enqueueProject(String id, long trigger) { }
        @Override public ListenableFuture<List<WorkInfo>> dailyWorkState() { return null; }
        @Override public ListenableFuture<List<WorkInfo>> projectWorkState(String id) { return null; }
    }
}
