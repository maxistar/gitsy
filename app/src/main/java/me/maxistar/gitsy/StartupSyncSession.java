package me.maxistar.gitsy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StartupSyncSession {
    public interface Clock {
        long currentTimeMillis();
    }

    public enum DecisionType {
        WAIT,
        RECOVERY_REQUIRED,
        PROMPT,
        START,
        FINISH
    }

    public enum FinishReason {
        ALREADY_EVALUATED,
        SERVICE_RUNNING,
        POLICY_DISABLED,
        NO_MATCHING_PROJECTS,
        USER_DECLINED
    }

    public static final class Decision {
        private final DecisionType type;
        private final FinishReason finishReason;
        private final List<ProjectModel> projects;
        private final int pendingCount;
        private final StartupSyncInterval interval;
        private final StartupSyncMode rememberedMode;

        private Decision(
                DecisionType type,
                FinishReason finishReason,
                List<ProjectModel> projects,
                int pendingCount,
                StartupSyncInterval interval,
                StartupSyncMode rememberedMode) {
            this.type = type;
            this.finishReason = finishReason;
            this.projects = Collections.unmodifiableList(new ArrayList<>(projects));
            this.pendingCount = pendingCount;
            this.interval = interval;
            this.rememberedMode = rememberedMode;
        }

        static Decision waitForReadiness() {
            return new Decision(DecisionType.WAIT, null, Collections.emptyList(), 0, null, null);
        }

        static Decision recoveryRequired() {
            return new Decision(DecisionType.RECOVERY_REQUIRED, null, Collections.emptyList(), 0, null, null);
        }

        static Decision prompt(int pendingCount, StartupSyncInterval interval) {
            return new Decision(DecisionType.PROMPT, null, Collections.emptyList(), pendingCount, interval, null);
        }

        static Decision start(List<ProjectModel> projects, StartupSyncMode rememberedMode) {
            return new Decision(DecisionType.START, null, projects, projects.size(), null, rememberedMode);
        }

        static Decision finish(FinishReason reason, StartupSyncMode rememberedMode) {
            return new Decision(DecisionType.FINISH, reason, Collections.emptyList(), 0, null, rememberedMode);
        }

        public DecisionType getType() { return type; }
        public FinishReason getFinishReason() { return finishReason; }
        public List<ProjectModel> getProjects() { return projects; }
        public int getPendingCount() { return pendingCount; }
        public StartupSyncInterval getInterval() { return interval; }
        public StartupSyncMode getRememberedMode() { return rememberedMode; }
    }

    private enum Phase { ACTIVE, PROMPTING, COMPLETE }

    private final StartupSyncPolicy policy;
    private final Clock clock;
    private Phase phase = Phase.ACTIVE;
    private final Set<String> pendingProjectIds = new HashSet<>();
    private StartupSyncInterval pendingInterval;

    public StartupSyncSession() {
        this(new StartupSyncPolicy(), System::currentTimeMillis);
    }

    StartupSyncSession(StartupSyncPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public synchronized Decision evaluate(
            boolean loadingComplete,
            boolean synchronizationServiceRunning,
            StartupSyncSettings settings,
            List<ProjectModel> projects) {
        if (phase == Phase.COMPLETE) {
            return Decision.finish(FinishReason.ALREADY_EVALUATED, null);
        }
        if (phase == Phase.PROMPTING) {
            return Decision.prompt(pendingProjectIds.size(), pendingInterval);
        }
        if (!loadingComplete) return Decision.waitForReadiness();

        if (synchronizationServiceRunning) {
            phase = Phase.COMPLETE;
            return Decision.finish(FinishReason.SERVICE_RUNNING, null);
        }
        if (hasInterruptedWork(projects)) {
            phase = Phase.COMPLETE;
            return Decision.recoveryRequired();
        }

        List<ProjectModel> selected = policy.select(settings, clock.currentTimeMillis(), projects);
        if (selected.isEmpty()) {
            phase = Phase.COMPLETE;
            FinishReason reason = settings.getMode() == StartupSyncMode.NEVER
                    ? FinishReason.POLICY_DISABLED
                    : FinishReason.NO_MATCHING_PROJECTS;
            return Decision.finish(reason, null);
        }

        if (settings.getMode() == StartupSyncMode.ASK_IF_STALE) {
            pendingProjectIds.clear();
            for (ProjectModel project : selected) pendingProjectIds.add(project.getFolderName());
            pendingInterval = settings.getInterval();
            phase = Phase.PROMPTING;
            return Decision.prompt(pendingProjectIds.size(), pendingInterval);
        }

        phase = Phase.COMPLETE;
        return Decision.start(selected, null);
    }

    public synchronized Decision respond(
            boolean synchronize,
            boolean remember,
            List<ProjectModel> currentProjects) {
        if (phase != Phase.PROMPTING) {
            return Decision.finish(FinishReason.ALREADY_EVALUATED, null);
        }
        phase = Phase.COMPLETE;
        StartupSyncMode rememberedMode = remember
                ? (synchronize ? StartupSyncMode.IF_STALE : StartupSyncMode.NEVER)
                : null;
        if (!synchronize) {
            return Decision.finish(FinishReason.USER_DECLINED, rememberedMode);
        }

        List<ProjectModel> stillStale = policy.select(
                new StartupSyncSettings(StartupSyncMode.IF_STALE, pendingInterval),
                clock.currentTimeMillis(), currentProjects);
        List<ProjectModel> resolved = new ArrayList<>();
        for (ProjectModel project : stillStale) {
            if (pendingProjectIds.contains(project.getFolderName())) resolved.add(project);
        }
        if (resolved.isEmpty()) {
            return Decision.finish(FinishReason.NO_MATCHING_PROJECTS, rememberedMode);
        }
        return Decision.start(resolved, rememberedMode);
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
