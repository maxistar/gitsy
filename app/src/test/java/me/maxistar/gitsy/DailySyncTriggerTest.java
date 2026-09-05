package me.maxistar.gitsy;

import androidx.work.WorkInfo;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DailySyncTriggerTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-05T00:00:00Z"), ZoneId.of("UTC"));

    @Test public void disabledTriggerDoesNothingAndDoesNotScheduleTomorrow() {
        FakeGateway gateway = new FakeGateway();
        assertEquals(0, trigger(ScheduledSyncSettings.DEFAULT, gateway,
                Collections.emptyList()).run());
        assertFalse(gateway.replaced); assertTrue(gateway.enqueued.isEmpty());
    }

    @Test public void noProjectsStillSchedulesTomorrow() {
        FakeGateway gateway = new FakeGateway();
        assertEquals(0, trigger(enabled(), gateway, Collections.emptyList()).run());
        assertTrue(gateway.replaced);
    }

    @Test public void oneEnqueueFailureDoesNotBlockOthersOrTomorrow() {
        FakeGateway gateway = new FakeGateway(); gateway.failingId = "bad";
        ProjectModel good = ready("good"); ProjectModel bad = ready("bad");
        assertEquals(1, trigger(enabled(), gateway, Arrays.asList(bad, good)).run());
        assertEquals(Collections.singletonList("good"), gateway.enqueued);
        assertTrue(gateway.replaced);
    }

    private DailySyncTrigger trigger(ScheduledSyncSettings value, FakeGateway gateway,
                                     List<ProjectModel> projects) {
        ScheduledSyncSettingsRepository settings = new ScheduledSyncSettingsRepository() {
            @Override public ScheduledSyncSettings load() { return value; }
            @Override public void save(ScheduledSyncSettings ignored) { }
        };
        ScheduledSyncScheduler scheduler = new ScheduledSyncScheduler(
                settings, gateway, CLOCK, ZoneId.of("Europe/Berlin"));
        return new DailySyncTrigger(settings, gateway, scheduler, () -> projects, CLOCK);
    }

    private ScheduledSyncSettings enabled() { return new ScheduledSyncSettings(true, 2, 0); }

    private ProjectModel ready(String id) {
        ProjectModel project = new ProjectModel("url", "user", "secret", "uri");
        project.folderName = id; project.setStatus(ProjectModel.STATUS_READY); return project;
    }

    private static final class FakeGateway implements WorkManagerGateway {
        final java.util.ArrayList<String> enqueued = new java.util.ArrayList<>();
        String failingId; boolean replaced;
        @Override public void replaceDailyTrigger(Instant at) { replaced = true; }
        @Override public void cancelDailyTrigger() { }
        @Override public void enqueueProject(String id, long trigger) {
            if (id.equals(failingId)) throw new IllegalStateException("test");
            enqueued.add(id);
        }
        @Override public ListenableFuture<List<WorkInfo>> dailyWorkState() { return null; }
        @Override public ListenableFuture<List<WorkInfo>> projectWorkState(String id) { return null; }
    }
}
