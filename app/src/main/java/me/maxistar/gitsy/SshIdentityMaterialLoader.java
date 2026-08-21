package me.maxistar.gitsy;

import java.util.Arrays;
import java.util.Objects;

public final class SshIdentityMaterialLoader {
    private final SshIdentityRepository identities;
    private final SshPassphraseRepository passphrases;

    public SshIdentityMaterialLoader(SshIdentityRepository identities,
                                     SshPassphraseRepository passphrases) {
        this.identities = Objects.requireNonNull(identities);
        this.passphrases = Objects.requireNonNull(passphrases);
    }

    public SshIdentityMaterial load() throws SshIdentityAccessException {
        if (!identities.contains()) {
            throw new SshIdentityAccessException(SshIdentityAccessError.MISSING_IDENTITY);
        }

        byte[] privateKey = null;
        try {
            SshIdentityMetadata metadata = identities.metadata();
            privateKey = identities.loadPrivateKey();
            if (privateKey == null || privateKey.length == 0) {
                throw new SshIdentityAccessException(SshIdentityAccessError.CORRUPT_IDENTITY);
            }
            byte[] passphrase = loadPassphrase();
            return new SshIdentityMaterial(metadata, privateKey, passphrase);
        } catch (SshIdentityAccessException error) {
            if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
            throw error;
        } catch (Exception error) {
            if (privateKey != null) Arrays.fill(privateKey, (byte) 0);
            throw new SshIdentityAccessException(SshIdentityAccessError.CORRUPT_IDENTITY, error);
        }
    }

    private byte[] loadPassphrase() throws SshIdentityAccessException {
        if (!passphrases.contains()) return null;
        try {
            return passphrases.load();
        } catch (Exception error) {
            throw new SshIdentityAccessException(SshIdentityAccessError.CORRUPT_PASSPHRASE, error);
        }
    }
}
