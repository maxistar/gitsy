package me.maxistar.gitsy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StartupSyncPolicyTest {
    private static final long NOW = 2L * StartupSyncInterval.TWENTY_FOUR_HOURS.getMilliseconds();
    private final StartupSyncPolicy policy = new StartupSyncPolicy();

    @Test
    public void neverSelectsNothing() {
        assertTrue(select(StartupSyncMode.NEVER, StartupSyncInterval.ONE_HOUR, ready(0)).isEmpty());
    }

    @Test
    public void alwaysSelectsReadyAndErrorStatesButNotActiveWork() {
        ProjectModel ready = ready(NOW);
        ProjectModel cloneError = project(ProjectModel.STATUS_CLONING_ERROR, NOW);
        ProjectModel syncError = project(ProjectModel.STATUS_SYNC_ERROR, NOW);
        ProjectModel active = project(ProjectModel.STATUS_SYNC_IN_PROGRESS, 0);
        assertEquals(Arrays.asList(ready, cloneError, syncError), policy.select(
                settings(StartupSyncMode.ALWAYS, StartupSyncInterval.ONE_HOUR),
                NOW, Arrays.asList(ready, cloneError, syncError, active)));
    }

    @Test
    public void ifStaleSelectsOldAndNeverSynchronizedProjects() {
        ProjectModel old = ready(NOW - StartupSyncInterval.ONE_HOUR.getMilliseconds());
        ProjectModel fresh = ready(NOW - 1);
        ProjectModel never = ready(0);
        assertEquals(Arrays.asList(old, never), policy.select(
                settings(StartupSyncMode.IF_STALE, StartupSyncInterval.ONE_HOUR),
                NOW, Arrays.asList(old, fresh, never)));
    }

    @Test
    public void emptyListSelectsNothingInEveryMode() {
        for (StartupSyncMode mode : StartupSyncMode.values()) {
            assertTrue(policy.select(settings(mode, StartupSyncInterval.ONE_HOUR),
                    NOW, Collections.emptyList()).isEmpty());
        }
    }

    @Test
    public void exactBoundaryIsEligibleForEveryInterval() {
        for (StartupSyncInterval interval : StartupSyncInterval.values()) {
            ProjectModel project = ready(NOW - interval.getMilliseconds());
            assertEquals(Collections.singletonList(project), policy.select(
                    settings(StartupSyncMode.IF_STALE, interval), NOW,
                    Collections.singletonList(project)));
        }
    }

    @Test
    public void justInsideBoundaryIsNotEligibleForEveryInterval() {
        for (StartupSyncInterval interval : StartupSyncInterval.values()) {
            ProjectModel project = ready(NOW - interval.getMilliseconds() + 1);
            assertTrue(policy.select(settings(StartupSyncMode.IF_STALE, interval),
                    NOW, Collections.singletonList(project)).isEmpty());
        }
    }

    @Test
    public void negativeClockAgeIsNotStale() {
        ProjectModel future = ready(NOW + 1);
        assertTrue(select(StartupSyncMode.IF_STALE, StartupSyncInterval.FIFTEEN_MINUTES,
                future).isEmpty());
    }

    private List<ProjectModel> select(
            StartupSyncMode mode, StartupSyncInterval interval, ProjectModel... projects) {
        return policy.select(settings(mode, interval), NOW, Arrays.asList(projects));
    }

    private StartupSyncSettings settings(StartupSyncMode mode, StartupSyncInterval interval) {
        return new StartupSyncSettings(mode, interval);
    }

    private ProjectModel ready(long lastSync) {
        return project(ProjectModel.STATUS_READY, lastSync);
    }

    private ProjectModel project(int status, long lastSync) {
        ProjectModel model = new ProjectModel("https://example.invalid/repo.git", "user", "token", "content://notes");
        model.setStatus(status);
        model.setLastSync(lastSync);
        return model;
    }
}
