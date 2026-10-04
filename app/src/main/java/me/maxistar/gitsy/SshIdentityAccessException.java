package me.maxistar.gitsy;

public final class SshIdentityAccessException extends Exception {
    private final SshIdentityAccessError error;

    public SshIdentityAccessException(SshIdentityAccessError error) {
        super(error.name());
        this.error = error;
    }

    public SshIdentityAccessException(SshIdentityAccessError error, Throwable cause) {
        super(error.name(), cause);
        this.error = error;
    }

    public SshIdentityAccessError getError() { return error; }
}
