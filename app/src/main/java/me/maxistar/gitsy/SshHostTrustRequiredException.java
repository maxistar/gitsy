package me.maxistar.gitsy;

public final class SshHostTrustRequiredException extends Exception {
    private final SshHostTrustRequest request;

    public SshHostTrustRequiredException(SshHostTrustRequest request) {
        super("SSH_HOST_TRUST_REQUIRED");
        this.request = request;
    }

    public SshHostTrustRequest getRequest() { return request; }
}
