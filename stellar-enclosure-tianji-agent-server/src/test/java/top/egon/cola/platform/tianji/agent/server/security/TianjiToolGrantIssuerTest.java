package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ServiceExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcSecurityContext;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synchronous request identity plus real owner SDK types with controlled replies; no deployed signing service is contacted. */
class TianjiToolGrantIssuerTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20"), RESOURCE = URI.create("https://agent.example/api"), SOURCE_RESOURCE = URI.create("https://process.example/api");
    private static final String HASH = "a".repeat(64), RUN = "00000000-0000-0000-0000-000000000001", TOKEN = "verified.request.token";
    private static final ToolDescriptor READ = descriptor("process.read", "read_process", ToolPolicy.Risk.READ_ONLY), WRITE = descriptor("process.start", "start_process", ToolPolicy.Risk.IDEMPOTENT_WRITE);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void real_rbac_user_issues_a_separate_tool_intent_without_session_authority_or_a_bearer_in_storage() {
        var fixture = new Fixture(); var current = fixture.issuer.ensure(fixture.authorized, fixture.submitted);
        assertNotNull(current); assertEquals(fixture.material.get().run(), current);
        var captured = org.mockito.ArgumentCaptor.forClass(ExecutionGrantContract.IssueRequest.class); verify(fixture.client).issue(captured.capture());
        var expected = fixture.scope(READ); var request = captured.getValue();
        assertEquals(TOKEN, request.userToken()); assertEquals(expected.purpose(), request.purpose()); assertEquals(expected.capabilityId(), request.intentId());
        assertEquals(expected.bindingSha256(), request.bindingSha256()); assertFalse(request.sessionRequired()); assertEquals(fixture.run.deadlineAt(), request.deadlineAt());
        assertNotEquals(fixture.run.runId(), request.intentId()); assertNotEquals(AgentExecutionGrant.fingerprint(fixture.run, RESOURCE), request.bindingSha256()); assertFalse(request.toString().contains(TOKEN));
        assertEquals(1, fixture.attached.size()); assertFalse(fixture.attached.getFirst().toString().contains(TOKEN));
        assertEquals(expected, fixture.attached.getFirst().reservation().binding());
        assertTrue(fixture.issuer.usesLedger(fixture.ledger)); assertTrue(fixture.issuer.usesBindings(fixture.bindings)); assertTrue(fixture.issuer.usesIssuerRegistry(fixture.issuers));
        assertTrue(fixture.issuer.usesResource(RESOURCE)); assertFalse(fixture.issuer.usesResource(URI.create("https://AGENT.example/api"))); assertTrue(fixture.issuer.usesServices(null));
        assertFalse(fixture.issuer.hasServiceBindings());
        verify(fixture.client, never()).issueService(any());
    }

    @Test void nonqueued_and_plain_runs_return_latest_without_requiring_a_request_carrier() {
        var fixture = new Fixture(); SecurityContextHolder.clearContext(); VerifiedUserTokenCarrier.clear(fixture.servlet);
        fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.RUNNING, false, 8));
        assertSame(fixture.material.get().run(), fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        fixture.material.set(fixture.material(List.of(), AgentRunStatusEnum.QUEUED, false, 9));
        assertSame(fixture.material.get().run(), fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        verifyNoInteractions(fixture.client); verify(fixture.store, never()).current(any(), any()); verify(fixture.store, never()).reserve(any(), any());
    }

    @Test void existing_evidence_is_reused_only_with_the_actual_current_rbac_owner_and_carrier() {
        var fixture = new Fixture(); fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 1));
        assertSame(fixture.material.get().run(), fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        verifyNoInteractions(fixture.client); verify(fixture.store, never()).reserve(any(), any());
        VerifiedUserTokenCarrier.clear(fixture.servlet);
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        VerifiedUserTokenCarrier.set(fixture.servlet, TOKEN); SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(fixture.userIdentity("alice", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString())), List.of(), ISSUER));
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
    }

    @Test void an_http_request_without_its_carrier_cannot_borrow_a_residual_rpc_user_token() {
        var fixture = new Fixture(); VerifiedUserTokenCarrier.clear(fixture.servlet);
        var rpc = IdpRpcSecurityContext.with(fixture.userIdentity("alice", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString())), TOKEN);
        var previous = rpc.attach();
        try {
            assertEquals(TOKEN, VerifiedUserTokenCarrier.currentOrNull());
            assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
            verifyNoInteractions(fixture.client); verify(fixture.store, never()).reserve(any(), any());
        } finally { rpc.detach(previous); }
    }

    @Test void wrong_user_identity_audience_issuer_or_expiry_never_reserves_or_calls_the_owner() {
        var fixture = new Fixture();
        for (var mutation : List.<Runnable>of(
                () -> fixture.user("bob", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE)),
                () -> fixture.user("alice", "token-1", NOW.plusSeconds(301), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE)),
                () -> fixture.user("alice", "token-1", NOW.plusSeconds(300), Set.of("https://other.example"), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE)),
                () -> fixture.user("alice", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString()), "https://other-idp.example/20", Set.of(TianjiAgentPermissions.START_SCOPE)))) {
            mutation.run(); assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        }
        verifyNoInteractions(fixture.client); verify(fixture.store, never()).reserve(any(), any());
    }

    @Test void post_issue_token_identity_scope_and_carrier_changes_are_not_attached() {
        var fixture = new Fixture();
        for (var mutation : List.<Runnable>of(
                () -> fixture.user("alice", "token-2", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE)),
                () -> fixture.user("alice", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of("another:scope")),
                () -> VerifiedUserTokenCarrier.set(fixture.servlet, "changed.request.token"))) {
            fixture.resetUser();
            doAnswer(call -> { var reply = fixture.userIssued(call.getArgument(0)); mutation.run(); return reply; }).when(fixture.client).issue(any());
            assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        }
        verify(fixture.store, never()).attach(any(), any());
    }

    @Test void post_issue_trust_or_immutable_source_drift_is_not_published_as_ready() {
        var fixture = new Fixture(); var original = fixture.material.get();
        for (var mutation : List.<Runnable>of(() -> fixture.trust.set(null), () -> {
            var changed = new AgentRunAuthorization.Request(fixture.actor, RUN, "assistant", 1, HASH, "b".repeat(64), HASH, "policy-v1", true, fixture.run.deadlineAt());
            fixture.material.set(new AgentLedgerService.ExecutionMaterial(original.run(), original.snapshot(), changed));
        })) {
            fixture.resetUser(); fixture.trust.set(fixture.originalTrust); fixture.material.set(original);
            doAnswer(call -> { var reply = fixture.userIssued(call.getArgument(0)); mutation.run(); return reply; }).when(fixture.client).issue(any());
            assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        }
        verify(fixture.store, never()).attach(any(), any());
    }

    @Test void late_owner_replies_and_wall_rollback_cannot_extend_the_single_monotonic_window() {
        var nanos = new AtomicLong(); var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ), Duration.ofSeconds(1), nanos::get);
        doAnswer(call -> { fixture.clock.now.set(NOW.minusSeconds(60)); nanos.set(31_000_000_000L); return fixture.userIssued(call.getArgument(0)); }).when(fixture.client).issue(any());
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        verify(fixture.store, never()).attach(any(), any());
    }

    @Test void every_next_owner_call_requires_its_full_budget_and_the_loop_does_not_reset_thirty_seconds() {
        var shortFixture = new Fixture();
        var shortAuthorized = new TianjiAgentAccess.Authorized(shortFixture.actor, NOW.plusMillis(500), ISSUER);
        shortFixture.user("alice", "token-1", shortAuthorized.expiresAt(), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE));
        assertUnavailable(() -> shortFixture.issuer.ensure(shortAuthorized, shortFixture.submitted));
        verifyNoInteractions(shortFixture.client); verify(shortFixture.store, never()).reserve(any(), any());
        var nanos = new AtomicLong(); var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ, WRITE), Duration.ofSeconds(5), nanos::get);
        doAnswer(call -> { nanos.set(26_000_000_000L); return fixture.userIssued(call.getArgument(0)); }).when(fixture.client).issue(any());
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        verify(fixture.client, times(1)).issue(any());
    }

    @Test void stale_attachment_can_reuse_a_concurrent_ready_ticket_but_never_reissues_a_closed_source() {
        var fixture = new Fixture();
        doAnswer(call -> { var value = (ToolExecutionGrant.EvidenceVO) call.getArgument(0); fixture.evidence.put(READ.key(), new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(value.reservation().binding(), 2), value.grantId(), value.ciphertext(), value.expiresAt())); return false; }).when(fixture.store).attach(any(), any());
        assertSame(fixture.material.get().run(), fixture.issuer.ensure(fixture.authorized, fixture.submitted)); verify(fixture.client).issue(any());
        fixture.evidence.clear();
        doAnswer(call -> { fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.CANCELLED, true, 2)); return fixture.userIssued(call.getArgument(0)); }).when(fixture.client).issue(any());
        assertEquals(AgentRunStatusEnum.CANCELLED, fixture.issuer.ensure(fixture.authorized, fixture.submitted).status());
    }

    @Test void denied_unknown_and_malformed_source_errors_remain_fixed_and_secret_free() {
        var fixture = new Fixture();
        doThrow(new HttpExecutionGrantClient.ExecutionGrantDeniedException()).when(fixture.client).issue(any());
        var denied = assertThrows(AgentLedgerException.class, () -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, denied.code()); assertNull(denied.getCause()); assertFalse(denied.getMessage().contains(TOKEN));
        doThrow(new IllegalStateException("private owner detail", new RuntimeException(TOKEN))).when(fixture.client).issue(any());
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        doThrow(new IllegalStateException("invalid source", new RuntimeException("private input"))).when(fixture.ledger).executionMaterial(any(), any());
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
    }

    @Test void real_linked_service_uses_the_verified_bearer_and_preserves_the_original_source_resource() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        var result = fixture.serviceEnsure(); assertNotNull(result); assertSame(fixture.material.get().run(), result);
        var captured = org.mockito.ArgumentCaptor.forClass(ServiceExecutionGrantContract.IssueRequest.class); verify(fixture.client).issueService(captured.capture());
        var scope = fixture.scope(READ); assertEquals(TOKEN, captured.getValue().serviceToken()); assertEquals(scope.capabilityId(), captured.getValue().intentId());
        assertEquals(scope.bindingSha256(), captured.getValue().bindingSha256()); assertEquals(scope.purpose(), captured.getValue().purpose()); assertFalse(captured.getValue().sessionRequired()); assertEquals(fixture.run.deadlineAt(), captured.getValue().deadlineAt());
        assertEquals(SOURCE_RESOURCE.toASCIIString(), fixture.attached.getFirst().reservation().binding().serviceSubject().resourceUri().toASCIIString()); assertNotEquals(RESOURCE, SOURCE_RESOURCE);
        assertTrue(fixture.issuer.usesServices(fixture.services)); assertTrue(fixture.issuer.hasServiceBindings()); assertNull(VerifiedAgentIssuerFilter.currentServiceToken(fixture.servlet)); verify(fixture.client, never()).issue(any());
    }

    @Test void v2_service_issues_and_reuses_the_existing_protocol_for_exact_persisted_tools() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        fixture.servlet.setRequestURI("/api/tianji/v2/agent-process-dispatches/submit");
        var runtime = fixture.runtimeMaterial();
        when(fixture.ledger.toolRunEvidence(eq(fixture.actor), any())).thenReturn(runtime);
        when(fixture.services.require(runtime.authorization(), runtime.processLink())).thenReturn(fixture.serviceSource);
        var expected = fixture.bindings.resolveRuntime(runtime).getFirst().binding();

        for (int request = 0; request < 2; request++) {
            new VerifiedAgentIssuerFilter().doFilter(fixture.servlet, new MockHttpServletResponse(), (incoming, response) ->
                    assertSame(runtime.run(), fixture.issuer.ensureRuntime(fixture.authorized, runtime.run())));
        }

        var proof = org.mockito.ArgumentCaptor.forClass(ServiceExecutionGrantContract.IssueRequest.class);
        verify(fixture.client).issueService(proof.capture());
        assertEquals(expected.bindingSha256(), proof.getValue().bindingSha256());
        assertEquals(expected.capabilityId(), proof.getValue().intentId());
        assertEquals(expected.serviceSubject(), fixture.attached.getFirst().reservation().binding().serviceSubject());
        assertEquals(READ.reference(), fixture.attached.getFirst().reservation().binding().tool());
        assertFalse(proof.getValue().sessionRequired());
        verify(fixture.ledger, never()).executionMaterial(any(), any());
        verify(fixture.client, never()).issue(any());
    }

    @Test void v2_service_grant_reuses_the_exact_fence_and_normalizes_wire_integer_representations() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        fixture.servlet.setRequestURI("/api/tianji/v2/agent-process-dispatches/submit");
        var runtime = fixture.runtimeMaterial();
        var principal = fixture.servicePrincipal(Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "app", RESOURCE);
        fixture.service(principal);
        when(fixture.ledger.toolRunEvidence(eq(fixture.actor), any())).thenReturn(runtime);
        when(fixture.services.require(runtime.authorization(), runtime.processLink())).thenReturn(fixture.serviceSource);

        var grantStore = mock(AgentServiceExecutionGrantStore.class);
        when(grantStore.usesLedger(fixture.ledger)).thenReturn(true);
        var reservation = new AgentServiceExecutionGrant.Reservation(runtime.authorization(), RESOURCE, fixture.serviceSource, 1,
                AgentServiceExecutionGrant.fingerprint(runtime.authorization(), RESOURCE, fixture.serviceSource));
        when(grantStore.current(runtime.authorization(), RESOURCE, fixture.serviceSource)).thenReturn(Optional.empty());
        when(grantStore.reserve(fixture.actor, RUN, RESOURCE, fixture.serviceSource)).thenReturn(Optional.of(reservation));
        when(grantStore.complete(eq(reservation), any())).thenReturn(true);
        var serviceIssuer = new TianjiServiceExecutionGrantIssuer(fixture.ledger, grantStore, fixture.services, RESOURCE,
                fixture.issuers, Map.of(ISSUER, fixture.client), fixture.clock, null, fixture.issuer);
        var dispatch = new TianjiLinkedDispatchProperties.Binding(ISSUER, 20, "worker", "app", "business", "application", "test", "source",
                Set.of(WorkerScopeTypeEnum.BPMN), List.of(new TianjiLinkedDispatchProperties.Definition("assistant", 1, HASH)));
        var request = linkedCommand(runtime, 1);
        var normalized = linkedCommand(runtime, 1L);
        var template = mock(AgentRuntimeTemplateCommand.class);
        when(fixture.ledger.runtimeExecutionMaterial(eq(fixture.actor), any())).thenReturn(
                new AgentLedgerService.RuntimeExecutionMaterialVO(runtime.run(), template, normalized, HASH, runtime.authorization()));

        new VerifiedAgentIssuerFilter().doFilter(fixture.servlet, new MockHttpServletResponse(), (incoming, response) ->
                serviceIssuer.ensureRuntime(fixture.authorized, principal, dispatch, request, runtime.run()));

        var calls = org.mockito.ArgumentCaptor.forClass(ServiceExecutionGrantContract.IssueRequest.class);
        verify(fixture.client, times(2)).issueService(calls.capture());
        assertNotEquals(AgentRunAuthorization.PURPOSE, calls.getAllValues().getFirst().purpose());
        assertEquals(AgentRunAuthorization.PURPOSE, calls.getAllValues().getLast().purpose());
        assertEquals(reservation.bindingSha256(), calls.getAllValues().getLast().bindingSha256());
        assertEquals(RUN, calls.getAllValues().getLast().intentId());
        verify(grantStore).complete(eq(reservation), any());
        verify(fixture.ledger, never()).executionMaterial(any(), any());

        new VerifiedAgentIssuerFilter().doFilter(fixture.servlet, new MockHttpServletResponse(), (incoming, response) ->
                assertUnavailable(() -> serviceIssuer.ensureRuntime(fixture.authorized, principal, dispatch,
                        linkedCommand(runtime, 2), runtime.run())));
        verify(fixture.client, times(2)).issueService(any());
        verify(grantStore).complete(eq(reservation), any());
    }

    private static LinkedAgentRuntimeCommand linkedCommand(AgentLedgerService.ToolRunEvidenceVO runtime, Number leaf) {
        var limits = AgentRuntimeTemplateCommand.TechnicalLimitsVO.fromExactWire(Map.of("totalTimeout", "PT2M",
                "modelIdleTimeout", "PT20S", "toolTimeout", "PT10S", "maxModelCalls", 2, "maxToolCalls", 2,
                "maxParallelism", 1, "contextWindowTokens", 8192, "maxOutputTokensPerCall", 1024));
        return new LinkedAgentRuntimeCommand("dispatch", runtime.processLink(), "node", "assistant", 1,
                HASH, HASH, HASH, HASH, Map.of("nested", List.of(Map.of("value", leaf))),
                Map.of("value", leaf), limits, NOW.plusSeconds(120), "trace");
    }

    @Test void v2_service_refuses_an_old_profile_and_never_attaches_a_changed_binding() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        fixture.servlet.setRequestURI("/api/tianji/v2/agent-process-dispatches/submit");
        var runtime = fixture.runtimeMaterial();
        var legacy = new AgentLedgerService.ToolRunEvidenceVO(runtime.run(), runtime.authorization(), runtime.processLink(),
                runtime.toolRefs(), runtime.maxToolCalls(), null, AgentLedgerService.RunProfileEnum.LEGACY_V1, null);
        when(fixture.ledger.toolRunEvidence(eq(fixture.actor), any())).thenReturn(legacy);
        assertUnavailable(() -> fixture.issuer.ensureRuntime(fixture.authorized, runtime.run()));
        verifyNoInteractions(fixture.client);
        verify(fixture.store, never()).reserve(any(), any());

        var current = new AtomicReference<>(runtime);
        when(fixture.ledger.toolRunEvidence(eq(fixture.actor), any())).thenAnswer(call -> current.get());
        when(fixture.services.require(runtime.authorization(), runtime.processLink())).thenReturn(fixture.serviceSource);
        doAnswer(call -> {
            current.set(new AgentLedgerService.ToolRunEvidenceVO(runtime.run(), runtime.authorization(), runtime.processLink(),
                    List.of(WRITE.reference()), runtime.maxToolCalls(), null, runtime.profile(), null));
            return fixture.serviceIssued(call.getArgument(0));
        }).when(fixture.client).issueService(any());
        new VerifiedAgentIssuerFilter().doFilter(fixture.servlet, new MockHttpServletResponse(), (incoming, response) ->
                assertUnavailable(() -> fixture.issuer.ensureRuntime(fixture.authorized, runtime.run())));
        verify(fixture.client).issueService(any());
        verify(fixture.store, never()).attach(any(), any());
    }

    @Test void service_missing_filter_scope_or_original_source_tuple_never_calls_the_owner() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted));
        fixture.service(fixture.servicePrincipal(Set.of("other:scope"), "app", RESOURCE));
        assertUnavailable(fixture::serviceEnsure);
        fixture.service(fixture.servicePrincipal(Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "another-app", RESOURCE));
        assertUnavailable(fixture::serviceEnsure);
        fixture.service(fixture.servicePrincipal(Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "app", URI.create("https://other.example/api")));
        assertUnavailable(fixture::serviceEnsure);
        verifyNoInteractions(fixture.client); verify(fixture.store, never()).reserve(any(), any());
    }

    @Test void service_bearer_or_current_principal_changed_during_exchange_is_not_attached() throws Exception {
        var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), System::nanoTime);
        doAnswer(call -> { fixture.servlet.removeHeader("Authorization"); fixture.servlet.addHeader("Authorization", "Bearer changed.service.token"); return fixture.serviceIssued(call.getArgument(0)); }).when(fixture.client).issueService(any());
        assertUnavailable(fixture::serviceEnsure);
        fixture.servlet.removeHeader("Authorization"); fixture.servlet.addHeader("Authorization", "Bearer " + TOKEN);
        doAnswer(call -> { fixture.service(fixture.servicePrincipal(Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "other-app", RESOURCE)); return fixture.serviceIssued(call.getArgument(0)); }).when(fixture.client).issueService(any());
        assertUnavailable(fixture::serviceEnsure); verify(fixture.store, never()).attach(any(), any());
    }

    @Test void legitimate_run_progress_does_not_require_full_dto_equality() {
        var fixture = new Fixture();
        doAnswer(call -> { fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.QUEUED, false, 99)); return fixture.userIssued(call.getArgument(0)); }).when(fixture.client).issue(any());
        var result = fixture.issuer.ensure(fixture.authorized, fixture.submitted); assertNotNull(result); assertEquals(99, result.revision()); assertEquals(1, fixture.attached.size());
    }

    @Test void ambient_transactions_and_invalid_constructor_dependencies_are_refused() {
        var fixture = new Fixture(); TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertUnavailable(() -> fixture.issuer.ensure(fixture.authorized, fixture.submitted)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(fixture.client); verify(fixture.store, never()).reserve(any(), any());
        for (var timeout : List.of(Duration.ofMillis(99), Duration.ofSeconds(5).plusNanos(1))) assertThrows(IllegalArgumentException.class,
                () -> fixture.create(fixture.store, timeout));
        assertThrows(IllegalArgumentException.class, () -> fixture.create(mock(ToolCapabilityStore.class), Duration.ofSeconds(1)));
    }

    @Test void public_constructor_reuses_the_existing_finite_user_issue_client_factory() {
        var fixture = new Fixture(); var starter = mock(IdpStarterProperties.class); var authority = mock(IdentityAuthorityProperties.class); var oauth = mock(IdpServiceOAuth2Client.class); var mapper = new ObjectMapper();
        when(starter.getResourceUri()).thenReturn(RESOURCE); when(authority.getTimeout()).thenReturn(Duration.ofSeconds(1));
        try (var factories = mockStatic(TianjiMemoryGrantIssuer.class)) {
            factories.when(() -> TianjiMemoryGrantIssuer.clients(starter, authority, fixture.issuers, oauth, mapper, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE)).thenReturn(Map.of(ISSUER, fixture.client));
            var issuer = new TianjiToolGrantIssuer(fixture.ledger, fixture.store, fixture.bindings, starter, authority, fixture.issuers, oauth, mapper);
            assertTrue(issuer.usesLedger(fixture.ledger)); assertTrue(issuer.usesBindings(fixture.bindings));
            factories.verify(() -> TianjiMemoryGrantIssuer.clients(starter, authority, fixture.issuers, oauth, mapper, IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE), times(1));
        }
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(AgentLedgerException.class, action); assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
        assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(TOKEN)); assertFalse(failure.getMessage().contains("private"));
    }
    private static ToolDescriptor descriptor(String key, String name, ToolPolicy.Risk risk) { return new ToolDescriptor(key, 1, name, "Fixed tool", new ToolSchema(Map.of(), Set.of()), new ToolSchema(Map.of(), Set.of()), new ToolPolicy(risk, key, "tianji.api.process.instance.start", HASH, null)); }
    private static String purpose(ToolDescriptor descriptor) { return descriptor == READ ? "TIANJI_TOOL_PROCESS_READ_V1" : "TIANJI_TOOL_PROCESS_START_V1"; }
    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    private static final class Fixture {
        final AgentLedgerService ledger = mock(AgentLedgerService.class); final ToolCapabilityStore store = mock(ToolCapabilityStore.class);
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class); final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
        final TrustedIssuerRegistry.TrustedIssuerVO originalTrust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
        final AtomicReference<TrustedIssuerRegistry.TrustedIssuerVO> trust = new AtomicReference<>(originalTrust); final AtomicReference<AgentLedgerService.ExecutionMaterial> material = new AtomicReference<>();
        final Map<String, ToolExecutionGrant.EvidenceVO> evidence = new HashMap<>(); final List<ToolExecutionGrant.EvidenceVO> attached = new ArrayList<>(); final MutableClock clock = new MutableClock();
        final LongSupplier nanoTime; final TrustedAgentActor actor; final AgentRunAuthorization.Request run; final TianjiAgentAccess.Authorized authorized;
        final TianjiServiceExecutionBindings services; final AgentServiceExecutionGrant.Subject serviceSource; final TianjiToolGrantBindings bindings; final TianjiToolGrantIssuer issuer; final AgentRunDTO submitted;
        final MockHttpServletRequest servlet;

        Fixture() { this(TrustedAgentActor.SubjectType.USER, List.of(READ), Duration.ofSeconds(1), System::nanoTime); }
        Fixture(TrustedAgentActor.SubjectType type, List<ToolDescriptor> visible, Duration timeout, LongSupplier nanoTime) {
            this.nanoTime = nanoTime; actor = TrustedAgentActor.verified(20, type, type == TrustedAgentActor.SubjectType.USER ? "alice" : "worker", ISSUER.toASCIIString());
            run = new AgentRunAuthorization.Request(actor, RUN, "assistant", 1, HASH, HASH, HASH, "policy-v1", true, NOW.plusSeconds(600)); authorized = new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(300), ISSUER);
            services = type == TrustedAgentActor.SubjectType.SERVICE ? mock(TianjiServiceExecutionBindings.class) : null;
            serviceSource = type == TrustedAgentActor.SubjectType.SERVICE ? new AgentServiceExecutionGrant.Subject("worker", "app", "business", "application", "test", SOURCE_RESOURCE) : null;
            if (services != null) { when(services.usesIssuerRegistry(issuers)).thenReturn(true); when(services.require(eq(run), any())).thenReturn(serviceSource); }
            bindings = new TianjiToolGrantBindings(List.of(READ, WRITE), List.of(mapping(READ), mapping(WRITE)), RESOURCE, services);
            material.set(material(visible, AgentRunStatusEnum.QUEUED, false, 1)); submitted = material.get().run();
            when(ledger.executionMaterial(eq(actor), any())).thenAnswer(call -> material.get()); when(ledger.get(eq(actor), any())).thenAnswer(call -> material.get().run()); when(ledger.databaseNow()).thenReturn(NOW);
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenAnswer(call -> Optional.ofNullable(trust.get())); when(store.usesLedger(ledger)).thenReturn(true);
            when(store.current(any(), any())).thenAnswer(call -> Optional.ofNullable(evidence.get(((ToolExecutionGrant.BindingVO) call.getArgument(0)).tool().key())));
            when(store.reserve(any(), any())).thenAnswer(call -> Optional.of(new ToolExecutionGrant.ReservationVO(call.getArgument(0), 1)));
            when(store.attach(any(), any())).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); var value = (ToolExecutionGrant.EvidenceVO) call.getArgument(0); attached.add(value); evidence.put(value.reservation().binding().tool().key(), value); return true; });
            when(client.issue(any())).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return userIssued(call.getArgument(0)); });
            when(client.issueService(any())).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return serviceIssued(call.getArgument(0)); });
            servlet = new MockHttpServletRequest("POST", type == TrustedAgentActor.SubjectType.USER ? "/api/tianji/v1/agent-runs" : "/api/tianji/v1/agent-process-dispatches/submit");
            servlet.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, ISSUER.toASCIIString()); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
            if (type == TrustedAgentActor.SubjectType.USER) resetUser(); else { servlet.addHeader("Authorization", "Bearer " + TOKEN); service(servicePrincipal(Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE), "app", RESOURCE)); }
            issuer = create(store, timeout);
        }
        AgentLedgerService.ToolRunEvidenceVO runtimeMaterial() {
            var grant = new AgentRunAuthorization.Request(actor, RUN, "assistant", 1, HASH, HASH, HASH, HASH, false, NOW.plusSeconds(600));
            var view = new AgentRunDTO(RUN, "assistant", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 1,
                    grant.deadlineAt(), NOW, null);
            return new AgentLedgerService.ToolRunEvidenceVO(view, grant,
                    new AgentProcessLink("source", WorkerScopeTypeEnum.BPMN, "root", "occurrence", HASH, NOW.plusSeconds(30)),
                    List.of(READ.reference()),
                    2, null, AgentLedgerService.RunProfileEnum.RUNTIME_V2, null);
        }

        void resetUser() { VerifiedUserTokenCarrier.set(servlet, TOKEN); user("alice", "token-1", NOW.plusSeconds(300), Set.of(RESOURCE.toASCIIString()), ISSUER.toASCIIString(), Set.of(TianjiAgentPermissions.START_SCOPE)); }
        IdentityPrincipal userIdentity(String subject, String tokenId, Instant expires, Set<String> audience) { return new IdentityPrincipal(subject, "20", tokenId, audience, NOW.minusSeconds(10), expires, AuthenticationContext.of("PASSWORD", NOW.minusSeconds(10))); }
        void user(String subject, String tokenId, Instant expires, Set<String> audience, String issuer, Set<String> scopes) {
            var snapshot = mock(SystemAuthorizationSnapshot.class); var permission = mock(EffectivePermissionSnapshot.class);
            when(snapshot.identitySub()).thenReturn(subject); when(snapshot.tenantId()).thenReturn("20"); when(snapshot.permissions()).thenReturn(Set.of("TIANJI_AGENT_START")); when(snapshot.permissionSnapshot()).thenReturn(permission); when(permission.issuer()).thenReturn(issuer);
            var details = new Rbac3UserDetails(userIdentity(subject, tokenId, expires, audience), snapshot);
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(details, scopes.stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList()));
        }
        ServiceIdentityPrincipal servicePrincipal(Set<String> scopes, String appId, URI resource) { return new ServiceIdentityPrincipal("worker", "20", "worker", "service-token-1", resource, 1, scopes, "business", "application", "test", "credential", NOW.minusSeconds(10), NOW.plusSeconds(300), appId, ServiceTokenContext.TENANT); }
        void service(ServiceIdentityPrincipal principal) { SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), ISSUER)); }
        AgentRunDTO serviceEnsure() throws Exception {
            var result = new AtomicReference<AgentRunDTO>(); new VerifiedAgentIssuerFilter().doFilter(servlet, new MockHttpServletResponse(), (request, response) -> result.set(issuer.ensure(authorized, submitted))); return result.get();
        }
        TianjiToolGrantBindings.BindingVO mapping(ToolDescriptor descriptor) { return new TianjiToolGrantBindings.BindingVO(20, actor.issuerFingerprint(), actor.subjectType(), "assistant", 1, HASH, descriptor.reference(), purpose(descriptor), TianjiAgentPermissions.START_API); }
        ToolExecutionGrant.BindingVO scope(ToolDescriptor descriptor) { return new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), purpose(descriptor), RESOURCE, TianjiAgentPermissions.START_API, serviceSource); }
        ToolExecutionGrant.EvidenceVO freshEvidence(ToolDescriptor descriptor, long epoch) { return new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(scope(descriptor), epoch), (actor.subjectType() == TrustedAgentActor.SubjectType.USER ? "9" : "8") + "1".repeat(62), "ENC:v1:key:A:sealed", NOW.plusSeconds(60)); }
        TianjiToolGrantIssuer create(ToolCapabilityStore storage, Duration timeout) { return new TianjiToolGrantIssuer(ledger, storage, bindings, RESOURCE, issuers, actor.subjectType() == TrustedAgentActor.SubjectType.USER ? Map.of(ISSUER, client) : Map.of(), actor.subjectType() == TrustedAgentActor.SubjectType.SERVICE ? Map.of(ISSUER, client) : Map.of(), timeout, clock, nanoTime); }
        AgentLedgerService.ExecutionMaterial material(List<ToolDescriptor> visible, AgentRunStatusEnum status, boolean cancelled, long revision) {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofSeconds(10), 5, visible.isEmpty() ? 0 : 5, 1000, 1000, null, null, 2, visible.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet()), null, null);
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "input-v1", "output-v1", new DefinitionDependenciesDTO(List.of(), List.of(), visible.stream().map(ToolDescriptor::reference).toList(), "model", "policy-v1"), policy, Map.of());
            var view = new AgentRunDTO(RUN, "assistant", 1, "session", status, null, cancelled, 1, revision, revision, run.deadlineAt(), NOW.minusSeconds(1), null);
            return new AgentLedgerService.ExecutionMaterial(view, new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("task", List.of(), Map.of()), null), run);
        }
        ExecutionGrantContract.Issued userIssued(ExecutionGrantContract.IssueRequest request) { return new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket("9" + "1".repeat(62), "ENC:v1:key:A:sealed"), NOW.plusSeconds(60)); }
        ServiceExecutionGrantContract.Issued serviceIssued(ServiceExecutionGrantContract.IssueRequest request) { return new ServiceExecutionGrantContract.Issued(new ServiceExecutionGrantContract.Ticket("8" + "1".repeat(62), "ENC:v1:key:A:sealed"), NOW.plusSeconds(60)); }
    }
}
