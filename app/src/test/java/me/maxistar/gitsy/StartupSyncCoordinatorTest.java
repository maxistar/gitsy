package me.maxistar.gitsy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StartupSyncCoordinatorTest {
    private static final long NOW = 10_000_000L;

    @Test
    public void initialValueAndDelayedLoadDoNotEvaluateEarly() {
        RecordingActions actions = new RecordingActions();
        StartupSyncCoordinator coordinator = coordinator(StartupSyncMode.ALWAYS, new StartupSyncRunGuard(), actions);

        assertEquals(StartupSyncCoordinator.Result.WAITING_FOR_LOAD,
                coordinator.onProjectsChanged(false, false, Collections.singletonList(ready(0))));
        assertEquals(0, actions.syncCalls);
        assertEquals(StartupSyncCoordinator.Result.SYNCHRONIZATION_STARTED,
                coordinator.onProjectsChanged(true, false, Collections.singletonList(ready(0))));
        assertEquals(1, actions.syncCalls);
    }

    @Test
    public void repeatedDeliveryAndActivityStyleNewCoordinatorRunOncePerGuard() {
        StartupSyncRunGuard guard = new StartupSyncRunGuard();
        RecordingActions first = new RecordingActions();
        StartupSyncCoordinator coordinator = coordinator(StartupSyncMode.ALWAYS, guard, first);
        assertEquals(StartupSyncCoordinator.Result.SYNCHRONIZATION_STARTED,
                coordinator.onProjectsChanged(true, false, Collections.singletonList(ready(0))));
        assertEquals(StartupSyncCoordinator.Result.ALREADY_EVALUATED,
                coordinator.onProjectsChanged(true, false, Collections.singletonList(ready(0))));

        RecordingActions recreatedActivity = new RecordingActions();
        assertEquals(StartupSyncCoordinator.Result.ALREADY_EVALUATED,
                coordinator(StartupSyncMode.ALWAYS, guard, recreatedActivity)
                        .onProjectsChanged(true, false, Collections.singletonList(ready(0))));
        assertEquals(0, recreatedActivity.syncCalls);
    }

    @Test
    public void freshProcessGuardIsEligibleAgain() {
        RecordingActions actions = new RecordingActions();
        assertEquals(StartupSyncCoordinator.Result.SYNCHRONIZATION_STARTED,
                coordinator(StartupSyncMode.ALWAYS, new StartupSyncRunGuard(), actions)
                        .onProjectsChanged(true, false, Collections.singletonList(ready(0))));
    }

    @Test
    public void recoveryWinsInEveryModeAndSuppressesSynchronization() {
        for (StartupSyncMode mode : StartupSyncMode.values()) {
            RecordingActions actions = new RecordingActions();
            StartupSyncCoordinator.Result result = coordinator(mode, new StartupSyncRunGuard(), actions)
                    .onProjectsChanged(true, false,
                            Collections.singletonList(project(ProjectModel.STATUS_SYNC_IN_PROGRESS, 0)));
            assertEquals(StartupSyncCoordinator.Result.RECOVERY_REQUIRED, result);
            assertEquals(1, actions.recoveryCalls);
            assertEquals(0, actions.syncCalls);
        }
    }

    @Test
    public void allInterruptedStatesRequireRecovery() {
        int[] states = { ProjectModel.STATUS_TO_CLONE, ProjectModel.STATUS_CLONING,
                ProjectModel.STATUS_TO_SYNC, ProjectModel.STATUS_SYNC_IN_PROGRESS };
        for (int state : states) {
            RecordingActions actions = new RecordingActions();
            assertEquals(StartupSyncCoordinator.Result.RECOVERY_REQUIRED,
                    coordinator(StartupSyncMode.NEVER, new StartupSyncRunGuard(), actions)
                            .onProjectsChanged(true, false, Collections.singletonList(project(state, 0))));
        }
    }

    @Test
    public void neverEmptyAndFreshSelectionsDoNotStartService() {
        assertNoStart(StartupSyncMode.NEVER, Collections.singletonList(ready(0)));
        assertNoStart(StartupSyncMode.ALWAYS, Collections.emptyList());
        assertNoStart(StartupSyncMode.IF_STALE, Collections.singletonList(ready(NOW)));
    }

    @Test
    public void selectedProjectsArePassedInOneSynchronizationRequest() {
        RecordingActions actions = new RecordingActions();
        List<ProjectModel> projects = new ArrayList<>();
        projects.add(ready(0));
        projects.add(ready(1));
        assertEquals(StartupSyncCoordinator.Result.SYNCHRONIZATION_STARTED,
                coordinator(StartupSyncMode.ALWAYS, new StartupSyncRunGuard(), actions)
                        .onProjectsChanged(true, false, projects));
        assertEquals(1, actions.syncCalls);
        assertEquals(projects, actions.selected);
    }

    @Test
    public void runningServiceSuppressesRecoveryAndNewWork() {
        RecordingActions actions = new RecordingActions();
        assertEquals(StartupSyncCoordinator.Result.NO_SYNCHRONIZATION,
                coordinator(StartupSyncMode.ALWAYS, new StartupSyncRunGuard(), actions)
                        .onProjectsChanged(true, true,
                                Collections.singletonList(project(ProjectModel.STATUS_SYNC_IN_PROGRESS, 0))));
        assertEquals(0, actions.recoveryCalls);
        assertEquals(0, actions.syncCalls);
    }

    private void assertNoStart(StartupSyncMode mode, List<ProjectModel> projects) {
        RecordingActions actions = new RecordingActions();
        assertEquals(StartupSyncCoordinator.Result.NO_SYNCHRONIZATION,
                coordinator(mode, new StartupSyncRunGuard(), actions)
                        .onProjectsChanged(true, false, projects));
        assertEquals(0, actions.syncCalls);
    }

    private StartupSyncCoordinator coordinator(
            StartupSyncMode mode, StartupSyncRunGuard guard, RecordingActions actions) {
        return new StartupSyncCoordinator(
                new FixedSettingsRepository(new StartupSyncSettings(mode, StartupSyncInterval.ONE_HOUR)),
                new StartupSyncPolicy(), guard, () -> NOW, actions);
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

    private static final class FixedSettingsRepository implements StartupSyncSettingsRepository {
        private final StartupSyncSettings settings;
        FixedSettingsRepository(StartupSyncSettings settings) { this.settings = settings; }
        @Override public StartupSyncSettings load() { return settings; }
        @Override public void save(StartupSyncSettings settings) { throw new AssertionError("Unexpected save"); }
    }

    private static final class RecordingActions implements StartupSyncCoordinator.Actions {
        int recoveryCalls;
        int syncCalls;
        List<ProjectModel> selected;
        @Override public void showRecovery() { recoveryCalls++; }
        @Override public void synchronize(List<ProjectModel> projects) {
            syncCalls++;
            selected = projects;
        }
    }
}
