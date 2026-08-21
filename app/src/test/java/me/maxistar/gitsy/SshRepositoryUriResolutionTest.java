package me.maxistar.gitsy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SshRepositoryUriResolutionTest {
    @Test public void resolvesUrlComponentsBeforeFallbacks() throws Exception {
        assertResolution("ssh://git@example.test:1022/group/repo.git", "stale", 2200,
                "git", 1022, SshConnectionResolution.Source.URL,
                SshConnectionResolution.Source.URL);
        assertResolution("git@example.test:group/repo.git", "stale", 2200,
                "git", 2200, SshConnectionResolution.Source.URL,
                SshConnectionResolution.Source.FALLBACK);
        assertResolution("ssh://example.test/group/repo.git", "fallback", null,
                "fallback", 22, SshConnectionResolution.Source.FALLBACK,
                SshConnectionResolution.Source.DEFAULT);
        assertResolution("ssh://example.test:1022/group/repo.git", "fallback", 2200,
                "fallback", 1022, SshConnectionResolution.Source.FALLBACK,
                SshConnectionResolution.Source.URL);
    }

    @Test public void canonicalizesScpAndPreservesRepositoryPath() throws Exception {
        SshConnectionResolution value = SshRepositoryUri.resolve(
                "git@example.test:group/repo.git", "ignored", 1022);
        assertEquals("ssh://git@example.test:1022/group/repo.git",
                value.getCanonicalUri().toString());
    }

    @Test public void rejectsMissingUserBadPortsAndOtherSchemes() throws Exception {
        assertInvalid("ssh://example.test/repo.git", "", 22);
        assertInvalid("ssh://git@example.test/repo.git", "fallback", 0);
        assertInvalid("ssh://git@example.test/repo.git", "fallback", 65536);
        assertInvalid("https://example.test/repo.git", "git", 22);
        assertInvalid("not a repository", "git", 22);
    }

    private static void assertResolution(String url, String fallbackUser, Integer fallbackPort,
                                         String user, int port,
                                         SshConnectionResolution.Source userSource,
                                         SshConnectionResolution.Source portSource) throws Exception {
        SshConnectionResolution value = SshRepositoryUri.resolve(url, fallbackUser, fallbackPort);
        assertEquals(user, value.getUsername());
        assertEquals(port, value.getPort());
        assertEquals(userSource, value.getUsernameSource());
        assertEquals(portSource, value.getPortSource());
    }

    private static void assertInvalid(String url, String user, Integer port) throws Exception {
        try { SshRepositoryUri.resolve(url, user, port); fail("Expected invalid SSH URL"); }
        catch (java.net.URISyntaxException expected) {
            assertTrue(expected.getReason().length() > 0);
        }
    }
}
