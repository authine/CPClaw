package com.cpclaw.yunshu.protocol;

import com.cpclaw.identity.PrincipalContextService;
import com.cpclaw.mcp.McpRequestAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class YunshuSkillHttpControllerTests {
    private static final String SHARED_SECRET = "skill-test-secret";
    @Test
    void canonicalHttpCommandNeverFallsBackToLegacyCpclawFlow() throws Exception {
        PrincipalContextService principals = new PrincipalContextService(
            "huangj", "黄杰", "18124691161", "default", true, "default-only");
        YunshuHostContextResolver cpclawResolver = new CpClawServerHostContextResolver(principals);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new YunshuSkillHttpController(
                new DefaultYunshuSkillGateway(new ContractGuardYunshuSkillGateway()),
                java.util.List.of(cpclawResolver))).build();

        mockMvc.perform(post("/api/yunshu-skill/tasks/command")
                .header(CpClawServerHostContextResolver.HOST_HEADER, CpClawServerHostContextResolver.HOST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocolVersion\":\"yunshu-skill/1.0\",\"operation\":\"start\",\"goal\":\"新增线索\",\"clientRequestId\":\"turn-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.protocolVersion").value("yunshu-skill/1.0"))
            .andExpect(jsonPath("$.error.code").value("CONTRACT_UNVERIFIED"))
            .andExpect(jsonPath("$.hostAction.type").value("report_failure"));
    }

    @Test
    void externalHostCannotUseTheServerOwnedCpclawPrincipal() throws Exception {
        PrincipalContextService principals = new PrincipalContextService(
            "huangj", "黄杰", "18124691161", "default", true, "default-only");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new YunshuSkillHttpController(
                new DefaultYunshuSkillGateway(new ContractGuardYunshuSkillGateway()),
                java.util.List.of(new CpClawServerHostContextResolver(principals)))).build();

        mockMvc.perform(post("/api/yunshu-skill/tasks/command")
                .header(CpClawServerHostContextResolver.HOST_HEADER, "workbuddy")
                .header("X-Yunshu-External-Principal", "someone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocolVersion\":\"yunshu-skill/1.0\",\"operation\":\"start\",\"goal\":\"新增线索\",\"clientRequestId\":\"turn-1\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void verifiedExternalAdapterCanSupplyAHostBoundContext() throws Exception {
        YunshuHostContextResolver verifiedWorkBuddy = new YunshuHostContextResolver() {
            @Override public String hostId() { return "workbuddy"; }
            @Override public Optional<YunshuHostContext> resolve(HttpServletRequest request) {
                return "Bearer verified".equals(request.getHeader("Authorization"))
                    ? Optional.of(new YunshuHostContext("tenant-a", "user-a", hostId(), java.util.Map.of("fieldPatch", true)))
                    : Optional.empty();
            }
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new YunshuSkillHttpController(
                new DefaultYunshuSkillGateway(new ContractGuardYunshuSkillGateway()),
                java.util.List.of(verifiedWorkBuddy))).build();

        mockMvc.perform(post("/api/yunshu-skill/tasks/command")
                .header(CpClawServerHostContextResolver.HOST_HEADER, "workbuddy")
                .header("Authorization", "Bearer verified")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocolVersion\":\"yunshu-skill/1.0\",\"operation\":\"start\",\"goal\":\"新增线索\",\"clientRequestId\":\"turn-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value("CONTRACT_UNVERIFIED"));
    }

    @Test
    void builtInHmacResolverAcceptsDirectSkillHostAndRejectsUnsignedRequest() throws Exception {
        PrincipalContextService principals = new PrincipalContextService(
            "huangj", "黄杰", "18124691161", "default", false, "default-only");
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", SHARED_SECRET, 300);
        HmacYunshuHostContextResolver resolver = new HmacYunshuHostContextResolver(authenticator, "workbuddy");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new YunshuSkillHttpController(
                new DefaultYunshuSkillGateway(new ContractGuardYunshuSkillGateway()),
                java.util.List.of(resolver))).build();
        String body = "{\"protocolVersion\":\"yunshu-skill/1.0\",\"operation\":\"start\",\"goal\":\"新增线索\",\"clientRequestId\":\"turn-1\"}";

        mockMvc.perform(post("/api/yunshu-skill/tasks/command")
                .header(CpClawServerHostContextResolver.HOST_HEADER, "workbuddy")
                .header(HmacYunshuHostContextResolver.TENANT_HEADER, "tenant-a")
                .header(HmacYunshuHostContextResolver.PRINCIPAL_HEADER, "workbuddy-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        long timestamp = java.time.Instant.now().getEpochSecond();
        String signature = authenticator.signature("workbuddy", "tenant-a", "workbuddy-user", timestamp);
        mockMvc.perform(post("/api/yunshu-skill/tasks/command")
                .header(CpClawServerHostContextResolver.HOST_HEADER, "workbuddy")
                .header(HmacYunshuHostContextResolver.TENANT_HEADER, "tenant-a")
                .header(HmacYunshuHostContextResolver.PRINCIPAL_HEADER, "workbuddy-user")
                .header(HmacYunshuHostContextResolver.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(HmacYunshuHostContextResolver.SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value("CONTRACT_UNVERIFIED"));
    }
}
