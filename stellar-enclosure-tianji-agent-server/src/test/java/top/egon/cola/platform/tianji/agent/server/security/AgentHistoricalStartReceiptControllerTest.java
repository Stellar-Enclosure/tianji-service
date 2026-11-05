package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization.Request;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService.ReceiptVO;
import top.egon.cola.platform.tianji.agent.recovery.TianjiHistoricalStartReceiptConfiguration.HistoricalStartReceiptHost;
import top.egon.cola.platform.tianji.agent.recovery.TianjiProtectedHistoricalStartReceiptConfiguration;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors;
import top.egon.cola.platform.tianji.agent.server.api.AgentHistoricalStartReceiptController;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.DefinitionLifecycle;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestCanonicalizer;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.cache.AuthorizationSnapshotLoader;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.*;

class AgentHistoricalStartReceiptControllerTest {
    private static final String RUN = "run-01";
    private static final UUID INTENT = UUID.fromString("b1884cdf-252b-41f5-aeb9-3cba0094a5c1");
    private static final String ISSUER = "https://idp.example/tenants/20";
    private static final String DIGEST = "a".repeat(64);
    private static final String BODY = "{\"runId\":\""+ RUN + "\",\"intentId\":\"" + INTENT + "\"}";
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER);
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "start-executor", ISSUER);
    private static final CatalogScope CATALOG = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
    private static final IdentityPrincipal USER = new IdentityPrincipal(OWNER.subjectId(), "20", "verified-token-id",
            Set.of("https://api.example/tianji"), NOW.minusSeconds(30), NOW.plusSeconds(60),
            AuthenticationContext.of("PASSWORD", NOW.minusSeconds(30)));
    private static final ReceiptVO RECEIPT = new ReceiptVO(INTENT, RUN, "process-01", NOW.minusSeconds(4), NOW);
    private static final Request BINDING = new Request(OWNER, RUN, INTENT, EXECUTOR, DIGEST, 2, 5, "UNKNOWN", NOW.plusSeconds(9));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void dedicated_route_is_disabled_by_default_and_has_an_exact_user_resource() throws Exception {
        var flags = AgentHistoricalStartReceiptController.class.getAnnotation(ConditionalOnProperty.class);
        assertFalse(flags.matchIfMissing());
        assertEquals("true", flags.havingValue());
        assertEquals(Set.of("stellar.tianji.agent.api.enabled", "stellar.tianji.agent.storage.enabled", ENABLED), Set.of(flags.name()));
        var method = AgentHistoricalStartReceiptController.class.getMethod("reconcile", jakarta.servlet.http.HttpServletRequest.class);
        var resource = method.getAnnotation(PermissionResource.class);
        assertArrayEquals(new String[]{PATH}, method.getAnnotation(PostMapping.class).path());
        assertEquals(RESOURCE, resource.key());
        assertArrayEquals(new String[]{SCOPE}, resource.oauthScopes());
        assertArrayEquals(new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER}, resource.allowedPrincipals());
        assertArrayEquals(new String[]{RECONCILE}, method.getAnnotation(RequiresPermissions.class).codes());
        assertEquals(IdpEndpointAuthenticationPolicy.Requirement.USER,
                new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy().requirement(new MockHttpServletRequest("POST", PATH)));
    }

    @Test
    void default_mock_servlet_shell_keeps_even_an_explicit_import_dormant() {
        new WebApplicationContextRunner()
                .withUserConfiguration(TianjiAgentServerApplication.class, TianjiProtectedHistoricalStartReceiptConfiguration.class)
                .withPropertyValues("spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                        "egon.cola.component.id.enabled=false",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AgentHistoricalStartReceiptController.class);
                    assertThat(context).doesNotHaveBean(TianjiServletHistoricalStartReceiptAuthority.class);
                    assertThat(context).doesNotHaveBean(HistoricalStartReceiptHost.class);
                });
    }

    @Test
    void refuses_a_host_bound_to_another_authority() {
        var fixture = new Fixture();
        assertThrows(IllegalStateException.class, () -> new AgentHistoricalStartReceiptController(
                mock(HistoricalStartReceiptHost.class), fixture.authority));
    }

    @Test
    void refreshes_both_exact_historical_decisions_and_returns_only_safe_receipt_fields() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(SCOPE);
        when(fixture.host.observe(OWNER, RUN, INTENT)).thenAnswer(call -> {
            var before = fixture.authority.authorize(BINDING).orElseThrow();
            assertEquals(BINDING.bindingSha256(), before.bindingSha256());
            assertEquals(3, before.policyVersion());
            assertEquals(NOW.plusSeconds(7), before.expiresAt());
            fixture.policy.set(4);
            var after = fixture.authority.authorize(BINDING).orElseThrow();
            assertEquals(4, after.policyVersion());
            assertEquals(BINDING.bindingSha256(), after.bindingSha256());
            assertNotEquals(before.checkId(), after.checkId());
            return RECEIPT;
        });

        var response = fixture.mvc.perform(fixture.command(BODY)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var json = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
        assertEquals(Set.of("intentId", "dispatchRunId", "instanceId", "recordedAt", "observedAt"), json.propertyNames());
        assertEquals(INTENT.toString(), json.get("intentId").asString());
        assertEquals(RUN, json.get("dispatchRunId").asString());
        assertEquals(3, fixture.refreshes.get());
        assertTrue(fixture.authority.authorize(BINDING).isEmpty());
    }

    @Test
    void revocation_after_process_read_refuses_the_second_permit() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(SCOPE);
        when(fixture.host.observe(OWNER, RUN, INTENT)).thenAnswer(call -> {
            assertTrue(fixture.authority.authorize(BINDING).isPresent());
            fixture.granted.set(Set.of());
            if (fixture.authority.authorize(BINDING).isPresent()) return RECEIPT;
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "private denial");
        });
        fixture.mvc.perform(fixture.command(BODY)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        assertEquals(3, fixture.refreshes.get());
        assertTrue(fixture.authority.authorize(BINDING).isEmpty());
    }

    @Test
    void existing_read_scope_cannot_authorize_the_historical_command() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(TianjiAgentPermissions.READ_SCOPE);
        fixture.mvc.perform(fixture.command(BODY)).andExpect(status().isForbidden());
        assertEquals(0, fixture.refreshes.get());
        verify(fixture.host, never()).observe(any(), anyString(), any());
    }

    @Test
    void authority_rejects_substituted_owner_intent_or_request_token_before_refresh() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(SCOPE);
        when(fixture.host.observe(OWNER, RUN, INTENT)).thenAnswer(call -> {
            var otherOwner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "mallory", ISSUER);
            assertTrue(fixture.authority.authorize(new Request(otherOwner, RUN, INTENT, EXECUTOR, DIGEST,
                    2, 5, "UNKNOWN", BINDING.deadlineAt())).isEmpty());
            assertTrue(fixture.authority.authorize(new Request(OWNER, RUN, UUID.randomUUID(), EXECUTOR, DIGEST,
                    2, 5, "UNKNOWN", BINDING.deadlineAt())).isEmpty());
            var replacement = new IdentityPrincipal(OWNER.subjectId(), "20", "other-token-id", USER.audience(),
                    NOW.minusSeconds(30), USER.expiresAt(), USER.authenticationContext());
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                    new Rbac3UserDetails(replacement, fixture.snapshot()), List.of(new SimpleGrantedAuthority("SCOPE_" + SCOPE))));
            assertTrue(fixture.authority.authorize(BINDING).isEmpty());
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "private denial");
        });
        fixture.mvc.perform(fixture.command(BODY)).andExpect(status().isForbidden());
        assertEquals(1, fixture.refreshes.get());
        assertTrue(fixture.authority.authorize(BINDING).isEmpty());
    }

    @Test
    void rejects_untrusted_fields_duplicate_json_and_oversized_body_before_the_host() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(SCOPE);
        for (String malformed : List.of("[]", BODY + "{}", BODY.replace("{", "{\"runId\":\"forged\","),
                BODY.replace("{", "{\"owner\":\"forged\","), BODY.replace(RUN, " " + RUN), BODY.replace(INTENT.toString(), "1-1-1-1-1"))) {
            fixture.mvc.perform(fixture.command(malformed)).andExpect(status().isBadRequest());
        }
        fixture.mvc.perform(fixture.command(BODY).queryParam("issuer", "forged")).andExpect(status().isBadRequest());
        fixture.mvc.perform(fixture.command(" ".repeat(513))).andExpect(status().isPayloadTooLarge());
        verify(fixture.host, never()).observe(any(), anyString(), any());
        assertEquals(0, fixture.refreshes.get());
    }

    @Test
    void missing_process_evidence_is_a_safe_no_store_conflict() throws Exception {
        var fixture = new Fixture();
        fixture.authenticate(SCOPE);
        when(fixture.host.observe(OWNER, RUN, INTENT)).thenThrow(
                new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, "private upstream content"));
        var response = fixture.mvc.perform(fixture.command(BODY)).andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_SIDE_EFFECT_UNKNOWN")).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("private"));
        assertTrue(fixture.authority.authorize(BINDING).isEmpty());
    }

    private static final class Fixture {
        private final HistoricalStartReceiptHost host = mock(HistoricalStartReceiptHost.class);
        private final AtomicReference<Set<String>> granted = new AtomicReference<>(Set.of(RECONCILE));
        private final AtomicInteger refreshes = new AtomicInteger();
        private final AtomicInteger policy = new AtomicInteger(3);
        private final PermissionManifest manifest = new PermissionManifest("1.0", new PermissionManifest.Application("xingyuan", "tianji"),
                new PermissionManifest.Producer("tianji-java", PermissionManifest.ProducerKind.JAVA, PermissionManifest.SnapshotMode.FULL,
                        List.of("tianji."), List.of("agent-server"), List.of("agent-server"), false),
                new PermissionManifest.Artifact("tianji-service", "a".repeat(40), "build-1", "1"), List.of(), List.of(RECONCILE),
                List.of(new PermissionManifest.ResourceDefinition(RESOURCE, ResourceType.API,
                        Map.of("methods", List.of("POST"), "paths", List.of(PATH)), ResourceAccess.PROTECTED,
                        new PermissionRequirement(PermissionMode.ALL, List.of(RECONCILE)), null,
                        List.of(PermissionManifest.PrincipalType.USER), List.of(SCOPE),
                        new PermissionManifest.I18nReference("tianji.permissions", "api.agent.toolReceiptReconcile"), DefinitionLifecycle.ACTIVE)),
                List.of(), List.of());
        private final TianjiServletHistoricalStartReceiptAuthority authority;
        private final MockMvc mvc;

        private Fixture() {
            var registry = new PermissionPolicyRegistry(manifest, DIGEST, CLOCK, List.of(CATALOG));
            Rbac3AuthorizationClient client = (system, principal) -> {
                assertEquals("tianji", system);
                assertEquals(USER, principal);
                refreshes.incrementAndGet();
                return snapshot();
            };
            var scopes = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                    .setPermissionScopes(Map.of(RECONCILE, Set.of(SCOPE)));
            var guard = new PermissionExecutionGuard(registry, new CurrentRbac3User(),
                    new AuthorizationSnapshotLoader(client, "tianji", CLOCK), null, scopes);
            authority = new TianjiServletHistoricalStartReceiptAuthority(new TianjiAgentAccess(guard, CLOCK), CLOCK);
            when(host.usesAuthority(authority)).thenReturn(true);
            mvc = MockMvcBuilders.standaloneSetup(new AgentHistoricalStartReceiptController(host, authority))
                    .setControllerAdvice(new AgentApiErrors()).build();
        }

        private void authenticate(String scope) {
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                    new Rbac3UserDetails(USER, snapshot()), List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
        }

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(String body) {
            return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(request -> {
                request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, ISSUER);
                VerifiedUserTokenCarrier.set(request, "verified-synthetic-user-token");
                return request;
            });
        }

        private SystemAuthorizationSnapshot snapshot() {
            var digests = new PermissionManifestCanonicalizer().digests(manifest);
            var binding = new RuntimePolicyBinding("tianji-java", "1", DIGEST, digests.policyDigest(), digests.manifestDigest());
            var permissions = granted.get();
            var view = new EffectivePermissionSnapshot(ISSUER, USER.subject(), CATALOG, "1", "2", Integer.toString(policy.get()),
                    new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of("50"), List.of("50")),
                    permissions.stream().sorted().toList(), NOW.plusSeconds(7));
            return new SystemAuthorizationSnapshot("20", USER.subject(), "60", "tianji", 2, policy.get(), List.of("50"),
                    null, null, permissions, Set.of(RESOURCE), Map.of(), Map.of(), "checksum", NOW, view.snapshotExpiresAt(), view, binding);
        }
    }
}
