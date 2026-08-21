package me.maxistar.gitsy;

import org.eclipse.jgit.transport.URIish;

public final class SshRepositoryUri {
    private SshRepositoryUri() { }

    public static URIish parse(String repositoryUrl, String fallbackUser, int port)
            throws java.net.URISyntaxException {
        return resolve(repositoryUrl, fallbackUser, port).getCanonicalUri();
    }

    public static SshConnectionResolution resolve(String repositoryUrl, String fallbackUser,
                                                   Integer fallbackPort)
            throws java.net.URISyntaxException {
        URIish parsed;
        try { parsed = new URIish(repositoryUrl); }
        catch (Exception error) {
            throw new java.net.URISyntaxException(repositoryUrl, "Invalid SSH repository URL");
        }

        boolean canonical = "ssh".equalsIgnoreCase(parsed.getScheme());
        boolean scpLike = parsed.getScheme() == null && parsed.getHost() != null
                && parsed.getPath() != null;
        if (!canonical && !scpLike) throw new java.net.URISyntaxException(
                repositoryUrl, "Not an SSH repository URL");

        String urlUser = trimToNull(parsed.getUser());
        String user = urlUser != null ? urlUser : trimToNull(fallbackUser);
        if (user == null) throw new java.net.URISyntaxException(
                repositoryUrl, "SSH username is required");

        boolean urlHasPort = canonical && parsed.getPort() > 0;
        int port;
        SshConnectionResolution.Source portSource;
        if (urlHasPort) {
            port = parsed.getPort();
            portSource = SshConnectionResolution.Source.URL;
        } else if (fallbackPort != null) {
            port = fallbackPort;
            portSource = SshConnectionResolution.Source.FALLBACK;
        } else {
            port = 22;
            portSource = SshConnectionResolution.Source.DEFAULT;
        }
        if (port < 1 || port > 65535) throw new java.net.URISyntaxException(
                String.valueOf(port), "SSH port must be between 1 and 65535");

        URIish canonicalUri = parsed.setScheme("ssh").setUser(user).setPort(port);
        return new SshConnectionResolution(canonicalUri, user, port,
                urlUser != null ? SshConnectionResolution.Source.URL
                        : SshConnectionResolution.Source.FALLBACK,
                portSource);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
