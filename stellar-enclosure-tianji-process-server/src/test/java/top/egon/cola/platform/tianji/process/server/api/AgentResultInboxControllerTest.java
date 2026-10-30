package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxProperties;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentResultInboxControllerTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AgentResultInbox inbox = mock(AgentResultInbox.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessActor producer = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-result-service", "https://idp.example/20");
    private final AgentTerminalEvent event = event();

    @Test
    void returns_only_the_durable_receipt_after_authorizing_a_service_identity() throws Exception {
        var receipt = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(producer);
        when(inbox.receive(producer, event)).thenReturn(receipt);
        var mvc = mvc();
        mvc.perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("RECEIVED")).andExpect(jsonPath("$.eventId").value(event.eventId()))
                .andExpect(jsonPath("$.eventFingerprint").value(event.fingerprint()));
        verify(inbox).receive(producer, event);
        when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "user", "https://idp.example/20"));
        mvc.perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isForbidden());
        verify(inbox, times(1)).receive(producer, event);
    }

    @Test
    void rejects_unknown_fields_coercion_duplicates_trailing_content_and_invalid_hashes() throws Exception {
        String body = mapper.writeValueAsString(event);
        var mvc = mvc();
        for (String invalid : List.of(
                body.replace("\"tenantId\":20", "\"tenantId\":\"20\""), body.replace("\"tenantId\":20", "\"tenantId\":20.0"),
                body.replace("\"schemaVersion\":1", "\"schemaVersion\":null"), body.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                body.replace("\"status\":\"COMPLETED\"", "\"status\":5"), body.replace("\"finishedAt\":\"2026-10-30T00:00:00Z\"", "\"finishedAt\":123"),
                body.replace("\"sourceId\":\"process-east\"", "\"sourceId\":\"process-east\",\"sourceId\":\"other\""),
                body.replace("\"scopeType\":\"BPMN\"", "\"scopeType\":0"), body.replace("\"eventId\":\"" + event.eventId() + "\"", "\"eventId\":\"" + "d".repeat(64) + "\""),
                body.substring(0, body.length() - 1) + ",\"executionId\":\"forged\"}", body + " {}")) {
            assertNotEquals(body, invalid);
            mvc.perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(inbox, access);
    }

    @Test
    void bounds_fixed_and_streamed_bodies_before_deserialization() throws Exception {
        byte[] oversized = new byte[ProcessRequestBodyAdvice.MAX_AGENT_RESULT_BYTES + 1];
        mvc().perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(oversized); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class, () -> new ProcessRequestBodyAdvice().beforeBodyRead(streamed, null, AgentTerminalEvent.class, null));
        verifyNoInteractions(inbox, access);
    }

    @Test
    void sanitizes_conflicts_and_authorization_dependency_failure() throws Exception {
        when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(producer);
        when(inbox.receive(producer, event)).thenThrow(new ProcessConflictException());
        mvc().perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
        when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenThrow(new Rbac3AuthorizationClient.AuthorizationUnavailableException("private-details"));
        String body = mvc().perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("private-details"));
    }

    @Test
    void producer_configuration_derives_exact_issuer_ownership_and_bounds_retention() {
        var entry = new AgentResultInboxProperties.Producer(20, "https://idp.example/20", "agent-result-service", "agent-east", "process-east",
                "https://idp.example/20", "process-worker");
        var properties = new AgentResultInboxProperties(List.of(entry), null);
        assertEquals(Duration.ofDays(1), properties.pendingRetention());
        assertEquals(producer, properties.registrations().getFirst().producer());
        assertThrows(IllegalArgumentException.class, () -> new AgentResultInboxProperties(List.of(entry), Duration.ofSeconds(59)));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultInboxProperties.Producer(20, "http://idp.example/20", "agent-result-service",
                "agent-east", "process-east", "https://idp.example/20", "process-worker"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new AgentResultInboxController(inbox, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }
    private static AgentTerminalEvent event() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "run-one", 3),
                20, "b".repeat(64), "process-worker", "intent", link, "research", 2, "run-one", 3, AgentRunStatusEnum.COMPLETED,
                NOW, "c".repeat(64), null, "UNKNOWN");
    }
}
