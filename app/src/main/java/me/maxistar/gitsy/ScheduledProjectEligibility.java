package me.maxistar.gitsy;

final class ScheduledProjectEligibility {
    boolean isEligible(ProjectModel project) {
        if (project == null) return false;
        if (project.getStatus() == ProjectModel.STATUS_READY) {
            return project.getSyncFailureCategory() == SyncFailureCategory.NONE
                    || project.getSyncFailureCategory() == SyncFailureCategory.TRANSIENT;
        }
        return project.getStatus() == ProjectModel.STATUS_SYNC_ERROR
                && project.getSyncFailureCategory() == SyncFailureCategory.TRANSIENT;
    }
}
