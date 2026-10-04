package me.maxistar.gitsy;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class SshHostIdentity {
    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final String host;
    private final int port;
    private final String algorithm;
    private final byte[] key;
    private final String fingerprint;

    public SshHostIdentity(String host, int port, String algorithm, byte[] key) {
        String normalizedHost = Objects.requireNonNull(host).trim().toLowerCase(Locale.ROOT);
        String normalizedAlgorithm = Objects.requireNonNull(algorithm).trim().toLowerCase(Locale.ROOT);
        if (normalizedHost.isEmpty()) throw new IllegalArgumentException("SSH host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid SSH port");
        if (normalizedAlgorithm.isEmpty()) throw new IllegalArgumentException("SSH host algorithm is required");
        if (key == null || key.length == 0) throw new IllegalArgumentException("SSH host key is required");
        this.host = normalizedHost;
        this.port = port;
        this.algorithm = normalizedAlgorithm;
        this.key = key.clone();
        this.fingerprint = sha256Fingerprint(this.key);
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getAlgorithm() { return algorithm; }
    public byte[] getKey() { return key.clone(); }
    public String getFingerprint() { return fingerprint; }

    public boolean sameEndpoint(SshHostIdentity other) {
        return host.equals(other.host) && port == other.port;
    }

    public boolean sameKey(SshHostIdentity other) {
        return sameEndpoint(other) && algorithm.equals(other.algorithm)
                && Arrays.equals(key, other.key);
    }

    private static String sha256Fingerprint(byte[] key) {
        try {
            return "SHA256:" + base64NoPadding(MessageDigest.getInstance("SHA-256").digest(key));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String base64NoPadding(byte[] bytes) {
        StringBuilder output = new StringBuilder((bytes.length * 4 + 2) / 3);
        for (int index = 0; index < bytes.length; index += 3) {
            int first = bytes[index] & 0xff;
            int second = index + 1 < bytes.length ? bytes[index + 1] & 0xff : 0;
            int third = index + 2 < bytes.length ? bytes[index + 2] & 0xff : 0;
            output.append(BASE64[first >>> 2]);
            output.append(BASE64[((first & 3) << 4) | (second >>> 4)]);
            if (index + 1 < bytes.length) output.append(BASE64[((second & 15) << 2) | (third >>> 6)]);
            if (index + 2 < bytes.length) output.append(BASE64[third & 63]);
        }
        return output.toString();
    }
}
