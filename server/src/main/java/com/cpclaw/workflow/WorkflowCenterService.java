package com.cpclaw.workflow;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.WorkflowContractProbeResult;
import com.cpclaw.cloudpivot.WorkflowReadResult;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.credential.CredentialUnavailableException;
import com.cpclaw.metadata.entity.CloudPivotApiEndpoint;
import com.cpclaw.metadata.repository.CloudPivotApiEndpointRepository;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowCenterService {
    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";
    /**
     * Defense in depth: the connector owns the concrete URL/method validation,
     * while this service prevents an alternative connector implementation from
     * registering or reading an arbitrary workflow endpoint.
     */
    private static final Set<String> READ_API_CODES = Set.of(
        "workflow_list_pending",
        "workflow_list_finished",
        "workflow_list_started",
        "workflow_list_instances",
        "workflow_instance_detail",
        "workflow_list_activity"
    );

    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector connector;
    private final CloudPivotApiEndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;

    public WorkflowCenterService(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector connector,
        CloudPivotApiEndpointRepository endpointRepository,
        ObjectMapper objectMapper
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.connector = connector;
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowContractProbeResult probeReadContracts() {
        SystemSettings settings = settings();
        String password = password();
        WorkflowContractProbeResult result = connector.probeWorkflowReadContracts(
            settings.getAdminCloudPivotBaseUrl(), settings.getCloudPivotUsername(), password
        );
        Instant verifiedAt = result.verifiedAt() == null ? Instant.now() : result.verifiedAt();
        List<WorkflowContractProbeResult.Contract> allowedContracts = result.contracts().stream()
            .filter(contract -> READ_API_CODES.contains(contract.apiCode()))
            .toList();
        for (WorkflowContractProbeResult.Contract contract : allowedContracts) {
            CloudPivotApiEndpoint endpoint = endpointRepository.findByApiCode(contract.apiCode()).orElseGet(() -> candidate(contract.apiCode()));
            endpoint.setMethod(contract.method());
            endpoint.setPath(contract.path());
            endpoint.setRawJson(toJson(Map.of(
                "verified", contract.verified(),
                "candidate", !contract.verified(),
                "source", "WorkflowCenterService.probeReadContracts",
                "verifiedAt", verifiedAt.toString(),
                "requestKeys", contract.requestKeys(),
                "responseShape", contract.responseShape(),
                "error", contract.error() == null ? "" : contract.error()
            )));
            endpoint.setSyncedAt(verifiedAt);
            endpointRepository.save(endpoint);
        }
        return new WorkflowContractProbeResult(allowedContracts, verifiedAt);
    }

    public WorkflowReadResult query(String apiCode, int pageSize) {
        if (!READ_API_CODES.contains(apiCode)) {
            throw new IllegalArgumentException("不在流程只读白名单中的接口：" + apiCode);
        }
        CloudPivotApiEndpoint endpoint = endpointRepository.findByApiCode(apiCode)
            .orElseThrow(() -> new IllegalStateException("流程查询接口尚未登记：" + apiCode));
        if (!isVerified(endpoint)) {
            throw new IllegalStateException("流程查询接口尚未完成只读契约验证：" + apiCode);
        }
        SystemSettings settings = settings();
        return connector.queryWorkflowRead(
            settings.getAdminCloudPivotBaseUrl(), settings.getCloudPivotUsername(), password(),
            apiCode, endpoint.getMethod(), endpoint.getPath(), pageSize, requestKeys(endpoint)
        );
    }

    /** Executes a verified workflow read contract with an explicit MCP installation binding. */
    public WorkflowReadResult queryForBinding(String apiCode, int pageSize, String baseUrl, String username, String password) {
        if (!READ_API_CODES.contains(apiCode)) {
            throw new IllegalArgumentException("不在流程只读白名单中的接口：" + apiCode);
        }
        CloudPivotApiEndpoint endpoint = endpointRepository.findByApiCode(apiCode)
            .orElseThrow(() -> new IllegalStateException("流程查询接口尚未登记：" + apiCode));
        if (!isVerified(endpoint)) {
            throw new IllegalStateException("流程查询接口尚未完成只读契约验证：" + apiCode);
        }
        return connector.queryWorkflowRead(
            baseUrl, username, password, apiCode, endpoint.getMethod(), endpoint.getPath(), pageSize, requestKeys(endpoint)
        );
    }

    @Transactional(readOnly = true)
    public boolean isVerified(String apiCode) {
        return endpointRepository.findByApiCode(apiCode).map(this::isVerified).orElse(false);
    }

    private boolean isVerified(CloudPivotApiEndpoint endpoint) {
        try {
            Map<?, ?> raw = objectMapper.readValue(endpoint.getRawJson(), Map.class);
            return Boolean.TRUE.equals(raw.get("verified"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> requestKeys(CloudPivotApiEndpoint endpoint) {
        try {
            Map<?, ?> raw = objectMapper.readValue(endpoint.getRawJson(), Map.class);
            Object keys = raw.get("requestKeys");
            if (keys instanceof List<?> values) return values.stream().map(String::valueOf).toList();
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private CloudPivotApiEndpoint candidate(String apiCode) {
        CloudPivotApiEndpoint endpoint = new CloudPivotApiEndpoint();
        endpoint.setId(UUID.randomUUID().toString());
        endpoint.setApiCode(apiCode);
        endpoint.setName(apiCode);
        endpoint.setCategory("workflow_center");
        endpoint.setOperationType("query_workflow");
        endpoint.setRiskLevel("low");
        endpoint.setRequiresConfirmation(false);
        endpoint.setInputSchemaJson("{}");
        endpoint.setOutputSchemaJson("{}");
        endpoint.setDataScope("当前用户有权限的流程数据");
        endpoint.setApplicableObjectType("workflow");
        return endpoint;
    }

    private SystemSettings settings() {
        return settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalStateException("请先配置普通用户云枢连接"));
    }

    private String password() {
        try {
            return credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
                .orElseThrow(() -> new IllegalStateException("请先配置普通用户云枢密码"));
        } catch (CredentialUnavailableException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"verified\":false,\"error\":\"无法序列化探查结果\"}";
        }
    }
}
