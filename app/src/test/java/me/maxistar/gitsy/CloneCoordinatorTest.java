package me.maxistar.gitsy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CloneCoordinatorTest {
    @Test public void authenticationFailurePersistsCategoryAndSkipsExport() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        CloneCoordinator coordinator = coordinator(calls,
                new HttpsCloneException(CloneFailureCategory.AUTHENTICATION,
                        new Exception("secret-token")), null);

        CloneCoordinator.Result result = coordinator.clone(project);

        assertFalse(result.isSuccess());
        assertEquals(Arrays.asList("state:1", "clone", "state:5"), calls);
        assertEquals(CloneFailureCategory.AUTHENTICATION, project.getCloneFailureCategory());
        assertEquals(ProjectModel.STATUS_CLONING_ERROR, project.getStatus());
    }

    @Test public void repeatedFailureRemainsRetryableAndDoesNotExport() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        CloneCoordinator coordinator = coordinator(calls,
                new HttpsCloneException(CloneFailureCategory.AUTHENTICATION, null), null);
        assertFalse(coordinator.clone(project).isSuccess());
        project.setStatus(ProjectModel.STATUS_TO_CLONE);
        assertFalse(coordinator.clone(project).isSuccess());
        assertEquals(2, count(calls, "clone"));
        assertEquals(0, count(calls, "export"));
    }

    @Test public void correctedRetryClearsPersistedFailureAndCompletes() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        project.setCloneFailureCategory(CloneFailureCategory.AUTHENTICATION);
        CloneCoordinator.Result result = coordinator(calls, null, null).clone(project);
        assertTrue(result.isSuccess());
        assertEquals(CloneFailureCategory.NONE, project.getCloneFailureCategory());
        assertEquals(ProjectModel.STATUS_READY, project.getStatus());
        assertEquals(3, project.getNumberFiles());
        assertEquals(42L, project.getLastSync());
        assertEquals(Arrays.asList("state:1", "clone", "state:1", "export", "state:2"), calls);
    }

    @Test public void nonAuthenticationFailureIsNotMislabeled() {
        ProjectModel project = project();
        CloneCoordinator.Result result = coordinator(new ArrayList<>(),
                new HttpsCloneException(CloneFailureCategory.NETWORK, null), null).clone(project);
        assertFalse(result.isSuccess());
        assertEquals(CloneFailureCategory.NETWORK, project.getCloneFailureCategory());
    }

    @Test public void exportFailureHappensAfterCloneAndKeepsCloneMetadataClear() {
        List<String> calls = new ArrayList<>();
        ProjectModel project = project();
        project.setCloneFailureCategory(CloneFailureCategory.AUTHENTICATION);
        CloneCoordinator.Result result = coordinator(calls, null, new Exception("export")).clone(project);
        assertFalse(result.isSuccess());
        assertEquals(Arrays.asList("state:1", "clone", "state:1", "export", "state:5"), calls);
        assertEquals(CloneFailureCategory.NONE, project.getCloneFailureCategory());
    }

    private static CloneCoordinator coordinator(List<String> calls, Exception cloneError,
                                                Exception exportError) {
        return new CloneCoordinator(project -> {
            calls.add("clone");
            if (cloneError != null) throw cloneError;
        }, project -> {
            calls.add("export");
            if (exportError != null) throw exportError;
            return 3;
        }, () -> 42L, project -> calls.add("state:" + project.getStatus()));
    }

    private static ProjectModel project() {
        return new ProjectModel("https://example.invalid/repo.git", "user", "token",
                "content://notes");
    }

    private static int count(List<String> values, String target) {
        int count = 0;
        for (String value : values) if (target.equals(value)) count++;
        return count;
    }
}
