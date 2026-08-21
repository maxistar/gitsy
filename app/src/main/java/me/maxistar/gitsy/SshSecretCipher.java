package me.maxistar.gitsy;

import androidx.annotation.Nullable;

public interface SshSecretCipher {
    String encrypt(byte[] plaintext, @Nullable String associatedData) throws Exception;
    byte[] decrypt(String ciphertext, @Nullable String associatedData) throws Exception;
}
