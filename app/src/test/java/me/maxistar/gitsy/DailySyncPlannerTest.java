package me.maxistar.gitsy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DailySyncPlannerTest {
    private final DailySyncPlanner planner =
            new DailySyncPlanner(new ScheduledProjectEligibility());

    @Test public void noProjectsProducesNoWork() {
        assertEquals(Collections.emptyList(), planner.eligibleProjectIds(Collections.emptyList()));
    }

    @Test public void mixedProjectsSelectOnlySafeStableIdentifiers() {
        ProjectModel ready = project("ready", ProjectModel.STATUS_READY, SyncFailureCategory.NONE);
        ProjectModel transientFailure = project("transient", ProjectModel.STATUS_SYNC_ERROR,
                SyncFailureCategory.TRANSIENT);
        ProjectModel attention = project("attention", ProjectModel.STATUS_READY,
                SyncFailureCategory.FOLDER_ACCESS);
        ProjectModel clone = project("clone", ProjectModel.STATUS_TO_CLONE, SyncFailureCategory.NONE);
        assertEquals(Arrays.asList("ready", "transient"), planner.eligibleProjectIds(
                Arrays.asList(ready, attention, clone, transientFailure)));
    }

    private ProjectModel project(String id, int status, SyncFailureCategory category) {
        ProjectModel project = new ProjectModel("url", "user", "secret", "uri");
        project.folderName = id; project.setStatus(status); project.setSyncFailureCategory(category);
        return project;
    }
}
