package com.cpclaw.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.repository.ModelConfigRepository;
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

    @SuppressWarnings("unchecked")
    private Optional<String> invoke(String stream, Consumer<String> chunkConsumer) throws Exception {
        return (Optional<String>) readStreamingAnswer.invoke(
            gateway,
            new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
            chunkConsumer
        );
    }
}
