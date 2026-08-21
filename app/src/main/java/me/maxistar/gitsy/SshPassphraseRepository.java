package me.maxistar.gitsy;

public interface SshPassphraseRepository {
    void save(byte[] passphrase) throws Exception;
    byte[] load() throws Exception;
    boolean contains();
    void delete() throws Exception;
}
