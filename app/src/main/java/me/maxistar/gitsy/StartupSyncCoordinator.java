package me.maxistar.gitsy;

import java.util.List;

public final class StartupSyncCoordinator {
    public interface Clock {
        long currentTimeMillis();
    }

    public interface Actions {
        void showRecovery();
        void synchronize(List<ProjectModel> projects);
    }

    public enum Result {
        WAITING_FOR_LOAD,
        ALREADY_EVALUATED,
        RECOVERY_REQUIRED,
        NO_SYNCHRONIZATION,
        SYNCHRONIZATION_STARTED
    }

    private final StartupSyncSettingsRepository settingsRepository;
    private final StartupSyncPolicy policy;
    private final StartupSyncRunGuard runGuard;
    private final Clock clock;
    private final Actions actions;

    public StartupSyncCoordinator(
            StartupSyncSettingsRepository settingsRepository,
            StartupSyncPolicy policy,
            StartupSyncRunGuard runGuard,
            Clock clock,
            Actions actions) {
        this.settingsRepository = settingsRepository;
        this.policy = policy;
        this.runGuard = runGuard;
        this.clock = clock;
        this.actions = actions;
    }

    public Result onProjectsChanged(
            boolean loadingComplete, boolean synchronizationServiceRunning, List<ProjectModel> projects) {
        if (!loadingComplete) return Result.WAITING_FOR_LOAD;
        if (!runGuard.claim()) return Result.ALREADY_EVALUATED;

        if (synchronizationServiceRunning) return Result.NO_SYNCHRONIZATION;
        if (hasInterruptedWork(projects)) {
            actions.showRecovery();
            return Result.RECOVERY_REQUIRED;
        }

        List<ProjectModel> selected = policy.select(
                settingsRepository.load(), clock.currentTimeMillis(), projects);
        if (selected.isEmpty()) return Result.NO_SYNCHRONIZATION;

        actions.synchronize(selected);
        return Result.SYNCHRONIZATION_STARTED;
    }

    private boolean hasInterruptedWork(List<ProjectModel> projects) {
        for (ProjectModel project : projects) {
            int status = project.getStatus();
            if (status == ProjectModel.STATUS_TO_CLONE
                    || status == ProjectModel.STATUS_CLONING
                    || status == ProjectModel.STATUS_TO_SYNC
                    || status == ProjectModel.STATUS_SYNC_IN_PROGRESS) {
                return true;
            }
        }
        return false;
    }
}
