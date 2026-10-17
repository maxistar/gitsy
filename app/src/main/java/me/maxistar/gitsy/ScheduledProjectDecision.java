package me.maxistar.gitsy;

final class ScheduledProjectDecision {
    private final ScheduledProjectEligibility eligibility;

    ScheduledProjectDecision(ScheduledProjectEligibility eligibility) {
        this.eligibility = eligibility;
    }

    boolean shouldRun(ProjectModel project, long triggerTime) {
        return eligibility.isEligible(project) && project.getLastSync() <= triggerTime;
    }
}
