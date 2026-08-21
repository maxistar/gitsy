package me.maxistar.gitsy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

public final class SshPrivateKeyParser {
    public SshKeySummary parse(byte[] privateKey, byte[] passphrase) throws SshKeyImportException {
        if (privateKey == null || privateKey.length == 0) {
            throw new SshKeyImportException(SshKeyImportError.MALFORMED);
        }

        KeyPair pair;
        try {
            pair = KeyPair.load(new JSch(), privateKey, null);
        } catch (Exception error) {
            throw new SshKeyImportException(SshKeyImportError.MALFORMED);
        }

        try {
            boolean encrypted = pair.isEncrypted();
            if (encrypted && passphrase == null) {
                throw new SshKeyImportException(SshKeyImportError.PASSPHRASE_REQUIRED);
            }
            if (encrypted && !pair.decrypt(passphrase)) {
                throw new SshKeyImportException(SshKeyImportError.INCORRECT_PASSPHRASE);
            }

            String algorithm;
            if (pair.getKeyType() == KeyPair.RSA) {
                algorithm = "RSA";
            } else if (pair.getKeyType() == KeyPair.ED25519) {
                algorithm = "Ed25519";
            } else {
                throw new SshKeyImportException(SshKeyImportError.UNSUPPORTED_ALGORITHM);
            }
            String fingerprint = pair.getFingerPrint();
            if (pair.getPublicKeyBlob() == null || fingerprint == null || fingerprint.isEmpty()) {
                throw new SshKeyImportException(SshKeyImportError.MALFORMED);
            }
            int keySizeBits = pair.getKeyType() == KeyPair.ED25519
                    ? pair.getKeySize() * Byte.SIZE : pair.getKeySize();
            return new SshKeySummary(algorithm, keySizeBits, fingerprint, encrypted);
        } catch (SshKeyImportException error) {
            throw error;
        } catch (Exception error) {
            throw new SshKeyImportException(SshKeyImportError.MALFORMED);
        } finally {
            pair.dispose();
        }
    }

}
