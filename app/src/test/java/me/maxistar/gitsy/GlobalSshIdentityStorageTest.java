package me.maxistar.gitsy;

import com.google.gson.Gson;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.Assert.*;

public class GlobalSshIdentityStorageTest {
    private File root;
    private EncryptedFileSshIdentityRepository identities;
    private EncryptedFileSshPassphraseRepository passphrases;
    @Before public void setUp() throws Exception {
        root = Files.createTempDirectory("gitsy-global-ssh").toFile();
        TestCipher cipher = new TestCipher();
        identities = new EncryptedFileSshIdentityRepository(new File(root, "identity"), cipher, new Gson());
        passphrases = new EncryptedFileSshPassphraseRepository(new File(root, "passphrase.enc"), cipher);
    }
    @After public void tearDown() throws Exception { deleteTree(root); }

    @Test public void encryptedCopySurvivesSourceDeletionAndContainsNoPlaintext() throws Exception {
        File source = File.createTempFile("selected-key", ".pem");
        byte[] key = "PRIVATE-KEY-TEST-CONTENT".getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), key);
        identities.save(metadata("first"), Files.readAllBytes(source.toPath()));
        assertTrue(source.delete());
        assertTrue(identities.contains());
        assertArrayEquals(key, identities.loadPrivateKey());
        assertFalse(storeContains(root, key));
    }

    @Test public void globalReplacementLeavesExactlyOneActiveIdentity() throws Exception {
        identities.save(metadata("first"), "first-key".getBytes(StandardCharsets.UTF_8));
        identities.save(metadata("second"), "second-key".getBytes(StandardCharsets.UTF_8));
        assertEquals("SHA256:second", identities.metadata().getFingerprint());
        assertArrayEquals("second-key".getBytes(StandardCharsets.UTF_8), identities.loadPrivateKey());
        File[] records = new File(root, "identity").listFiles(file -> file.getName().equals("active"));
        assertNotNull(records); assertEquals(1, records.length);
    }

    @Test public void wrongPassphraseAndStorageFailurePreservePreviousGlobalPair() throws Exception {
        byte[] oldKey = "old-key".getBytes(StandardCharsets.UTF_8);
        identities.save(metadata("old"), oldKey);
        passphrases.save("old-pass".getBytes(StandardCharsets.UTF_8));
        SshPassphraseRepository failing = new SshPassphraseRepository() {
            @Override public void save(byte[] value) throws Exception { throw new Exception("failure"); }
            @Override public byte[] load() throws Exception { return passphrases.load(); }
            @Override public boolean contains() { return passphrases.contains(); }
            @Override public void delete() throws Exception { passphrases.delete(); }
        };
        SshIdentityImporter importer = new SshIdentityImporter(new SshPrivateKeyParser(), identities, failing);
        byte[] encrypted = generate("correct".getBytes(StandardCharsets.UTF_8));
        try {
            try { importer.importKey(encrypted, "wrong".getBytes(StandardCharsets.UTF_8), true); fail(); }
            catch (SshKeyImportException expected) { assertEquals(SshKeyImportError.INCORRECT_PASSPHRASE, expected.getError()); }
            assertArrayEquals(oldKey, identities.loadPrivateKey());
            try { importer.importKey(encrypted, "correct".getBytes(StandardCharsets.UTF_8), true); fail(); }
            catch (Exception expected) { assertEquals("failure", expected.getMessage()); }
            assertArrayEquals(oldKey, identities.loadPrivateKey());
        } finally { Arrays.fill(encrypted, (byte) 0); }
    }

    @Test public void missingAndCorruptMaterialUseTypedSecretFreeErrorsAndBuffersClear() throws Exception {
        SshIdentityMaterialLoader loader = new SshIdentityMaterialLoader(identities, passphrases);
        try { loader.load(); fail(); }
        catch (SshIdentityAccessException e) { assertEquals(SshIdentityAccessError.MISSING_IDENTITY, e.getError()); }
        identities.save(metadata("ok"), "secret-key".getBytes(StandardCharsets.UTF_8));
        passphrases.save("secret-pass".getBytes(StandardCharsets.UTF_8));
        SshIdentityMaterial material = loader.load();
        byte[] key = material.getPrivateKey(); byte[] pass = material.getPassphrase();
        material.close();
        for (byte value : key) assertEquals(0, value);
        for (byte value : pass) assertEquals(0, value);
    }

    private static SshIdentityMetadata metadata(String value) { return new SshIdentityMetadata("RSA", 3072, "SHA256:" + value, false); }
    private static byte[] generate(byte[] passphrase) throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 3072); ByteArrayOutputStream out = new ByteArrayOutputStream();
        pair.writeOpenSSHv1PrivateKey(out, passphrase); pair.dispose(); return out.toByteArray();
    }
    private static boolean storeContains(File file, byte[] needle) throws Exception {
        if (file.isFile()) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1)
                .contains(new String(needle, StandardCharsets.ISO_8859_1));
        File[] children = file.listFiles(); if (children != null) for (File child : children) if (storeContains(child, needle)) return true;
        return false;
    }
    private static void deleteTree(File file) throws Exception {
        if (file == null || !file.exists()) return; File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child); if (!file.delete()) throw new Exception("cleanup");
    }
    private static final class TestCipher implements SshSecretCipher {
        @Override public String encrypt(byte[] value, String aad) { return aad + ":" + Base64.getEncoder().encodeToString(reverse(value)); }
        @Override public byte[] decrypt(String value, String aad) { String prefix = aad + ":"; if (!value.startsWith(prefix)) throw new IllegalArgumentException(); return reverse(Base64.getDecoder().decode(value.substring(prefix.length()))); }
        private static byte[] reverse(byte[] input) { byte[] out = input.clone(); for (int i=0,j=out.length-1;i<j;i++,j--){byte b=out[i];out[i]=out[j];out[j]=b;} return out; }
    }
}
