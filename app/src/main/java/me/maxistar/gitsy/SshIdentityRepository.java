package me.maxistar.gitsy;

public interface SshIdentityRepository {
    void save(SshIdentityMetadata metadata, byte[] privateKey) throws Exception;
    SshIdentityMetadata metadata() throws Exception;
    byte[] loadPrivateKey() throws Exception;
    boolean contains();
    void delete() throws Exception;
}
