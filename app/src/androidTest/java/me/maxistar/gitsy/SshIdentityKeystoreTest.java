package me.maxistar.gitsy;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SshIdentityKeystoreTest {
    @Test
    public void encryptedIdentityRoundTripsThroughAndroidKeystore() throws Exception {
        ValueEncryptor encryptor = new ValueEncryptor();
        encryptor.ensureKey();
        EncryptedFileSshIdentityRepository repository = new EncryptedFileSshIdentityRepository(
                ApplicationProvider.getApplicationContext(), encryptor);
        byte[] privateKey = "ANDROID-KEYSTORE-PRIVATE-KEY-TEST".getBytes(StandardCharsets.UTF_8);

        try {
            repository.save(new SshIdentityMetadata(
                    "RSA", 3072, "SHA256:android-test", false), privateKey);

            assertTrue(repository.contains());
            assertArrayEquals(privateKey, repository.loadPrivateKey());
        } finally {
            repository.delete();
        }
        assertFalse(repository.contains());
    }
}
