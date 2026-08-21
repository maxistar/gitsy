package me.maxistar.gitsy;

public interface TrustedHostRepository {
    TrustedHostMatch check(SshHostIdentity candidate) throws Exception;
    void trustUnknown(SshHostIdentity candidate) throws Exception;
    void replace(SshHostIdentity candidate) throws Exception;
    void remove(String host, int port) throws Exception;
}
