package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScheduledProjectEligibilityTest {
    private final ScheduledProjectEligibility eligibility = new ScheduledProjectEligibility();

    @Test public void readyProjectIsEligibleRegardlessOfRecentSync() {
        ProjectModel project = project(ProjectModel.STATUS_READY, SyncFailureCategory.NONE);
        project.setLastSync(System.currentTimeMillis());
        assertTrue(eligibility.isEligible(project));
    }

    @Test public void transientFailureCanTryOnNextDailyTrigger() {
        assertTrue(eligibility.isEligible(project(
                ProjectModel.STATUS_SYNC_ERROR, SyncFailureCategory.TRANSIENT)));
    }

    @Test public void unsafeStatesAreSkipped() {
        assertFalse(eligibility.isEligible(project(
                ProjectModel.STATUS_TO_CLONE, SyncFailureCategory.NONE)));
        assertFalse(eligibility.isEligible(project(
                ProjectModel.STATUS_SYNC_IN_PROGRESS, SyncFailureCategory.NONE)));
        assertFalse(eligibility.isEligible(project(
                ProjectModel.STATUS_READY, SyncFailureCategory.AUTHENTICATION)));
        assertFalse(eligibility.isEligible(project(
                ProjectModel.STATUS_READY, SyncFailureCategory.UNKNOWN)));
    }

    private ProjectModel project(int status, SyncFailureCategory category) {
        ProjectModel project = new ProjectModel("url", "user", "secret", "uri");
        project.setStatus(status); project.setSyncFailureCategory(category); return project;
    }
}
