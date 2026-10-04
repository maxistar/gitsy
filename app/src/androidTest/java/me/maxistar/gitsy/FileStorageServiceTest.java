package me.maxistar.gitsy;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FileStorageServiceTest {
    private static final String SOURCE = "connected-test-source";
    private Context context;
    private FileStorageService storage;
    private Uri treeUri;
    private Context testContext;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        storage = new FileStorageService();
        treeUri = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID);
        grantTreeAccess(context.getPackageName());
        grantTreeAccess(testContext.getPackageName());
        storage.deleteLocalDirectoryRecursively(context, SOURCE);
        clearDocuments();
        assertTrue(localRoot().mkdirs() || localRoot().isDirectory());
    }

    @After
    public void tearDown() {
        storage.deleteLocalDirectoryRecursively(context, SOURCE);
        clearDocuments();
        testContext.revokeUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Test
    public void controlledProviderSupportsDocumentLifecycleAndUnicode() throws Exception {
        DocumentFile root = requireRoot();
        DocumentFile directory = root.createDirectory("вложенная папка");
        assertNotNull(directory);
        DocumentFile file = directory.createFile("text/plain", "заметка с пробелом.md");
        assertNotNull(file);
        write(file, "first");
        assertEquals("first", read(file));
        write(file, "replacement");
        assertEquals("replacement", read(file));
        assertTrue(file.delete());
        assertNull(directory.findFile("заметка с пробелом.md"));
    }

    @Test
    public void copyToSafAndBackUsesOwnedFixture() throws Exception {
        writeLocal("note.md", "top level");
        writeLocal("nested/empty.md", "");
        writeLocal("nested/заметка.md", "unicode");

        assertEquals(3, storage.copyToSaf(context, SOURCE, treeUri));
        DocumentFile root = requireRoot();
        assertNotNull(root.findFile("note.md"));
        assertNotNull(root.findFile("nested"));

        storage.deleteLocalDirectoryRecursively(context, SOURCE);
        assertTrue(localRoot().mkdirs());
        storage.copyFromSaf(context, treeUri, SOURCE);
        assertEquals("top level", readLocal("note.md"));
        assertEquals("unicode", readLocal("nested/заметка.md"));
        assertEquals("", readLocal("nested/empty.md"));
    }

    @Test
    public void unavailableProviderFailsWithoutTouchingOwnedFixture() throws Exception {
        writeLocal("preserved.md", "preserved");
        Uri unavailable = DocumentsContract.buildTreeDocumentUri("me.maxistar.gitsync.missing.documents", "root");
        try {
            storage.copyFromSaf(context, unavailable, SOURCE);
            fail("Expected unavailable provider to fail");
        } catch (RuntimeException expected) {
            assertEquals("preserved", readLocal("preserved.md"));
            assertEquals(0, requireRoot().listFiles().length);
        }
    }

    private DocumentFile requireRoot() {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        assertNotNull(root);
        assertTrue(root.exists());
        return root;
    }

    private void grantTreeAccess(String packageName) {
        testContext.grantUriPermission(
                packageName,
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    }

    private void clearDocuments() {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.exists()) return;
        for (DocumentFile child : root.listFiles()) assertTrue("Could not delete " + child.getName(), child.delete());
    }

    private File localRoot() {
        return new File(context.getFilesDir(), SOURCE);
    }

    private void writeLocal(String relativePath, String contents) throws Exception {
        File file = new File(localRoot(), relativePath);
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.mkdirs() || parent.isDirectory());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readLocal(String relativePath) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(new File(localRoot(), relativePath).toPath()), StandardCharsets.UTF_8);
    }

    private void write(DocumentFile file, String contents) throws Exception {
        try (OutputStream output = context.getContentResolver().openOutputStream(file.getUri(), "wt")) {
            assertNotNull(output);
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String read(DocumentFile file) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {
            assertNotNull(input);
            byte[] buffer = new byte[1024];
            int count = input.read(buffer);
            return count < 0 ? "" : new String(buffer, 0, count, StandardCharsets.UTF_8);
        }
    }
}
