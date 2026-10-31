package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServiceExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SDK contract types with controlled decisions; these tests do not contact deployed IAM or verify ciphertext. */
class TianjiToolGrantAuthorizerTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final URI ISSUER = URI.create("https://idp.example/20"), RESOURCE = URI.create("https://agent.example/api");
    private static final String HASH = "a".repeat(64), RUN = "00000000-0000-0000-0000-000000000001", INTENT = "00000000-0000-0000-0000-000000000002";
    private static final ToolDescriptor READ = descriptor("process.read", "read_process", ToolPolicy.Risk.READ_ONLY);
    private static final ToolDescriptor WRITE = descriptor("process.start", "start_process", ToolPolicy.Risk.IDEMPOTENT_WRITE);

    @AfterEach void clearContext() { MDC.clear(); }

    @Test void user_check_uses_the_independent_capability_and_real_upstream_version_slots() {
        try (var fixture = new Fixture()) {
            var request = fixture.request(READ); var result = fixture.authority.check(request, ToolExecutionAuthorization.Boundary.PREPARE);
            assertNotNull(result); assertEquals(ToolExecutionAuthorization.Decision.ALLOW, result.decision()); assertEquals(request, result.request());
            assertNotNull(result.audit()); var audit = result.audit();
            assertEquals(Long.valueOf(7), audit.authVersion()); assertNull(audit.catalogVersion()); assertEquals(Long.valueOf(9), audit.policyVersion());
            assertEquals(fixture.evidence(READ).grantId(), audit.grantId()); assertNotNull(audit.localCheckId()); assertEquals(NOW, audit.checkedAt());
            var captured = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.Expectation.class);
            verify(fixture.client).decide(any(), captured.capture()); var expected = captured.getValue(); var binding = fixture.scope(READ);
            assertEquals(binding.capabilityId(), expected.intentId()); assertEquals(binding.bindingSha256(), expected.bindingSha256());
            assertEquals(binding.purpose(), expected.purpose()); assertEquals(RESOURCE.toASCIIString(), expected.resource().toASCIIString()); assertFalse(expected.sessionRequired());
            assertEquals(fixture.run.deadlineAt(), expected.deadlineAt()); assertNotEquals(fixture.run.runId(), expected.intentId());
            assertNotEquals(request.bindingSha256(), expected.bindingSha256()); assertNotEquals(AgentExecutionGrant.fingerprint(fixture.run, RESOURCE), expected.bindingSha256());
            assertTrue(fixture.authority.usesLedger(fixture.ledger)); assertTrue(fixture.authority.usesBindings(fixture.bindings));
            assertTrue(fixture.authority.usesIssuerRegistry(fixture.issuers)); assertTrue(fixture.authority.usesResource(RESOURCE));
            assertFalse(fixture.authority.usesResource(URI.create("https://AGENT.example/api")));
            verify(fixture.client, never()).decideService(any(), any()); verify(fixture.client, never()).decideScopes(any(), any());
        }
    }

    @Test void service_check_uses_the_original_six_field_source_and_catalog_version() {
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.SERVICE, List.of(READ), Duration.ofSeconds(1), 2, System::nanoTime)) {
            var result = fixture.authority.check(fixture.request(READ), ToolExecutionAuthorization.Boundary.DISPATCH);
            assertNotNull(result); assertEquals(ToolExecutionAuthorization.Decision.ALLOW, result.decision()); assertNotNull(result.audit());
            assertNull(result.audit().authVersion()); assertEquals(Long.valueOf(11), result.audit().catalogVersion()); assertEquals(Long.valueOf(9), result.audit().policyVersion());
            var captured = org.mockito.ArgumentCaptor.forClass(HttpExecutionAuthorizationClient.ServiceExpectation.class);
            verify(fixture.client).decideService(any(), captured.capture());
            var source = fixture.subject.get();
            assertEquals(new ServiceExecutionAuthorizationContract.Subject(source.clientId(), source.appId(), source.businessCode(), source.applicationCode(),
                    source.environment(), source.resourceUri().toASCIIString()), captured.getValue().subjectSource());
            assertFalse(captured.getValue().intent().sessionRequired());
            verify(fixture.client, never()).decide(any(), any());
            doAnswer(call -> {
                var reply = fixture.serviceDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30));
                fixture.subject.set(new AgentServiceExecutionGrant.Subject("worker", "app", "business", "application", "other", URI.create("https://worker.example/source")));
                return reply;
            }).when(fixture.client).decideService(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
        }
    }

    @Test void visible_tool_reference_backend_and_intent_kind_must_match_before_pdp() {
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ, WRITE), Duration.ofSeconds(1), 2, System::nanoTime)) {
            var valid = fixture.request(READ);
            var badBackend = new ToolExecutionAuthorization.Request(valid.run(), valid.lease(), valid.tool(), "b".repeat(64), valid.parametersSha256(), null, valid.deadlineAt());
            var hidden = new ToolExecutionAuthorization.Request(valid.run(), valid.lease(), new top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef("hidden.tool", 1, HASH), HASH, HASH, null, valid.deadlineAt());
            var readIntent = new ToolExecutionAuthorization.Request(valid.run(), valid.lease(), valid.tool(), valid.bindingSha256(), valid.parametersSha256(), INTENT, valid.deadlineAt());
            var write = fixture.request(WRITE);
            var noWriteIntent = new ToolExecutionAuthorization.Request(write.run(), write.lease(), write.tool(), write.bindingSha256(), write.parametersSha256(), null, write.deadlineAt());
            for (var request : List.of(badBackend, hidden, readIntent, noWriteIntent)) assertEquals(ToolExecutionAuthorization.Decision.DENY, fixture.authority.authorize(request, ToolExecutionAuthorization.Boundary.PREPARE));
            verifyNoInteractions(fixture.client);
        }
    }

    @Test void missing_or_unknown_sources_evidence_and_trust_are_unavailable() {
        try (var fixture = new Fixture()) {
            fixture.evidence.clear(); assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 1, "sealed")); fixture.trust.set(null);
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            fixture.trust.set(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.INACTIVE, 1));
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            fixture.trust.set(fixture.originalTrust); doThrow(new IllegalStateException("private source", new RuntimeException("private cause"))).when(fixture.ledger).executionMaterial(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            verifyNoInteractions(fixture.client);
        }
    }

    @Test void postflight_evidence_epoch_cipher_trust_and_source_drift_never_allow() {
        try (var fixture = new Fixture()) {
            var original = fixture.material.get();
            for (var mutation : List.<Runnable>of(
                    () -> fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 2, "sealed")),
                    () -> fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 1, "other")),
                    () -> fixture.trust.set(null),
                    () -> fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.CANCELLED, true, 2)))) {
                fixture.material.set(original); fixture.trust.set(fixture.originalTrust); fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 1, "sealed"));
                doAnswer(call -> { var response = fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); mutation.run(); return response; }).when(fixture.client).decide(any(), any());
                assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            }
        }
    }

    @Test void confirmed_denial_requires_unchanged_source_and_carries_no_invented_upstream_fields() {
        try (var fixture = new Fixture()) {
            doThrow(new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException()).when(fixture.client).decide(any(), any());
            var result = fixture.authority.check(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE);
            assertNotNull(result); assertEquals(ToolExecutionAuthorization.Decision.DENY, result.decision()); assertNotNull(result.audit());
            assertNull(result.audit().grantId()); assertNull(result.audit().authVersion()); assertNull(result.audit().catalogVersion()); assertNull(result.audit().policyVersion()); assertNull(result.audit().expiresAt());
            doAnswer(call -> { fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 2, "sealed")); throw new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException(); }).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            doThrow(new IllegalStateException("private remote cause")).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
        }
    }

    @Test void both_sealed_and_decided_windows_cover_the_request_without_extending_the_ticket() {
        try (var fixture = new Fixture()) {
            var reservation = fixture.evidence(READ).reservation();
            fixture.evidence.put(READ.key(), new ToolExecutionGrant.EvidenceVO(reservation, fixture.evidence(READ).grantId(), fixture.evidence(READ).ciphertext(), NOW.plusSeconds(4)));
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE)); verifyNoInteractions(fixture.client);
            fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 1, "sealed"));
            for (var expiry : List.of(NOW.plusSeconds(4), NOW.plusSeconds(61))) {
                doAnswer(call -> fixture.userDecision(call.getArgument(0), call.getArgument(1), expiry)).when(fixture.client).decide(any(), any());
                assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            }
            doAnswer(call -> fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(5))).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.ALLOW, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            doAnswer(call -> fixture.userDecision(new ExecutionAuthorizationContract.Request("tianji", "9" + "8".repeat(62), "ENC:v1:key:A:sealed"), call.getArgument(1), NOW.plusSeconds(30))).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            doAnswer(call -> { fixture.dbNow.set(NOW.plusSeconds(6)); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
        }
    }

    @Test void queue_gate_checks_every_visible_scope_and_plain_runs_do_not_need_a_fake_lease_or_pdp() {
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ, WRITE), Duration.ofSeconds(1), 2, System::nanoTime)) {
            fixture.material.set(fixture.material(List.of(READ, WRITE), AgentRunStatusEnum.QUEUED, false, 1));
            assertEquals(AgentRunAuthorization.Decision.ALLOW, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM)); verify(fixture.client, times(2)).decide(any(), any());
            fixture.evidence.remove(WRITE.key());
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM));
        }
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(), Duration.ofSeconds(1), 2, System::nanoTime)) {
            fixture.material.set(fixture.material(List.of(), AgentRunStatusEnum.QUEUED, false, 1));
            assertEquals(AgentRunAuthorization.Decision.ALLOW, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM));
            verifyNoInteractions(fixture.client); verify(fixture.store, never()).current(any(), any());
        }
    }

    @Test void queue_gate_rechecks_first_evidence_after_the_last_tool_without_repeating_pdp() {
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ, WRITE), Duration.ofSeconds(1), 2, System::nanoTime)) {
            fixture.material.set(fixture.material(List.of(READ, WRITE), AgentRunStatusEnum.QUEUED, false, 1));
            doAnswer(call -> {
                HttpExecutionAuthorizationClient.Expectation expected = call.getArgument(1);
                if (expected.purpose().equals(purpose(WRITE))) fixture.evidence.put(READ.key(), fixture.freshEvidence(READ, 2, "sealed"));
                return fixture.userDecision(call.getArgument(0), expected, NOW.plusSeconds(30));
            }).when(fixture.client).decide(any(), any());
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM));
            verify(fixture.client, times(2)).decide(any(), any());
        }
    }

    @Test void multiple_tools_share_one_overall_monotonic_budget() {
        var nanos = new AtomicLong();
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ, WRITE), Duration.ofSeconds(1), 2, nanos::get)) {
            fixture.material.set(fixture.material(List.of(READ, WRITE), AgentRunStatusEnum.QUEUED, false, 1));
            doAnswer(call -> { nanos.addAndGet(600_000_000L); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM));
            verify(fixture.client, times(2)).decide(any(), any());
        }
    }

    @Test void wall_clock_rollback_does_not_extend_the_monotonic_call_budget() {
        var nanos = new AtomicLong();
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ), Duration.ofSeconds(1), 2, nanos::get)) {
            doAnswer(call -> { fixture.clock.now.set(NOW.minusSeconds(60)); nanos.addAndGet(2_000_000_000L); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            verify(fixture.client).decide(any(), any());
        }
    }

    @Test void normal_progress_revision_changes_do_not_invalidate_the_immutable_source() {
        try (var fixture = new Fixture()) {
            doAnswer(call -> { fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.RUNNING, false, 99)); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.ALLOW, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
        }
    }

    @Test void invalid_lifecycle_ambient_transactions_and_closed_instances_make_no_pdp_calls() {
        try (var fixture = new Fixture()) {
            assertThrows(IllegalArgumentException.class, () -> fixture.authority.check(null, ToolExecutionAuthorization.Boundary.PREPARE));
            assertThrows(IllegalArgumentException.class, () -> fixture.authority.check(fixture.request(READ), null));
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(null, ToolExecutionAuthorization.Boundary.PREPARE));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
                assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.MODEL));
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.QUEUED, false, 1));
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.MODEL));
            fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.RUNNING, false, 1));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, fixture.authority.authorizeRun(fixture.run, AgentRunAuthorization.Boundary.CLAIM));
            fixture.material.set(fixture.material(List.of(READ), AgentRunStatusEnum.RUNNING, true, 1));
            assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            fixture.authority.close(); assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            verifyNoInteractions(fixture.client); verify(fixture.store, never()).current(any(), any());
        }
    }

    @Test void trace_context_is_propagated_without_inheriting_request_thread_locals() {
        var inherited = new InheritableThreadLocal<String>(); inherited.set("request-only-proof");
        MDC.put("traceId", "a".repeat(32)); MDC.put("spanId", "b".repeat(16));
        try (var fixture = new Fixture()) {
            doAnswer(call -> { assertNull(inherited.get()); assertEquals("a".repeat(32), MDC.get("traceId")); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            assertEquals(ToolExecutionAuthorization.Decision.ALLOW, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
            assertEquals("request-only-proof", inherited.get()); assertEquals("a".repeat(32), MDC.get("traceId"));
        } finally { inherited.remove(); }
    }

    @Test void an_uninterruptible_dependency_retains_capacity_until_it_actually_returns() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var fixture = new Fixture(TrustedAgentActor.SubjectType.USER, List.of(READ), Duration.ofMillis(120), 1, System::nanoTime)) {
            doAnswer(call -> { entered.countDown(); awaitIgnoringInterrupts(release); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            try {
                assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE));
                verify(fixture.client).decide(any(), any());
            } finally { release.countDown(); }
        }
    }

    @Test void close_prevents_a_late_allow_from_a_real_inflight_task() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var answer = new AtomicReference<ToolExecutionAuthorization.Decision>();
        try (var fixture = new Fixture()) {
            doAnswer(call -> { entered.countDown(); awaitIgnoringInterrupts(release); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            Thread caller = Thread.ofPlatform().daemon().start(() -> answer.set(fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE)));
            try { assertTrue(entered.await(1, TimeUnit.SECONDS)); fixture.authority.close(); release.countDown(); caller.join(2000); assertFalse(caller.isAlive()); assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, answer.get()); }
            finally { release.countDown(); caller.join(2000); }
        }
    }

    @Test void interrupted_callers_keep_the_interrupt_flag_and_cannot_receive_allow() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var answer = new AtomicReference<ToolExecutionAuthorization.Decision>(); var interrupted = new AtomicBoolean();
        try (var fixture = new Fixture()) {
            doAnswer(call -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return fixture.userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)); }).when(fixture.client).decide(any(), any());
            Thread caller = Thread.ofPlatform().daemon().start(() -> { answer.set(fixture.authority.authorize(fixture.request(READ), ToolExecutionAuthorization.Boundary.PREPARE)); interrupted.set(Thread.currentThread().isInterrupted()); });
            try { assertTrue(entered.await(1, TimeUnit.SECONDS)); caller.interrupt(); caller.join(2000); assertFalse(caller.isAlive()); assertTrue(interrupted.get()); assertEquals(ToolExecutionAuthorization.Decision.UNAVAILABLE, answer.get()); }
            finally { release.countDown(); caller.join(2000); }
        }
    }

    @Test void constructor_bounds_and_dependency_identities_are_checked_before_use() {
        try (var fixture = new Fixture()) {
            for (var timeout : List.of(Duration.ofMillis(99), Duration.ofSeconds(10).plusNanos(1))) assertThrows(IllegalArgumentException.class,
                    () -> fixture.create(fixture.bindings, fixture.store, timeout, 1));
            for (int capacity : List.of(0, 9)) assertThrows(IllegalArgumentException.class, () -> fixture.create(fixture.bindings, fixture.store, Duration.ofSeconds(1), capacity));
            var foreignStore = mock(ToolCapabilityStore.class);
            assertThrows(IllegalArgumentException.class, () -> fixture.create(fixture.bindings, foreignStore, Duration.ofSeconds(1), 1));
        }
    }

    @Test void public_constructor_reuses_the_finite_existing_sdk_client_factory() {
        try (var fixture = new Fixture(); var factories = mockStatic(TianjiExecutionGrantAuthorizer.class)) {
            var starter = mock(IdpStarterProperties.class); var authority = mock(IdentityAuthorityProperties.class); var oauth = mock(IdpServiceOAuth2Client.class); var mapper = new ObjectMapper(); var rbac = mock(Rbac3StarterProperties.class);
            when(starter.getResourceUri()).thenReturn(RESOURCE); when(rbac.getSystemCode()).thenReturn("tianji");
            factories.when(() -> TianjiExecutionGrantAuthorizer.systemCode("tianji")).thenCallRealMethod();
            factories.when(() -> TianjiExecutionGrantAuthorizer.clients(starter, authority, fixture.issuers, oauth, mapper, rbac,
                    IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionAuthorizationContract.SERVICE_SCOPE)).thenReturn(fixture.users());
            try (var constructed = new TianjiToolGrantAuthorizer(fixture.ledger, fixture.store, fixture.bindings, starter, authority, fixture.issuers, oauth, mapper, rbac, Duration.ofSeconds(1), 2)) {
                // Construction uses the finite factory; this test performs no credential or HTTP request.
                assertTrue(constructed.usesLedger(fixture.ledger)); assertTrue(constructed.usesBindings(fixture.bindings));
                factories.verify(() -> TianjiExecutionGrantAuthorizer.clients(starter, authority, fixture.issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionAuthorizationContract.SERVICE_SCOPE), times(1));
            }
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) { while (latch.getCount() > 0) { try { latch.await(); } catch (InterruptedException ignored) { } } }
    private static ToolDescriptor descriptor(String key, String name, ToolPolicy.Risk risk) {
        return new ToolDescriptor(key, 1, name, "Fixed tool", new ToolSchema(Map.of(), Set.of()), new ToolSchema(Map.of(), Set.of()), new ToolPolicy(risk, key, "tianji.api.process.instance.start", HASH, null));
    }
    private static String purpose(ToolDescriptor descriptor) { return descriptor == READ ? "TIANJI_TOOL_PROCESS_READ_V1" : "TIANJI_TOOL_PROCESS_START_V1"; }

    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    private static final class Fixture implements AutoCloseable {
        final AgentLedgerService ledger = mock(AgentLedgerService.class); final ToolCapabilityStore store = mock(ToolCapabilityStore.class);
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class); final HttpExecutionAuthorizationClient client = mock(HttpExecutionAuthorizationClient.class);
        final TrustedIssuerRegistry.TrustedIssuerVO originalTrust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
        final AtomicReference<TrustedIssuerRegistry.TrustedIssuerVO> trust = new AtomicReference<>(originalTrust); final AtomicReference<AgentServiceExecutionGrant.Subject> subject = new AtomicReference<>();
        final ConcurrentHashMap<String, ToolExecutionGrant.EvidenceVO> evidence = new ConcurrentHashMap<>(); final AtomicReference<AgentLedgerService.ExecutionMaterial> material = new AtomicReference<>();
        final AtomicReference<Instant> dbNow = new AtomicReference<>(NOW); final MutableClock clock = new MutableClock(); final LongSupplier nanoTime;
        final TrustedAgentActor actor; final AgentRunAuthorization.Request run; final TianjiToolGrantBindings bindings; final TianjiToolGrantAuthorizer authority;

        Fixture() { this(TrustedAgentActor.SubjectType.USER, List.of(READ), Duration.ofSeconds(1), 2, System::nanoTime); }
        Fixture(TrustedAgentActor.SubjectType type, List<ToolDescriptor> visible, Duration timeout, int capacity, LongSupplier nanoTime) {
            this.nanoTime = nanoTime; actor = TrustedAgentActor.verified(20, type, type == TrustedAgentActor.SubjectType.USER ? "alice" : "worker", ISSUER.toASCIIString());
            run = new AgentRunAuthorization.Request(actor, RUN, "assistant", 1, HASH, HASH, HASH, "policy-v1", true, NOW.plusSeconds(600));
            TianjiServiceExecutionBindings services = null;
            if (type == TrustedAgentActor.SubjectType.SERVICE) {
                services = mock(TianjiServiceExecutionBindings.class); when(services.usesIssuerRegistry(issuers)).thenReturn(true);
                subject.set(new AgentServiceExecutionGrant.Subject("worker", "app", "business", "application", "test", URI.create("https://worker.example/source")));
                when(services.require(eq(run), any())).thenAnswer(call -> subject.get());
            }
            bindings = new TianjiToolGrantBindings(List.of(READ, WRITE), List.of(mapping(READ), mapping(WRITE)), RESOURCE, services);
            when(store.usesLedger(ledger)).thenReturn(true); when(store.current(any(), any())).thenAnswer(call -> Optional.ofNullable(evidence.get(((ToolExecutionGrant.BindingVO) call.getArgument(0)).tool().key())));
            when(ledger.executionMaterial(eq(actor), any())).thenAnswer(call -> material.get()); when(ledger.databaseNow()).thenAnswer(call -> dbNow.get());
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenAnswer(call -> Optional.ofNullable(trust.get()));
            when(client.decide(any(), any())).thenAnswer(call -> userDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)));
            when(client.decideService(any(), any())).thenAnswer(call -> serviceDecision(call.getArgument(0), call.getArgument(1), NOW.plusSeconds(30)));
            material.set(material(visible, AgentRunStatusEnum.RUNNING, false, 1));
            evidence.put(READ.key(), freshEvidence(READ, 1, "sealed")); evidence.put(WRITE.key(), freshEvidence(WRITE, 1, "sealed"));
            authority = create(bindings, store, timeout, capacity);
        }
        TianjiToolGrantBindings.BindingVO mapping(ToolDescriptor descriptor) { return new TianjiToolGrantBindings.BindingVO(20, actor.issuerFingerprint(), actor.subjectType(), "assistant", 1, HASH, descriptor.reference(), purpose(descriptor), "tianji.api.agent.start"); }
        ToolExecutionGrant.BindingVO scope(ToolDescriptor descriptor) { return new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), purpose(descriptor), RESOURCE, "tianji.api.agent.start", subject.get()); }
        ToolExecutionGrant.EvidenceVO evidence(ToolDescriptor descriptor) { return evidence.get(descriptor.key()); }
        ToolExecutionGrant.EvidenceVO freshEvidence(ToolDescriptor descriptor, long epoch, String ciphertext) {
            String prefix = actor.subjectType() == TrustedAgentActor.SubjectType.USER ? "9" : "8";
            return new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(scope(descriptor), epoch), prefix + (descriptor == READ ? "1" : "2").repeat(62), "ENC:v1:key:A:" + ciphertext, NOW.plusSeconds(60));
        }
        ToolExecutionAuthorization.Request request(ToolDescriptor descriptor) { return new ToolExecutionAuthorization.Request(run, new AgentRunLease(RUN, "runtime", 1, NOW.minusSeconds(1)), descriptor.reference(), descriptor.policy().bindingSha256(), "f".repeat(64), descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY ? null : INTENT, NOW.plusSeconds(5)); }
        Map<String, TianjiExecutionGrantAuthorizer.BoundClient> users() { return actor.subjectType() == TrustedAgentActor.SubjectType.USER ? Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", client)) : Map.of(); }
        Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients() { return actor.subjectType() == TrustedAgentActor.SubjectType.SERVICE ? Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "20", client)) : Map.of(); }
        TianjiToolGrantAuthorizer create(TianjiToolGrantBindings selected, ToolCapabilityStore storage, Duration timeout, int capacity) { return new TianjiToolGrantAuthorizer(ledger, storage, selected, RESOURCE, "tianji", issuers, users(), clients(), timeout, capacity, clock, nanoTime); }
        AgentLedgerService.ExecutionMaterial material(List<ToolDescriptor> visible, AgentRunStatusEnum status, boolean cancelled, long revision) {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofSeconds(10), 5, visible.isEmpty() ? 0 : 5, 1000, 1000, null, null, 2,
                    visible.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet()), null, null);
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "input-v1", "output-v1", new DefinitionDependenciesDTO(List.of(), List.of(), visible.stream().map(ToolDescriptor::reference).toList(), "model", "policy-v1"), policy, Map.of());
            var runView = new AgentRunDTO(RUN, "assistant", 1, "session", status, null, cancelled, 1, revision, revision, run.deadlineAt(), NOW.minusSeconds(1), null);
            return new AgentLedgerService.ExecutionMaterial(runView, new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("task", List.of(), Map.of()), null), run);
        }
        ExecutionAuthorizationContract.Decision userDecision(ExecutionAuthorizationContract.Request request, HttpExecutionAuthorizationClient.Expectation expected, Instant expires) {
            return new ExecutionAuthorizationContract.Decision(request.grantId(), expected.issuer().toASCIIString(), expected.tenantId(), expected.subject(), expected.resource().toASCIIString(), expected.purpose(), expected.intentId(), expected.bindingSha256(), false, expected.deadlineAt(), expires, 7, 9);
        }
        ServiceExecutionAuthorizationContract.Decision serviceDecision(ServiceExecutionAuthorizationContract.Request request, HttpExecutionAuthorizationClient.ServiceExpectation expected, Instant expires) {
            var intent = expected.intent(); return new ServiceExecutionAuthorizationContract.Decision(request.grantId(), intent.issuer().toASCIIString(), intent.tenantId(), intent.subject(), expected.subjectSource(), intent.resource().toASCIIString(), intent.purpose(), intent.intentId(), intent.bindingSha256(), false, intent.deadlineAt(), expires, 11, 9);
        }
        @Override public void close() { authority.close(); }
    }
}
