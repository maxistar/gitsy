package me.maxistar.gitsy;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

final class SshProjectSessionFactory extends JschConfigSessionFactory implements AutoCloseable {
    private final SshIdentityMaterial material;
    private final SshHostTrustCoordinator trust;
    private final String operationId;
    private final String host;
    private final int port;
    private volatile Exception hostFailure;
    private volatile HostKey verifiedHostKey;

    SshProjectSessionFactory(SshIdentityMaterial material, SshHostTrustCoordinator trust,
                             String operationId, String host, int port) {
        this.material = material; this.trust = trust; this.operationId = operationId;
        this.host = host; this.port = port;
    }

    @Override protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch jsch = new JSch();
        jsch.setHostKeyRepository(new VerifyingHostKeys());
        jsch.addIdentity("gitsy-global", material.getPrivateKey(), null, material.getPassphrase());
        return jsch;
    }

    @Override protected void configure(OpenSshConfig.Host ignored, Session session) {
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", "publickey");
        session.setUserInfo(new NonInteractiveUserInfo());
    }

    void rethrowHostFailure() throws Exception {
        if (hostFailure != null) throw hostFailure;
    }

    @Override public void close() { material.close(); }

    private final class VerifyingHostKeys implements HostKeyRepository {
        @Override public int check(String ignoredHost, byte[] key) {
            try {
                HostKey parsed = new HostKey(host, key);
                trust.verify(operationId, new SshHostIdentity(host, port, parsed.getType(), key));
                verifiedHostKey = parsed;
                return OK;
            } catch (SshHostKeyChangedException changed) {
                hostFailure = changed; return CHANGED;
            } catch (Exception requiredOrFailure) {
                hostFailure = requiredOrFailure; return NOT_INCLUDED;
            }
        }
        @Override public void add(HostKey hostkey, UserInfo userinfo) { }
        @Override public void remove(String host, String type) { }
        @Override public void remove(String host, String type, byte[] key) { }
        @Override public String getKnownHostsRepositoryID() { return "gitsy-app-private"; }
        @Override public HostKey[] getHostKey() {
            return verifiedHostKey == null ? new HostKey[0] : new HostKey[]{verifiedHostKey};
        }
        @Override public HostKey[] getHostKey(String host, String type) { return getHostKey(); }
    }

    private static final class NonInteractiveUserInfo implements UserInfo {
        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return null; }
        @Override public boolean promptPassword(String message) { return false; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) { return false; }
        @Override public void showMessage(String message) { }
    }
}
