package me.maxistar.gitsy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class CloneWorkspaceTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void promotesCompleteStagingRepository() throws Exception {
        File root = temporaryFolder.newFolder("root");
        CloneWorkspace workspace = new CloneWorkspace(root, "project_1");
        File staging = workspace.begin("operation-1");
        assertTrue(new File(staging, ".git").mkdirs());
        assertTrue(new File(staging, "note.md").createNewFile());

        workspace.promote(staging);

        assertFalse(staging.exists());
        assertTrue(new File(workspace.finalDirectory(), ".git").isDirectory());
        assertTrue(new File(workspace.finalDirectory(), "note.md").isFile());
    }

    @Test public void discardAndNextBeginRemovePartialAndStaleContent() throws Exception {
        File root = temporaryFolder.newFolder("root");
        CloneWorkspace workspace = new CloneWorkspace(root, "project_2");
        File first = workspace.begin("first");
        assertTrue(first.mkdirs());
        assertTrue(new File(first, "partial").createNewFile());
        workspace.discard(first);
        assertFalse(first.exists());

        File stale = new File(root, "project_2.clone-staging-interrupted");
        assertTrue(stale.mkdirs());
        assertTrue(new File(stale, "partial").createNewFile());
        File second = workspace.begin("second");
        assertFalse(stale.exists());
        assertEquals("project_2.clone-staging-second", second.getName());
    }

    @Test public void removesLegacyPartialFinalButPreservesValidRepository() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File partial = new File(root, "project_partial");
        assertTrue(partial.mkdirs());
        assertTrue(new File(partial, "partial").createNewFile());
        new CloneWorkspace(root, "project_partial").begin("retry");
        assertFalse(partial.exists());

        File valid = new File(root, "project_valid/.git");
        assertTrue(valid.mkdirs());
        try {
            new CloneWorkspace(root, "project_valid").begin("retry");
            fail("Expected valid repository preservation");
        } catch (IOException expected) {
            assertTrue(valid.isDirectory());
        }
    }

    @Test public void rejectsUnsafeNamesAndForeignDiscardPaths() throws Exception {
        File root = temporaryFolder.newFolder("root");
        for (String name : new String[] { "../escape", "a/b", ".." }) {
            try { new CloneWorkspace(root, name); fail("Expected rejection: " + name); }
            catch (IOException expected) { }
        }
        CloneWorkspace workspace = new CloneWorkspace(root, "project_safe");
        File foreign = temporaryFolder.newFolder("foreign");
        try { workspace.discard(foreign); fail("Expected foreign path rejection"); }
        catch (IOException expected) { assertTrue(foreign.isDirectory()); }
    }
}
