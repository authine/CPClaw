package com.cpclaw.yunshu.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class YunshuProtocolTests {
    @Test
    void startHasStrictCanonicalRequirements() {
        CanonicalTaskCommand command = new CanonicalTaskCommand(
            YunshuProtocol.VERSION, YunshuProtocol.Operation.start, "", "新增线索", Map.of(), Map.of(), "turn-1", null, "");

        assertTrue(command.toRequest().isValid());
        assertEquals(YunshuProtocol.VERSION, command.protocolVersion());
    }

    @Test
    void continuationRequiresTaskStateAndRequestIdentity() {
        CanonicalTaskCommand command = new CanonicalTaskCommand(
            YunshuProtocol.VERSION, YunshuProtocol.Operation.input, "task-1", "", Map.of("questionId", "q1"), Map.of(), "", null, "ticket");

        assertFalse(command.toRequest().isValid());
        assertEquals("EXPECTED_STATE_VERSION_REQUIRED", command.toRequest().validationError());
    }

    @Test
    void guardDoesNotExposeLegacyCpclawConfirmationPath() {
        ContractGuardYunshuSkillGateway gateway = new ContractGuardYunshuSkillGateway();
        YunshuTaskEnvelope envelope = gateway.execute(new YunshuTaskRequest(
            YunshuProtocol.VERSION, YunshuProtocol.Operation.start, "", "新增线索", Map.of(), Map.of(), "turn-1", null, ""),
            new YunshuHostContext("tenant", "subject", "workbuddy", Map.of()));

        assertEquals(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED, envelope.error().code());
        assertEquals(YunshuProtocol.HostActionType.report_failure, envelope.hostAction().type());
        assertFalse(envelope.hostAction().type().name().contains("cpclaw"));
    }
}
