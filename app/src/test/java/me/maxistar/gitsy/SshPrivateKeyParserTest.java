package me.maxistar.gitsy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SshPrivateKeyParserTest {
    private final SshPrivateKeyParser parser = new SshPrivateKeyParser();

    @Test
    public void summarizesRsaAndEd25519WithoutSecretMaterial() throws Exception {
        assertSummary(KeyPair.RSA, 3072, "RSA");
        assertSummary(KeyPair.ED25519, 256, "Ed25519");
    }

    @Test
    public void reportsEncryptedAndWrongPassphraseWithoutLibraryMessage() throws Exception {
        byte[] key = generate(KeyPair.RSA, 3072, "correct".getBytes(StandardCharsets.UTF_8));
        try {
            assertError(key, null, SshKeyImportError.PASSPHRASE_REQUIRED);
            assertError(key, "wrong".getBytes(StandardCharsets.UTF_8),
                    SshKeyImportError.INCORRECT_PASSPHRASE);
            SshKeySummary summary = parser.parse(key, "correct".getBytes(StandardCharsets.UTF_8));
            assertTrue(summary.isEncrypted());
            assertEquals("RSA", summary.getAlgorithm());
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @Test
    public void distinguishesMalformedAndUnsupportedKeys() throws Exception {
        assertError("not a key".getBytes(StandardCharsets.UTF_8), null,
                SshKeyImportError.MALFORMED);
        byte[] dsa = generate(KeyPair.DSA, 1024, new byte[0]);
        try {
            assertError(dsa, null, SshKeyImportError.UNSUPPORTED_ALGORITHM);
        } finally {
            Arrays.fill(dsa, (byte) 0);
        }
    }

    private void assertSummary(int type, int size, String algorithm) throws Exception {
        byte[] key = generate(type, size, new byte[0]);
        try {
            SshKeySummary summary = parser.parse(key, null);
            assertEquals(algorithm, summary.getAlgorithm());
            assertEquals(size, summary.getKeySize());
            assertTrue(summary.getFingerprint().startsWith("SHA256:"));
            assertFalse(summary.isEncrypted());
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private void assertError(byte[] key, byte[] passphrase, SshKeyImportError expected) throws Exception {
        try {
            parser.parse(key, passphrase);
            fail("Expected " + expected);
        } catch (SshKeyImportException error) {
            assertEquals(expected, error.getError());
            assertEquals(expected.name(), error.getMessage());
        }
    }

    private static byte[] generate(int type, int size, byte[] passphrase) throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), type, size);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        pair.writeOpenSSHv1PrivateKey(output, passphrase);
        pair.dispose();
        return output.toByteArray();
    }
}
