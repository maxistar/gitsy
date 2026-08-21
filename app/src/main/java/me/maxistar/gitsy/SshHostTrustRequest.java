package me.maxistar.gitsy;

public final class SshHostTrustRequest {
    private final String token;
    private final String host;
    private final int port;
    private final String algorithm;
    private final String fingerprint;

    SshHostTrustRequest(String token, SshHostIdentity identity) {
        this.token = token;
        this.host = identity.getHost();
        this.port = identity.getPort();
        this.algorithm = identity.getAlgorithm();
        this.fingerprint = identity.getFingerprint();
    }

    public String getToken() { return token; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getAlgorithm() { return algorithm; }
    public String getFingerprint() { return fingerprint; }
}
