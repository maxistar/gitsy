package me.maxistar.gitsy;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduledProjectSyncWorkerTest {
    private Context context;
    private ProjectRepository repository;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteFile("projects.json");
        repository = ProjectRepository.getInstance();
        repository.loadProjects(context);
    }

    @Test public void invalidInputFailsWithoutRetry() {
        ListenableWorker.Result result = worker(new Data.Builder().build()).doWork();
        assertTrue(result instanceof ListenableWorker.Result.Failure);
    }

    @Test public void removedAndAttentionProjectsFinishWithoutRetry() {
        assertSuccess(worker(input("removed", 10)).doWork());

        ProjectModel attention = ready("attention");
        attention.setSyncFailureCategory(SyncFailureCategory.SSH_ATTENTION);
        repository.addProject(context, attention);
        assertSuccess(worker(input("attention", 10)).doWork());
        assertEquals(ProjectModel.STATUS_READY, attention.getStatus());
    }

    @Test public void newerSyncAndGateContentionSkipWithoutTouchingStorage() {
        ProjectModel recent = ready("recent"); recent.setLastSync(11);
        repository.addProject(context, recent);
        assertSuccess(worker(input("recent", 10)).doWork());

        ProjectModel active = ready("active"); repository.addProject(context, active);
        ProjectExecutionGate gate = ServiceLocator.getInstance().getProjectExecutionGate();
        assertTrue(gate.tryAcquire("active"));
        try { assertSuccess(worker(input("active", 10)).doWork()); }
        finally { gate.release("active"); }
        assertEquals(ProjectModel.STATUS_READY, active.getStatus());
    }

    private ScheduledProjectSyncWorker worker(Data input) {
        return TestListenableWorkerBuilder.from(context, ScheduledProjectSyncWorker.class)
                .setInputData(input).setRunAttemptCount(9).build();
    }

    private Data input(String projectId, long triggerTime) {
        return new Data.Builder().putString(ScheduledSyncConstants.KEY_PROJECT_ID, projectId)
                .putLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, triggerTime).build();
    }

    private ProjectModel ready(String id) {
        ProjectModel project = new ProjectModel("url", "user", "secret", "uri");
        project.folderName = id; project.setStatus(ProjectModel.STATUS_READY); return project;
    }

    private void assertSuccess(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Success);
    }
}
