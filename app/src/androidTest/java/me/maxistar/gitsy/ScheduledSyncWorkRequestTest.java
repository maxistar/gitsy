package me.maxistar.gitsy;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduledSyncWorkRequestTest {
    @Test public void dailyRequestHasControlledDelayAndConnectedConstraint() {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        OneTimeWorkRequest request = AndroidWorkManagerGateway.dailyRequest(
                now.plus(90, ChronoUnit.MINUTES), now);
        assertEquals(90 * 60 * 1000L, request.getWorkSpec().initialDelay);
        assertEquals(NetworkType.CONNECTED,
                request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test public void projectRequestContainsOnlyStableIdAndTriggerTime() {
        OneTimeWorkRequest request = AndroidWorkManagerGateway.projectRequest("project_123", 42L);
        String values = request.getWorkSpec().input.toString();
        assertEquals("project_123", request.getWorkSpec().input
                .getString(ScheduledSyncConstants.KEY_PROJECT_ID));
        assertEquals(42L, request.getWorkSpec().input
                .getLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, -1));
        assertFalse(values.contains("https://"));
        assertFalse(values.contains("content://"));
        assertEquals(NetworkType.CONNECTED,
                request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test public void uniqueDailyWorkReplacesAndCancelsIdempotently() throws Exception {
        AndroidWorkManagerGateway gateway = new AndroidWorkManagerGateway(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        gateway.cancelDailyTrigger();
        gateway.replaceDailyTrigger(Instant.now().plus(2, ChronoUnit.DAYS));
        gateway.replaceDailyTrigger(Instant.now().plus(3, ChronoUnit.DAYS));
        assertEquals(1, unfinished(gateway.dailyWorkState().get()));
        gateway.cancelDailyTrigger();
        for (int attempt = 0; attempt < 20 && unfinished(gateway.dailyWorkState().get()) > 0;
             attempt++) Thread.sleep(50);
        assertEquals(0, unfinished(gateway.dailyWorkState().get()));
    }

    private int unfinished(java.util.List<WorkInfo> values) {
        int count = 0;
        for (WorkInfo value : values) if (!value.getState().isFinished()) count++;
        return count;
    }
}
