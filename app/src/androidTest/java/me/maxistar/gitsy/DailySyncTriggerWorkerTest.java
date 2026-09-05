package me.maxistar.gitsy;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DailySyncTriggerWorkerTest {
    private Context context;
    private AndroidWorkManagerGateway gateway;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        gateway = new AndroidWorkManagerGateway(context);
        gateway.cancelDailyTrigger();
        context.getSharedPreferences(SharedPreferencesScheduledSyncSettings.PREFERENCES_NAME,
                Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteFile("projects.json");
        ProjectRepository.getInstance().loadProjects(context);
    }

    @After public void tearDown() {
        gateway.cancelDailyTrigger();
        context.getSharedPreferences(SharedPreferencesScheduledSyncSettings.PREFERENCES_NAME,
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void enabledTriggerEnqueuesProjectAndTomorrowIndependently() throws Exception {
        new SharedPreferencesScheduledSyncSettings(context)
                .save(new ScheduledSyncSettings(true, 2, 0));
        ProjectModel project = new ProjectModel("https://example.invalid/repo.git", "user",
                "secret", "content://missing/tree/root");
        project.folderName = "trigger_project";
        project.setStatus(ProjectModel.STATUS_READY);
        ProjectRepository.getInstance().addProject(context, project);

        DailySyncTriggerWorker worker = TestListenableWorkerBuilder
                .from(context, DailySyncTriggerWorker.class).build();
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertFalse(gateway.projectWorkState(project.getFolderName()).get().isEmpty());
        assertEquals(1, unfinished(gateway.dailyWorkState().get()));
    }

    @Test public void disabledTriggerEnqueuesNothing() throws Exception {
        DailySyncTriggerWorker worker = TestListenableWorkerBuilder
                .from(context, DailySyncTriggerWorker.class).build();
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertTrue(gateway.projectWorkState("missing").get().isEmpty());
        for (int attempt = 0; attempt < 20 && unfinished(gateway.dailyWorkState().get()) > 0;
             attempt++) Thread.sleep(50);
        assertEquals(0, unfinished(gateway.dailyWorkState().get()));
    }

    private int unfinished(java.util.List<androidx.work.WorkInfo> values) {
        int count = 0;
        for (androidx.work.WorkInfo value : values) if (!value.getState().isFinished()) count++;
        return count;
    }
}
