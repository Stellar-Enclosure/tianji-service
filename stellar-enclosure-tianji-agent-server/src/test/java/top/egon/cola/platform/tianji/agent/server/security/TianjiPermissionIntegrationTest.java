package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.api.AgentRunSseController;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServicePolicySnapshot;
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
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.cache.AuthorizationSnapshotLoader;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3MethodAuthorizationManager;
import top.egon.cola.platform.tianquan.jianshen.starter.authorization.AuthorizationService;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedTenantContextFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real permission guard with a controlled authorization backend. */
class TianjiPermissionIntegrationTest {
    private static final Instant NOW = Instant.now();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String DIGEST = "a".repeat(64);
    private static final CatalogScope SCOPE = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
    private static final IdentityPrincipal USER = new IdentityPrincipal("alice", "20", "token-id",
            Set.of("https://api.example/tianji"), NOW.minusSeconds(60), NOW.plusSeconds(300),
            AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60)));
    private final PermissionManifest manifest = manifest();
    private final AtomicReference<Set<String>> granted = new AtomicReference<>(Set.of(TianjiAgentPermissions.READ));
    private final Rbac3AuthorizationClient client = new Rbac3AuthorizationClient() {
        @Override public SystemAuthorizationSnapshot fetch(String system, IdentityPrincipal principal) {
            return snapshot(granted.get());
        }
        @Override public ServicePolicySnapshot fetchPolicy(String system, ServiceIdentityPrincipal principal) {
            return new ServicePolicySnapshot(SCOPE, "1", "3", binding(),
                    Set.of(TianjiAgentPermissions.GET_API, TianjiAgentPermissions.LIST_API, TianjiAgentPermissions.EVENTS_API),
                    NOW.plusSeconds(30));
        }
    };

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void user_needs_current_permission_scope_and_verified_request_token() {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.READ_SCOPE))));
        TianjiAgentAccess access = access();
        assertEquals(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "alice"),
                access.require(TianjiAgentPermissions.GET_API).actor());
        granted.set(Set.of());
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.GET_API));
        granted.set(Set.of(TianjiAgentPermissions.READ));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get()))));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.GET_API));
        VerifiedUserTokenCarrier.clear(request);
        assertThrows(Rbac3AuthorizationClient.AuthorizationUnavailableException.class,
                () -> access.require(TianjiAgentPermissions.GET_API));
    }

    @Test
    void service_requires_its_own_verified_identity_scope_and_policy() {
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiAgentPermissions.READ_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                URI.create("https://idp.example/tenants/20")));
        assertEquals(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker"),
                access().require(TianjiAgentPermissions.GET_API).actor());
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access().require(TianjiAgentPermissions.GET_API));
    }

    @Test
    void declared_sse_resource_matches_the_compiled_permission_contract() throws Exception {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.READ_SCOPE))));
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(AgentRunSseController.class.getMethod("events", String.class,
                long.class, int.class, String.class, jakarta.servlet.http.HttpServletResponse.class));
        var manager = new Rbac3MethodAuthorizationManager(mock(AuthorizationService.class), permissionGuard());
        assertTrue(manager.check(() -> SecurityContextHolder.getContext().getAuthentication(), invocation).isGranted());
    }

    @Test
    void servlet_filter_chain_keeps_verified_user_token_available_for_event_authorization() {
        var verifier = mock(IdpJwtVerifier.class);
        when(verifier.verifyAny("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiAgentPermissions.READ_SCOPE), null, URI.create("https://idp.example/tenants/20")));
        when(verifier.verifyUser("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiAgentPermissions.READ_SCOPE), null, URI.create("https://idp.example/tenants/20")));
        var identityFilter = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier),
                new ServiceAccessTokenVerifier(verifier), new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy(),
                new com.fasterxml.jackson.databind.ObjectMapper(), new VerifiedTenantContextFilter(), verifier);
        var loader = new AuthorizationSnapshotLoader(client, "tianji", CLOCK);
        var rbacFilter = new Rbac3BearerAuthenticationFilter(loader, new com.fasterxml.jackson.databind.ObjectMapper());
        var ledger = mock(AgentLedgerService.class);
        var actor = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "alice");
        var run = new AgentRunDTO("run-1", "research", 1, null, AgentRunStatusEnum.COMPLETED, null,
                false, 1, 2, 1, NOW.plusSeconds(60), NOW, NOW);
        var event = new AgentEventDTO("event-1", "run-1", 1, "RUN_COMPLETED", null, null, null,
                NOW, null, "1", Map.of());
        when(ledger.get(actor, new GetAgentRunQuery("run-1"))).thenReturn(run);
        when(ledger.list(eq(actor), any())).thenReturn(new AgentRunPageDTO(List.of(run), null));
        when(ledger.get(actor, new GetAgentRunQuery("foreign"))).thenThrow(
                new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "not found"));
        when(ledger.events(eq(actor), any())).thenReturn(List.of(event));
        new WebApplicationContextRunner().withUserConfiguration(TianjiAgentServerApplication.class)
                .withPropertyValues("stellar.tianji.agent.api.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false",
                        "egon.cola.component.id.enabled=false",
                        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4")
                .withBean(AgentLedgerService.class, () -> ledger)
                .withBean(PermissionExecutionGuard.class, this::permissionGuard)
                .withBean(CurrentEffectivePermissionView.class, this::currentPermissionView)
                .withBean(IdpBearerAuthenticationFilter.class, () -> identityFilter)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> rbacFilter)
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")).andExpect(status().isUnauthorized());
                    mvc.perform(get("/api/v1/authorization/effective-permissions")).andExpect(status().isUnauthorized());
                    mvc.perform(get("/api/v1/authorization/effective-permissions")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.data.subject").value("alice"));
                    mvc.perform(get("/api/v1/authorization/effective-permissions").param("tenant", "other")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isBadRequest());
                    mvc.perform(get("/unclassified").header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isOk());
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")
                                    .header("Authorization", "Bearer verified-test-token")
                                    .header("X-Egon-Tianji-Tenant", "21"))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
                    mvc.perform(get("/api/tianji/v1/agent-runs")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].runId").value("run-1"));
                    mvc.perform(get("/api/tianji/v1/agent-runs/foreign")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isNotFound());
                    granted.set(Set.of());
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    granted.set(Set.of(TianjiAgentPermissions.READ));
                    var result = mvc.perform(get("/api/tianji/v1/agent-runs/run-1/events")
                                    .header("Authorization", "Bearer verified-test-token")
                                    .accept(MediaType.TEXT_EVENT_STREAM))
                            .andExpect(status().isOk()).andReturn();
                    assertTrue(result.getResponse().getContentAsString().contains("event: stream.complete"));
                    assertThrows(IllegalStateException.class, () -> VerifiedUserTokenCarrier.current(result.getRequest()));
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                }));
    }

    private TianjiAgentAccess access() {
        return new TianjiAgentAccess(permissionGuard(), CLOCK);
    }

    private PermissionExecutionGuard permissionGuard() {
        var registry = new PermissionPolicyRegistry(manifest, DIGEST, CLOCK, List.of(SCOPE));
        var scopes = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of(TianjiAgentPermissions.READ, Set.of(TianjiAgentPermissions.READ_SCOPE)));
        return new PermissionExecutionGuard(registry, new CurrentRbac3User(),
                new AuthorizationSnapshotLoader(client, "tianji", CLOCK),
                new PermissionServicePolicyGuard(registry, client, "tianji", CLOCK), scopes);
    }

    private CurrentEffectivePermissionView currentPermissionView() {
        return new CurrentEffectivePermissionView(new CurrentRbac3User(),
                new PermissionPolicyRegistry(manifest, DIGEST, CLOCK, List.of(SCOPE)), CLOCK);
    }

    private PermissionManifest manifest() {
        var requirement = new PermissionRequirement(PermissionMode.ALL, List.of(TianjiAgentPermissions.READ));
        var resource = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.GET_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/tianji/v1/agent-runs/{runId}")),
                ResourceAccess.PROTECTED, requirement, null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.agent.get"), DefinitionLifecycle.ACTIVE);
        var events = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.EVENTS_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/tianji/v1/agent-runs/{runId}/events")),
                ResourceAccess.PROTECTED, requirement, null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.agent.events"), DefinitionLifecycle.ACTIVE);
        var list = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.LIST_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/tianji/v1/agent-runs")),
                ResourceAccess.PROTECTED, requirement, null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.agent.list"), DefinitionLifecycle.ACTIVE);
        var current = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.CURRENT_AUTH_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/v1/authorization/effective-permissions")),
                ResourceAccess.PUBLIC, null, null, List.of(PermissionManifest.PrincipalType.USER), List.of(),
                new PermissionManifest.I18nReference("tianji.permissions", "api.authorization.current"), DefinitionLifecycle.ACTIVE);
        return new PermissionManifest("1.0", new PermissionManifest.Application("xingyuan", "tianji"),
                new PermissionManifest.Producer("tianji-java", PermissionManifest.ProducerKind.JAVA,
                        PermissionManifest.SnapshotMode.FULL, List.of("tianji."), List.of("agent-server"),
                        List.of("agent-server"), false),
                new PermissionManifest.Artifact("tianji-service", "a".repeat(40), "build-1", "1"),
                List.of(), List.of(TianjiAgentPermissions.READ), List.of(resource, events, list, current), List.of(), List.of());
    }

    private RuntimePolicyBinding binding() {
        var digests = new PermissionManifestCanonicalizer().digests(manifest);
        return new RuntimePolicyBinding("tianji-java", "1", DIGEST, digests.policyDigest(), digests.manifestDigest());
    }

    private SystemAuthorizationSnapshot snapshot(Set<String> permissions) {
        var view = new EffectivePermissionSnapshot("https://idp.example/tenants/20", USER.subject(), SCOPE,
                "1", "2", "3", new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of("50"), List.of("50")),
                permissions.stream().sorted().toList(), NOW.plusSeconds(30));
        return new SystemAuthorizationSnapshot("20", USER.subject(), "60", "tianji", 2, 3, List.of("50"),
                null, null, permissions, Set.of(TianjiAgentPermissions.GET_API, TianjiAgentPermissions.LIST_API,
                        TianjiAgentPermissions.EVENTS_API, TianjiAgentPermissions.CURRENT_AUTH_API),
                Map.of(), Map.of(), "checksum",
                NOW, view.snapshotExpiresAt(), view, binding());
    }
}
