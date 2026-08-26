package com.cpclaw.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.ModelUsageContext;
import com.cpclaw.model.TokenUsage;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.skill.yunshu.YunshuModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YunshuModelGatewayStreamTests {

    private YunshuModelGateway gateway;
    private ModelUsageContext usageContext;
    private Method readStreamingAnswer;

    @BeforeEach
    void setUp() throws Exception {
        usageContext = new ModelUsageContext();
        gateway = new YunshuModelGateway(
            mock(ModelConfigRepository.class),
            mock(CredentialService.class),
            mock(SensitiveDataMasker.class),
            new ObjectMapper(),
            usageContext
        );
        readStreamingAnswer = YunshuModelGateway.class.getDeclaredMethod(
            "readStreamingAnswer",
            java.io.InputStream.class,
            Consumer.class
        );
        readStreamingAnswer.setAccessible(true);
    }

    @Test
    void streamsContentAndCompletesAfterDoneSignal() throws Exception {
        List<String> chunks = new ArrayList<>();

        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"实时\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"输出\"}}]}\n\n"
                + "data: [DONE]\n\n",
            chunks::add
        );

        assertEquals(Optional.of("实时输出"), result);
        assertEquals(List.of("实时", "输出"), chunks);
    }

    @Test
    void rejectsPartialStreamWithoutCompletionSignal() throws Exception {
        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"未完成的回答\"}}]}\n\n",
            ignored -> { }
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void recordsUsageSentImmediatelyAfterDoneSignal() throws Exception {
        usageContext.beginCapture();

        Optional<String> result = invoke(
            "data: {\"choices\":[{\"delta\":{\"content\":\"完整回答\"}}]}\n\n"
                + "data: [DONE]\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}\n\n",
            ignored -> { }
        );
        TokenUsage usage = usageContext.finishCapture();

        assertEquals(Optional.of("完整回答"), result);
        assertEquals(12, usage.promptTokens());
        assertEquals(8, usage.completionTokens());
        assertEquals(20, usage.totalTokens());
    }

    @SuppressWarnings("unchecked")
    private Optional<String> invoke(String stream, Consumer<String> chunkConsumer) throws Exception {
        return (Optional<String>) readStreamingAnswer.invoke(
            gateway,
            new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
            chunkConsumer
        );
    }
}
