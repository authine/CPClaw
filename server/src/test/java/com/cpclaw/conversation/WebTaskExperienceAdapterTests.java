package com.cpclaw.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebTaskExperienceAdapterTests {

    @Test
    void exposesStructuredInsightArtifactToWebInsteadOfOnlyMarkdown() {
        TaskExperienceEnvelope envelope = new TaskExperienceEnvelope(
            "2.0",
            new TaskExperienceEnvelope.Task("task-1", "completed", Instant.now(), false),
            Map.of("matchedObject", "商机"),
            List.of(),
            Map.of(
                "message", "商机阶段结果",
                "intent", "analyze_data",
                "artifact", Map.ofEntries(
                    Map.entry("type", "data_insight"),
                    Map.entry("title", "商机分析报告"),
                    Map.entry("subject", "商机"),
                    Map.entry("period", "全部时间"),
                    Map.entry("scope", "当前可见数据"),
                    Map.entry("confidence", "high"),
                    Map.entry("kpis", List.of(Map.of("label", "商机数", "value", "10", "unit", "条", "tone", "primary", "description", "真实数据"))),
                    Map.entry("charts", List.of(Map.of("id", "stage-distribution", "type", "funnel", "title", "商机阶段漏斗", "unit", "条", "semantic", "ordered_stage", "description", "阶段分布", "labels", List.of("需求", "方案"), "series", List.of(Map.of("name", "数量", "values", List.of(6, 4)))))),
                    Map.entry("sections", List.of(Map.of("title", "核心结论", "findings", List.of("商机共 10 条")))),
                    Map.entry("followUps", List.of("哪些商机需要跟进？")),
                    Map.entry("sources", List.of("商机")),
                    Map.entry("warnings", List.of())
                )
            ),
            Map.of(), Map.of()
        );

        AgentResponse response = new WebTaskExperienceAdapter(new ObjectMapper()).adapt(
            envelope,
            new MessageItem("message-1", "assistant", "", Instant.now().toString(), "{}"),
            AgentProgressListener.NOOP
        );

        assertNotNull(response.insightReport());
        assertEquals("商机分析报告", response.insightReport().title());
        assertEquals("funnel", response.insightReport().charts().getFirst().type());
        assertTrue(response.assistantMessage().metadataJson().contains("insightReport"));
    }
}
