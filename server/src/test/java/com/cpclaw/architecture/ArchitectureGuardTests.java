package com.cpclaw.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.task.TaskGateway;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTests {
    @Test
    void taskGatewayExposesOnlySkillRegistryResolvedExecution() {
        Method[] methods = TaskGateway.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        assertEquals("execute", methods[0].getName());
        assertEquals(4, methods[0].getParameterCount());
        assertFalse(Arrays.stream(methods[0].getParameterTypes()).anyMatch(type -> type.getName().contains("TaskExecutor")));
    }

    @Test
    void removedLegacyOrchestratorDoesNotReturnToTheSourceTree() {
        Path source = Path.of("src/main/java/com/cpclaw/skill/yunshu/YunshuAgentOrchestrator.java");
        assertFalse(Files.exists(source));
    }

    @Test
    void yunshuPlanningAndSemanticsDoNotLiveInFrameworkPackages() {
        assertFalse(Files.exists(Path.of("src/main/java/com/cpclaw/agent/MetadataExecutionPlanner.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/cpclaw/skill/GenericSkillQuestionSemantics.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/cpclaw/skill/yunshu/runtime/MetadataExecutionPlanner.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/cpclaw/skill/yunshu/runtime/DefaultYunshuQuestionSemantics.java")));
    }

    @Test
    void webAndMcpUseTheSameTaskGatewayBoundary() throws Exception {
        String mcp = Files.readString(Path.of("src/main/java/com/cpclaw/mcp/McpSemanticTaskService.java"));
        String web = Files.readString(Path.of("src/main/java/com/cpclaw/conversation/ConversationService.java"));
        assertTrue(mcp.contains("TaskGateway"));
        assertTrue(web.contains("TaskGateway"));
    }
}
