package me.maxistar.gitsy;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class GitServiceTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final GitService service = new GitService();
    private LocalGitFixture fixture;
    private File workingDirectory;

    @Before
    public void setUp() throws Exception {
        fixture = new LocalGitFixture(temporaryFolder);
        fixture.create();
        workingDirectory = new File(temporaryFolder.getRoot(), "gitsy");
        service.cloneRepository(workingDirectory, fixture.origin().toURI().toString(), "", "");
        try (Git git = Git.open(workingDirectory)) {
            LocalGitFixture.configureIdentity(git);
        }
    }

    @After
    public void tearDown() {
        fixture.close();
    }

    @Test
    public void cloneUsesRemoteDefaultBranchAndTrackingConfiguration() throws Exception {
        try (Git git = Git.open(workingDirectory)) {
            assertEquals(Constants.MASTER, git.getRepository().getBranch());
            assertEquals("initial note\n", TestFiles.read(workingDirectory, "notes/initial.md"));
            assertEquals("origin", git.getRepository().getConfig().getString("branch", Constants.MASTER, "remote"));
            assertEquals("refs/heads/master", git.getRepository().getConfig().getString("branch", Constants.MASTER, "merge"));
        }
    }

    @Test
    public void syncPushesTrackedModificationAndRemoval() throws Exception {
        TestFiles.write(workingDirectory, "notes/initial.md", "changed locally\n");
        service.syncRepository(workingDirectory, "", "");

        fixture.peer().pull().call();
        assertEquals("changed locally\n", TestFiles.read(fixture.peer().getRepository().getWorkTree(), "notes/initial.md"));

        assertTrue(new File(workingDirectory, "notes/initial.md").delete());
        service.syncRepository(workingDirectory, "", "");
        fixture.peer().pull().call();
        assertFalse(new File(fixture.peer().getRepository().getWorkTree(), "notes/initial.md").exists());
    }

    @Test
    public void syncPullsRemoteOnlyChange() throws Exception {
        commitAndPush(fixture.peer(), "notes/remote.md", "from peer\n", "remote change");
        service.syncRepository(workingDirectory, "", "");
        assertEquals("from peer\n", TestFiles.read(workingDirectory, "notes/remote.md"));
    }

    @Test
    public void syncAutomaticallyMergesIndependentChanges() throws Exception {
        TestFiles.write(workingDirectory, "notes/local.md", "local\n");
        try (Git git = Git.open(workingDirectory)) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("local change").call();
        }
        commitAndPush(fixture.peer(), "notes/remote.md", "remote\n", "remote change");

        service.syncRepository(workingDirectory, "", "");
        fixture.peer().pull().call();
        assertEquals("local\n", TestFiles.read(fixture.peer().getRepository().getWorkTree(), "notes/local.md"));
        assertEquals("remote\n", TestFiles.read(fixture.peer().getRepository().getWorkTree(), "notes/remote.md"));
    }

    @Test
    public void conflictingPullCommitsConflictMarkersForNoteVisibility() throws Exception {
        TestFiles.write(workingDirectory, "notes/initial.md", "local line\n");
        commitAndPush(fixture.peer(), "notes/initial.md", "remote line\n", "remote conflict");

        service.syncRepository(workingDirectory, "", "");
        try (Git git = Git.open(workingDirectory)) {
            assertTrue("Conflict markers are committed so the conflict remains visible as note content",
                    git.status().call().isClean());
            assertNull(git.getRepository().resolve(Constants.MERGE_HEAD));
            assertTrue(TestFiles.read(workingDirectory, "notes/initial.md").contains("<<<<<<<"));
        }
    }

    @Test
    public void failedPushPreservesLocalCommitForRetry() throws Exception {
        TestFiles.write(workingDirectory, "notes/initial.md", "local pending\n");
        File unavailableOrigin = new File(temporaryFolder.getRoot(), "origin-unavailable.git");
        assertTrue(fixture.origin().renameTo(unavailableOrigin));

        try {
            service.syncRepository(workingDirectory, "", "");
            fail("Expected synchronization to fail while origin is unavailable");
        } catch (GitAPIException expected) {
            try (Git git = Git.open(workingDirectory)) {
                assertTrue(git.status().call().isClean());
                assertNotNull(git.getRepository().resolve(Constants.HEAD));
            }
        }
    }

    @Test
    public void noChangeSyncDoesNotCreateCommit() throws Exception {
        ObjectId before;
        try (Git git = Git.open(workingDirectory)) {
            before = git.getRepository().resolve(Constants.HEAD);
        }
        service.syncRepository(workingDirectory, "", "");
        try (Git git = Git.open(workingDirectory)) {
            assertEquals(before, git.getRepository().resolve(Constants.HEAD));
        }
    }

    @Test
    public void syncPushesUntrackedAdditionAfterPull() throws Exception {
        TestFiles.write(workingDirectory, "notes/new.md", "new note\n");
        service.syncRepository(workingDirectory, "", "");
        fixture.peer().pull().call();
        assertEquals("new note\n", TestFiles.read(fixture.peer().getRepository().getWorkTree(), "notes/new.md"));
    }

    private static void commitAndPush(Git git, String path, String contents, String message)
            throws IOException, GitAPIException {
        TestFiles.write(git.getRepository().getWorkTree(), path, contents);
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message).call();
        git.push().call();
    }
}
