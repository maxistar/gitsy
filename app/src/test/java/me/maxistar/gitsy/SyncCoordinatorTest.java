package me.maxistar.gitsy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SyncCoordinatorTest {
    private static final long NOW = 424242L;

    @Test
    public void successRunsOperationsInOrderAndUpdatesMetadataOnce() {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        ProjectModel project = project();
        SyncCoordinator coordinator = coordinator(calls, null, null, null);

        SyncCoordinator.Result result = coordinator.synchronize(project);

        assertTrue(result.isSuccess());
        assertEquals(Arrays.asList("state:4", "import", "git", "export", "clock", "state:2"), calls);
        assertEquals(ProjectModel.STATUS_READY, project.getStatus());
        assertEquals(NOW, project.getLastSync());
        assertEquals(1, count(calls, "clock"));
    }

    @Test
    public void importFailureSkipsGitAndExportAndLeavesErrorState() {
        assertFailureAt("import", Arrays.asList("state:4", "import", "state:6"));
    }

    @Test
    public void gitFailureSkipsExportAndLeavesErrorState() {
        assertFailureAt("git", Arrays.asList("state:4", "import", "git", "state:6"));
    }

    @Test
    public void exportFailureDoesNotRecordSuccess() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        SyncCoordinator.Result result = coordinator(calls, null, null, "export").synchronize(project);
        assertFalse(result.isSuccess());
        assertEquals(ProjectModel.STATUS_SYNC_ERROR, project.getStatus());
        assertEquals(0L, project.getLastSync());
        assertEquals(Arrays.asList("state:4", "import", "git", "export", "state:6"), calls);
    }

    @Test
    public void repeatedInvocationRepeatsTheCurrentSequence() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        SyncCoordinator coordinator = coordinator(calls, null, null, null);
        assertTrue(coordinator.synchronize(project).isSuccess());
        assertTrue(coordinator.synchronize(project).isSuccess());
        assertEquals(2, count(calls, "import"));
        assertEquals(2, count(calls, "git"));
        assertEquals(2, count(calls, "export"));
    }

    @Test
    public void overlappingInvocationIsCurrentlyNotSerialized() throws Exception {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch bothEnteredImport = new CountDownLatch(2);
        CountDownLatch releaseImport = new CountDownLatch(1);
        SyncCoordinator coordinator = new SyncCoordinator(
                project -> {
                    calls.add("import");
                    bothEnteredImport.countDown();
                    if (!releaseImport.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
                },
                project -> calls.add("git"),
                project -> calls.add("export"),
                () -> NOW,
                project -> calls.add("state:" + project.getStatus()));

        Thread first = new Thread(() -> coordinator.synchronize(project()));
        Thread second = new Thread(() -> coordinator.synchronize(project()));
        first.start();
        second.start();
        assertTrue("Both current invocations may enter import concurrently",
                bothEnteredImport.await(5, TimeUnit.SECONDS));
        releaseImport.countDown();
        first.join(5000);
        second.join(5000);
        assertEquals(2, count(calls, "import"));
    }

    private static void assertFailureAt(String failure, List<String> expectedCalls) {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        SyncCoordinator.Result result = coordinator(
                calls,
                "import".equals(failure) ? failure : null,
                "git".equals(failure) ? failure : null,
                null).synchronize(project);
        assertFalse(result.isSuccess());
        assertEquals(failure, result.getError().getMessage());
        assertEquals(ProjectModel.STATUS_SYNC_ERROR, project.getStatus());
        assertEquals(expectedCalls, calls);
    }

    private static SyncCoordinator coordinator(
            List<String> calls, String importFailure, String gitFailure, String exportFailure) {
        return new SyncCoordinator(
                project -> { calls.add("import"); failIf(importFailure); },
                project -> { calls.add("git"); failIf(gitFailure); },
                project -> { calls.add("export"); failIf(exportFailure); },
                () -> { calls.add("clock"); return NOW; },
                project -> calls.add("state:" + project.getStatus()));
    }

    private static void failIf(String message) throws Exception {
        if (message != null) throw new Exception(message);
    }

    private static ProjectModel project() {
        return new ProjectModel("https://example.invalid/repo.git", "user", "token", "content://test/tree/notes");
    }

    private static int count(List<String> values, String target) {
        int count = 0;
        for (String value : values) if (target.equals(value)) count++;
        return count;
    }
}
