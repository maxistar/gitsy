package me.maxistar.gitsy;


import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class ValueEncryptor implements SshSecretCipher {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "value_encryptor_v1"; // уникален в рамках приложения
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12; // стандарт для GCM

    //private ValueEncryptor() {}

    /** Вызвать один раз (например, в Application.onCreate). */
    public void ensureKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.getKey(KEY_ALIAS, null) != null) return;

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setKeySize(AES_KEY_SIZE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

                // --- Дополнительно (раскомментируйте, если хотите требовать биометрию/пин) ---
                // .setUserAuthenticationRequired(true)
                // .setUserAuthenticationParameters(0 /* каждый раз */, KeyProperties.AUTH_BIOMETRIC_STRONG)
                // ---------------------------------------------------------------------------

                .build();

        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(spec);
        kg.generateKey();
    }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    /** Шифрует строку. Возвращает blob "v1:Base64(iv):Base64(ciphertext)". AAD необязателен. */
    public String encryptValue(String plaintext, @Nullable String aad) throws Exception {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), aad);
    }

    @Override
    public String encrypt(byte[] plaintext, @Nullable String aad) throws Exception {
        SecretKey key = getKey();

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        if (aad != null) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }

        byte[] ct = cipher.doFinal(plaintext);
        byte[] iv = cipher.getIV();

        String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
        String ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP);
        return "v1:" + ivB64 + ":" + ctB64;
    }

    /** Расшифровывает blob "v1:Base64(iv):Base64(ciphertext)". AAD должен совпадать с тем, что был при шифровании. */
    public String decryptValue(String blob, @Nullable String aad) throws Exception {
        return new String(decrypt(blob, aad), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] decrypt(String blob, @Nullable String aad) throws Exception {
        String[] parts = blob.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported blob format/version");
        }
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ct = Base64.decode(parts[2], Base64.NO_WRAP);

        SecretKey key = getKey();
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        if (aad != null) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }

        return cipher.doFinal(ct);
    }

}
