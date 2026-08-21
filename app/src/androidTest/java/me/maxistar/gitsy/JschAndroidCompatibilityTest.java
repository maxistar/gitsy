package me.maxistar.gitsy;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class JschAndroidCompatibilityTest {
    @Test
    public void rsaAndEd25519OpenSshKeysLoadOnAndroidRuntime() throws Exception {
        assertLoads(KeyPair.RSA, 3072);
        assertLoads(KeyPair.ED25519, 256);
    }

    @Test
    public void encryptedOpenSshKeyRequiresCorrectPassphraseOnAndroidRuntime() throws Exception {
        byte[] passphrase = "android-passphrase".getBytes(StandardCharsets.UTF_8);
        byte[] privateKey = generate(KeyPair.RSA, 3072, passphrase);
        try {
            KeyPair pair = KeyPair.load(new JSch(), privateKey, null);
            assertTrue(pair.isEncrypted());
            assertFalse(pair.decrypt("wrong".getBytes(StandardCharsets.UTF_8)));
            assertTrue(pair.decrypt(passphrase));
            pair.dispose();
        } finally {
            Arrays.fill(passphrase, (byte) 0);
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private static void assertLoads(int type, int size) throws Exception {
        byte[] privateKey = generate(type, size, new byte[0]);
        try {
            KeyPair loaded = KeyPair.load(new JSch(), privateKey, null);
            assertEquals(type, loaded.getKeyType());
            assertFalse(loaded.isEncrypted());
            loaded.dispose();
        } finally {
            Arrays.fill(privateKey, (byte) 0);
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
