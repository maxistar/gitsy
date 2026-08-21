package me.maxistar.gitsy;

import android.content.Context;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** A single encrypted application-wide SSH identity stored below app-private files. */
public final class EncryptedFileSshIdentityRepository implements SshIdentityRepository {
    private static final String STORE_DIRECTORY = "ssh-identity";
    private static final String METADATA_FILE = "metadata.json";
    private static final String PRIVATE_KEY_FILE = "private-key.enc";
    private static final String PRIVATE_KEY_AAD = "gitsy:ssh-private-key:global";
    private final File root;
    private final SshSecretCipher cipher;
    private final Gson gson;

    public EncryptedFileSshIdentityRepository(Context context, SshSecretCipher cipher) {
        this(new File(context.getFilesDir(), STORE_DIRECTORY), cipher, new Gson());
    }
    EncryptedFileSshIdentityRepository(File root, SshSecretCipher cipher, Gson gson) {
        this.root = Objects.requireNonNull(root);
        this.cipher = Objects.requireNonNull(cipher);
        this.gson = Objects.requireNonNull(gson);
    }

    @Override public synchronized void save(SshIdentityMetadata metadata, byte[] privateKey) throws Exception {
        Objects.requireNonNull(metadata); Objects.requireNonNull(privateKey); ensureRoot();
        File staging = new File(root, ".staging-" + System.nanoTime());
        File backup = new File(root, ".backup-" + System.nanoTime());
        File active = active();
        if (!staging.mkdir()) throw new IOException("Cannot stage global SSH identity");
        boolean movedOld = false;
        try {
            write(new File(staging, METADATA_FILE), gson.toJson(metadata).getBytes(StandardCharsets.UTF_8));
            write(new File(staging, PRIVATE_KEY_FILE),
                    cipher.encrypt(privateKey, PRIVATE_KEY_AAD).getBytes(StandardCharsets.UTF_8));
            if (active.exists()) { move(active, backup); movedOld = true; }
            move(staging, active);
            if (movedOld) deleteTree(backup);
        } catch (Exception error) {
            deleteTree(staging);
            if (movedOld && !active.exists() && backup.exists()) move(backup, active);
            throw error;
        }
    }

    @Override public synchronized SshIdentityMetadata metadata() throws Exception {
        SshIdentityMetadata result = gson.fromJson(new String(read(required(METADATA_FILE)),
                StandardCharsets.UTF_8), SshIdentityMetadata.class);
        if (result == null || result.getAlgorithm() == null || result.getFingerprint() == null)
            throw new IOException("Global SSH identity metadata is corrupt");
        return result;
    }
    @Override public synchronized byte[] loadPrivateKey() throws Exception {
        return cipher.decrypt(new String(read(required(PRIVATE_KEY_FILE)), StandardCharsets.UTF_8),
                PRIVATE_KEY_AAD);
    }
    @Override public synchronized boolean contains() {
        return new File(active(), METADATA_FILE).isFile() && new File(active(), PRIVATE_KEY_FILE).isFile();
    }
    @Override public synchronized void delete() throws Exception { deleteTree(active()); }

    private File active() { return new File(root, "active"); }
    private File required(String name) throws IOException {
        File file = new File(active(), name);
        if (!file.isFile()) throw new IOException("Global SSH identity is missing");
        return file;
    }
    private void ensureRoot() throws IOException {
        if ((!root.exists() && !root.mkdirs()) || !root.isDirectory())
            throw new IOException("Cannot create global SSH identity store");
    }
    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes); out.flush(); out.getFD().sync();
        }
    }
    private static byte[] read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()]; int offset = 0;
            while (offset < bytes.length) { int n = in.read(bytes, offset, bytes.length - offset); if (n < 0) break; offset += n; }
            if (offset != bytes.length) throw new IOException("Cannot read global SSH identity");
            return bytes;
        }
    }
    private static void move(File source, File target) throws IOException {
        if (!source.renameTo(target)) throw new IOException("Cannot move global SSH identity");
    }
    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("Cannot delete global SSH identity");
    }
}
