package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScheduledProjectDecisionTest {
    private final ScheduledProjectDecision decision =
            new ScheduledProjectDecision(new ScheduledProjectEligibility());

    @Test public void missingProjectIsSkipped() { assertFalse(decision.shouldRun(null, 10)); }

    @Test public void newerSuccessfulSyncIsSkipped() {
        ProjectModel project = ready(); project.setLastSync(11);
        assertFalse(decision.shouldRun(project, 10));
    }

    @Test public void readyProjectAtOrBeforeTriggerRuns() {
        ProjectModel project = ready(); project.setLastSync(10);
        assertTrue(decision.shouldRun(project, 10));
    }

    private ProjectModel ready() {
        ProjectModel project = new ProjectModel("url", "user", "secret", "uri");
        project.setStatus(ProjectModel.STATUS_READY); return project;
    }
}
