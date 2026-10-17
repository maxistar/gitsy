package me.maxistar.gitsy;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduledProjectSyncWorkerSafTest {
    private static final String PROJECT_ID = "scheduled_worker_project";
    private Context context;
    private Context testContext;
    private Uri treeUri;
    private File origin;
    private File seedDirectory;

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        treeUri = DocumentsContract.buildTreeDocumentUri(
                TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID);
        grant(context.getPackageName()); grant(testContext.getPackageName());
        clearDocuments();
        new FileStorageService().deleteLocalDirectoryRecursively(context, PROJECT_ID);
        deleteRecursively(new File(context.getCacheDir(), "scheduled-worker-origin"));
        deleteRecursively(new File(context.getCacheDir(), "scheduled-worker-seed"));
        origin = new File(context.getCacheDir(), "scheduled-worker-origin");
        seedDirectory = new File(context.getCacheDir(), "scheduled-worker-seed");
        createRepository();
        context.deleteFile("projects.json");
        ProjectRepository.getInstance().loadProjects(context);
    }

    @After public void tearDown() {
        new FileStorageService().deleteLocalDirectoryRecursively(context, PROJECT_ID);
        deleteRecursively(origin); deleteRecursively(seedDirectory); clearDocuments();
        testContext.revokeUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test public void persistedSafProjectSynchronizesInWorkerAndReturnsTerminalSuccess()
            throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        assertNotNull(root);
        DocumentFile note = root.createFile("text/plain", "note.md");
        assertNotNull(note);
        try (OutputStream output = context.getContentResolver().openOutputStream(note.getUri(), "wt")) {
            assertNotNull(output);
            output.write("changed overnight\n".getBytes(StandardCharsets.UTF_8));
        }

        ProjectModel project = new ProjectModel(origin.toURI().toString(), "", "", treeUri.toString());
        project.folderName = PROJECT_ID;
        project.setStatus(ProjectModel.STATUS_READY);
        ProjectRepository.getInstance().addProject(context, project);

        Data input = new Data.Builder()
                .putString(ScheduledSyncConstants.KEY_PROJECT_ID, PROJECT_ID)
                .putLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, System.currentTimeMillis())
                .build();
        ScheduledProjectSyncWorker worker = TestListenableWorkerBuilder
                .from(context, ScheduledProjectSyncWorker.class).setInputData(input).build();

        ListenableWorker.Result result = worker.doWork();

        assertTrue(result instanceof ListenableWorker.Result.Success);
        assertEquals(ProjectModel.STATUS_READY, project.getStatus());
        assertEquals(SyncFailureCategory.NONE, project.getSyncFailureCategory());
        assertTrue(project.getLastSync() > 0);
        try (Git originGit = Git.open(origin)) {
            assertEquals(2, count(originGit.log().call()));
        }
    }

    @Test public void unavailableSafFolderPersistsAttentionAndDoesNotRetry() {
        ProjectModel project = new ProjectModel(origin.toURI().toString(), "", "",
                DocumentsContract.buildTreeDocumentUri("missing.provider", "root").toString());
        project.folderName = PROJECT_ID;
        project.setStatus(ProjectModel.STATUS_READY);
        ProjectRepository.getInstance().addProject(context, project);
        Data input = new Data.Builder()
                .putString(ScheduledSyncConstants.KEY_PROJECT_ID, PROJECT_ID)
                .putLong(ScheduledSyncConstants.KEY_TRIGGER_TIME, System.currentTimeMillis()).build();
        ScheduledProjectSyncWorker worker = TestListenableWorkerBuilder
                .from(context, ScheduledProjectSyncWorker.class).setInputData(input)
                .setRunAttemptCount(7).build();

        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertEquals(ProjectModel.STATUS_SYNC_ERROR, project.getStatus());
        assertEquals(SyncFailureCategory.FOLDER_ACCESS, project.getSyncFailureCategory());
    }

    private void createRepository() throws Exception {
        try (Git ignored = Git.init().setBare(true).setDirectory(origin).call();
             Git seed = Git.init().setDirectory(seedDirectory).call()) {
            configure(seed);
            write(new File(seedDirectory, "note.md"), "initial\n");
            seed.add().addFilepattern(".").call(); seed.commit().setMessage("initial").call();
            seed.remoteAdd().setName("origin").setUri(new URIish(origin.toURI().toString())).call();
            seed.push().setRemote("origin").setPushAll().call();
        }
        File working = new File(context.getFilesDir(), PROJECT_ID);
        try (Git git = Git.cloneRepository().setURI(origin.toURI().toString())
                .setDirectory(working).call()) { configure(git); }
    }

    private void configure(Git git) throws Exception {
        git.getRepository().getConfig().setString("user", null, "name", "GitSy Test");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.invalid");
        git.getRepository().getConfig().save();
    }

    private void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        assertNotNull(parent); assertTrue(parent.mkdirs() || parent.isDirectory());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private int count(Iterable<?> values) { int count = 0; for (Object ignored : values) count++; return count; }

    private void grant(String packageName) {
        testContext.grantUriPermission(packageName, treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    }

    private void clearDocuments() {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.exists()) return;
        for (DocumentFile child : root.listFiles()) child.delete();
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        return file.delete();
    }
}
