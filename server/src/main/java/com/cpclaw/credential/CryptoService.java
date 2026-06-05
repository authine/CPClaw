package com.cpclaw.credential;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    public CryptoService(@Value("${cpclaw.encryption-key:}") String configuredKey) {
        this.keySpec = new SecretKeySpec(normalizeKey(configuredKey), "AES");
    }

    public EncryptedValue encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encryptedWithTag = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            int tagLengthBytes = TAG_LENGTH_BITS / 8;
            byte[] encrypted = Arrays.copyOf(encryptedWithTag, encryptedWithTag.length - tagLengthBytes);
            byte[] tag = Arrays.copyOfRange(encryptedWithTag, encryptedWithTag.length - tagLengthBytes, encryptedWithTag.length);
            Base64.Encoder encoder = Base64.getEncoder();
            return new EncryptedValue(encoder.encodeToString(encrypted), encoder.encodeToString(iv), encoder.encodeToString(tag));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt credential", exception);
        }
    }

    public String decrypt(String encryptedValue, String iv, String authTag) {
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] encrypted = decoder.decode(encryptedValue);
            byte[] tag = decoder.decode(authTag);
            byte[] encryptedWithTag = new byte[encrypted.length + tag.length];
            System.arraycopy(encrypted, 0, encryptedWithTag, 0, encrypted.length);
            System.arraycopy(tag, 0, encryptedWithTag, encrypted.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, decoder.decode(iv)));
            return new String(cipher.doFinal(encryptedWithTag), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to decrypt credential", exception);
        }
    }

    private byte[] normalizeKey(String configuredKey) {
        String source = configuredKey == null || configuredKey.isBlank() ? "cpclaw-local-development-key" : configuredKey;
        try {
            return MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize encryption key", exception);
        }
    }

    public record EncryptedValue(String encryptedValue, String iv, String authTag) {
    }
}
