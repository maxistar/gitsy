package me.maxistar.gitsy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Generates an SSH identity in memory and stores it through the normal encrypted import path. */
public final class SshPrivateKeyGenerator {
    public static final int ED25519 = KeyPair.ED25519;
    public static final int RSA = KeyPair.RSA;
    private static final int RSA_KEY_SIZE_BITS = 3072;

    private final SshPrivateKeyParser parser;
    private final SshIdentityImporter importer;

    public SshPrivateKeyGenerator(SshPrivateKeyParser parser, SshIdentityImporter importer) {
        this.parser = parser;
        this.importer = importer;
    }

    public GeneratedKey generateAndStore(int keyType) throws Exception {
        if (keyType != ED25519 && keyType != RSA) {
            throw new IllegalArgumentException("Unsupported SSH key type");
        }

        KeyPair pair = null;
        WipingByteArrayOutputStream privateKey = new WipingByteArrayOutputStream();
        ByteArrayOutputStream publicKey = new ByteArrayOutputStream();
        byte[] privateBytes = null;
        try {
            JSch jsch = new JSch();
            pair = keyType == RSA
                    ? KeyPair.genKeyPair(jsch, keyType, RSA_KEY_SIZE_BITS)
                    : KeyPair.genKeyPair(jsch, keyType);
            pair.writeOpenSSHv1PrivateKey(privateKey, null);
            pair.writePublicKey(publicKey, "gitsy@android");

            privateBytes = privateKey.toByteArray();
            parser.parse(privateBytes, null);
            String publicLine = new String(publicKey.toByteArray(), StandardCharsets.UTF_8).trim();
            if (publicLine.isEmpty()) throw new IllegalStateException("Generated public key is empty");

            SshIdentityMetadata metadata = importer.importKey(privateBytes, null, false);
            return new GeneratedKey(metadata, publicLine);
        } finally {
            if (privateBytes != null) Arrays.fill(privateBytes, (byte) 0);
            privateKey.clear();
            if (pair != null) pair.dispose();
        }
    }

    public static final class GeneratedKey {
        private final SshIdentityMetadata metadata;
        private final String publicKey;

        GeneratedKey(SshIdentityMetadata metadata, String publicKey) {
            this.metadata = metadata;
            this.publicKey = publicKey;
        }

        public SshIdentityMetadata getMetadata() { return metadata; }
        public String getPublicKey() { return publicKey; }
    }

    private static final class WipingByteArrayOutputStream extends ByteArrayOutputStream {
        void clear() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }
}
