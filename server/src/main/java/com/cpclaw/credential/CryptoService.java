package com.cpclaw.credential;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    public CryptoService(
        @Value("${cpclaw.encryption-key:}") String configuredKey,
        @Value("${cpclaw.encryption-key-file:./storage/.encryption-key}") String keyFile
    ) {
        this.keySpec = new SecretKeySpec(resolveKey(configuredKey, keyFile), "AES");
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
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decrypt credential", exception);
        }
    }

    private byte[] resolveKey(String configuredKey, String keyFile) {
        try {
            byte[] configuredDigest = hasText(configuredKey)
                ? MessageDigest.getInstance("SHA-256").digest(configuredKey.trim().getBytes(StandardCharsets.UTF_8))
                : null;
            Path path = Path.of(hasText(keyFile) ? keyFile.trim() : "./storage/.encryption-key").toAbsolutePath().normalize();
            if (configuredDigest != null) {
                persistOrValidate(path, configuredDigest);
                return configuredDigest;
            }
            if (Files.exists(path)) {
                byte[] persisted = decodePersistedKey(Files.readString(path, StandardCharsets.UTF_8));
                if (persisted.length != 32) throw new IllegalStateException("本地凭据加密密钥文件格式无效：" + path);
                return persisted;
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            persistOrValidate(path, generated);
            return generated;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize encryption key", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取或保存 CPClaw 凭据加密密钥文件", exception);
        }
    }

    private void persistOrValidate(Path path, byte[] expected) throws java.io.IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        String encoded = Base64.getEncoder().encodeToString(expected) + System.lineSeparator();
        try {
            Files.writeString(path, encoded, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows ACLs are managed by the account running CPClaw.
            }
        } catch (FileAlreadyExistsException ignored) {
            byte[] persisted = decodePersistedKey(Files.readString(path, StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, persisted)) {
                throw new IllegalStateException("CPC_ENCRYPTION_KEY 与本地密钥文件不一致，已停止启动以保护现有凭据");
            }
        }
    }

    private byte[] decodePersistedKey(String value) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("本地凭据加密密钥文件格式无效", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record EncryptedValue(String encryptedValue, String iv, String authTag) {
    }
}
