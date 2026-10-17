package me.maxistar.gitsy;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.IOException;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.Assert.assertEquals;

public class HttpsCloneFailureTranslatorTest {
    private final HttpsCloneFailureTranslator translator = new HttpsCloneFailureTranslator();

    @Test public void recognizesSupportedAuthenticationMessages() {
        for (String message : new String[] { "not authorized", "Authentication is required",
                "authentication failed", "HTTP 401", "status code 403" }) {
            assertEquals(message, CloneFailureCategory.AUTHENTICATION,
                    translator.translate(new TransportException(message)));
        }
    }

    @Test public void categorizesTypedCausesAcrossWrappedFailures() throws Exception {
        assertWrapped(CloneFailureCategory.NETWORK, new UnknownHostException("host"));
        assertWrapped(CloneFailureCategory.NETWORK, new ConnectException("refused"));
        assertWrapped(CloneFailureCategory.NETWORK, new SocketTimeoutException("timeout"));
        assertWrapped(CloneFailureCategory.TLS, new SSLHandshakeException("certificate"));
        assertWrapped(CloneFailureCategory.LOCAL,
                new IOException("path denied"));
        assertEquals(CloneFailureCategory.REMOTE, translator.translate(
                new NoRemoteRepositoryException(
                        new URIish("https://example.invalid/missing.git"), "missing")));
    }

    @Test public void ambiguousFailuresAreNotCalledAuthenticationErrors() {
        for (String message : new String[] { "repository not found", "HTTP 404",
                "connection closed", "invalid credentials file path" }) {
            assertEquals(message, CloneFailureCategory.UNKNOWN,
                    translator.translate(new TransportException(message)));
        }
        assertEquals(CloneFailureCategory.UNKNOWN,
                translator.translate(new IllegalStateException("boom")));
    }

    private void assertWrapped(CloneFailureCategory expected, Exception cause) {
        assertEquals(expected, translator.translate(
                new TransportException("sanitized wrapper", cause)));
    }
}
