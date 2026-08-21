package me.maxistar.gitsy;

public final class SshKeyImportException extends Exception {
    private final SshKeyImportError error;

    public SshKeyImportException(SshKeyImportError error) {
        super(error.name());
        this.error = error;
    }

    public SshKeyImportError getError() {
        return error;
    }
}
