package top.egon.cola.platform.tianji.process.server.api;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxProperties;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentResultInboxControllerTest {
    private static final String REMOTE_TRACE = "1".repeat(32);
    private static final String REMOTE_SPAN = "2".repeat(16);
    private static final String LINK = "00-" + REMOTE_TRACE + "-" + REMOTE_SPAN + "-01";
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AgentResultInbox inbox = mock(AgentResultInbox.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessActor producer = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-result-service", "https://idp.example/20");
    private final AgentTerminalEvent event = event();

    @Test
    void accepts_exact_legacy_and_v2_terminal_events_on_the_same_private_route() throws Exception {
        when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(producer);
        var oldReceipt = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        when(inbox.receive(producer, event)).thenReturn(oldReceipt);
        var v2 = typedEvent();
        var typedReceipt = new AgentResultReceipt(v2.eventId(), v2.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        when(inbox.receiveV2(producer, v2)).thenReturn(typedReceipt);
        var mvc = mvc();
        mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(event)))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.eventFingerprint").value(event.fingerprint()));
        mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON).content(v2.canonicalBytes()))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.eventId").value(v2.eventId())).andExpect(jsonPath("$.eventFingerprint").value(v2.fingerprint()))
                .andExpect(jsonPath("$.state").value("RECEIVED")).andExpect(jsonPath("$.receivedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.length()").value(4));
        verify(inbox).receive(producer, event);
        verify(inbox).receiveV2(producer, v2);
        verifyNoMoreInteractions(inbox);
    }

    @Test
    void rejects_mixed_or_open_v2_envelopes_before_authentication_or_storage() throws Exception {
        var v2 = typedEvent();
        String wire = new String(v2.canonicalBytes(), StandardCharsets.UTF_8);
        var mvc = mvc();
        for (String invalid : List.of(
                wire.replace("\"schemaVersion\":2,", ""),
                wire.replace("\"schemaVersion\":2", "\"schemaVersion\":3"),
                wire.replace("\"schemaVersion\":2", "\"schemaVersion\":2.0"),
                wire.replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"schemaVersion\":2"),
                wire.replace("\"schemaVersion\":2", "\"schemaVersion\":1"),
                wire.replace("\"artifactRef\":null,", ""),
                wire.replace("\"errorCode\":null,", ""),
                wire.replace("\"output\":{\"approved\":true},", ""),
                wire.replace("\"approved\":true", "\"approved\":true,\"approved\":true"),
                wire.replace("\"scopeType\":\"BPMN\"", "\"scopeType\":\"CMMN\""),
                wire.replace("\"finishedAt\":\"" + NOW + "\"", "\"finishedAt\":\"2026-10-30T00:00:00.0000001Z\""),
                wire.replace("\"finishedAt\":\"" + NOW + "\"", "\"finishedAt\":\"2026-10-30T08:00:00+08:00\""),
                wire.replace(v2.resultSha256(), "1".repeat(64)),
                wire.substring(0, wire.length() - 1) + ",\"unregistered\":true}",
                wire + " {}")) {
            assertNotEquals(wire, invalid);
            mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(inbox, access);
    }

    @Test
    void bounds_actual_v2_request_and_inline_output_separately() throws Exception {
        var v2 = typedEvent();
        String wire = new String(v2.canonicalBytes(), StandardCharsets.UTF_8);
        String largeEnvelope = wire + " ".repeat(16 * 1024 + 1 - wire.length());
        String largeInline = wire.replace("{\"approved\":true}", "{\"approved\":\"" + "a".repeat(8 * 1024) + "\"}");
        assertTrue(largeInline.getBytes(StandardCharsets.UTF_8).length < 16 * 1024);
        var mvc = mvc();
        mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON).content(largeEnvelope))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON).content(largeInline))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(inbox, access);
    }

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

    @Test
    void records_one_remote_link_only_after_durable_receive_and_keeps_the_local_parent() throws Exception {
        var collected = new CollectingSpans();
        try (var provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).addSpanProcessor(collected).build()) {
            var observation = AgentResultInboxReceiveObservation.enabled(() -> telemetry(provider));
            var receipt = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
            when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(producer);
            when(inbox.receive(producer, event)).thenAnswer(ignored -> {
                assertTrue(collected.spans.isEmpty());
                return receipt;
            });
            var parent = provider.get("local-ps-parent").spanBuilder("existing PS request").startSpan();
            try (var scope = parent.makeCurrent()) {
                performLinked(mvc(new AgentResultInboxController(inbox, access, observation)), LINK)
                        .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(header().doesNotExist(AgentResultTraceLinkCarrier.HEADER))
                        .andExpect(jsonPath("$.state").value("RECEIVED"))
                        .andExpect(jsonPath("$.eventFingerprint").value(event.fingerprint()));
                assertEquals(parent.getSpanContext(), Span.current().getSpanContext());
                assertEquals(1, collected.spans.size());
                var span = collected.spans.getFirst();
                assertEquals("tianji.process.agent.result.receive", span.getName());
                assertEquals(SpanKind.CONSUMER, span.getKind());
                assertEquals(parent.getSpanContext().getTraceId(), span.getSpanContext().getTraceId());
                assertEquals(parent.getSpanContext().getSpanId(), span.getParentSpanContext().getSpanId());
                assertTrue(span.getAttributes().isEmpty());
                assertTrue(span.getEvents().isEmpty());
                assertEquals(1, span.getLinks().size());
                var link = span.getLinks().getFirst();
                assertEquals(REMOTE_TRACE, link.getSpanContext().getTraceId());
                assertEquals(REMOTE_SPAN, link.getSpanContext().getSpanId());
                assertTrue(link.getSpanContext().isRemote());
                assertTrue(link.getSpanContext().isSampled());
                assertTrue(link.getAttributes().isEmpty());
            } finally {
                parent.end();
            }
            verify(inbox).receive(producer, event);
        }
    }

    @Test
    void ignores_disabled_malformed_duplicate_unsampled_or_unavailable_links_and_failed_receipts() throws Exception {
        var collected = new CollectingSpans();
        var lookups = new AtomicInteger();
        try (var provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).addSpanProcessor(collected).build()) {
            var observation = AgentResultInboxReceiveObservation.enabled(() -> {
                lookups.incrementAndGet();
                return telemetry(provider);
            });
            when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(producer);
            when(inbox.receive(producer, event)).thenReturn(new AgentResultReceipt(
                    event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW));
            performLinked(mvc(), LINK).andExpect(status().isAccepted());
            var mvc = mvc(new AgentResultInboxController(inbox, access, observation));
            for (String invalid : List.of(LINK + ", " + LINK, " " + LINK, LINK.substring(0, 53) + "00",
                    "00-" + "0".repeat(32) + "-" + REMOTE_SPAN + "-01", "00-A" + LINK.substring(4))) {
                performLinked(mvc, invalid).andExpect(status().isAccepted());
            }
            performLinked(mvc, LINK, LINK).andExpect(status().isAccepted());
            mvc.perform(post(AgentResultTraceLinkCarrier.PATH).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(event))).andExpect(status().isAccepted());
            mvc.perform(post("/internal" + AgentResultTraceLinkCarrier.PATH).contextPath("/internal")
                    .contentType(MediaType.APPLICATION_JSON).header(AgentResultTraceLinkCarrier.HEADER, LINK)
                    .content(mapper.writeValueAsBytes(event)))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("RECEIVED"));
            assertObservationSkipsBeforeReadingHeader(observation, "GET", AgentResultTraceLinkCarrier.PATH);
            assertObservationSkipsBeforeReadingHeader(observation, "POST", "/%61pi/tianji/v1/agent-result-inbox");
            assertEquals(0, lookups.get());

            for (var unavailable : List.of(AgentResultInboxReceiveObservation.enabled(() -> null),
                    AgentResultInboxReceiveObservation.enabled(() -> { throw new IllegalStateException("private detail"); }))) {
                performLinked(mvc(new AgentResultInboxController(inbox, access, unavailable)), LINK)
                        .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("RECEIVED"));
            }
            when(inbox.receive(producer, event)).thenThrow(new ProcessConflictException());
            performLinked(mvc, LINK).andExpect(status().isConflict());
            when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(
                    ProcessActor.verified(20, ProcessActor.SubjectType.USER, "user", "https://idp.example/20"));
            performLinked(mvc, LINK).andExpect(status().isForbidden());
            assertEquals(0, lookups.get());
            assertTrue(collected.spans.isEmpty());
        }
    }

    private static void assertObservationSkipsBeforeReadingHeader(
            AgentResultInboxReceiveObservation observation, String method, String rawUri) {
        var request = spy(new MockHttpServletRequest(method, rawUri));
        request.addHeader(AgentResultTraceLinkCarrier.HEADER, LINK);
        var previous = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            observation.afterReceive();
            verify(request, never()).getHeaders(AgentResultTraceLinkCarrier.HEADER);
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    private org.springframework.test.web.servlet.ResultActions performLinked(
            org.springframework.test.web.servlet.MockMvc mvc, String... values) throws Exception {
        return mvc.perform(post("/api/tianji/v1/agent-result-inbox").contentType(MediaType.APPLICATION_JSON)
                .header(AgentResultTraceLinkCarrier.HEADER, (Object[]) values).content(mapper.writeValueAsBytes(event)));
    }

    private static OpenTelemetry telemetry(TracerProvider provider) {
        return new OpenTelemetry() {
            @Override public TracerProvider getTracerProvider() { return provider; }
            @Override public ContextPropagators getPropagators() { return ContextPropagators.noop(); }
        };
    }

    private static final class CollectingSpans implements SpanProcessor {
        private final List<SpanData> spans = new ArrayList<>();
        @Override public void onStart(Context parentContext, ReadWriteSpan span) { }
        @Override public boolean isStartRequired() { return false; }
        @Override public void onEnd(ReadableSpan span) { spans.add(span.toSpanData()); }
        @Override public boolean isEndRequired() { return true; }
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return mvc(new AgentResultInboxController(inbox, access));
    }

    private org.springframework.test.web.servlet.MockMvc mvc(AgentResultInboxController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }
    private static AgentTerminalEvent event() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "run-one", 3),
                20, "b".repeat(64), "process-worker", "intent", link, "research", 2, "run-one", 3, AgentRunStatusEnum.COMPLETED,
                NOW, "c".repeat(64), null, "UNKNOWN");
    }

    private static AgentTerminalEventV2 typedEvent() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "typed-occurrence", "a".repeat(64), NOW.plusSeconds(60));
        var status = AgentRunStatusEnum.COMPLETED;
        var outcome = AgentTerminalEventV2.SideEffectOutcomeEnum.NONE;
        Map<String, Object> output = Map.of("approved", true);
        String id = AgentTerminalEventV2.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "typed-run", 3);
        String result = AgentTerminalEventV2.resultSha256(status, output, null, null, outcome);
        return new AgentTerminalEventV2(2, "agent-east", id, 20, "b".repeat(64), "process-worker", "typed-intent", link,
                "research", 2, "typed-run", 3, "typed-node", "d".repeat(64), "e".repeat(64), "f".repeat(64),
                status, NOW, result, output, null, null, outcome);
    }
}
