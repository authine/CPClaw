package com.cpclaw.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CryptoServiceTests {
    @TempDir
    Path tempDir;

    @Test
    void persistsGeneratedKeyAndReusesItAcrossInstances() throws Exception {
        Path keyFile = tempDir.resolve(".encryption-key");
        CryptoService first = new CryptoService("", keyFile.toString());
        CryptoService second = new CryptoService("", keyFile.toString());
        CryptoService.EncryptedValue encrypted = first.encrypt("云枢密码");

        assertEquals("云枢密码", second.decrypt(encrypted.encryptedValue(), encrypted.iv(), encrypted.authTag()));
        assertEquals(1, Files.readAllLines(keyFile).size());
    }

    @Test
    void rejectsDifferentConfiguredKeyWhenKeyFileAlreadyExists() {
        Path keyFile = tempDir.resolve(".encryption-key");
        new CryptoService("first-stable-key", keyFile.toString());

        assertThrows(IllegalStateException.class, () -> new CryptoService("second-key", keyFile.toString()));
    }
}
