package me.maxistar.gitsy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class FileTreeSnapshotTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void comparesAddedModifiedUnchangedAndRemovedNestedEntries() throws Exception {
        File source = temporaryFolder.newFolder("source");
        File destination = temporaryFolder.newFolder("destination");
        TestFiles.write(source, "added.md", "added");
        TestFiles.write(source, "nested/modified.md", "new");
        TestFiles.write(destination, "nested/modified.md", "old");
        TestFiles.write(source, "same.md", "same");
        TestFiles.write(destination, "same.md", "same");
        TestFiles.write(destination, "removed.md", "removed");

        assertEquals(Arrays.asList(
                        "added.md=ADDED",
                        "nested=UNCHANGED",
                        "nested/modified.md=MODIFIED",
                        "removed.md=REMOVED",
                        "same.md=UNCHANGED"),
                describe(FileTreeSnapshot.scan(source).compareTo(FileTreeSnapshot.scan(destination))));
    }

    @Test
    public void handlesEmptyUnicodeSpacedAndNestedFiles() throws Exception {
        File source = temporaryFolder.newFolder("edge-source");
        File destination = temporaryFolder.newFolder("edge-destination");
        assertTrue(new File(source, "empty folder").mkdirs());
        assertTrue(new File(destination, "empty folder").mkdirs());
        TestFiles.write(source, "заметки/новая заметка.md", "");
        TestFiles.write(destination, "заметки/новая заметка.md", "");

        assertEquals(Arrays.asList(
                        "empty folder=UNCHANGED",
                        "заметки=UNCHANGED",
                        "заметки/новая заметка.md=UNCHANGED"),
                describe(FileTreeSnapshot.scan(source).compareTo(FileTreeSnapshot.scan(destination))));
    }

    @Test
    public void identicalContentIgnoresDifferentModificationMetadata() throws Exception {
        File source = temporaryFolder.newFolder("metadata-source");
        File destination = temporaryFolder.newFolder("metadata-destination");
        File sourceFile = TestFiles.write(source, "note.md", "same bytes");
        File destinationFile = TestFiles.write(destination, "note.md", "same bytes");
        assertTrue(sourceFile.setLastModified(1000L));
        assertTrue(destinationFile.setLastModified(9000L));

        assertEquals(Arrays.asList("note.md=UNCHANGED"),
                describe(FileTreeSnapshot.scan(source).compareTo(FileTreeSnapshot.scan(destination))));
    }

    @Test
    public void failedDiscoveryProducesNoPartialSnapshot() throws Exception {
        File source = temporaryFolder.newFolder("failed-source");
        TestFiles.write(source, "nested/note.md", "note");
        try {
            FileTreeSnapshot.scan(source, directory -> {
                if (directory.getName().equals("nested")) throw new IOException("injected discovery failure");
                File[] children = directory.listFiles();
                if (children == null) throw new IOException("unreadable");
                return children;
            });
            fail("Expected discovery failure");
        } catch (IOException expected) {
            assertEquals("injected discovery failure", expected.getMessage());
        }
    }

    @Test
    public void repeatedSnapshotsAreDeterministicAndIndependent() throws Exception {
        File source = temporaryFolder.newFolder("repeat-source");
        File destination = temporaryFolder.newFolder("repeat-destination");
        TestFiles.write(source, "note.md", "version one");
        List<String> first = describe(FileTreeSnapshot.scan(source).compareTo(FileTreeSnapshot.scan(destination)));
        TestFiles.write(source, "note.md", "version two");
        List<String> second = describe(FileTreeSnapshot.scan(source).compareTo(FileTreeSnapshot.scan(destination)));
        assertEquals(Arrays.asList("note.md=ADDED"), first);
        assertEquals(Arrays.asList("note.md=ADDED"), second);
    }

    private static List<String> describe(List<FileTreeSnapshot.Difference> differences) {
        List<String> result = new ArrayList<>();
        for (FileTreeSnapshot.Difference difference : differences) {
            result.add(difference.path + "=" + difference.change.name());
        }
        return result;
    }
}
