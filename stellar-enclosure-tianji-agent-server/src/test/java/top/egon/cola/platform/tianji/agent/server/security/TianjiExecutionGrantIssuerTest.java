package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiExecutionGrantIssuerTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final String ID = "9" + "1".repeat(62);
    private static final String USER_TOKEN = "user-proof-sentinel";
    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toString());
    private final TianjiAgentAccess.Authorized authorized = new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(60), ISSUER);
    private final AgentRunDTO run = run(AgentRunStatusEnum.QUEUED);
    private final AgentRunAuthorization.Request binding = new AgentRunAuthorization.Request(actor, RUN, "review", 1,
            "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, NOW.plusSeconds(600));
    private final AgentExecutionGrant.Reservation reservation = new AgentExecutionGrant.Reservation(binding, RESOURCE, 1,
            AgentExecutionGrant.fingerprint(binding, RESOURCE));
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentExecutionGrantStore store = mock(AgentExecutionGrantStore.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);
    private TianjiExecutionGrantIssuer issuer;

    @BeforeEach void setup() {
        var request = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(request, USER_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust));
        when(ledger.executionMaterial(actor, new GetAgentRunQuery(RUN))).thenReturn(new AgentLedgerService.ExecutionMaterial(run, null, binding));
        when(ledger.get(actor, new GetAgentRunQuery(RUN))).thenReturn(run);
        when(store.current(binding, RESOURCE)).thenReturn(Optional.empty());
        when(store.reserve(actor, RUN, RESOURCE)).thenReturn(Optional.of(reservation));
        when(client.issue(any())).thenReturn(new ExecutionGrantContract.Issued(
                new ExecutionGrantContract.Ticket(ID, "ENC:v1:key:nonce:sealed"), NOW.plusSeconds(300)));
        when(store.complete(any(), any())).thenReturn(true);
        issuer = new TianjiExecutionGrantIssuer(ledger, store, RESOURCE, issuers, Map.of(ISSUER, client));
    }
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test void issues_the_request_only_user_proof_for_the_exact_persisted_intent() {
        assertEquals(run, issuer.ensure(authorized, run));
        var request = org.mockito.ArgumentCaptor.forClass(ExecutionGrantContract.IssueRequest.class);
        verify(client).issue(request.capture());
        assertEquals(USER_TOKEN, request.getValue().userToken()); assertEquals(AgentRunAuthorization.PURPOSE, request.getValue().purpose());
        assertEquals(RUN, request.getValue().intentId()); assertEquals(reservation.bindingSha256(), request.getValue().bindingSha256());
        assertTrue(request.getValue().sessionRequired()); assertEquals(binding.deadlineAt(), request.getValue().deadlineAt());
        verify(store).complete(eq(reservation), argThat(ticket -> ticket.grantId().equals(ID)
                && ticket.ciphertext().equals("ENC:v1:key:nonce:sealed")));
        assertFalse(request.getValue().toString().contains(USER_TOKEN));
    }

    @Test void missing_proof_denial_and_authority_failure_fail_closed_without_retry_or_secret() {
        RequestContextHolder.resetRequestAttributes();
        var missing = assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, missing.code()); verifyNoInteractions(client);
        var request = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(request, USER_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        doThrow(new HttpExecutionGrantClient.ExecutionGrantDeniedException()).when(client).issue(any());
        var denied = assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run));
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, denied.code());
        doThrow(new HttpExecutionGrantClient.ExecutionGrantUnavailableException()).when(client).issue(any());
        var unavailable = assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, unavailable.code());
        assertFalse(unavailable.toString().contains(USER_TOKEN)); assertNull(unavailable.getCause());
        verify(client, times(2)).issue(any()); verify(store, never()).complete(any(), any());
    }

    @Test void stale_or_cancelled_responses_do_not_claim_a_saved_grant() {
        when(store.complete(any(), any())).thenReturn(false);
        when(store.current(binding, RESOURCE)).thenReturn(Optional.empty(), Optional.empty());
        when(ledger.get(actor, new GetAgentRunQuery(RUN))).thenReturn(run(AgentRunStatusEnum.CANCELLED));
        assertEquals(AgentRunStatusEnum.CANCELLED, issuer.ensure(authorized, run).status());
        when(ledger.get(actor, new GetAgentRunQuery(RUN))).thenReturn(run);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run)).code());
    }

    @Test void existing_grant_or_terminal_run_never_reissues_and_other_identities_cannot_select_a_client() {
        var ticket = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:existing", NOW.plusSeconds(300));
        when(store.current(binding, RESOURCE)).thenReturn(Optional.of(ticket));
        assertEquals(run, issuer.ensure(authorized, run)); verifyNoInteractions(client);
        assertEquals(AgentRunStatusEnum.CANCELLED, issuer.ensure(authorized, run(AgentRunStatusEnum.CANCELLED)).status());
        var other = new TianjiAgentAccess.Authorized(actor, NOW.plusSeconds(60), URI.create("https://other.example/tenants/21"));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> issuer.ensure(other, run)).code());
        var serviceActor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "agent", ISSUER.toString());
        assertEquals(run, issuer.ensure(new TianjiAgentAccess.Authorized(serviceActor, NOW.plusSeconds(60), ISSUER), run));
        verifyNoInteractions(client);
    }

    @Test void a_trust_change_after_remote_issue_discards_the_response() {
        when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust), Optional.empty());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run)).code());
        verify(store, never()).complete(any(), any());
    }

    @Test void configuration_is_default_off_and_requires_the_agent_api() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(TianjiExecutionGrantConfiguration.class)
                .run(context -> assertTrue(context.getBeansOfType(TianjiExecutionGrantIssuer.class).isEmpty()));
        assertThrows(IllegalStateException.class, () -> new TianjiExecutionGrantConfiguration()
                .tianjiExecutionGrantStore(null, null, new org.springframework.mock.env.MockEnvironment()));
    }

    @Test void an_existing_user_grant_still_prepares_tools_after_memory_and_preferences() {
        var memory = mock(TianjiMemoryGrantIssuer.class); var preferences = mock(TianjiMemoryPreferenceGrantIssuer.class);
        var tools = mock(TianjiToolGrantIssuer.class);
        when(memory.usesLedger(ledger)).thenReturn(true); when(memory.usesIssuerRegistry(issuers)).thenReturn(true); when(memory.usesResource(RESOURCE)).thenReturn(true);
        when(preferences.usesLedger(ledger)).thenReturn(true); when(preferences.usesIssuerRegistry(issuers)).thenReturn(true); when(preferences.usesResource(RESOURCE)).thenReturn(true);
        when(tools.usesLedger(ledger)).thenReturn(true); when(tools.usesIssuerRegistry(issuers)).thenReturn(true); when(tools.usesResource(RESOURCE)).thenReturn(true);
        when(memory.ensure(authorized, run)).thenReturn(run); when(preferences.ensure(authorized, run)).thenReturn(run); when(tools.ensure(authorized, run)).thenReturn(run);
        when(store.current(binding, RESOURCE)).thenReturn(Optional.of(new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:existing", NOW.plusSeconds(300))));
        issuer = new TianjiExecutionGrantIssuer(ledger, store, RESOURCE, issuers, Map.of(ISSUER, client), memory, preferences, tools);
        assertEquals(run, issuer.ensure(authorized, run));
        var order = inOrder(memory, preferences, tools); order.verify(memory).ensure(authorized, run); order.verify(preferences).ensure(authorized, run); order.verify(tools).ensure(authorized, run);
        assertTrue(issuer.usesToolIssuer(tools)); verifyNoInteractions(client);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool scope refused")).when(tools).ensure(authorized, run);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> issuer.ensure(authorized, run)).code());
    }

    @Test void a_tool_issuer_with_a_different_source_cannot_be_added_to_the_user_chain() {
        var tools = mock(TianjiToolGrantIssuer.class);
        assertThrows(IllegalArgumentException.class, () -> new TianjiExecutionGrantIssuer(ledger, store, RESOURCE, issuers, Map.of(ISSUER, client), null, null, tools));
    }

    private static AgentRunDTO run(AgentRunStatusEnum status) {
        return new AgentRunDTO(RUN, "review", 1, "session", status, null, false, 0, 0, 1,
                NOW.plusSeconds(600), NOW, status == AgentRunStatusEnum.CANCELLED ? NOW.plusSeconds(1) : null);
    }
}
