package me.maxistar.gitsy;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SshHostTrustCoordinatorTest {
    @Test
    public void unknownHostReturnsDisplaySafeStableRequestAndRetriesOnce() throws Exception {
        MemoryTrustedHosts trusted = new MemoryTrustedHosts();
        SshHostTrustCoordinator coordinator = new SshHostTrustCoordinator(trusted);
        SshHostIdentity identity = identity("git.example.test", 1022, "first");

        SshHostTrustRequest first = required(coordinator, "project-1", identity);
        SshHostTrustRequest repeated = required(coordinator, "project-1", identity);
        assertEquals(first.getToken(), repeated.getToken());
        assertEquals("git.example.test", first.getHost());
        assertEquals(1022, first.getPort());
        assertEquals("ssh-ed25519", first.getAlgorithm());
        assertTrue(first.getFingerprint().startsWith("SHA256:"));
        assertNotNull(coordinator.pending(first.getToken()));

        assertTrue(coordinator.accept(first.getToken()));
        assertFalse(coordinator.accept(first.getToken()));
        assertNull(coordinator.pending(first.getToken()));
        coordinator.verify("project-1-retry", identity);
    }

    @Test
    public void declineDoesNotPersistAndConcurrentProjectsRemainIndependent() throws Exception {
        MemoryTrustedHosts trusted = new MemoryTrustedHosts();
        SshHostTrustCoordinator coordinator = new SshHostTrustCoordinator(trusted);
        SshHostTrustRequest first = required(coordinator, "project-1",
                identity("one.example", 22, "one"));
        SshHostTrustRequest second = required(coordinator, "project-2",
                identity("two.example", 22, "two"));
        assertNotEquals(first.getToken(), second.getToken());

        assertTrue(coordinator.decline(first.getToken()));
        assertFalse(coordinator.decline(first.getToken()));
        assertNotNull(coordinator.pending(second.getToken()));
        assertEquals(TrustedHostMatch.UNKNOWN,
                trusted.check(identity("one.example", 22, "one")));
    }

    @Test
    public void changedHostIsHardFailureAndCannotBecomePending() throws Exception {
        MemoryTrustedHosts trusted = new MemoryTrustedHosts();
        SshHostIdentity original = identity("git.example", 22, "original");
        trusted.trustUnknown(original);
        SshHostTrustCoordinator coordinator = new SshHostTrustCoordinator(trusted);

        try {
            coordinator.verify("sync", identity("git.example", 22, "rotated"));
            fail("Changed host was accepted");
        } catch (SshHostKeyChangedException error) {
            assertEquals("SSH_HOST_KEY_CHANGED", error.getMessage());
            assertEquals("git.example", error.getHost());
        }
    }

    private static SshHostTrustRequest required(SshHostTrustCoordinator coordinator,
                                                String operationId,
                                                SshHostIdentity identity) throws Exception {
        try {
            coordinator.verify(operationId, identity);
            fail("Expected trust request");
            return null;
        } catch (SshHostTrustRequiredException required) {
            assertEquals("SSH_HOST_TRUST_REQUIRED", required.getMessage());
            return required.getRequest();
        }
    }

    private static SshHostIdentity identity(String host, int port, String key) {
        return new SshHostIdentity(host, port, "ssh-ed25519",
                key.getBytes(StandardCharsets.UTF_8));
    }

    private static final class MemoryTrustedHosts implements TrustedHostRepository {
        private final Map<String, SshHostIdentity> records = new HashMap<>();
        @Override public TrustedHostMatch check(SshHostIdentity candidate) {
            SshHostIdentity existing = records.get(endpoint(candidate));
            if (existing == null) return TrustedHostMatch.UNKNOWN;
            return existing.sameKey(candidate) ? TrustedHostMatch.MATCH : TrustedHostMatch.CHANGED;
        }
        @Override public void trustUnknown(SshHostIdentity candidate) {
            if (check(candidate) == TrustedHostMatch.CHANGED) throw new IllegalStateException();
            records.put(endpoint(candidate), candidate);
        }
        @Override public void replace(SshHostIdentity candidate) { records.put(endpoint(candidate), candidate); }
        @Override public void remove(String host, int port) { records.remove(host + ":" + port); }
        private static String endpoint(SshHostIdentity identity) {
            return identity.getHost() + ":" + identity.getPort();
        }
    }
}
