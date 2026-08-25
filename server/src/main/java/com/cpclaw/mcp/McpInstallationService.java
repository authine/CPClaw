package com.cpclaw.mcp;

import com.cpclaw.mcp.dto.McpCloudPivotBindingRequest;
import com.cpclaw.mcp.dto.McpCloudPivotInstallationResponse;
import com.cpclaw.mcp.entity.McpInstallation;
import com.cpclaw.mcp.repository.McpInstallationRepository;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MCP publication control plane. CloudPivot endpoint is owned by CPClaw; caller credentials stay in the MCP client. */
@Service
public class McpInstallationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private final McpInstallationRepository installationRepository;
    private final SystemSettingsRepository settingsRepository;
    private final String publicBaseUrl;

    public McpInstallationService(McpInstallationRepository installationRepository, SystemSettingsRepository settingsRepository,
            @Value("${cpclaw.mcp.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.installationRepository = installationRepository;
        this.settingsRepository = settingsRepository;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public McpCloudPivotInstallationResponse getOrCreate(String installationId) {
        return response(installation(installationId));
    }

    @Transactional
    public McpCloudPivotInstallationResponse enable(McpCloudPivotBindingRequest request) {
        McpInstallation installation = installation(request == null ? null : request.installationId());
        environmentBaseUrl();
        Instant now = Instant.now();
        if (request != null && hasText(request.displayName())) installation.setDisplayName(request.displayName().trim());
        installation.setStatus(STATUS_ENABLED);
        installation.setEnabledAt(now);
        installation.setUpdatedAt(now);
        installationRepository.save(installation);
        return response(installation);
    }

    @Transactional
    public McpCloudPivotInstallationResponse disable(String installationId) {
        McpInstallation installation = installation(installationId);
        installation.setStatus(STATUS_DISABLED);
        installation.setUpdatedAt(Instant.now());
        installationRepository.save(installation);
        return response(installation);
    }

    public BoundCloudPivotConnection requireEnabledConnection(String installationKey, String username, String password) {
        McpInstallation installation = installationRepository.findByInstallationKey(normalizeInstallationKey(installationKey))
            .orElseThrow(() -> new IllegalArgumentException("未识别的 MCP 安装实例，请先在系统设置中发布 MCP 服务。"));
        if (!STATUS_ENABLED.equals(installation.getStatus())) {
            throw new IllegalStateException("该 MCP 安装实例尚未启用。请先在 CPClaw 系统设置发布 MCP 服务。");
        }
        if (!hasText(username) || !hasText(password)) {
            throw new IllegalArgumentException("MCP 客户端未配置云枢账号或密码。请在客户端 MCP 配置中设置 CPC_CLOUDPIVOT_USERNAME 和 CPC_CLOUDPIVOT_PASSWORD。");
        }
        return new BoundCloudPivotConnection(installation.getInstallationKey(), environmentBaseUrl(), username.trim(), password);
    }

    private McpInstallation installation(String installationKey) {
        String key = normalizeInstallationKey(installationKey);
        return installationRepository.findByInstallationKey(key).orElseGet(() -> {
            Instant now = Instant.now();
            McpInstallation created = new McpInstallation();
            created.setId(UUID.randomUUID().toString());
            created.setInstallationKey(key);
            created.setDisplayName("云枢MCP");
            created.setStatus(STATUS_PENDING);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            return installationRepository.save(created);
        });
    }

    private McpCloudPivotInstallationResponse response(McpInstallation installation) {
        return new McpCloudPivotInstallationResponse(
            installation.getInstallationKey(), installation.getDisplayName(), installation.getStatus(),
            hasEnvironmentBaseUrl(), "CPClaw 已配置的云枢环境", installation.getEnabledAt(), mcpClientConfig(installation), capabilities()
        );
    }

    private Map<String, Object> mcpClientConfig(McpInstallation installation) {
        return Map.of(
            "type", "sse",
            "url", publicBaseUrl() + "/api/mcp/cloudpivot/sse",
            "headers", Map.of(
                "x-cpclaw-installation-id", installation.getInstallationKey(),
                "x-cpclaw-cloudpivot-username", "请填写当前终端用户的云枢账号",
                "x-cpclaw-cloudpivot-password", "请通过 MCP 客户端的安全配置填写密码"
            ),
            "disabled", !STATUS_ENABLED.equals(installation.getStatus()),
            "note", "这是 CPClaw 提供的 SSE MCP 服务地址。账号与密码只在 OpenClaw 兼容 MCP 客户端配置，不写入 CPClaw 数据库。"
        );
    }

    private String publicBaseUrl() {
        String value = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (value.isEmpty()) return "http://localhost:8080";
        return value.replaceAll("/+$", "");
    }

    private List<Map<String, String>> capabilities() {
        return List.of(
            Map.of("name", "智能问数与数据查询卡片", "status", "enabled", "rule", "使用 MCP 客户端终端账号及本地元数据白名单"),
            Map.of("name", "流程待办、已办、我发起查询", "status", "verified-readonly", "rule", "仅限完成契约验证的只读接口"),
            Map.of("name", "智能填单、流程处理、数据操作", "status", "confirmation-required", "rule", "未验证的写契约不可执行；任何写入都必须由用户确认")
        );
    }

    private boolean hasEnvironmentBaseUrl() {
        try { environmentBaseUrl(); return true; } catch (IllegalStateException exception) { return false; }
    }

    private String environmentBaseUrl() {
        SystemSettings settings = settingsRepository.findById("default")
            .orElseThrow(() -> new IllegalStateException("CPClaw 尚未配置云枢环境地址。请先在“个人云枢账号”或“管理员云枢环境”配置服务地址。"));
        String baseUrl = settings.getAdminCloudPivotBaseUrl();
        if (!hasText(baseUrl)) throw new IllegalStateException("CPClaw 尚未配置云枢环境地址。请先在“个人云枢账号”或“管理员云枢环境”配置服务地址。");
        return baseUrl.trim();
    }

    private String normalizeInstallationKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) return "CloudPivotMCP";
        if (!key.matches("[A-Za-z0-9._-]{8,64}")) throw new IllegalArgumentException("MCP 安装标识格式无效。");
        return key;
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    public record BoundCloudPivotConnection(String installationId, String baseUrl, String username, String password) {}
}
