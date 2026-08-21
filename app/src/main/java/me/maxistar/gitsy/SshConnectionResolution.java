package me.maxistar.gitsy;

import org.eclipse.jgit.transport.URIish;

public final class SshConnectionResolution {
    public enum Source { URL, FALLBACK, DEFAULT }

    private final URIish canonicalUri;
    private final String username;
    private final int port;
    private final Source usernameSource;
    private final Source portSource;

    SshConnectionResolution(URIish canonicalUri, String username, int port,
                            Source usernameSource, Source portSource) {
        this.canonicalUri = canonicalUri;
        this.username = username;
        this.port = port;
        this.usernameSource = usernameSource;
        this.portSource = portSource;
    }

    public URIish getCanonicalUri() { return canonicalUri; }
    public String getUsername() { return username; }
    public int getPort() { return port; }
    public Source getUsernameSource() { return usernameSource; }
    public Source getPortSource() { return portSource; }
    public boolean isUsernameFromUrl() { return usernameSource == Source.URL; }
    public boolean isPortFromUrl() { return portSource == Source.URL; }
}
