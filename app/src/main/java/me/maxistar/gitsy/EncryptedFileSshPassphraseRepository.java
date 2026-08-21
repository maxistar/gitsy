package me.maxistar.gitsy;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class EncryptedFileSshPassphraseRepository implements SshPassphraseRepository {
    private static final String AAD = "gitsy:ssh-passphrase:global";
    private final File file;
    private final SshSecretCipher cipher;

    public EncryptedFileSshPassphraseRepository(Context context, SshSecretCipher cipher) {
        this(new File(context.getFilesDir(), "ssh-identity/passphrase.enc"), cipher);
    }
    EncryptedFileSshPassphraseRepository(File file, SshSecretCipher cipher) {
        this.file = Objects.requireNonNull(file); this.cipher = Objects.requireNonNull(cipher);
    }
    @Override public synchronized void save(byte[] passphrase) throws Exception {
        Objects.requireNonNull(passphrase);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create SSH secret store");
        File staging = new File(parent, ".passphrase-staging-" + System.nanoTime());
        try (FileOutputStream out = new FileOutputStream(staging)) {
            out.write(cipher.encrypt(passphrase, AAD).getBytes(StandardCharsets.UTF_8));
            out.flush(); out.getFD().sync();
        } catch (Exception error) { staging.delete(); throw error; }
        File backup = new File(parent, ".passphrase-backup-" + System.nanoTime());
        boolean movedOld = false;
        try {
            if (file.exists()) { if (!file.renameTo(backup)) throw new IOException("Cannot stage old SSH secret"); movedOld = true; }
            if (!staging.renameTo(file)) throw new IOException("Cannot commit SSH secret");
            if (movedOld && !backup.delete()) throw new IOException("Cannot remove old SSH secret");
        } catch (Exception error) {
            staging.delete();
            if (movedOld && !file.exists()) backup.renameTo(file);
            throw error;
        }
    }
    @Override public synchronized byte[] load() throws Exception {
        if (!file.isFile()) throw new IOException("Global SSH passphrase is missing");
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0; while (offset < bytes.length) { int n = in.read(bytes, offset, bytes.length - offset); if (n < 0) break; offset += n; }
            if (offset != bytes.length) throw new IOException("Cannot read global SSH passphrase");
        }
        return cipher.decrypt(new String(bytes, StandardCharsets.UTF_8), AAD);
    }
    @Override public synchronized boolean contains() { return file.isFile(); }
    @Override public synchronized void delete() throws Exception {
        if (file.exists() && !file.delete()) throw new IOException("Cannot delete global SSH passphrase");
    }
}
