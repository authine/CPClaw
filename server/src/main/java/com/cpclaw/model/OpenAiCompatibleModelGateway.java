package com.cpclaw.model;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.skill.yunshu.YunshuModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Compatibility facade. Domain-specific fallback planning is implemented by the Skill. */
@Service
public class OpenAiCompatibleModelGateway extends YunshuModelGateway {
    public OpenAiCompatibleModelGateway(
        ModelConfigRepository modelConfigRepository,
        CredentialService credentialService,
        SensitiveDataMasker sensitiveDataMasker,
        ObjectMapper objectMapper,
        ModelUsageContext modelUsageContext
    ) {
        super(modelConfigRepository, credentialService, sensitiveDataMasker, objectMapper, modelUsageContext);
    }

    private java.util.Optional<String> readStreamingAnswer(java.io.InputStream inputStream, java.util.function.Consumer<String> chunkConsumer) throws java.io.IOException {
        try {
            var method = YunshuModelGateway.class.getDeclaredMethod("readStreamingAnswer", java.io.InputStream.class, java.util.function.Consumer.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked") var value = (java.util.Optional<String>) method.invoke(this, inputStream, chunkConsumer);
            return value;
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof java.io.IOException io) throw io;
            throw new java.io.IOException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new java.io.IOException(exception);
        }
    }

    private void applyModelRequestOptions(java.util.Map<String, Object> body, com.cpclaw.model.entity.ModelConfig config, boolean thinkingEnabled) {
        invoke("applyModelRequestOptions", new Class<?>[]{java.util.Map.class, com.cpclaw.model.entity.ModelConfig.class, boolean.class}, body, config, thinkingEnabled);
    }

    private java.util.Optional<com.cpclaw.model.IntentPlanningResult> parseIntentPlan(String content, String fallback) {
        @SuppressWarnings("unchecked")
        java.util.Optional<com.cpclaw.model.IntentPlanningResult> value =
            (java.util.Optional<com.cpclaw.model.IntentPlanningResult>) invoke(
                "parseIntentPlan",
                new Class<?>[]{String.class, String.class},
                content,
                fallback
            );
        return value;
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            var method = YunshuModelGateway.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(this, args);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IllegalStateException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
