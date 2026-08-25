package com.cpclaw.mcp;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.skill.yunshu.YunshuMcpTaskExecutor;
import com.cpclaw.skill.SkillCatalog;
import com.cpclaw.skill.SkillExecutionContext;
import com.cpclaw.task.TaskGateway;
import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.task.dto.TaskSpec;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** MCP transport facade. Domain interpretation is owned by the resolved Skill. */
@Service
public class McpSemanticTaskService {
    private final TaskGateway taskGateway;
    private final com.cpclaw.identity.PrincipalContextService principalContextService;

    public McpSemanticTaskService(TaskGateway taskGateway, com.cpclaw.identity.PrincipalContextService principalContextService) {
        this.taskGateway = taskGateway;
        this.principalContextService = principalContextService;
    }

    public TaskExperienceEnvelope handle(BoundCloudPivotConnection connection, String request, List<String> context, String clientRequestId, String conversationId, Consumer<TaskProgressEvent> progressConsumer) {
        return handle(connection, request, context, clientRequestId, conversationId, "", TaskSpec.empty(request, conversationId, "", clientRequestId), "", progressConsumer);
    }

    public TaskExperienceEnvelope handle(BoundCloudPivotConnection connection, String request, List<String> context, String clientRequestId, String conversationId, String turnId, TaskSpec requestedSpec, Consumer<TaskProgressEvent> progressConsumer) {
        return handle(connection, request, context, clientRequestId, conversationId, turnId, requestedSpec, "", progressConsumer);
    }

    public TaskExperienceEnvelope handle(BoundCloudPivotConnection connection, String request, List<String> context, String clientRequestId, String conversationId, String turnId, TaskSpec requestedSpec, String externalPrincipal, Consumer<TaskProgressEvent> progressConsumer) {
        TaskSpec spec = requestedSpec == null ? TaskSpec.empty(request, conversationId, turnId, clientRequestId) : new TaskSpec(
            requestedSpec.protocolVersion(), requestedSpec.goal().isBlank() ? request : requestedSpec.goal(), requestedSpec.deliverables(),
            requestedSpec.constraints(), requestedSpec.contextRefs().isEmpty() ? context : requestedSpec.contextRefs(), requestedSpec.presentationMode(),
            conversationId, turnId, clientRequestId, requestedSpec.continuationToken()
        );
        String principal = externalPrincipal == null || externalPrincipal.isBlank()
            ? principalContextService.current().principalId()
            : principalContextService.resolveExternal(externalPrincipal.trim()).principalId();
        SemanticTaskRequest semanticRequest = new SemanticTaskRequest(
            "mcp", connection.installationId(), principal, scoped(conversationId, clientRequestId), scoped(conversationId, turnId), request, context, spec
        );
        return taskGateway.execute(semanticRequest, SkillCatalog.YUNSHU_BUSINESS_SYSTEM,
            new SkillExecutionContext(java.util.Map.of("cloudPivotConnection", connection)), progressConsumer);
    }

    private String scoped(String conversationId, String value) {
        String stable = value == null ? "" : value.trim();
        if (stable.isBlank()) return "";
        String conversation = conversationId == null ? "" : conversationId.trim();
        return conversation.isBlank() ? stable : conversation + ":" + stable;
    }
}
