package com.cpclaw.yunshu.protocol;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for {@code yunshu-skill/1.0}. It does not proxy into CPClaw's
 * conversation API; only a YunshuSkillGateway implementation receives commands.
 */
@RestController
@RequestMapping("/api/yunshu-skill/tasks")
public class YunshuSkillHttpController {
    private final YunshuSkillGateway gateway;
    private final Map<String, YunshuHostContextResolver> hostContextResolvers;

    public YunshuSkillHttpController(@Qualifier("persistentFormTaskRuntime") YunshuSkillGateway gateway,
            List<YunshuHostContextResolver> hostContextResolvers) {
        this.gateway = gateway;
        Map<String, YunshuHostContextResolver> resolvers = new LinkedHashMap<>();
        for (YunshuHostContextResolver resolver : hostContextResolvers == null ? List.<YunshuHostContextResolver>of() : hostContextResolvers) {
            if (resolver == null || resolver.hostId() == null || resolver.hostId().isBlank()) continue;
            if (resolvers.putIfAbsent(resolver.hostId().trim(), resolver) != null) {
                throw new IllegalStateException("A Yunshu host may have only one trusted context resolver: " + resolver.hostId());
            }
        }
        this.hostContextResolvers = Map.copyOf(resolvers);
    }

    @PostMapping(value = "/command", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<YunshuTaskEnvelope> command(@RequestBody CanonicalTaskCommand command, HttpServletRequest request) {
        YunshuHostContext context = resolveContext(request);
        if (context == null) return ResponseEntity.status(401).body(unauthenticated(command));
        YunshuTaskEnvelope envelope = gateway.handle(command.toRequest(), context);
        return ResponseEntity.ok(envelope);
    }

    private YunshuHostContext resolveContext(HttpServletRequest request) {
        String requestedHost = request.getHeader(CpClawServerHostContextResolver.HOST_HEADER);
        if (requestedHost == null || requestedHost.isBlank()) return null;
        YunshuHostContextResolver resolver = hostContextResolvers.get(requestedHost.trim());
        if (resolver != null) {
            Optional<YunshuHostContext> resolved = resolver.resolve(request);
            return resolved.orElse(null);
        }
        // A host declaration without a matching verification adapter is never
        // allowed to fall back to the CPClaw default principal.
        return null;
    }

    private YunshuTaskEnvelope unauthenticated(CanonicalTaskCommand command) {
        String taskId = command == null ? "" : command.taskId();
        String message = "当前宿主未配置可信身份验证适配器。";
        return new YunshuTaskEnvelope(
            YunshuProtocol.VERSION,
            new YunshuTaskEnvelope.Task(taskId, YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.failed, 0, false, Instant.now()),
            new YunshuTaskEnvelope.Interaction(YunshuProtocol.InteractionType.none, List.of()),
            new YunshuTaskEnvelope.Draft(Map.of(), "", 0, List.of()),
            new YunshuTaskEnvelope.Validation(List.of()), new YunshuTaskEnvelope.Next(Map.of(), "", null),
            new YunshuTaskEnvelope.Completion("failed", List.of()), Map.of("message", message), Map.of(),
            new YunshuTaskEnvelope.HostAction(YunshuProtocol.HostActionType.report_failure, false, Map.of()), Map.of(),
            new YunshuTaskEnvelope.Error(YunshuProtocol.ErrorCode.PERMISSION_DENIED, message, false));
    }
}
