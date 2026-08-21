package me.maxistar.gitsy;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class GitLabSshAndroidGateTest {
    private static final String KEY_FILE = "ssh-gate-key";

    @Test
    public void clonePushPullAgainstConfiguredGitLabRepository() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bundle arguments = InstrumentationRegistry.getArguments();
        String repositoryUrl = arguments.getString("sshGateUrl", "");
        File keyFile = new File(context.getFilesDir(), KEY_FILE);
        Assume.assumeTrue("SSH gate requires -e sshGateUrl and an injected key",
                !repositoryUrl.isEmpty() && keyFile.isFile());

        int expectedPort = Integer.parseInt(arguments.getString("sshGateExpectedPort", "22"));
        SshConnectionResolution connection = SshRepositoryUri.resolve(
                repositoryUrl, "stale-user", null);
        assertEquals("git", connection.getUsername());
        assertEquals(expectedPort, connection.getPort());
        String effectiveRepositoryUrl = connection.getCanonicalUri().toString();

        byte[] privateKey = Files.readAllBytes(keyFile.toPath());
        RecordingHostKeys hostKeys = new RecordingHostKeys();
        TransportConfigCallback callback = transport -> ((SshTransport) transport)
                .setSshSessionFactory(new IdentitySessionFactory(privateKey, hostKeys));
        String branch = "gitsy-android-ssh-gate-" + System.currentTimeMillis();
        File firstDirectory = new File(context.getCacheDir(), branch + "-first");
        File secondDirectory = new File(context.getCacheDir(), branch + "-second");
        deleteRecursively(firstDirectory);
        deleteRecursively(secondDirectory);

        Git first = null;
        try {
            try {
                Git.cloneRepository().setURI(effectiveRepositoryUrl).setDirectory(firstDirectory)
                        .setTransportConfigCallback(callback).call();
                fail("unknown GitLab host was accepted");
            } catch (Exception expected) {
                assertEquals(HostKeyRepository.NOT_INCLUDED, hostKeys.lastResult);
            }
            hostKeys.acceptCaptured();

            first = Git.cloneRepository().setURI(effectiveRepositoryUrl).setDirectory(firstDirectory)
                    .setTransportConfigCallback(callback).call();
            first.checkout().setCreateBranch(true).setName(branch).call();
            write(firstDirectory, "android-ssh-gate.txt", "first\n");
            first.add().addFilepattern("android-ssh-gate.txt").call();
            first.commit().setMessage("Android SSH gate: first")
                    .setAuthor("GitSy SSH Gate", "ssh-gate@example.invalid").call();
            RefSpec branchSpec = new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch);
            first.push().setRefSpecs(branchSpec).setTransportConfigCallback(callback).call();
            first.getRepository().getConfig().setString("branch", branch, "remote", "origin");
            first.getRepository().getConfig().setString(
                    "branch", branch, "merge", "refs/heads/" + branch);
            first.getRepository().getConfig().save();

            try (Git second = Git.cloneRepository().setURI(effectiveRepositoryUrl)
                    .setBranch(branch).setDirectory(secondDirectory)
                    .setTransportConfigCallback(callback).call()) {
                write(secondDirectory, "android-ssh-gate.txt", "second\n");
                second.add().addFilepattern("android-ssh-gate.txt").call();
                second.commit().setMessage("Android SSH gate: second")
                        .setAuthor("GitSy SSH Gate", "ssh-gate@example.invalid").call();
                second.push().setTransportConfigCallback(callback).call();
            }

            first.pull().setTransportConfigCallback(callback).call();
            String pulled = new String(Files.readAllBytes(
                    new File(firstDirectory, "android-ssh-gate.txt").toPath()),
                    StandardCharsets.UTF_8);
            assertEquals("second\n", pulled);
            assertEquals(HostKeyRepository.OK, hostKeys.lastResult);
        } finally {
            if (first != null) {
                try {
                    first.push().setRefSpecs(new RefSpec(":refs/heads/" + branch))
                            .setTransportConfigCallback(callback).call();
                } finally {
                    first.close();
                }
            }
            Arrays.fill(privateKey, (byte) 0);
            deleteRecursively(firstDirectory);
            deleteRecursively(secondDirectory);
        }
    }

    private static void write(File directory, String name, String value) throws Exception {
        Files.write(new File(directory, name).toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        assertTrue(file.delete());
    }

    private static final class IdentitySessionFactory extends JschConfigSessionFactory {
        private final byte[] privateKey;
        private final HostKeyRepository hostKeys;

        IdentitySessionFactory(byte[] privateKey, HostKeyRepository hostKeys) {
            this.privateKey = privateKey;
            this.hostKeys = hostKeys;
        }

        @Override protected JSch createDefaultJSch(FS fs) throws com.jcraft.jsch.JSchException {
            JSch jsch = new JSch();
            jsch.setHostKeyRepository(hostKeys);
            jsch.addIdentity("gitlab-gate", privateKey, null, null);
            return jsch;
        }

        @Override protected void configure(OpenSshConfig.Host host, Session session) {
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey");
            session.setConfig("PubkeyAcceptedAlgorithms",
                    "ssh-ed25519,ecdsa-sha2-nistp521,ecdsa-sha2-nistp384,"
                            + "ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256");
            session.setConfig("enable_server_sig_algs", "yes");
            session.setUserInfo(new NonInteractiveUserInfo());
        }
    }

    private static final class NonInteractiveUserInfo implements UserInfo {
        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return null; }
        @Override public boolean promptPassword(String message) { return false; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) { return false; }
        @Override public void showMessage(String message) { }
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
            return lastResult = accepted.getKey().equals(captured.getKey()) ? OK : CHANGED;
        }

        void acceptCaptured() { accepted = captured; }
        @Override public void add(HostKey hostkey, UserInfo userinfo) { accepted = hostkey; }
        @Override public void remove(String host, String type) { accepted = null; }
        @Override public void remove(String host, String type, byte[] key) { accepted = null; }
        @Override public String getKnownHostsRepositoryID() { return "gitlab-gate-memory"; }
        @Override public HostKey[] getHostKey() { return accepted == null ? null : new HostKey[]{accepted}; }
        @Override public HostKey[] getHostKey(String host, String type) { return getHostKey(); }
    }
}
