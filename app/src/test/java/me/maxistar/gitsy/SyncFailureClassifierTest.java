package me.maxistar.gitsy;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SyncFailureClassifierTest {
    private final SyncFailureClassifier classifier = new SyncFailureClassifier();

    @Test public void ioIsTransient() {
        assertEquals(SyncFailureCategory.TRANSIENT, classifier.classify(new IOException("private")));
    }

    @Test public void sshIdentityRequiresAttention() {
        assertEquals(SyncFailureCategory.SSH_ATTENTION, classifier.classify(
                new SshIdentityAccessException(SshIdentityAccessError.MISSING_IDENTITY)));
    }

    @Test public void unknownFailsClosed() {
        assertEquals(SyncFailureCategory.UNKNOWN,
                classifier.classify(new IllegalStateException("private path")));
    }

    @Test public void safFailureRequiresFolderRecoveryWithoutRawDetails() {
        SyncFailureCategory category = classifier.classify(new SafAccessException(
                new SecurityException("content://private/folder")));
        assertEquals(SyncFailureCategory.FOLDER_ACCESS, category);
        assertFalse(category.getSerializedValue().contains("content://"));
    }

    @Test public void httpsAuthenticationFailureRequiresAttention() {
        ProjectModel project = new ProjectModel("https://example.invalid/x.git",
                "user", "secret", "uri");
        Exception error = new org.eclipse.jgit.errors.TransportException(
                "https://example.invalid/x.git: authentication failed");
        assertEquals(SyncFailureCategory.AUTHENTICATION,
                classifier.classify(error, project));
    }
}
