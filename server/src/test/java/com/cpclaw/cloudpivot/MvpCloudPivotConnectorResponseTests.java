package com.cpclaw.cloudpivot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * A CloudPivot call can answer HTTP 200 while rejecting the business request.
 * These cases pin the decision that guards every write from being reported as
 * successful when the body says otherwise.
 */
class MvpCloudPivotConnectorResponseTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void explicitFailureFlagIsDetected() throws Exception {
        assertTrue(failure("{\"success\":false,\"message\":\"validation failed\"}"));
        assertTrue(failure("{\"ok\":0}"));
        assertTrue(failure("{\"success\":\"false\"}"));
    }

    @Test
    void errorCodeIsDetected() throws Exception {
        assertTrue(failure("{\"errcode\":400,\"errmsg\":\"字段校验失败\"}"));
        assertTrue(failure("{\"code\":500}"));
    }

    @Test
    void failureStatusIsDetected() throws Exception {
        assertTrue(failure("{\"status\":\"error\"}"));
        assertTrue(failure("{\"status\":\"FAILED\"}"));
        assertTrue(failure("{\"resultStatus\":\"rejected\"}"));
    }

    @Test
    void emptyBodyIsDetected() {
        assertTrue(MvpCloudPivotConnector.businessFailureReason(null).isPresent());
    }

    @Test
    void successfulBodiesAreNotFlagged() throws Exception {
        assertTrue(MvpCloudPivotConnector.businessFailureReason(node("{\"success\":true,\"bizObjectId\":\"abc\"}")).isEmpty());
        assertTrue(MvpCloudPivotConnector.businessFailureReason(node("{\"errcode\":0,\"data\":{\"id\":\"abc\"}}")).isEmpty());
        assertTrue(MvpCloudPivotConnector.businessFailureReason(node("{\"status\":200,\"data\":{}}")).isEmpty());
        assertTrue(MvpCloudPivotConnector.businessFailureReason(node("{\"status\":\"ok\"}")).isEmpty());
        assertTrue(MvpCloudPivotConnector.businessFailureReason(node("{\"data\":{\"id\":\"abc\"}}")).isEmpty());
    }

    private boolean failure(String json) throws Exception {
        return MvpCloudPivotConnector.businessFailureReason(node(json)).isPresent();
    }

    private JsonNode node(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
