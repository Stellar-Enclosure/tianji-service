package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessAgentLocalWorkerRouteEvidenceTest {
    private static final long TENANT = 20;
    private static final String ISSUER = "https://idp.example/20";
    private static final String TOPIC = "agent-start";
    private static final String VERSION = "1";
    private static final URI AGENT = URI.create("https://agent.example/");
    private static final URI RESOURCE = URI.create("https://api.example/agent");
    private static final String WORKER = "worker-a";
    private static final String OTHER = "worker-b";
    private static final String APP_ID = "process-app";
    private static final Set<String> RUN_TARGETS = Set.of("agentRunId", "linkedRunId");

    @Test
    void exact_enabled_local_route_returns_binding_and_digest_without_token_authorization() {
        var binding = binding(WORKER, RESOURCE);
        var fixture = fixture(true, List.of(binding), List.of(registration(WORKER)), APP_ID);

        var result = fixture.build().find(TENANT, TOPIC, VERSION).orElseThrow();
        assertEquals(binding.dispatch(), result.binding());
        assertTrue(result.registrationSha256().matches("[0-9a-f]{64}"));
        assertFalse(result.toString().contains(WORKER));
        assertFalse(result.toString().contains(RESOURCE.toString()));
        assertTrue(fixture.build().find(21, TOPIC, VERSION).isEmpty());
        assertTrue(fixture.build().find(TENANT, TOPIC, "2").isEmpty());
        assertTrue(fixture.build().find(TENANT, "another-topic", VERSION).isEmpty());
        verify(fixture.oauth(), never()).authorize(any());
        verify(fixture.oauth(), never()).authorizeBounded(any(), anyString(), any());
    }

    @Test
    void two_valid_different_worker_actors_on_the_same_native_tuple_are_ambiguous() {
        var fixture = fixture(true, List.of(binding(WORKER, RESOURCE), binding(OTHER, RESOURCE)),
                List.of(registration(WORKER), registration(OTHER)), APP_ID);

        assertTrue(fixture.build().find(TENANT, TOPIC, VERSION).isEmpty());
    }

    @Test
    void disabled_dispatch_rejects_before_oauth_and_invalid_existing_bridge_validation_propagates() {
        var disabled = fixture(false, List.of(binding(WORKER, RESOURCE)), List.of(registration(WORKER)), APP_ID);
        assertThrows(ProcessResourceNotFoundException.class, disabled::build);
        verifyNoInteractions(disabled.oauth());

        var invalid = fixture(true, List.of(binding(WORKER, RESOURCE)),
                List.of(registration(WORKER, Duration.ofSeconds(10), Duration.ofSeconds(2), 2, RUN_TARGETS, false)), APP_ID);
        assertThrows(IllegalStateException.class, invalid::build);
        verifyNoInteractions(invalid.oauth());

        var oauthMismatch = fixture(true, List.of(binding(WORKER, RESOURCE)), List.of(registration(WORKER)), APP_ID);
        doAnswer(invocation -> ServiceAuthorizationKey.from(invocation.getArgument(0), ISSUER, OTHER, 1))
                .when(oauthMismatch.oauth()).authorizationKey(any());
        assertThrows(IllegalStateException.class, oauthMismatch::build);
        verify(oauthMismatch.oauth(), never()).authorizeBounded(any(), anyString(), any());
    }

    @Test
    void registration_digest_ignores_collection_order_and_binds_registration_and_oauth_fields() {
        String canonical = digest(binding(WORKER, RESOURCE), registration(WORKER), APP_ID);
        assertEquals("5c85e72f54d0730fd8f7a2990c5300b173aac743c3b17e4fad23d5f4fef3553c", canonical);
        assertEquals(canonical, digest(binding(WORKER, RESOURCE),
                registration(WORKER, Duration.ofSeconds(15), Duration.ofSeconds(2), 2,
                        new LinkedHashSet<>(List.of("linkedRunId", "agentRunId")), true), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, RESOURCE),
                registration(WORKER, Duration.ofSeconds(16), Duration.ofSeconds(2), 2, RUN_TARGETS, false), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, RESOURCE),
                registration(WORKER, Duration.ofSeconds(15), Duration.ofSeconds(3), 2, RUN_TARGETS, false), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, RESOURCE),
                registration(WORKER, Duration.ofSeconds(15), Duration.ofSeconds(2), 3, RUN_TARGETS, false), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, RESOURCE),
                registration(WORKER, Duration.ofSeconds(15), Duration.ofSeconds(2), 2, Set.of("agentRunId"), false), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, URI.create("https://api.example/other")), registration(WORKER), APP_ID));
        assertNotEquals(canonical, digest(binding(WORKER, RESOURCE), registration(WORKER), "different-app"));
        assertNotEquals(canonical, digest(binding(OTHER, RESOURCE), registration(OTHER), APP_ID));
    }

    private static String digest(ProcessAgentBridgeProperties.Binding binding, WorkerTopicRegistry.Registration registration,
                                 String appId) {
        return fixture(true, List.of(binding), List.of(registration), appId).build().find(TENANT, TOPIC, VERSION)
                .orElseThrow().registrationSha256();
    }

    private static Fixture fixture(boolean dispatchEnabled, List<ProcessAgentBridgeProperties.Binding> bindings,
                                   List<WorkerTopicRegistry.Registration> registrations, String appId) {
        var settings = new ProcessAgentBridgeProperties(bindings, dispatchEnabled, 0, 0, 0,
                null, null, null, null, null, 0);
        var topics = new WorkerTopicRegistry(registrations);
        var inbox = new AgentResultInboxProperties(bindings.stream().map(binding -> new AgentResultInboxProperties.Producer(
                TENANT, ISSUER, "agent", "agent-east", binding.sourceId(), ISSUER, binding.subjectId())).toList(), null);
        var producers = new AgentResultProducerRegistry(inbox.registrations());
        var starter = new IdpStarterProperties();
        starter.getServiceClient().setAppId(appId);
        starter.getServiceClient().setRegistrationId("client-" + WORKER);
        var issuer = new IdpStarterProperties.TrustedIssuerSettings();
        issuer.setIssuer(ISSUER);
        issuer.setJwksUri(ISSUER + "/jwks");
        issuer.setRealmKey("tenant:" + TENANT);
        issuer.setRealmType("TENANT");
        issuer.setTenantId(Long.toString(TENANT));
        issuer.setStatus("ACTIVE");
        issuer.setTrustVersion(1);
        starter.setTrustedIssuers(Map.of("tenant", issuer));
        var issuers = new TrustedIssuerRegistry(starter);
        var oauth = mock(IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(invocation -> {
            IdpServiceTokenRequest request = invocation.getArgument(0);
            return ServiceAuthorizationKey.from(request, ISSUER, request.registrationId().substring("client-".length()), 1);
        });
        return new Fixture(settings, topics, producers, inbox, starter, issuers, oauth);
    }

    private static ProcessAgentBridgeProperties.Binding binding(String subject, URI resource) {
        return new ProcessAgentBridgeProperties.Binding(TENANT, URI.create(ISSUER), subject, "source-" + subject, TOPIC, VERSION,
                "assistant", 1, "a".repeat(64), "Handle the supplied input.", null, AGENT, resource,
                "client-" + subject, null, 0);
    }

    private static WorkerTopicRegistry.Registration registration(String subject) {
        return registration(subject, Duration.ofSeconds(15), Duration.ofSeconds(2), 2, RUN_TARGETS, false);
    }

    private static WorkerTopicRegistry.Registration registration(String subject, Duration lease, Duration retry,
            int concurrent, Set<String> outputTargets, boolean reverseInputOrder) {
        Map<String, ValueType> inputs = new LinkedHashMap<>();
        if (reverseInputOrder) {
            inputs.put("account", ValueType.STRING);
            inputs.put("amount", ValueType.INTEGER);
        } else {
            inputs.put("amount", ValueType.INTEGER);
            inputs.put("account", ValueType.STRING);
        }
        return new WorkerTopicRegistry.Registration(TENANT, ISSUER, subject, TOPIC, VERSION, inputs,
                Map.of("runId", ValueType.STRING), Map.of("account", Set.of("accountId"), "amount", Set.of("amount")),
                Map.of("runId", outputTargets), concurrent, lease, retry, WorkerEffect.EXTERNAL_IDEMPOTENT, true);
    }

    private record Fixture(ProcessAgentBridgeProperties settings, WorkerTopicRegistry topics,
                           AgentResultProducerRegistry producers, AgentResultInboxProperties inbox,
                           IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        private ProcessAgentLocalWorkerRouteEvidence build() {
            return ProcessAgentLocalWorkerRouteEvidence.validate(settings, topics, producers, inbox, starter, issuers, oauth);
        }
    }
}
