package me.maxistar.gitsy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectViewModelCloneRoutingTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void missingInternalRepositoryRoutesToClone() {
        File files = temporaryFolder.getRoot();
        ProjectModel project = project("missing-repository");
        assertEquals(ProjectModel.STATUS_TO_CLONE,
                ProjectViewModel.statusForRequestedSynchronization(files, project));
    }

    @Test public void completedInternalRepositoryRoutesToSync() throws Exception {
        File files = temporaryFolder.getRoot();
        ProjectModel project = project("completed-repository");
        File git = new File(new File(files, project.getFolderName()), ".git");
        assertTrue(git.mkdirs() || git.isDirectory());
        assertEquals(ProjectModel.STATUS_TO_SYNC,
                ProjectViewModel.statusForRequestedSynchronization(files, project));
        CloneWorkspace.deleteRecursively(git.getParentFile());
    }

    private static ProjectModel project(String folderName) {
        ProjectModel project = new ProjectModel("https://example.invalid/repo.git", "user",
                "token", "content://notes");
        project.folderName = folderName;
        return project;
    }
}
