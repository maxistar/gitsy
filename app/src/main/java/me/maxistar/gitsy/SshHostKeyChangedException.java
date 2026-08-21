package me.maxistar.gitsy;

public final class SshHostKeyChangedException extends Exception {
    private final String host;
    private final int port;
    private final String algorithm;
    private final String fingerprint;

    public SshHostKeyChangedException(SshHostIdentity candidate) {
        super("SSH_HOST_KEY_CHANGED");
        host = candidate.getHost();
        port = candidate.getPort();
        algorithm = candidate.getAlgorithm();
        fingerprint = candidate.getFingerprint();
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getAlgorithm() { return algorithm; }
    public String getFingerprint() { return fingerprint; }
}
