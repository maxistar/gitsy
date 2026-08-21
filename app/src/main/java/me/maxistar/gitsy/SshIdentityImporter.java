package me.maxistar.gitsy;

import java.util.Arrays;
import java.util.Objects;

/** Validates first and rolls back both global secret slots if replacement cannot finish. */
public final class SshIdentityImporter {
    private final SshPrivateKeyParser parser;
    private final SshIdentityRepository identities;
    private final SshPassphraseRepository passphrases;

    public SshIdentityImporter(SshPrivateKeyParser parser, SshIdentityRepository identities,
                               SshPassphraseRepository passphrases) {
        this.parser = Objects.requireNonNull(parser);
        this.identities = Objects.requireNonNull(identities);
        this.passphrases = Objects.requireNonNull(passphrases);
    }

    public SshIdentityMetadata importKey(byte[] privateKey, byte[] passphrase,
                                         boolean retainPassphrase) throws Exception {
        SshKeySummary summary = parser.parse(privateKey, passphrase);
        SshIdentityMetadata replacement = new SshIdentityMetadata(summary.getAlgorithm(),
                summary.getKeySize(), summary.getFingerprint(), summary.isEncrypted());
        Snapshot previous = snapshot();
        try {
            identities.save(replacement, privateKey);
            if (summary.isEncrypted() && retainPassphrase) passphrases.save(passphrase);
            else passphrases.delete();
            return replacement;
        } catch (Exception error) {
            restore(previous);
            throw error;
        } finally {
            previous.clear();
        }
    }

    public void deleteGlobalIdentity() throws Exception {
        passphrases.delete();
        identities.delete();
    }

    private Snapshot snapshot() throws Exception {
        SshIdentityMetadata metadata = null;
        byte[] key = null;
        byte[] retainedPassphrase = null;
        if (identities.contains()) {
            metadata = identities.metadata();
            key = identities.loadPrivateKey();
        }
        if (passphrases.contains()) retainedPassphrase = passphrases.load();
        return new Snapshot(metadata, key, retainedPassphrase);
    }

    private void restore(Snapshot previous) throws Exception {
        if (previous.key == null) identities.delete();
        else identities.save(previous.metadata, previous.key);
        if (previous.passphrase == null) passphrases.delete();
        else passphrases.save(previous.passphrase);
    }

    private static final class Snapshot {
        final SshIdentityMetadata metadata; final byte[] key; final byte[] passphrase;
        Snapshot(SshIdentityMetadata metadata, byte[] key, byte[] passphrase) {
            this.metadata = metadata; this.key = key; this.passphrase = passphrase;
        }
        void clear() {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (passphrase != null) Arrays.fill(passphrase, (byte) 0);
        }
    }
}
