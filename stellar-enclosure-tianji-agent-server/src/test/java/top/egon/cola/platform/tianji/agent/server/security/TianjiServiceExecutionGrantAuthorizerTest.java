package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiServiceExecutionGrantAuthorizerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final String ID = "8" + "1".repeat(62);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toASCIIString());
    private final AgentRunAuthorization.Request request = new AgentRunAuthorization.Request(actor, RUN, "review", 1,
            "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, NOW.plusSeconds(600));
    private final AgentServiceExecutionGrant.Subject subject = new AgentServiceExecutionGrant.Subject("worker", "process-app", "platform",
            "process", "test", URI.create("https://api.example/process"));
    private final TianjiServiceExecutionBindings.Bound binding = new TianjiServiceExecutionBindings.Bound(ISSUER, subject);
    private final AgentProcessLink link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-instance",
            "activity", "a".repeat(64), NOW.plusSeconds(60));
    private final AgentServiceExecutionGrant.Ticket ticket = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:sealed", NOW.plusSeconds(60));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentServiceExecutionGrantStore store = mock(AgentServiceExecutionGrantStore.class);
    private final TianjiServiceExecutionBindings bindings = mock(TianjiServiceExecutionBindings.class);
    private final GetAgentRunQuery query = new GetAgentRunQuery(RUN);
    private final AgentRunDTO run = new AgentRunDTO(RUN, request.definitionKey(), request.definitionVersion(), "session-1",
            AgentRunStatusEnum.RUNNING, null, false, 1, 2, 3, request.deadlineAt(), NOW.minusSeconds(1), null);
    private final AgentLedgerService.LinkedRunEvidenceVO evidence = new AgentLedgerService.LinkedRunEvidenceVO(run, link, request);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiServiceExecutionGrantAuthorizer authorizer;

    @BeforeEach void setUp() {
        when(store.usesLedger(ledger)).thenReturn(true);
        when(bindings.usesIssuerRegistry(issuers)).thenReturn(true);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        when(ledger.linkedRunEvidence(actor, query)).thenReturn(evidence);
        when(bindings.resolve(request, link)).thenReturn(binding);
        when(store.current(request, RESOURCE, subject)).thenReturn(Optional.of(ticket));
        when(ledger.databaseNow()).thenReturn(NOW);
        when(client.decideService(any(), any())).thenReturn(decision(NOW.plusSeconds(30)));
        authorizer = create(issuers, Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "21", client)));
    }

    @Test void every_evaluation_checks_the_service_tuple_and_current_bound_ticket() {
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.evaluate(request));
        assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.evaluate(request));
        var sent = org.mockito.ArgumentCaptor.forClass(ServiceExecutionAuthorizationContract.Request.class);
        var expected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.ServiceExpectation.class);
        verify(client, times(2)).decideService(sent.capture(), expected.capture());
        assertEquals("tianji", sent.getValue().systemCode()); assertEquals(ID, sent.getValue().grantId());
        assertEquals(ticket.ciphertext(), sent.getValue().ciphertext());
        assertEquals("worker", expected.getValue().intent().subject());
        assertEquals(subject.resourceUri().toASCIIString(), expected.getValue().subjectSource().resourceUri());
        assertEquals(AgentServiceExecutionGrant.fingerprint(request, RESOURCE, subject), expected.getValue().intent().bindingSha256());
        assertEquals(request.deadlineAt(), expected.getValue().intent().deadlineAt());
        verify(store, times(4)).current(request, RESOURCE, subject);
        verify(ledger, times(4)).linkedRunEvidence(actor, query);
        verify(ledger, never()).executionMaterial(any(), any());
    }

    @Test void denial_is_definitive_only_when_the_same_fence_ticket_and_trust_remain_current() {
        doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(client).decideService(any(), any());
        assertEquals(AgentRunAuthorization.Decision.DENY, authorizer.evaluate(request));
        var changed = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:other", NOW.plusSeconds(60));
        when(store.current(request, RESOURCE, subject)).thenReturn(Optional.of(ticket), Optional.of(changed));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
        when(store.current(request, RESOURCE, subject)).thenReturn(Optional.of(ticket));
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust), Optional.empty());
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
    }

    @Test void missing_expired_changed_or_unbound_evidence_never_allows_execution() {
        when(store.current(request, RESOURCE, subject)).thenReturn(Optional.empty());
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
        verifyNoInteractions(client);
        when(store.current(request, RESOURCE, subject)).thenReturn(Optional.of(ticket));
        when(client.decideService(any(), any())).thenReturn(decision(NOW));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
        when(client.decideService(any(), any())).thenReturn(decision(NOW.plusSeconds(61)));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
        doThrow(new IllegalStateException("private detail")).when(store).current(request, RESOURCE, subject);
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
    }

    @Test void exact_source_uri_and_immutable_snapshot_are_rechecked_after_remote_decision() {
        var changedSubject = new AgentServiceExecutionGrant.Subject("worker", "process-app", "platform", "process", "test",
                URI.create("https://API.example/process"));
        when(bindings.resolve(request, link)).thenReturn(binding, new TianjiServiceExecutionBindings.Bound(ISSUER, changedSubject));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));
        when(bindings.resolve(request, link)).thenReturn(binding);
        var changed = new AgentRunAuthorization.Request(actor, RUN, "review", 1, "a".repeat(64), "b".repeat(64),
                "d".repeat(64), "policy-v1", true, request.deadlineAt());
        when(ledger.linkedRunEvidence(actor, query))
                .thenReturn(evidence, new AgentLedgerService.LinkedRunEvidenceVO(run, link, changed));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));

        var changedLink = new AgentProcessLink(link.sourceId(), link.scopeType(), link.scopeInstanceId(),
                "different-activity", link.definitionContentSha256(), link.dispatchNotAfter());
        when(ledger.linkedRunEvidence(actor, query))
                .thenReturn(evidence, new AgentLedgerService.LinkedRunEvidenceVO(run, changedLink, request));
        assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(request));

        verify(client, times(3)).decideService(any(), any());
        verify(bindings, never()).resolve(request, changedLink);
    }

    @Test void user_legacy_or_mismatched_configuration_does_not_gain_service_authority() {
        for (var wrong : java.util.List.of(new TrustedAgentActor(21, TrustedAgentActor.SubjectType.SERVICE, "worker"),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "worker", ISSUER.toASCIIString()))) {
            var changed = new AgentRunAuthorization.Request(wrong, RUN, "review", 1, "a".repeat(64), "b".repeat(64),
                    "c".repeat(64), "policy-v1", true, NOW.plusSeconds(600));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.evaluate(changed));
        }
        verifyNoInteractions(client);
        when(store.usesLedger(ledger)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> create(issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "21", client))));
        when(store.usesLedger(ledger)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> create(mock(TrustedIssuerRegistry.class),
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "21", client))));
        assertThrows(IllegalArgumentException.class, () -> create(issuers,
                Map.of("0".repeat(64), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "21", client))));
    }

    @Test void service_decision_client_uses_the_dedicated_scope_without_fetching_a_token_at_startup() {
        var starter = new top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties();
        starter.setResourceUri(RESOURCE); starter.getServiceClient().setAppId("agent-app");
        var authority = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties();
        var source = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.Binding();
        source.setIssuer(ISSUER); source.setSourceResource(RESOURCE); source.setRegistrationId("agent");
        source.setAudience(URI.create("https://api.example/idp")); source.setContext(top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext.TENANT);
        source.setTenantId("21"); source.setScopes(java.util.Set.of(
                top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE));
        authority.setBindings(Map.of("agent", source));
        var rbac = new top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties();
        rbac.setSystemCode("tianji"); rbac.getAuthorization().setEndpoint("https://rbac.example");
        rbac.getAuthorization().getServiceToken().setResourceUri("https://api.example/rbac");
        var oauth = mock(top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(inv -> {
            top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest value = inv.getArgument(0);
            return new top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey(ISSUER.toASCIIString(),
                    value.registrationId(), "agent", 1, value.appId(), value.audience(), value.context(), value.tenantId(), value.scopes());
        });
        assertNotNull(new TianjiServiceExecutionGrantAuthorizer(ledger, store, bindings, starter, authority, issuers, oauth,
                new com.fasterxml.jackson.databind.ObjectMapper(), rbac));
        verify(oauth, never()).authorize(any()); verify(oauth, never()).authorizeBounded(any(), anyString(), any());
    }

    private TianjiServiceExecutionGrantAuthorizer create(TrustedIssuerRegistry registry, Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients) {
        return new TianjiServiceExecutionGrantAuthorizer(ledger, store, bindings, RESOURCE, "tianji", registry, clients);
    }

    private ServiceExecutionAuthorizationContract.Decision decision(Instant expiry) {
        var source = new ServiceExecutionAuthorizationContract.Subject(subject.clientId(), subject.appId(), subject.businessCode(),
                subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString());
        return new ServiceExecutionAuthorizationContract.Decision(ID, ISSUER.toASCIIString(), "21", "worker", source,
                RESOURCE.toASCIIString(), AgentRunAuthorization.PURPOSE, RUN, AgentServiceExecutionGrant.fingerprint(request, RESOURCE, subject),
                true, request.deadlineAt(), expiry, 1, 3);
    }
}
