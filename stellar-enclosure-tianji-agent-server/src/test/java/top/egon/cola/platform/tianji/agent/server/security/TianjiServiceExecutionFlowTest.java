package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual local issuer and bounded authorizer with controlled persistence and IAM boundaries. */
class TianjiServiceExecutionFlowTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void issued_service_intent_is_rechecked_before_claim_and_model_and_stops_without_current_evidence() throws Exception {
        URI issuer = URI.create("https://idp.example/tenants/21"), resource = URI.create("https://api.example/tianji");
        URI sourceUri = URI.create("https://api.example/process");
        Instant now = Instant.parse("2026-10-29T00:00:00Z"); String hash = "a".repeat(64);
        String runId = "00000000-0000-0000-0000-000000000123", grantId = "8" + "1".repeat(62);
        var actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", issuer.toASCIIString());
        var principal = new ServiceIdentityPrincipal("worker", "21", "worker", "42", resource, 1,
                Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "platform", "process", "test", "credential",
                now.minusSeconds(1), now.plusSeconds(300), "process-app", ServiceTokenContext.TENANT);
        var authorized = new TianjiAgentAccess.Authorized(actor, principal.expiresAt(), issuer);
        var linkedBinding = new TianjiLinkedDispatchProperties.Binding(issuer, 21, "worker", "process-app", "platform", "process",
                "test", "process-east", Set.of(WorkerScopeTypeEnum.BPMN), List.of(new TianjiLinkedDispatchProperties.Definition("review", 1, hash)));
        var linked = new TianjiLinkedDispatchProperties(true, true, List.of(linkedBinding));
        var serviceProperties = new TianjiServiceExecutionProperties(true,
                List.of(new TianjiServiceExecutionProperties.Source(issuer, 21, "worker", sourceUri)));
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(issuer, URI.create("https://idp.example/jwks"), "tenant:21",
                TrustedIssuerRegistry.RealmType.TENANT, "21", TrustedIssuerRegistry.Status.ACTIVE, 1);
        var issuers = mock(TrustedIssuerRegistry.class);
        when(issuers.findByIssuer(issuer.toASCIIString())).thenReturn(Optional.of(trust));
        var bindings = new TianjiServiceExecutionBindings(serviceProperties, linked, issuers);
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "activity", hash, now.plusSeconds(60));
        var command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "request", "business", "trace",
                new AgentInputDTO("input", List.of(), Map.of()), null), link);
        var request = new AgentRunAuthorization.Request(actor, runId, "review", 1, hash, "b".repeat(64), "c".repeat(64),
                "policy-v1", false, now.plusSeconds(600));
        var subject = bindings.require(request, link);
        var reservation = new AgentServiceExecutionGrant.Reservation(request, resource, subject, 1,
                AgentServiceExecutionGrant.fingerprint(request, resource, subject));
        var ledger = mock(AgentLedgerService.class); var store = mock(AgentServiceExecutionGrantStore.class);
        var userStore = mock(AgentExecutionGrantStore.class); var issueClient = mock(HttpExecutionGrantClient.class);
        var decisionClient = mock(HttpExecutionAuthorizationClient.class);
        var run = mock(AgentRunDTO.class); var snapshot = mock(AgentExecutionSnapshot.class);
        var material = mock(AgentLedgerService.ExecutionMaterial.class);
        when(run.runId()).thenReturn(runId); when(run.status()).thenReturn(AgentRunStatusEnum.QUEUED);
        when(snapshot.processLink()).thenReturn(link); when(material.run()).thenReturn(run);
        when(material.snapshot()).thenReturn(snapshot); when(material.authorization()).thenReturn(request);
        when(ledger.executionMaterial(actor, new GetAgentRunQuery(runId))).thenReturn(material);
        when(ledger.linkedRunEvidence(actor, new GetAgentRunQuery(runId)))
                .thenReturn(new AgentLedgerService.LinkedRunEvidenceVO(run, link, request));
        when(ledger.databaseNow()).thenReturn(now);
        when(store.usesLedger(ledger)).thenReturn(true); when(userStore.usesLedger(ledger)).thenReturn(true);
        var saved = new AtomicReference<AgentServiceExecutionGrant.Ticket>();
        when(store.current(request, resource, subject)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(store.reserve(actor, runId, resource, subject)).thenReturn(Optional.of(reservation));
        when(store.complete(eq(reservation), any())).thenAnswer(inv -> { saved.set(inv.getArgument(1)); return true; });
        when(issueClient.issueService(any())).thenReturn(new ServiceExecutionGrantContract.Issued(
                new ServiceExecutionGrantContract.Ticket(grantId, "ENC:v1:key:nonce:sealed"), now.plusSeconds(120)));
        var issuedBy = new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, resource, issuers, Map.of(issuer, issueClient),
                Clock.fixed(now, ZoneOffset.UTC));
        var source = new ServiceExecutionAuthorizationContract.Subject("worker", "process-app", "platform", "process", "test", sourceUri.toASCIIString());
        when(decisionClient.decideService(any(), any())).thenReturn(new ServiceExecutionAuthorizationContract.Decision(grantId,
                issuer.toASCIIString(), "21", "worker", source, resource.toASCIIString(), AgentRunAuthorization.PURPOSE, runId,
                reservation.bindingSha256(), false, request.deadlineAt(), now.plusSeconds(30), 1, 2));
        var serviceAuthorizer = new TianjiServiceExecutionGrantAuthorizer(ledger, store, bindings, resource, "tianji", issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(issuer, "21", decisionClient)));
        try (var authorizer = new TianjiExecutionGrantAuthorizer(ledger, userStore, resource, "tianji", issuers,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(issuer, "21", decisionClient)),
                Duration.ofSeconds(1), 2, Clock.fixed(now, ZoneOffset.UTC), serviceAuthorizer)) {
            SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), issuer));
            var servlet = new MockHttpServletRequest("POST", "/api/tianji/v1/agent-process-dispatches/submit");
            servlet.addHeader("Authorization", "Bearer verified-worker-proof");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
            new VerifiedAgentIssuerFilter().doFilter(servlet, new MockHttpServletResponse(),
                    (incoming, response) -> issuedBy.ensure(authorized, principal, linkedBinding, command, run));
            assertNotNull(saved.get());
            assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
            assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
            var expected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.ServiceExpectation.class);
            verify(decisionClient, times(2)).decideService(any(), expected.capture());
            assertEquals(reservation.bindingSha256(), expected.getValue().intent().bindingSha256());
            assertEquals(source, expected.getValue().subjectSource());
            saved.set(null);
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
            verify(decisionClient, times(2)).decideService(any(), any());
        }
    }

    @Test void runtime_v2_uses_the_verified_template_and_source_fence_at_both_authorization_boundaries() {
        URI issuer = URI.create("https://idp.example/tenants/21");
        URI resource = URI.create("https://api.example/tianji");
        URI sourceUri = URI.create("https://api.example/process");
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        String runId = "00000000-0000-0000-0000-000000000124";
        String grantId = "8" + "2".repeat(62);
        String templateSha256 = "a".repeat(64);
        String configurationSha256 = "d".repeat(64);
        var actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", issuer.toASCIIString());
        var linked = new TianjiLinkedDispatchProperties(true, true, List.of(new TianjiLinkedDispatchProperties.Binding(
                issuer, 21, "worker", "process-app", "platform", "process", "test", "process-east",
                Set.of(WorkerScopeTypeEnum.BPMN), List.of(new TianjiLinkedDispatchProperties.Definition("review-v2", 7, templateSha256)))));
        var issuers = mock(TrustedIssuerRegistry.class);
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(issuer, URI.create("https://idp.example/jwks"), "tenant:21",
                TrustedIssuerRegistry.RealmType.TENANT, "21", TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(issuers.findByIssuer(issuer.toASCIIString())).thenReturn(Optional.of(trust));
        var bindings = new TianjiServiceExecutionBindings(new TianjiServiceExecutionProperties(true,
                List.of(new TianjiServiceExecutionProperties.Source(issuer, 21, "worker", sourceUri))), linked, issuers);
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance-v2", "activity-v2",
                templateSha256, now.plusSeconds(60));
        var request = new AgentRunAuthorization.Request(actor, runId, "review-v2", 7, templateSha256, "b".repeat(64),
                "c".repeat(64), configurationSha256, false, now.plusSeconds(120));
        var subject = bindings.require(request, link);
        var ticket = new AgentServiceExecutionGrant.Ticket(grantId, "ENC:v1:key:nonce:sealed", now.plusSeconds(75));
        var ledger = mock(AgentLedgerService.class);
        var store = mock(AgentServiceExecutionGrantStore.class);
        var userStore = mock(AgentExecutionGrantStore.class);
        var decisionClient = mock(HttpExecutionAuthorizationClient.class);
        var run = new AgentRunDTO(runId, "review-v2", 7, null, AgentRunStatusEnum.QUEUED, null, false,
                0, 0, 1, request.deadlineAt(), now, null);
        var evidence = new AgentLedgerService.LinkedRunEvidenceVO(run, link, request);
        var currentEvidence = new AtomicReference<>(evidence);
        var query = new GetAgentRunQuery(runId);
        when(ledger.linkedRunEvidence(actor, query)).thenAnswer(invocation -> currentEvidence.get());
        when(ledger.executionMaterial(actor, query)).thenThrow(new IllegalStateException("The legacy snapshot reader rejects V2"));
        when(ledger.databaseNow()).thenReturn(now);
        when(store.usesLedger(ledger)).thenReturn(true);
        when(userStore.usesLedger(ledger)).thenReturn(true);
        when(store.current(request, resource, subject)).thenReturn(Optional.of(ticket));

        String fingerprint = AgentServiceExecutionGrant.fingerprint(request, resource, subject);
        var source = new ServiceExecutionAuthorizationContract.Subject("worker", "process-app", "platform", "process", "test", sourceUri.toASCIIString());
        var authority = new ServiceExecutionAuthorizationContract.Decision(grantId, issuer.toASCIIString(), "21", "worker", source,
                resource.toASCIIString(), AgentRunAuthorization.PURPOSE, runId, fingerprint, false, request.deadlineAt(), now.plusSeconds(30), 1, 2);
        when(decisionClient.decideService(any(), any())).thenReturn(authority);
        var clients = Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(issuer, "21", decisionClient));
        var serviceAuthorizer = new TianjiServiceExecutionGrantAuthorizer(ledger, store, bindings, resource, "tianji", issuers, clients);
        try (var authorizer = new TianjiExecutionGrantAuthorizer(ledger, userStore, resource, "tianji", issuers,
                clients, Duration.ofSeconds(1), 2, Clock.fixed(now, ZoneOffset.UTC), serviceAuthorizer)) {
            assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.CLAIM));
            assertEquals(AgentRunAuthorization.Decision.ALLOW, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
            var selected = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.ServiceExpectation.class);
            verify(decisionClient, times(2)).decideService(any(), selected.capture());
            assertEquals(fingerprint, selected.getValue().intent().bindingSha256());
            assertEquals(source, selected.getValue().subjectSource());
            verify(ledger, never()).executionMaterial(actor, query);

            var invalidLink = new AgentProcessLink(link.sourceId(), link.scopeType(), link.scopeInstanceId(), link.activityOccurrenceId(),
                    "e".repeat(64), link.dispatchNotAfter());
            currentEvidence.set(new AgentLedgerService.LinkedRunEvidenceVO(run, invalidLink, request));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
            verify(decisionClient, times(2)).decideService(any(), any());

            currentEvidence.set(evidence);
            when(decisionClient.decideService(any(), any())).thenAnswer(invocation -> {
                currentEvidence.set(new AgentLedgerService.LinkedRunEvidenceVO(run, invalidLink, request));
                return authority;
            });
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, authorizer.authorize(request, AgentRunAuthorization.Boundary.MODEL));
            verify(decisionClient, times(3)).decideService(any(), any());
        }
    }
}
