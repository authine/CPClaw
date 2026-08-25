package com.cpclaw.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleModelGatewayStreamTests {

    private OpenAiCompatibleModelGateway gateway;
    private ModelUsageContext usageContext;
    private Method readStreamingAnswer;

    @BeforeEach
    void setUp() throws Exception {
        usageContext = new ModelUsageContext();
        gateway = new OpenAiCompatibleModelGateway(
            mock(ModelConfigRepository.class),
            mock(CredentialService.class),
            mock(SensitiveDataMasker.class),
            new ObjectMapper(),
            usageContext
        );
        readStreamingAnswer = OpenAiCompatibleModelGateway.class.getDeclaredMethod(
            "readStreamingAnswer",
            java.io.InputStream.class,
            Consumer.class
        );
        readStreamingAnswer.setAccessible(true);
    }

    @Test
    void rejectsPartialStreamWithoutCompletionSignal() throws Exception {
        List<String> chunks = new ArrayList<>();
        Optional<String> result = invoke("data: {\"choices\":[{\"delta\":{\"content\":\"半截回答\"}}]}\n\n", chunks::add);

        assertTrue(result.isEmpty());
        assertEquals(List.of("半截回答"), chunks);
    }

    @Test
    void acceptsStreamWithDoneSignal() throws Exception {
        List<String> chunks = new ArrayList<>();
        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"完整回答\"}}]}\n\n" +
                "data: [DONE]\n\n",
            chunks::add
        );

        assertEquals(Optional.of("完整回答"), result);
        assertEquals(List.of("完整回答"), chunks);
    }

    @Test
    void capturesUsageFromStreamingUsageBlock() throws Exception {
        usageContext.beginCapture();
        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"完整回答\"}}]}\n\n" +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30,\"total_tokens\":150}}\n\n" +
                "data: [DONE]\n\n",
            ignored -> { }
        );

        TokenUsage usage = usageContext.finishCapture();
        assertEquals(Optional.of("完整回答"), result);
        assertEquals(120, usage.promptTokens());
        assertEquals(30, usage.completionTokens());
        assertEquals(0, usage.cachedTokens());
        assertEquals(150, usage.totalTokens());
    }

    @Test
    void rejectsLengthTruncatedStream() throws Exception {
        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"被截断的回答\"},\"finish_reason\":\"length\"}]}\n\n" +
                "data: [DONE]\n\n",
            ignored -> { }
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void capturesUsageAfterDoneAndNestedProviderUsage() throws Exception {
        usageContext.beginCapture();
        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"完整回答\"}}]}\n\n" +
                "data: [DONE]\n\n" +
                "data: {\"response\":{\"usage\":{\"input_tokens\":12,\"output_tokens\":8}}}\n\n",
            ignored -> { }
        );

        TokenUsage usage = usageContext.finishCapture();
        assertEquals(Optional.of("完整回答"), result);
        assertEquals(12, usage.promptTokens());
        assertEquals(8, usage.completionTokens());
        assertEquals(0, usage.cachedTokens());
        assertEquals(20, usage.totalTokens());
    }

    @Test
    void capturesCachedInputTokensFromOpenAiDetails() throws Exception {
        usageContext.beginCapture();
        invoke("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30,\"total_tokens\":150,\"prompt_tokens_details\":{\"cached_tokens\":80}}}\n\n" + "data: [DONE]\n\n", ignored -> { });
        TokenUsage usage = usageContext.finishCapture();

        assertEquals(120, usage.promptTokens());
        assertEquals(30, usage.completionTokens());
        assertEquals(80, usage.cachedTokens());
        assertEquals(150, usage.totalTokens());
    }

    @Test
    void sendsThinkingExtensionsOnlyForModelsThatDeclareSupport() throws Exception {
        ModelConfig model = new ModelConfig();
        model.setSupportsThinking(true);
        Map<String, Object> body = applyModelOptions(model, true);

        assertEquals(true, body.get("enable_thinking"));
        assertEquals("high", body.get("reasoning_effort"));

        model.setSupportsThinking(false);
        assertTrue(applyModelOptions(model, true).isEmpty());
    }

    @Test
    void letsProviderExtraBodyOverrideDefaultThinkingExtensions() throws Exception {
        ModelConfig model = new ModelConfig();
        model.setSupportsThinking(true);
        model.setExtraBodyJson("{\"enable_thinking\":false,\"reasoning_effort\":\"medium\",\"provider_option\":\"value\"}");

        Map<String, Object> body = applyModelOptions(model, true);

        assertEquals(false, body.get("enable_thinking"));
        assertEquals("medium", body.get("reasoning_effort"));
        assertEquals("value", body.get("provider_option"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesProviderReasoningWithSafeSummaryWhenStructuredReasoningIsAbsent() throws Exception {
        Method parser = OpenAiCompatibleModelGateway.class.getDeclaredMethod("parseIntentPlan", String.class, String.class);
        parser.setAccessible(true);
        Optional<IntentPlanningResult> fallback = (Optional<IntentPlanningResult>) parser.invoke(
            gateway,
            "{\"intent\":\"query_data\",\"confidence\":0.9}",
            "供应商推理摘要"
        );
        Optional<IntentPlanningResult> structured = (Optional<IntentPlanningResult>) parser.invoke(
            gateway,
            "{\"intent\":\"query_data\",\"reasoning\":\"结构化业务依据\",\"confidence\":0.9}",
            "不应覆盖"
        );

        assertEquals("基于当前目标、上下文和可用元数据生成受限计划。", fallback.orElseThrow().reasoning());
        assertEquals("结构化业务依据", structured.orElseThrow().reasoning());
    }

    @SuppressWarnings("unchecked")
    private Optional<String> invoke(String stream, Consumer<String> chunkConsumer) throws Exception {
        return (Optional<String>) readStreamingAnswer.invoke(
            gateway,
            new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
            chunkConsumer
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyModelOptions(ModelConfig model, boolean thinkingEnabled) throws Exception {
        Method method = OpenAiCompatibleModelGateway.class.getDeclaredMethod(
            "applyModelRequestOptions", Map.class, ModelConfig.class, boolean.class
        );
        method.setAccessible(true);
        Map<String, Object> body = new LinkedHashMap<>();
        method.invoke(gateway, body, model, thinkingEnabled);
        return body;
    }
}
