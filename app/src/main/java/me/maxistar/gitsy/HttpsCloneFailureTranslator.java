package me.maxistar.gitsy;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLException;

final class HttpsCloneFailureTranslator {
    CloneFailureCategory translate(Throwable error) {
        Set<Throwable> visited = new HashSet<>();
        for (Throwable current = error; current != null && visited.add(current);
             current = current.getCause()) {
            if (current instanceof SSLException) return CloneFailureCategory.TLS;
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return CloneFailureCategory.NETWORK;
            }
            if (current instanceof NoRemoteRepositoryException) {
                return CloneFailureCategory.REMOTE;
            }
            if (current instanceof TransportException && isAuthenticationMessage(
                    current.getMessage())) {
                return CloneFailureCategory.AUTHENTICATION;
            }
            if (current instanceof IOException
                    && !(current instanceof TransportException)) {
                return CloneFailureCategory.LOCAL;
            }
        }
        return CloneFailureCategory.UNKNOWN;
    }

    private static boolean isAuthenticationMessage(String message) {
        if (message == null) return false;
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("not authorized")
                || value.contains("authentication is required")
                || value.contains("authentication failed")
                || value.contains("authorization failed")
                || value.contains("http 401")
                || value.contains("status code 401")
                || value.contains("http 403")
                || value.contains("status code 403");
    }
}
