package me.maxistar.gitsy;

import java.io.File;
import java.io.IOException;

final class CloneWorkspace {
    private static final String STAGING_MARKER = ".clone-staging-";

    private final File root;
    private final String folderName;
    private final File finalDirectory;

    CloneWorkspace(File root, String folderName) throws IOException {
        this.root = requireDirectoryRoot(root);
        this.folderName = requireSimpleName(folderName);
        this.finalDirectory = child(root, folderName);
    }

    File begin(String operationId) throws IOException {
        cleanStaleStaging();
        if (finalDirectory.exists()) {
            if (new File(finalDirectory, ".git").isDirectory()) {
                throw new IOException("A valid project repository already exists");
            }
            deleteRecursively(finalDirectory);
        }
        String safeOperationId = requireSimpleName(operationId);
        File staging = child(root, folderName + STAGING_MARKER + safeOperationId);
        if (staging.exists()) deleteRecursively(staging);
        return staging;
    }

    void promote(File staging) throws IOException {
        requireOwnedStaging(staging);
        if (!new File(staging, ".git").isDirectory()) {
            throw new IOException("Clone staging directory is not a Git repository");
        }
        if (finalDirectory.exists()) {
            throw new IOException("Cannot replace an existing project repository");
        }
        if (!staging.renameTo(finalDirectory)) {
            throw new IOException("Cannot promote cloned repository");
        }
    }

    void discard(File staging) throws IOException {
        requireOwnedStaging(staging);
        deleteRecursively(staging);
    }

    File finalDirectory() {
        return finalDirectory;
    }

    private void cleanStaleStaging() throws IOException {
        File[] children = root.listFiles();
        if (children == null) throw new IOException("Cannot inspect clone storage");
        String prefix = folderName + STAGING_MARKER;
        for (File child : children) {
            if (child.getName().startsWith(prefix)) deleteRecursively(child);
        }
    }

    private void requireOwnedStaging(File staging) throws IOException {
        if (staging == null || !staging.getCanonicalFile().getParentFile().equals(root)
                || !staging.getName().startsWith(folderName + STAGING_MARKER)) {
            throw new IOException("Unsafe clone staging path");
        }
    }

    private static File requireDirectoryRoot(File root) throws IOException {
        if (root == null || (!root.isDirectory() && !root.mkdirs())) {
            throw new IOException("Clone storage is unavailable");
        }
        return root.getCanonicalFile();
    }

    private static String requireSimpleName(String value) throws IOException {
        if (value == null || value.isEmpty() || ".".equals(value) || "..".equals(value)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || !new File(value).getName().equals(value)) {
            throw new IOException("Unsafe clone path component");
        }
        return value;
    }

    private static File child(File root, String name) throws IOException {
        File child = new File(root, name).getCanonicalFile();
        if (!root.getCanonicalFile().equals(child.getParentFile())) {
            throw new IOException("Clone path escapes app-private storage");
        }
        return child;
    }

    static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) throw new IOException("Cannot clean clone staging path");
    }
}
