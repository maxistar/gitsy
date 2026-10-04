package me.maxistar.gitsy;

import java.io.FileNotFoundException;
import java.io.IOException;

final class SyncFailureClassifier {
    SyncFailureCategory classify(Exception error) {
        return classify(error, null);
    }

    SyncFailureCategory classify(Exception error, ProjectModel project) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SshHostTrustRequiredException
                    || current instanceof SshHostKeyChangedException
                    || current instanceof SshIdentityAccessException) {
                return SyncFailureCategory.SSH_ATTENTION;
            }
            if (current instanceof SafAccessException || current instanceof SecurityException
                    || current instanceof FileNotFoundException) {
                return SyncFailureCategory.FOLDER_ACCESS;
            }
            if (current instanceof HttpsCloneException
                    && ((HttpsCloneException) current).getCategory()
                    == CloneFailureCategory.AUTHENTICATION) {
                return SyncFailureCategory.AUTHENTICATION;
            }
            current = current.getCause();
        }
        if (project != null && project.getAuthenticationType() == ProjectAuthenticationType.HTTPS) {
            CloneFailureCategory category = new HttpsCloneFailureTranslator().translate(error);
            if (category == CloneFailureCategory.AUTHENTICATION) {
                return SyncFailureCategory.AUTHENTICATION;
            }
            if (category == CloneFailureCategory.NETWORK || category == CloneFailureCategory.TLS) {
                return SyncFailureCategory.TRANSIENT;
            }
            if (category == CloneFailureCategory.LOCAL) return SyncFailureCategory.STORAGE;
        }
        if (error instanceof IOException
                || error instanceof org.eclipse.jgit.api.errors.TransportException
                || error instanceof org.eclipse.jgit.errors.TransportException) {
            return SyncFailureCategory.TRANSIENT;
        }
        return SyncFailureCategory.UNKNOWN;
    }
}
