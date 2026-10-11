package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;

class YunshuReconciliationConfigurationTests {

    @Test
    void schedulerUsesTheSameNestedPropertyPathAsApplicationConfiguration() throws Exception {
        ConditionalOnProperty condition = YunshuUnknownReconciliationScheduler.class
            .getAnnotation(ConditionalOnProperty.class);
        Scheduled schedule = YunshuUnknownReconciliationScheduler.class
            .getDeclaredMethod("reconcileUnknown")
            .getAnnotation(Scheduled.class);
        String yaml = new ClassPathResource("application.yml")
            .getContentAsString(StandardCharsets.UTF_8);

        assertEquals("cpclaw.yunshu-skill.reconciliation.enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertEquals("${cpclaw.yunshu-skill.reconciliation.fixed-delay-ms:30000}", schedule.fixedDelayString());
        assertTrue(yaml.contains("enabled: ${CPCLAW_YUNSHU_RECONCILIATION_ENABLED:false}"));
        assertTrue(yaml.contains("fixed-delay-ms: ${CPCLAW_YUNSHU_RECONCILIATION_FIXED_DELAY_MS:30000}"));
        assertTrue(yaml.contains("instance-id: ${CPCLAW_YUNSHU_RECONCILIATION_INSTANCE_ID:local}"));
        assertTrue(yaml.contains("lease-duration-ms: ${CPCLAW_YUNSHU_RECONCILIATION_LEASE_DURATION_MS:120000}"));
    }
}
