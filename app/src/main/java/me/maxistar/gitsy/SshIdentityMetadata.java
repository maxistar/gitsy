package me.maxistar.gitsy;

import java.util.Objects;

public final class SshIdentityMetadata {
    private final String algorithm;
    private final int keySize;
    private final String fingerprint;
    private final boolean encrypted;

    public SshIdentityMetadata(String algorithm, int keySize,
                               String fingerprint, boolean encrypted) {
        this.algorithm = Objects.requireNonNull(algorithm);
        this.keySize = keySize;
        this.fingerprint = Objects.requireNonNull(fingerprint);
        this.encrypted = encrypted;
    }

    public String getAlgorithm() { return algorithm; }
    public int getKeySize() { return keySize; }
    public String getFingerprint() { return fingerprint; }
    public boolean isEncrypted() { return encrypted; }
}
