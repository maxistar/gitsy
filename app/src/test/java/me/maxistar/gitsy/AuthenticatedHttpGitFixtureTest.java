package me.maxistar.gitsy;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.*;

public class AuthenticatedHttpGitFixtureTest {
    private static final String USER = "notes-user";
    private static final String TOKEN = "correct-token";
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void rejectsWrongTokenThenPromotesCorrectedCloneWithoutLeakingSecret()
            throws Exception {
        File origin = temporaryFolder.newFolder("origin");
        try (Git git = Git.init().setDirectory(origin).call()) {
            try (FileWriter writer = new FileWriter(new File(origin, "note.md"))) {
                writer.write("hello\n");
            }
            git.add().addFilepattern("note.md").call();
            git.commit().setMessage("initial").setAuthor("Test", "test@example.invalid").call();
        }

        Server server = authenticatedServer(origin);
        server.start();
        try {
            int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
            ProjectModel project = new ProjectModel("http://127.0.0.1:" + port + "/repo.git",
                    USER, "wrong-token", "content://notes");
            project.folderName = "http-auth-recovery";
            File filesRoot = temporaryFolder.newFolder("files");
            GitService service = new GitService();
            ProjectTransportAuthenticationFactory authentication = httpsAuthenticationFactory();

            try {
                service.cloneRepositoryInRoot(filesRoot, project, authentication);
                fail("Wrong token must be rejected");
            } catch (HttpsCloneException expected) {
                assertEquals(CloneFailureCategory.AUTHENTICATION, expected.getCategory());
                assertFalse(expected.getMessage().contains("wrong-token"));
            }
            assertFalse(new File(filesRoot, project.getFolderName()).exists());
            assertEquals(0, stagingCount(filesRoot));

            project.useHttpsAuthentication(USER, TOKEN);
            service.cloneRepositoryInRoot(filesRoot, project, authentication);
            File promoted = new File(filesRoot, project.getFolderName());
            assertTrue(new File(promoted, ".git").isDirectory());
            assertTrue(new File(promoted, "note.md").isFile());
            assertEquals(0, stagingCount(filesRoot));
        } finally {
            server.stop();
            server.join();
        }
    }

    private static Server authenticatedServer(File origin) throws java.io.IOException {
        Server server = new Server(0);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        GitServlet servlet = new GitServlet();
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(origin, ".git")).build();
        servlet.setRepositoryResolver((request, name) -> repository);
        context.addServlet(new ServletHolder(servlet), "/*");
        context.addFilter(new FilterHolder(new BasicAuthenticationFilter()), "/*",
                EnumSet.of(DispatcherType.REQUEST));
        return server;
    }

    private static ProjectTransportAuthenticationFactory httpsAuthenticationFactory() {
        SshIdentityRepository identities = new SshIdentityRepository() {
            public boolean contains() { return false; }
            public SshIdentityMetadata metadata() { return null; }
            public byte[] loadPrivateKey() { return null; }
            public void save(SshIdentityMetadata metadata, byte[] privateKey) { }
            public void delete() { }
        };
        SshPassphraseRepository passphrases = new SshPassphraseRepository() {
            public boolean contains() { return false; }
            public byte[] load() { return null; }
            public void save(byte[] passphrase) { }
            public void delete() { }
        };
        TrustedHostRepository hosts = new TrustedHostRepository() {
            public TrustedHostMatch check(SshHostIdentity candidate) { return TrustedHostMatch.UNKNOWN; }
            public void trustUnknown(SshHostIdentity candidate) { }
            public void replace(SshHostIdentity candidate) { }
            public void remove(String host, int port) { }
        };
        return new ProjectTransportAuthenticationFactory(
                new SshIdentityMaterialLoader(identities, passphrases),
                new SshHostTrustCoordinator(hosts));
    }

    private static int stagingCount(File root) {
        File[] files = root.listFiles((directory, name) -> name.contains(".clone-staging-"));
        return files == null ? 0 : files.length;
    }

    private static final class BasicAuthenticationFilter implements Filter {
        private final String expected = "Basic " + Base64.getEncoder().encodeToString(
                (USER + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
        @Override public void init(FilterConfig filterConfig) { }
        @Override public void destroy() { }
        @Override public void doFilter(ServletRequest request, ServletResponse response,
                                       FilterChain chain) throws java.io.IOException, ServletException {
            if (!expected.equals(((HttpServletRequest) request).getHeader("Authorization"))) {
                ((HttpServletResponse) response).setHeader("WWW-Authenticate", "Basic realm=GitSyTest");
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
