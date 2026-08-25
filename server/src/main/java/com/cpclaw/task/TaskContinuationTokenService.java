package com.cpclaw.task;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Signs short-lived continuation tickets; a host cannot turn an arbitrary second call into continuation. */
@Service
public class TaskContinuationTokenService {
    private final byte[] secret;
    private final long ttlSeconds;

    public TaskContinuationTokenService(
        @Value("${cpclaw.task.continuation-secret:change-me-in-production}") String secret,
        @Value("${cpclaw.task.continuation-ttl-seconds:900}") long ttlSeconds
    ) {
        if (secret == null || secret.isBlank() || "change-me-in-production".equals(secret)) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            this.secret = generated;
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public String issue(String taskId, String principal) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = encodePart(taskId) + "." + encodePart(safe(principal)) + "." + expiresAt + "." + UUID.randomUUID();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature(payload);
    }

    public boolean verify(String token, String taskId, String principal) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 2);
            if (parts.length != 2) return false;
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Claims claims = claims(payload);
            if (claims == null || !MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8), signature(payload).getBytes(StandardCharsets.UTF_8))) return false;
            return taskId.equals(claims.taskId()) && safe(principal).equals(claims.principal()) && claims.expiresAt() >= Instant.now().getEpochSecond();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public long ttlSeconds() { return ttlSeconds; }

    /** Parses and verifies a token without granting validity to an untrusted payload. */
    public Claims verifyAndRead(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 2);
            if (parts.length != 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8), signature(payload).getBytes(StandardCharsets.UTF_8))) return null;
            Claims claims = claims(payload);
            return claims != null && claims.expiresAt() >= Instant.now().getEpochSecond() ? claims : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法签发任务续接票据", exception);
        }
    }

    private String safe(String value) { return value == null ? "" : value; }

    private String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe(value).getBytes(StandardCharsets.UTF_8));
    }

    private String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Claims claims(String payload) {
        String[] fields = payload.split("\\.", 4);
        if (fields.length != 4 || fields[0].isBlank() || fields[2].isBlank() || fields[3].isBlank()) return null;
        try {
            return new Claims(decodePart(fields[0]), decodePart(fields[1]), Long.parseLong(fields[2]), fields[3]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record Claims(String taskId, String principal, long expiresAt, String nonce) { }
}
