package me.maxistar.gitsy;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.api.TransportConfigCallback;

public final class GitTransportAuthentication implements AutoCloseable {
    private final CredentialsProvider credentials;
    private final TransportConfigCallback callback;
    private final SshProjectSessionFactory sshFactory;

    GitTransportAuthentication(CredentialsProvider credentials,
                               TransportConfigCallback callback,
                               SshProjectSessionFactory sshFactory) {
        this.credentials = credentials; this.callback = callback; this.sshFactory = sshFactory;
    }

    public <C extends GitCommand<?>, T extends TransportCommand<C, ?>> T apply(T command) {
        if (credentials != null) command.setCredentialsProvider(credentials);
        if (callback != null) command.setTransportConfigCallback(callback);
        return command;
    }

    public void rethrowHostFailure() throws Exception {
        if (sshFactory != null) sshFactory.rethrowHostFailure();
    }

    @Override public void close() {
        if (sshFactory != null) sshFactory.close();
    }
}
