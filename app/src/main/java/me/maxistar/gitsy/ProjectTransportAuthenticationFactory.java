package me.maxistar.gitsy;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import java.util.Objects;

public final class ProjectTransportAuthenticationFactory {
    private final SshIdentityMaterialLoader identities;
    private final SshHostTrustCoordinator hostTrust;

    public ProjectTransportAuthenticationFactory(SshIdentityMaterialLoader identities,
                                                  SshHostTrustCoordinator hostTrust) {
        this.identities = Objects.requireNonNull(identities);
        this.hostTrust = Objects.requireNonNull(hostTrust);
    }

    public GitTransportAuthentication create(ProjectModel project, String operationId)
            throws Exception {
        if (project.getAuthenticationType() == ProjectAuthenticationType.HTTPS) {
            return new GitTransportAuthentication(new UsernamePasswordCredentialsProvider(
                    project.getUserName(), project.getPassword()), null, null);
        }
        return create(project, operationId, SshRepositoryUri.resolve(project.getRepoUrl(),
                project.getUserName(), project.getSshPort()));
    }

    GitTransportAuthentication create(ProjectModel project, String operationId,
                                      SshConnectionResolution resolution) throws Exception {
        URIish uri = resolution.getCanonicalUri();
        SshIdentityMaterial material = identities.load();
        SshProjectSessionFactory sessionFactory = new SshProjectSessionFactory(material,
                hostTrust, operationId, uri.getHost(), uri.getPort());
        TransportConfigCallback callback = transport -> {
            if (!(transport instanceof SshTransport)) {
                throw new IllegalArgumentException("SSH authentication requires SSH transport");
            }
            ((SshTransport) transport).setSshSessionFactory(sessionFactory);
        };
        return new GitTransportAuthentication(null, callback, sessionFactory);
    }
}
