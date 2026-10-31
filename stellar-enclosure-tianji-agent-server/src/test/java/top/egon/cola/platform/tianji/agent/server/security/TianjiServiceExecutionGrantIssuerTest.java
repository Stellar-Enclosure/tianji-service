package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiServiceExecutionGrantIssuerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final String HASH = "a".repeat(64);
    private static final String TOKEN = "verified-worker-proof";
    private static final String ID = "8" + "1".repeat(62);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentServiceExecutionGrantStore store = mock(AgentServiceExecutionGrantStore.class);
    private final TianjiServiceExecutionBindings bindings = mock(TianjiServiceExecutionBindings.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
    private final Clock clock = mock(Clock.class);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toASCIIString());
    private final ServiceIdentityPrincipal principal = new ServiceIdentityPrincipal("worker", "21", "worker", "42", RESOURCE, 1,
            Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "platform", "process", "test", "credential",
            NOW.minusSeconds(1), NOW.plusSeconds(300), "process-app", ServiceTokenContext.TENANT);
    private final TianjiAgentAccess.Authorized authorized = new TianjiAgentAccess.Authorized(actor, principal.expiresAt(), ISSUER);
    private final TianjiLinkedDispatchProperties.Binding binding = new TianjiLinkedDispatchProperties.Binding(ISSUER, 21, "worker",
            "process-app", "platform", "process", "test", "process-east", Set.of(WorkerScopeTypeEnum.BPMN),
            List.of(new TianjiLinkedDispatchProperties.Definition("review", 1, HASH)));
    private final AgentProcessLink link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-instance",
            "activity-occurrence", HASH, NOW.plusSeconds(60));
    private final LinkedAgentStartCommand command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null,
            "request", "business", "trace", new AgentInputDTO("input", List.of(), Map.of()), null), link);
    private final AgentServiceExecutionGrant.Subject subject = new AgentServiceExecutionGrant.Subject("worker", "process-app",
            "platform", "process", "test", URI.create("https://api.example/process"));
    private final AgentRunAuthorization.Request authorization = new AgentRunAuthorization.Request(actor, RUN, "review", 1, HASH,
            "b".repeat(64), "c".repeat(64), "policy-v1", false, NOW.plusSeconds(600));
    private final AgentServiceExecutionGrant.Reservation reservation = new AgentServiceExecutionGrant.Reservation(authorization,
            RESOURCE, subject, 1, AgentServiceExecutionGrant.fingerprint(authorization, RESOURCE, subject));
    private final AgentRunDTO run = mock(AgentRunDTO.class);
    private final AgentExecutionSnapshot snapshot = mock(AgentExecutionSnapshot.class);
    private final AgentLedgerService.ExecutionMaterial material = mock(AgentLedgerService.ExecutionMaterial.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiServiceExecutionGrantIssuer issuer;

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(store.usesLedger(ledger)).thenReturn(true);
        when(bindings.usesIssuerRegistry(issuers)).thenReturn(true);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        when(run.runId()).thenReturn(RUN); when(run.status()).thenReturn(AgentRunStatusEnum.QUEUED);
        when(snapshot.processLink()).thenReturn(link);
        when(material.run()).thenReturn(run); when(material.snapshot()).thenReturn(snapshot); when(material.authorization()).thenReturn(authorization);
        when(ledger.executionMaterial(actor, new GetAgentRunQuery(RUN))).thenReturn(material);
        when(bindings.require(authorization, link)).thenReturn(subject);
        when(store.current(authorization, RESOURCE, subject)).thenReturn(Optional.empty());
        when(store.reserve(actor, RUN, RESOURCE, subject)).thenReturn(Optional.of(reservation));
        when(store.complete(eq(reservation), any())).thenReturn(true);
        when(client.issueService(any())).thenReturn(new ServiceExecutionGrantContract.Issued(
                new ServiceExecutionGrantContract.Ticket(ID, "ENC:v1:key:nonce:sealed"), NOW.plusSeconds(120)));
        issuer = new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void memory_scopes_are_ensured_after_service_issue_and_also_when_the_ordinary_ticket_is_reused() throws Exception {
        var memory = mock(TianjiMemoryGrantIssuer.class); when(memory.usesLedger(ledger)).thenReturn(true);
        when(memory.usesIssuerRegistry(issuers)).thenReturn(true); when(memory.usesResource(RESOURCE)).thenReturn(true); when(memory.ensure(authorized, run)).thenReturn(run);
        issuer = new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock, memory);
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        var order = inOrder(store, memory); order.verify(store).current(authorization, RESOURCE, subject); order.verify(store).reserve(actor, RUN, RESOURCE, subject);
        order.verify(store).complete(eq(reservation), any()); order.verify(memory).ensure(authorized, run);
        when(store.current(authorization, RESOURCE, subject)).thenReturn(Optional.of(new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:existing", NOW.plusSeconds(60))));
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        verify(memory, times(2)).ensure(authorized, run); verify(client, times(1)).issueService(any());
    }

    @Test void issues_the_verified_request_token_for_the_exact_reserved_run() throws Exception {
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        var sent = org.mockito.ArgumentCaptor.forClass(ServiceExecutionGrantContract.IssueRequest.class);
        verify(client).issueService(sent.capture());
        assertEquals(TOKEN, sent.getValue().serviceToken());
        assertEquals(AgentRunAuthorization.PURPOSE, sent.getValue().purpose());
        assertEquals(RUN, sent.getValue().intentId());
        assertEquals(reservation.bindingSha256(), sent.getValue().bindingSha256());
        assertEquals(authorization.deadlineAt(), sent.getValue().deadlineAt());
        assertFalse(sent.getValue().toString().contains(TOKEN));
        var saved = org.mockito.ArgumentCaptor.forClass(AgentServiceExecutionGrant.Ticket.class);
        verify(store).complete(eq(reservation), saved.capture());
        assertEquals(ID, saved.getValue().grantId()); assertFalse(saved.getValue().toString().contains("sealed"));
    }

    @Test void an_existing_ticket_or_nonqueued_run_is_not_reissued() throws Exception {
        when(store.current(authorization, RESOURCE, subject)).thenReturn(Optional.of(
                new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:existing", NOW.plusSeconds(60))));
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        verify(store, never()).reserve(any(), anyString(), any(), any()); verifyNoInteractions(client);
        when(run.status()).thenReturn(AgentRunStatusEnum.RUNNING);
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        verifyNoInteractions(client);
    }

    @Test void a_replaced_bearer_or_changed_process_link_never_reaches_the_owner() throws Exception {
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> withRequest(request -> {
            request.removeHeader("Authorization"); request.addHeader("Authorization", "Bearer other-proof");
            issuer.ensure(authorized, principal, binding, command, run);
        }));
        verifyNoInteractions(client);
        var different = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "other-process", "activity-occurrence", HASH, link.dispatchNotAfter());
        var changed = new LinkedAgentStartCommand(command.start(), different);
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> withRequest(request -> issuer.ensure(authorized, principal, binding, changed, run)));
        verifyNoInteractions(client);
    }

    @Test void a_subject_tuple_mismatch_is_not_exchanged_for_a_grant() {
        when(bindings.require(authorization, link)).thenReturn(new AgentServiceExecutionGrant.Subject("worker", "other-app",
                "platform", "process", "test", URI.create("https://api.example/process")));
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        verifyNoInteractions(client); verify(store, never()).reserve(any(), anyString(), any(), any());
    }

    @Test void a_service_without_the_linked_submit_scope_cannot_request_a_grant() {
        var wrongScope = new ServiceIdentityPrincipal("worker", "21", "worker", "42", RESOURCE, 1,
                Set.of("tianji:agent:linked:resolve"), "platform", "process", "test", "credential",
                NOW.minusSeconds(1), NOW.plusSeconds(300), "process-app", ServiceTokenContext.TENANT);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(wrongScope, List.of(), ISSUER));
        var caller = new TianjiAgentAccess.Authorized(actor, wrongScope.expiresAt(), ISSUER);
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> withRequest(request -> issuer.ensure(caller, wrongScope, binding, command, run)));
        verifyNoInteractions(client); verify(store, never()).reserve(any(), anyString(), any(), any());
    }

    @Test void late_or_unavailable_results_never_publish_a_ticket() throws Exception {
        when(store.complete(eq(reservation), any())).thenReturn(false);
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(301));
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        verify(store, times(1)).complete(eq(reservation), any());
    }

    @Test void owner_denial_is_distinct_from_unavailability_and_never_leaks_the_token() {
        when(client.issueService(any())).thenThrow(new HttpExecutionGrantClient.ExecutionGrantDeniedException());
        var denied = assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        assertFalse(denied.toString().contains(TOKEN));
        doThrow(new IllegalStateException(TOKEN)).when(client).issueService(any());
        var unavailable = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        assertFalse(unavailable.toString().contains(TOKEN)); assertNull(unavailable.getCause());
        verify(store, never()).complete(any(), any());
    }

    @Test void trust_change_after_issuance_prevents_publication() {
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust), Optional.empty());
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        verify(store, never()).complete(any(), any());
    }

    @Test void exact_subject_resource_is_rechecked_after_issuance() {
        var changed = new AgentServiceExecutionGrant.Subject("worker", "process-app", "platform", "process", "test",
                URI.create("https://API.example/process"));
        when(bindings.require(authorization, link)).thenReturn(subject, changed);
        assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> withRequest(request -> issuer.ensure(authorized, principal, binding, command, run)));
        verify(store, never()).complete(any(), any());
    }

    @Test void service_issuer_uses_only_a_dedicated_trusted_source_registration() {
        var starter = new top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties();
        starter.setResourceUri(RESOURCE); starter.getServiceClient().setAppId("agent-app");
        var authority = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties();
        var source = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.Binding();
        source.setIssuer(ISSUER); source.setSourceResource(RESOURCE); source.setRegistrationId("agent");
        source.setAudience(URI.create("https://api.example/idp")); source.setContext(ServiceTokenContext.TENANT);
        source.setTenantId("21"); source.setScopes(Set.of(IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE));
        authority.setBindings(Map.of("agent", source));
        var selected = TianjiExecutionAuthorityBindings.source(starter, authority, issuers,
                IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE);
        assertEquals(1, selected.size()); assertEquals("21", selected.get(ISSUER).tenantId());
        assertTrue(TianjiExecutionAuthorityBindings.source(starter, authority, issuers).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TianjiExecutionAuthorityBindings.source(starter, authority, issuers, "other:scope"));
    }

    @Test void issuer_rejects_a_different_ledger_or_issuer_registry() {
        when(store.usesLedger(ledger)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionGrantIssuer(
                ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock));
        when(store.usesLedger(ledger)).thenReturn(true);
        var other = mock(TrustedIssuerRegistry.class);
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionGrantIssuer(
                ledger, store, bindings, RESOURCE, other, Map.of(ISSUER, client), clock));
    }

    @Test void linked_service_replay_prepares_tools_after_memory_without_reissuing_the_run_grant() throws Exception {
        var memory = mock(TianjiMemoryGrantIssuer.class); var tools = mock(TianjiToolGrantIssuer.class);
        when(memory.usesLedger(ledger)).thenReturn(true); when(memory.usesIssuerRegistry(issuers)).thenReturn(true); when(memory.usesResource(RESOURCE)).thenReturn(true);
        when(memory.ensure(authorized, run)).thenReturn(run);
        when(tools.usesLedger(ledger)).thenReturn(true); when(tools.usesIssuerRegistry(issuers)).thenReturn(true); when(tools.usesResource(RESOURCE)).thenReturn(true); when(tools.usesServices(bindings)).thenReturn(true);
        when(tools.ensure(authorized, run)).thenReturn(run);
        issuer = new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock, memory, tools);
        when(store.current(authorization, RESOURCE, subject)).thenReturn(Optional.of(new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:existing", NOW.plusSeconds(60))));
        withRequest(request -> issuer.ensure(authorized, principal, binding, command, run));
        var order = inOrder(memory, tools); order.verify(memory).ensure(authorized, run); order.verify(tools).ensure(authorized, run);
        assertTrue(issuer.usesToolIssuer(tools)); verifyNoInteractions(client);
    }

    @Test void a_tool_issuer_from_another_service_source_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock, null, mock(TianjiToolGrantIssuer.class)));
    }

    @Test void user_only_tools_do_not_require_a_service_mapping_but_configured_service_tools_must_share_it() {
        var tools = mock(TianjiToolGrantIssuer.class);
        when(tools.usesLedger(ledger)).thenReturn(true); when(tools.usesIssuerRegistry(issuers)).thenReturn(true); when(tools.usesResource(RESOURCE)).thenReturn(true);
        assertDoesNotThrow(() -> new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock, null, tools));
        when(tools.hasServiceBindings()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, RESOURCE, issuers, Map.of(ISSUER, client), clock, null, tools));
    }

    private static void withRequest(Consumer<MockHttpServletRequest> action) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/tianji/v1/agent-process-dispatches/submit");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try { new VerifiedAgentIssuerFilter().doFilter(request, new MockHttpServletResponse(), (incoming, response) -> action.accept(request)); }
        finally { RequestContextHolder.resetRequestAttributes(); }
    }

    private static AgentLedgerException assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(AgentLedgerException.class, action);
        assertEquals(code, failure.code());
        return failure;
    }
}
