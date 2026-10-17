package me.maxistar.gitsy;

import java.time.Clock;
import java.util.List;

final class DailySyncTrigger {
    interface Projects { List<ProjectModel> load(); }

    private final ScheduledSyncSettingsRepository settings;
    private final WorkManagerGateway gateway;
    private final ScheduledSyncScheduler scheduler;
    private final Projects projects;
    private final Clock clock;

    DailySyncTrigger(ScheduledSyncSettingsRepository settings, WorkManagerGateway gateway,
                     ScheduledSyncScheduler scheduler, Projects projects, Clock clock) {
        this.settings = settings; this.gateway = gateway; this.scheduler = scheduler;
        this.projects = projects; this.clock = clock;
    }

    int run() {
        if (!settings.load().isEnabled()) return 0;
        int enqueued = 0;
        try {
            long triggerTime = clock.millis();
            for (String projectId : new DailySyncPlanner(new ScheduledProjectEligibility())
                    .eligibleProjectIds(projects.load())) {
                try {
                    gateway.enqueueProject(projectId, triggerTime);
                    enqueued++;
                } catch (RuntimeException ignored) {
                    // A single bad request cannot block other projects or tomorrow's trigger.
                }
            }
        } finally {
            scheduler.reconcile();
        }
        return enqueued;
    }
}
