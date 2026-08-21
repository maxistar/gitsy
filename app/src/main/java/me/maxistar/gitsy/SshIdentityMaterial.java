package me.maxistar.gitsy;

import java.util.Arrays;

public final class SshIdentityMaterial implements AutoCloseable {
    private final SshIdentityMetadata metadata;
    private final byte[] privateKey;
    private final byte[] passphrase;

    SshIdentityMaterial(SshIdentityMetadata metadata, byte[] privateKey, byte[] passphrase) {
        this.metadata = metadata;
        this.privateKey = privateKey;
        this.passphrase = passphrase;
    }

    public SshIdentityMetadata getMetadata() { return metadata; }
    public byte[] getPrivateKey() { return privateKey; }
    public byte[] getPassphrase() { return passphrase; }

    @Override public void close() {
        Arrays.fill(privateKey, (byte) 0);
        if (passphrase != null) Arrays.fill(passphrase, (byte) 0);
    }
}
