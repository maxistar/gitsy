package me.maxistar.gitsy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class FileTreeSnapshot {
    enum Kind { FILE, DIRECTORY }
    enum Change { ADDED, MODIFIED, UNCHANGED, REMOVED }

    static final class Entry {
        final String path;
        final Kind kind;
        final long size;
        final long modifiedAt;
        final String contentHash;

        Entry(String path, Kind kind, long size, long modifiedAt, String contentHash) {
            this.path = path;
            this.kind = kind;
            this.size = size;
            this.modifiedAt = modifiedAt;
            this.contentHash = contentHash;
        }

        boolean sameContent(Entry other) {
            return kind == other.kind && size == other.size && contentHash.equals(other.contentHash);
        }
    }

    static final class Difference {
        final String path;
        final Change change;

        Difference(String path, Change change) {
            this.path = path;
            this.change = change;
        }
    }

    interface DirectoryReader {
        File[] listFiles(File directory) throws IOException;
    }

    private final Map<String, Entry> entries;

    private FileTreeSnapshot(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    static FileTreeSnapshot scan(File root) throws IOException {
        return scan(root, directory -> {
            File[] children = directory.listFiles();
            if (children == null) throw new IOException("Could not list directory: " + directory);
            return children;
        });
    }

    static FileTreeSnapshot scan(File root, DirectoryReader reader) throws IOException {
        if (!root.isDirectory()) throw new IOException("Snapshot root is not a directory: " + root);
        Map<String, Entry> entries = new TreeMap<>();
        scanChildren(root, "", reader, entries);
        return new FileTreeSnapshot(entries);
    }

    List<Difference> compareTo(FileTreeSnapshot destination) {
        List<Difference> differences = new ArrayList<>();
        Map<String, Entry> remaining = new TreeMap<>(destination.entries);
        for (Map.Entry<String, Entry> sourceEntry : entries.entrySet()) {
            Entry destinationEntry = remaining.remove(sourceEntry.getKey());
            Change change = destinationEntry == null
                    ? Change.ADDED
                    : sourceEntry.getValue().sameContent(destinationEntry)
                    ? Change.UNCHANGED
                    : Change.MODIFIED;
            differences.add(new Difference(sourceEntry.getKey(), change));
        }
        for (String removedPath : remaining.keySet()) {
            differences.add(new Difference(removedPath, Change.REMOVED));
        }
        differences.sort((left, right) -> left.path.compareTo(right.path));
        return differences;
    }

    private static void scanChildren(
            File directory, String parentPath, DirectoryReader reader, Map<String, Entry> entries) throws IOException {
        File[] children = reader.listFiles(directory);
        for (File child : children) {
            String path = parentPath.isEmpty() ? child.getName() : parentPath + "/" + child.getName();
            if (child.isDirectory()) {
                entries.put(path, new Entry(path, Kind.DIRECTORY, 0L, child.lastModified(), "directory"));
                scanChildren(child, path, reader, entries);
            } else if (child.isFile()) {
                entries.put(path, new Entry(path, Kind.FILE, child.length(), child.lastModified(), hash(child)));
            } else {
                throw new IOException("Unsupported file-tree entry: " + child);
            }
        }
    }

    private static String hash(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
