package me.maxistar.gitsy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.Assert.*;

public class GitServiceSshAuthenticationTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void globalIdentityAndPinnedHostApplyToClonePullAndPush() throws Exception {
        File remoteDirectory = temporaryFolder.newFolder("remote");
        File seedDirectory = temporaryFolder.newFolder("seed");
        File cloneDirectory = temporaryFolder.newFolder("clone"); assertTrue(cloneDirectory.delete());
        File secondClone = temporaryFolder.newFolder("second"); assertTrue(secondClone.delete());
        File thirdClone = temporaryFolder.newFolder("third"); assertTrue(thirdClone.delete());
        byte[] privateKey = identity();
        try (Git remote = Git.init().setBare(true).setDirectory(remoteDirectory).call();
             Git seed = Git.init().setDirectory(seedDirectory).call();
             SshGitServerFixture server = new SshGitServerFixture(remote.getRepository(),
                     temporaryFolder.newFolder("server").toPath())) {
            TestFiles.write(seedDirectory, "note.md", "initial\n");
            seed.add().addFilepattern(".").call(); seed.commit().setMessage("seed").call();
            seed.push().setRemote(remoteDirectory.toURI().toString()).call();
            server.start();

            MemoryIdentities identities = new MemoryIdentities(privateKey);
            MemoryPassphrases passphrases = new MemoryPassphrases();
            MemoryTrustedHosts trusted = new MemoryTrustedHosts();
            SshHostTrustCoordinator coordinator = new SshHostTrustCoordinator(trusted);
            ProjectTransportAuthenticationFactory factory = new ProjectTransportAuthenticationFactory(
                    new SshIdentityMaterialLoader(identities, passphrases), coordinator);
            ProjectModel project = sshProject(server.port());
            GitService service = new GitService();

            SshHostTrustRequest request;
            try {
                service.cloneRepository(cloneDirectory, project, factory);
                fail("Unknown host was accepted"); return;
            } catch (SshHostTrustRequiredException required) {
                request = required.getRequest();
            }
            assertEquals(server.port(), request.getPort());
            assertTrue(coordinator.accept(request.getToken()));
            service.cloneRepository(cloneDirectory, project, factory);

            try (Git clone = Git.open(cloneDirectory)) { LocalGitFixture.configureIdentity(clone); }
            TestFiles.write(cloneDirectory, "local.md", "from first\n");
            service.syncRepository(cloneDirectory, project, factory);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> second = executor.submit(() -> cloneUnchecked(
                        service, secondClone, project, factory));
                Future<?> third = executor.submit(() -> cloneUnchecked(
                        service, thirdClone, project, factory));
                second.get(); third.get();
            } finally { executor.shutdownNow(); }
            assertEquals("from first\n", TestFiles.read(secondClone, "local.md"));
            assertEquals("from first\n", TestFiles.read(thirdClone, "local.md"));
            assertTrue("Each operation loaded the same global identity", identities.loadCount >= 5);
        } finally { java.util.Arrays.fill(privateKey, (byte) 0); }
    }

    private static ProjectModel sshProject(int port) {
        ProjectModel project = new ProjectModel("git@127.0.0.1:notes.git", "git", null, "folder");
        project.useSshKeyAuthentication("git", port); return project;
    }
    private static void cloneUnchecked(GitService service, File target, ProjectModel project,
                                       ProjectTransportAuthenticationFactory factory) {
        try { service.cloneRepository(target, project, factory); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private static byte[] identity() throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 3072);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); pair.writeOpenSSHv1PrivateKey(out, new byte[0]);
        pair.dispose(); return out.toByteArray();
    }
    private static final class MemoryIdentities implements SshIdentityRepository {
        final byte[] key; int loadCount;
        MemoryIdentities(byte[] key) { this.key = key; }
        @Override public void save(SshIdentityMetadata metadata, byte[] privateKey) { }
        @Override public SshIdentityMetadata metadata() { return new SshIdentityMetadata("RSA", 3072, "SHA256:test", false); }
        @Override public synchronized byte[] loadPrivateKey() { loadCount++; return key.clone(); }
        @Override public boolean contains() { return true; }
        @Override public void delete() { }
    }
    private static final class MemoryPassphrases implements SshPassphraseRepository {
        @Override public void save(byte[] passphrase) { }
        @Override public byte[] load() { return null; }
        @Override public boolean contains() { return false; }
        @Override public void delete() { }
    }
    private static final class MemoryTrustedHosts implements TrustedHostRepository {
        final Map<String,SshHostIdentity> records = new HashMap<>();
        @Override public synchronized TrustedHostMatch check(SshHostIdentity value) {
            SshHostIdentity old = records.get(value.getHost()+":"+value.getPort());
            return old == null ? TrustedHostMatch.UNKNOWN : old.sameKey(value) ? TrustedHostMatch.MATCH : TrustedHostMatch.CHANGED;
        }
        @Override public synchronized void trustUnknown(SshHostIdentity value) { records.put(value.getHost()+":"+value.getPort(), value); }
        @Override public synchronized void replace(SshHostIdentity value) { trustUnknown(value); }
        @Override public synchronized void remove(String host, int port) { records.remove(host+":"+port); }
    }
}
