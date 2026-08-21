package me.maxistar.gitsy;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

final class LocalGitFixture implements AutoCloseable {
    private final TemporaryFolder temporaryFolder;
    private Git seed;
    private Git peer;
    private File origin;

    LocalGitFixture(TemporaryFolder temporaryFolder) {
        this.temporaryFolder = temporaryFolder;
    }

    void create() throws Exception {
        origin = temporaryFolder.newFolder("origin.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(origin).call()) {
            // Bare origin is fully initialized when the command closes.
        }
        File seedDirectory = temporaryFolder.newFolder("seed");
        seed = Git.init().setDirectory(seedDirectory).call();
        configureIdentity(seed);
        TestFiles.write(seedDirectory, "notes/initial.md", "initial note\n");
        seed.add().addFilepattern(".").call();
        seed.commit().setMessage("initial").call();
        seed.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish(origin.toURI().toString()))
                .call();
        seed.push().setRemote("origin").setPushAll().call();

        File peerDirectory = temporaryFolder.newFolder("peer");
        peer = Git.cloneRepository().setURI(origin.toURI().toString()).setDirectory(peerDirectory).call();
        configureIdentity(peer);
    }

    File origin() {
        return origin;
    }

    Git peer() {
        return peer;
    }

    static void configureIdentity(Git git) throws IOException {
        git.getRepository().getConfig().setString("user", null, "name", "GitSy Test");
        git.getRepository().getConfig().setString("user", null, "email", "gitsy-test@example.invalid");
        git.getRepository().getConfig().save();
    }

    @Override
    public void close() {
        if (peer != null) peer.close();
        if (seed != null) seed.close();
    }
}
