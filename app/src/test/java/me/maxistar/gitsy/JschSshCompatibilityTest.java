package me.maxistar.gitsy;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JschSshCompatibilityTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rsaClonePushPullAndHostRotationAreOperationScoped() throws Exception {
        exerciseTransport(KeyPair.RSA, null);
    }

    @Test
    public void ed25519ClonePushPullAreSupported() throws Exception {
        exerciseTransport(KeyPair.ED25519, null);
    }

    @Test
    public void encryptedKeyAcceptsCorrectAndRejectsWrongPassphrase() throws Exception {
        byte[] privateKey = identity(KeyPair.RSA, "correct".getBytes(StandardCharsets.UTF_8));
        JSch correct = new JSch();
        correct.addIdentity("encrypted", privateKey, null, "correct".getBytes(StandardCharsets.UTF_8));
        try {
            new JSch().addIdentity("encrypted", privateKey, null,
                    "wrong".getBytes(StandardCharsets.UTF_8));
            fail("wrong passphrase accepted");
        } catch (com.jcraft.jsch.JSchException expected) {
            assertNotNull(expected);
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private void exerciseTransport(int keyType, byte[] passphrase) throws Exception {
        File remoteDirectory = temporaryFolder.newFolder("remote-" + keyType);
        File seedDirectory = temporaryFolder.newFolder("seed-" + keyType);
        File cloneDirectory = temporaryFolder.newFolder("clone-" + keyType);
        assertTrue(cloneDirectory.delete());

        try (Git remote = Git.init().setBare(true).setDirectory(remoteDirectory).call();
             Git seed = Git.init().setDirectory(seedDirectory).call();
             SshGitServerFixture server = new SshGitServerFixture(
                     remote.getRepository(), temporaryFolder.newFolder("server-" + keyType).toPath())) {
            write(seedDirectory, "note.md", "initial");
            seed.add().addFilepattern(".").call();
            seed.commit().setMessage("seed").call();
            seed.push().setRemote(remoteDirectory.toURI().toString()).call();

            byte[] privateKey = identity(keyType, passphrase);
            RecordingHostKeys hostKeys = new RecordingHostKeys();
            server.start();
            String uri = "ssh://git@127.0.0.1:" + server.port() + "/notes.git";
            TransportConfigCallback callback = callback(privateKey, passphrase, hostKeys);

            expectUnknownHost(() -> Git.cloneRepository().setURI(uri)
                    .setDirectory(cloneDirectory).setTransportConfigCallback(callback).call());
            hostKeys.acceptCaptured();

            try (Git clone = Git.cloneRepository().setURI(uri)
                    .setDirectory(cloneDirectory).setTransportConfigCallback(callback).call()) {
                write(cloneDirectory, "note.md", "from clone");
                clone.add().addFilepattern(".").call();
                clone.commit().setMessage("client").call();
                clone.push().setTransportConfigCallback(callback).call();

                File otherDirectory = temporaryFolder.newFolder("other-" + keyType);
                assertTrue(otherDirectory.delete());
                try (Git other = Git.cloneRepository().setURI(remoteDirectory.toURI().toString())
                        .setDirectory(otherDirectory).call()) {
                    write(otherDirectory, "second.md", "from other");
                    other.add().addFilepattern(".").call();
                    other.commit().setMessage("other second").call();
                    other.push().call();
                }
                clone.pull().setTransportConfigCallback(callback).call();
                assertTrue(new File(cloneDirectory, "second.md").isFile());
            }

            server.rotateHostKey();
            String rotatedUri = "ssh://git@127.0.0.1:" + server.port() + "/notes.git";
            File rejected = temporaryFolder.newFolder("rejected-" + keyType);
            assertTrue(rejected.delete());
            try {
                Git.cloneRepository().setURI(rotatedUri).setDirectory(rejected)
                        .setTransportConfigCallback(callback).call();
                fail("changed host key accepted");
            } catch (Exception expected) {
                assertEquals(HostKeyRepository.CHANGED, hostKeys.lastResult);
            }
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private static TransportConfigCallback callback(byte[] key, byte[] passphrase,
                                                      RecordingHostKeys hostKeys) {
        return transport -> ((SshTransport) transport).setSshSessionFactory(
                new IdentitySessionFactory(key, passphrase, hostKeys));
    }

    private static byte[] identity(int type, byte[] passphrase) throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), type, type == KeyPair.RSA ? 3072 : 256);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pair.writeOpenSSHv1PrivateKey(output, passphrase == null ? new byte[0] : passphrase);
        pair.dispose();
        return output.toByteArray();
    }

    private static void write(File directory, String name, String contents) throws Exception {
        java.nio.file.Files.write(new File(directory, name).toPath(),
                contents.getBytes(StandardCharsets.UTF_8));
    }

    private static void expectUnknownHost(ThrowingRunnable operation) throws Exception {
        try {
            operation.run();
            fail("unknown host accepted");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static final class IdentitySessionFactory extends JschConfigSessionFactory {
        private final byte[] privateKey;
        private final byte[] passphrase;
        private final HostKeyRepository hostKeys;

        IdentitySessionFactory(byte[] privateKey, byte[] passphrase, HostKeyRepository hostKeys) {
            this.privateKey = privateKey;
            this.passphrase = passphrase;
            this.hostKeys = hostKeys;
        }

        @Override protected JSch createDefaultJSch(FS fs) throws com.jcraft.jsch.JSchException {
            JSch jsch = new JSch();
            jsch.setHostKeyRepository(hostKeys);
            jsch.addIdentity("project", privateKey, null, passphrase);
            return jsch;
        }

        @Override protected void configure(OpenSshConfig.Host host, Session session) {
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey");
            session.setUserInfo(new UserInfo() {
                @Override public String getPassphrase() { return null; }
                @Override public String getPassword() { return null; }
                @Override public boolean promptPassword(String message) { return false; }
                @Override public boolean promptPassphrase(String message) { return false; }
                @Override public boolean promptYesNo(String message) { return false; }
                @Override public void showMessage(String message) { }
            });
        }
    }

    private static final class RecordingHostKeys implements HostKeyRepository {
        private HostKey accepted;
        private HostKey captured;
        private int lastResult = NOT_INCLUDED;

        @Override public int check(String host, byte[] key) {
            try {
                captured = new HostKey(host, key);
            } catch (com.jcraft.jsch.JSchException exception) {
                throw new AssertionError(exception);
            }
            if (accepted == null) return lastResult = NOT_INCLUDED;
            return lastResult = Arrays.equals(accepted.getKey().getBytes(StandardCharsets.US_ASCII),
                    captured.getKey().getBytes(StandardCharsets.US_ASCII)) ? OK : CHANGED;
        }

        void acceptCaptured() { accepted = captured; }
        @Override public void add(HostKey hostkey, UserInfo userinfo) { accepted = hostkey; }
        @Override public void remove(String host, String type) { accepted = null; }
        @Override public void remove(String host, String type, byte[] key) { accepted = null; }
        @Override public String getKnownHostsRepositoryID() { return "memory"; }
        @Override public HostKey[] getHostKey() { return accepted == null ? null : new HostKey[]{accepted}; }
        @Override public HostKey[] getHostKey(String host, String type) { return getHostKey(); }
    }
}
