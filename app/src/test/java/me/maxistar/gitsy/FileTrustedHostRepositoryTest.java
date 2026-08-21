package me.maxistar.gitsy;

import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FileTrustedHostRepositoryTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void canonicalIdentityUsesSha256FingerprintAndEffectivePort() throws Exception {
        byte[] key = "host-public-key".getBytes(StandardCharsets.UTF_8);
        SshHostIdentity identity = new SshHostIdentity("Git.Example.Test", 1022, "SSH-ED25519", key);
        String expected = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(key));

        assertEquals("git.example.test", identity.getHost());
        assertEquals(1022, identity.getPort());
        assertEquals("ssh-ed25519", identity.getAlgorithm());
        assertEquals(expected, identity.getFingerprint());
    }

    @Test
    public void persistsMatchingKeyAndRejectsSilentRotation() throws Exception {
        File store = new File(temporaryFolder.getRoot(), "trusted.json");
        FileTrustedHostRepository repository = new FileTrustedHostRepository(store, new Gson());
        SshHostIdentity original = identity(1022, "original");
        SshHostIdentity rotated = identity(1022, "rotated");

        assertEquals(TrustedHostMatch.UNKNOWN, repository.check(original));
        repository.trustUnknown(original);
        assertEquals(TrustedHostMatch.MATCH, repository.check(original));
        assertEquals(TrustedHostMatch.CHANGED, repository.check(rotated));

        try {
            repository.trustUnknown(rotated);
            fail("Changed key was silently trusted");
        } catch (IllegalStateException expected) {
            assertEquals("SSH host key changed", expected.getMessage());
        }
        assertEquals(TrustedHostMatch.MATCH,
                new FileTrustedHostRepository(store, new Gson()).check(original));

        repository.replace(rotated);
        assertEquals(TrustedHostMatch.MATCH, repository.check(rotated));
        assertEquals(TrustedHostMatch.CHANGED, repository.check(original));
    }

    @Test
    public void differentPortsHaveIndependentTrustRecords() throws Exception {
        FileTrustedHostRepository repository = new FileTrustedHostRepository(
                new File(temporaryFolder.getRoot(), "trusted.json"), new Gson());
        repository.trustUnknown(identity(22, "port-22"));

        assertEquals(TrustedHostMatch.UNKNOWN, repository.check(identity(1022, "port-22")));
    }

    private static SshHostIdentity identity(int port, String key) {
        return new SshHostIdentity("git.example.test", port, "ssh-ed25519",
                key.getBytes(StandardCharsets.UTF_8));
    }
}
