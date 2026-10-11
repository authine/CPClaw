package com.cpclaw.mcp;

import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/**
 * Authenticates remote MCP hosts before any task or CloudPivot credential is used.
 * The signed envelope deliberately contains no password or request body.
 */
@Service
public class McpRequestAuthenticator {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PrincipalContextService principalContextService;
    private final String mode;
    private final String sharedSecret;
    private final long clockSkewSeconds;

    public McpRequestAuthenticator(
        PrincipalContextService principalContextService,
        @Value("${cpclaw.mcp.auth.mode:hmac}") String mode,
        @Value("${cpclaw.mcp.auth.shared-secret:}") String sharedSecret,
        @Value("${cpclaw.mcp.auth.clock-skew-seconds:300}") long clockSkewSeconds
    ) {
        this.principalContextService = principalContextService;
        this.mode = normalize(mode, "hmac");
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.clockSkewSeconds = Math.max(1, clockSkewSeconds);
    }

    public PrincipalContext authenticate(String installationId, String tenantId, String externalPrincipal,
            String timestamp, String signature) {
        if ("disabled".equals(mode)) {
            return principalContextService.resolveExternal(externalPrincipal);
        }
        if (!"hmac".equals(mode)) {
            throw unauthorized("MCP 外部身份认证模式不受支持");
        }
        if (sharedSecret.isBlank()) {
            throw unauthorized("MCP 外部身份认证未配置共享密钥");
        }
        String installation = required(installationId, "MCP 安装实例标识");
        String tenant = required(tenantId, "MCP 租户标识");
        if (tenant.length() > 128) throw unauthorized("MCP 租户标识过长");
        String principal = required(externalPrincipal, "MCP 外部用户标识");
        if (principal.length() > 128) {
            throw unauthorized("MCP 外部用户标识过长");
        }
        long epochSeconds = parseTimestamp(timestamp);
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - epochSeconds) > clockSkewSeconds) {
            throw unauthorized("MCP 身份签名已过期");
        }
        String normalizedTimestamp = Long.toString(epochSeconds);
        String expected = sign(installation + "\n" + tenant + "\n" + principal + "\n" + normalizedTimestamp);
        if (!constantTimeEquals(expected, signature)) {
            throw unauthorized("MCP 身份签名无效");
        }
        return principalContextService.resolveAuthenticatedExternal(tenant, principal);
    }

    public String signature(String installationId, String tenantId, String externalPrincipal, long epochSeconds) {
        if (sharedSecret.isBlank()) {
            throw new IllegalStateException("MCP 外部身份认证未配置共享密钥");
        }
        return sign(required(installationId, "MCP 安装实例标识") + "\n"
            + required(tenantId, "MCP 租户标识") + "\n"
            + required(externalPrincipal, "MCP 外部用户标识") + "\n" + epochSeconds);
    }

    @PostConstruct
    void validateConfiguration() {
        if ("hmac".equals(mode) && sharedSecret.length() < 16) {
            throw new IllegalStateException("MCP HMAC 认证已启用，但共享密钥未配置或长度不足 16 个字符");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("MCP 身份签名计算失败", exception);
        }
    }

    private long parseTimestamp(String value) {
        try {
            if (value == null || value.isBlank()) throw new NumberFormatException();
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw unauthorized("MCP 身份时间戳无效");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null || actual.isBlank()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw unauthorized("缺少" + label);
        return value.trim();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
    }

    private McpAuthenticationException unauthorized(String message) {
        return new McpAuthenticationException(message);
    }
}
