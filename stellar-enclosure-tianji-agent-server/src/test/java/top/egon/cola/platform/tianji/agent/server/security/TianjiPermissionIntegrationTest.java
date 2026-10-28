package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.api.AgentRunSseController;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionListDTO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private final AtomicReference<String> snapshotIssuer = new AtomicReference<>("https://idp.example/tenants/20");
    private final Rbac3AuthorizationClient client = new Rbac3AuthorizationClient() {
        @Override public SystemAuthorizationSnapshot fetch(String system, IdentityPrincipal principal) {
            return snapshot(granted.get());
        }
        @Override public ServicePolicySnapshot fetchPolicy(String system, ServiceIdentityPrincipal principal) {
            return new ServicePolicySnapshot(SCOPE, "1", "3", binding(),
                    Set.of(TianjiAgentPermissions.GET_API, TianjiAgentPermissions.LIST_API, TianjiAgentPermissions.EVENTS_API,
                            TianjiAgentPermissions.SESSION_GET_API, TianjiAgentPermissions.SESSION_LIST_API,
                            TianjiAgentPermissions.SESSION_START_API, TianjiAgentPermissions.LINKED_SUBMIT_API,
                            TianjiAgentPermissions.LINKED_RESOLVE_API, TianjiAgentPermissions.LINKED_CLOSE_API),
                    NOW.plusSeconds(30));
        }
    };

    @BeforeEach
    void bindIssuer() {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void user_needs_current_permission_scope_and_verified_request_token() {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.READ_SCOPE))));
        TianjiAgentAccess access = access();
        assertEquals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/tenants/20"),
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
        assertEquals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/20"),
                access().require(TianjiAgentPermissions.GET_API).actor());
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal));
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> access().require(TianjiAgentPermissions.GET_API));
    }

    @Test
    void linked_resources_are_service_only_and_require_their_separate_scopes() {
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiAgentPermissions.LINKED_RESOLVE_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                URI.create("https://idp.example/tenants/20")));
        assertNotNull(access().require(TianjiAgentPermissions.LINKED_RESOLVE_API));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access().require(TianjiAgentPermissions.LINKED_CLOSE_API));
        var request = new MockHttpServletRequest(); request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        granted.set(Set.of(TianjiAgentPermissions.LINKED_RESOLVE));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.LINKED_RESOLVE_SCOPE))));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access().require(TianjiAgentPermissions.LINKED_RESOLVE_API));
    }

    @Test
    void linked_paths_require_service_credentials_at_the_identity_filter_boundary() {
        var policy = new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy();
        for (String action : List.of("submit", "resolve", "close")) {
            var request = new MockHttpServletRequest("POST", "/api/tianji/v1/agent-process-dispatches/" + action);
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.SERVICE,
                    policy.requirement(request));
        }
    }

    @Test
    void linked_method_annotation_matches_the_compiled_service_policy() throws Exception {
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiAgentPermissions.LINKED_RESOLVE_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                URI.create("https://idp.example/tenants/20")));
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(top.egon.cola.platform.tianji.agent.server.api.LinkedAgentDispatchController.class
                .getMethod("resolve", top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery.class));
        var manager = new Rbac3MethodAuthorizationManager(mock(AuthorizationService.class), permissionGuard());
        assertTrue(manager.check(() -> SecurityContextHolder.getContext().getAuthentication(), invocation).isGranted());
    }

    @Test
    void linked_resolve_passes_the_real_identity_filter_and_service_policy_guard() throws Exception {
        URI issuer = URI.create("https://idp.example/tenants/20");
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "service-token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiAgentPermissions.LINKED_RESOLVE_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        var verifier = mock(IdpJwtVerifier.class);
        when(verifier.verifyService("service-token")).thenReturn(new AccessTokenVerification.Valid<>(principal, principal.scopes(), null, issuer));
        when(verifier.verifyService("user-token")).thenReturn(new AccessTokenVerification.Invalid<>("JWT_PRINCIPAL_TYPE_INVALID"));
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier), new ServiceAccessTokenVerifier(verifier),
                new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy(), new com.fasterxml.jackson.databind.ObjectMapper(),
                new VerifiedTenantContextFilter(), verifier);
        var rbac = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", CLOCK), new com.fasterxml.jackson.databind.ObjectMapper());
        var issuers = mock(top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.class);
        when(issuers.findByIssuer(issuer.toString())).thenReturn(java.util.Optional.of(
                new top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.TrustedIssuerVO(issuer,
                        URI.create("https://idp.example/jwks"), "tenant:20", top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.RealmType.TENANT,
                        "20", top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.Status.ACTIVE, 1)));
        var binding = new TianjiLinkedDispatchProperties.Binding(issuer, 20, "worker", "worker", "xingyuan", "worker", "test",
                "process-east", Set.of(top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum.BPMN), List.of());
        var properties = new TianjiLinkedDispatchProperties(true, false, List.of(binding));
        var submissions = mock(top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", issuer.toString());
        var link = new top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink("process-east",
                top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum.BPMN, "process", "occurrence",
                "a".repeat(64), NOW.plusSeconds(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        var query = new top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery("intent", link);
        when(submissions.resolve(actor, query)).thenReturn(java.util.Optional.empty());
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var controller = new top.egon.cola.platform.tianji.agent.server.api.LinkedAgentDispatchController(submissions, access(),
                new TianjiLinkedDispatchBindings(properties, issuers, CLOCK), properties, beans.getBeanProvider(TianjiLinkedServiceAdmission.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new org.springframework.web.filter.RequestContextFilter(),
                identity, new VerifiedAgentIssuerFilter(), rbac).setControllerAdvice(new top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors(),
                new top.egon.cola.platform.tianji.agent.server.api.AgentRequestBodyAdvice()).build();
        byte[] body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(query);
        mvc.perform(post("/api/tianji/v1/agent-process-dispatches/resolve").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        verify(submissions, never()).resolve(any(), any());
        var result = mvc.perform(post("/api/tianji/v1/agent-process-dispatches/resolve").header("Authorization", "Bearer service-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.found").value(false)).andReturn();
        verify(submissions).resolve(actor, query);
        assertNull(result.getRequest().getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void service_session_start_requires_both_scopes_in_one_policy_decision() {
        var startOnly = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiAgentPermissions.START_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(startOnly, List.of(),
                URI.create("https://idp.example/tenants/20")));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access().require(TianjiAgentPermissions.SESSION_START_API));
        var both = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.START_SCOPE, TianjiAgentPermissions.READ_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(both, List.of(),
                URI.create("https://idp.example/tenants/20")));
        assertEquals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/20"),
                access().require(TianjiAgentPermissions.SESSION_START_API).actor());
    }

    @Test
    void session_read_and_delete_require_current_permissions_and_oauth_scopes() {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        request.setAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE, "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        granted.set(Set.of(TianjiAgentPermissions.START));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())), List.of(
                new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.START_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.READ_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.SESSION_DELETE_SCOPE))));
        TianjiAgentAccess access = access();
        assertNotNull(access.require(TianjiAgentPermissions.START_API));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.SESSION_START_API));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.SESSION_GET_API));
        granted.set(Set.of(TianjiAgentPermissions.START, TianjiAgentPermissions.READ));
        assertNotNull(access.require(TianjiAgentPermissions.SESSION_GET_API));
        assertNotNull(access.require(TianjiAgentPermissions.SESSION_START_API));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.SESSION_DELETE_API));
        granted.set(Set.of(TianjiAgentPermissions.READ, TianjiAgentPermissions.SESSION_DELETE));
        assertNotNull(access.require(TianjiAgentPermissions.SESSION_DELETE_API));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiAgentPermissions.READ_SCOPE))));
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiAgentPermissions.SESSION_DELETE_API));
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
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/tenants/20");
        var sessions = mock(AgentSessionManager.class);
        when(sessions.list(actor)).thenReturn(new AgentSessionListDTO(List.of(new AgentSessionDTO("session-1", NOW))));
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
                .withBean(AgentSessionManager.class, () -> sessions)
                .withBean(PermissionExecutionGuard.class, this::permissionGuard)
                .withBean(CurrentEffectivePermissionView.class, this::currentPermissionView)
                .withBean(IdpBearerAuthenticationFilter.class, () -> identityFilter)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> rbacFilter)
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")).andExpect(status().isUnauthorized());
                    mvc.perform(get("/api/tianji/v1/agent-sessions")).andExpect(status().isUnauthorized());
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
                    mvc.perform(get("/api/tianji/v1/agent-sessions")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sessionId").value("session-1"));
                    mvc.perform(get("/api/tianji/v1/agent-runs/foreign")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isNotFound());
                    granted.set(Set.of());
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    granted.set(Set.of(TianjiAgentPermissions.READ));
                    snapshotIssuer.set("https://another-idp.example/tenants/20");
                    mvc.perform(get("/api/tianji/v1/agent-runs/run-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    snapshotIssuer.set("https://idp.example/tenants/20");
                    var result = mvc.perform(get("/api/tianji/v1/agent-runs/run-1/events")
                                    .header("Authorization", "Bearer verified-test-token")
                                    .accept(MediaType.TEXT_EVENT_STREAM))
                            .andExpect(status().isOk()).andReturn();
                    assertTrue(result.getResponse().getContentAsString().contains("event: stream.complete"));
                    assertThrows(IllegalStateException.class, () -> VerifiedUserTokenCarrier.current(result.getRequest()));
                    assertNull(result.getRequest().getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                }));
    }

    @Test
    void verified_user_request_supplies_the_original_proof_only_to_the_grant_issuer() throws Exception {
        granted.set(Set.of(TianjiAgentPermissions.START));
        URI issuer = URI.create("https://idp.example/tenants/20");
        URI resource = URI.create("https://api.example/tianji");
        var verifier = mock(IdpJwtVerifier.class);
        when(verifier.verifyAny("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiAgentPermissions.START_SCOPE), null, issuer));
        when(verifier.verifyUser("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiAgentPermissions.START_SCOPE), null, issuer));
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier),
                new ServiceAccessTokenVerifier(verifier), new TianjiAgentSecurityConfiguration().tianjiEndpointAuthenticationPolicy(),
                new com.fasterxml.jackson.databind.ObjectMapper(), new VerifiedTenantContextFilter(), verifier);
        var rbac = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", CLOCK),
                new com.fasterxml.jackson.databind.ObjectMapper());
        var ledger = mock(AgentLedgerService.class);
        var store = mock(top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore.class);
        var authority = mock(top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient.class);
        var issuers = mock(top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.class);
        var trust = new top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.TrustedIssuerVO(issuer,
                URI.create("https://idp.example/jwks"), "tenant:20",
                top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.RealmType.TENANT, "20",
                top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(issuers.findByIssuer(issuer.toString())).thenReturn(java.util.Optional.of(trust));
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", issuer.toString());
        String runId = "00000000-0000-0000-0000-000000000123";
        var run = new AgentRunDTO(runId, "research", 1, null, AgentRunStatusEnum.QUEUED, null,
                false, 0, 0, 1, NOW.plusSeconds(60), NOW, null);
        var binding = new top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization.Request(actor, runId, "research", 1,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, run.deadlineAt());
        var reservation = new top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant.Reservation(binding, resource, 1,
                top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant.fingerprint(binding, resource));
        when(ledger.start(eq(actor), any())).thenReturn(run);
        when(ledger.executionMaterial(actor, new GetAgentRunQuery(runId))).thenReturn(new AgentLedgerService.ExecutionMaterial(run, null, binding));
        when(ledger.get(actor, new GetAgentRunQuery(runId))).thenReturn(run);
        when(store.current(binding, resource)).thenReturn(java.util.Optional.empty());
        when(store.reserve(actor, runId, resource)).thenReturn(java.util.Optional.of(reservation));
        when(store.complete(any(), any())).thenReturn(true);
        when(authority.issue(any())).thenReturn(new top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract.Issued(
                new top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract.Ticket("9" + "1".repeat(62),
                        "ENC:v1:key:nonce:sealed"), NOW.plusSeconds(30)));
        var grantIssuer = new TianjiExecutionGrantIssuer(ledger, store, resource, issuers, Map.of(issuer, authority));
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiExecutionGrantIssuer> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(grantIssuer);
        var controller = new top.egon.cola.platform.tianji.agent.server.api.AgentRunController(ledger, access(), provider,
                new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true"));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new org.springframework.web.filter.RequestContextFilter(),
                identity, new VerifiedAgentIssuerFilter(), rbac).setControllerAdvice(new top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors()).build();
        var command = new top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand("research", 1, null, "request", null, null,
                new top.egon.cola.platform.tianji.contract.agent.AgentInputDTO("input", List.of(), Map.of()), null);
        var result = mvc.perform(post("/api/tianji/v1/agent-runs").header("Authorization", "Bearer verified-test-token")
                .contentType(MediaType.APPLICATION_JSON).content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(command)))
                .andExpect(status().isAccepted()).andReturn();
        verify(authority).issue(argThat(value -> value.userToken().equals("verified-test-token")
                && value.bindingSha256().equals(reservation.bindingSha256())));
        assertThrows(IllegalStateException.class, () -> VerifiedUserTokenCarrier.current(result.getRequest()));
        assertNull(result.getRequest().getAttribute(VerifiedAgentIssuerFilter.ATTRIBUTE));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private TianjiAgentAccess access() {
        return new TianjiAgentAccess(permissionGuard(), CLOCK);
    }

    private PermissionExecutionGuard permissionGuard() {
        var registry = new PermissionPolicyRegistry(manifest, DIGEST, CLOCK, List.of(SCOPE));
        var scopes = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of(TianjiAgentPermissions.READ, Set.of(TianjiAgentPermissions.READ_SCOPE),
                        TianjiAgentPermissions.START, Set.of(TianjiAgentPermissions.START_SCOPE),
                        TianjiAgentPermissions.SESSION_DELETE, Set.of(TianjiAgentPermissions.SESSION_DELETE_SCOPE),
                        TianjiAgentPermissions.LINKED_RESOLVE, Set.of(TianjiAgentPermissions.LINKED_RESOLVE_SCOPE)));
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
        var start = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.START_API, ResourceType.API,
                Map.of("methods", List.of("POST"), "paths", List.of("/api/tianji/v1/agent-runs")),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of(TianjiAgentPermissions.START)), null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.START_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.agent.start"), DefinitionLifecycle.ACTIVE);
        var sessionGet = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.SESSION_GET_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/tianji/v1/agent-sessions/detail")),
                ResourceAccess.PROTECTED, requirement, null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.session.get"), DefinitionLifecycle.ACTIVE);
        var sessionStart = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.SESSION_START_API, ResourceType.API,
                Map.of("methods", List.of("POST"), "paths", List.of("/api/tianji/v1/agent-runs/with-session")),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL,
                        List.of(TianjiAgentPermissions.READ, TianjiAgentPermissions.START)), null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE, TianjiAgentPermissions.START_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.agent.startSession"), DefinitionLifecycle.ACTIVE);
        var sessionList = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.SESSION_LIST_API, ResourceType.API,
                Map.of("methods", List.of("GET"), "paths", List.of("/api/tianji/v1/agent-sessions")),
                ResourceAccess.PROTECTED, requirement, null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.session.list"), DefinitionLifecycle.ACTIVE);
        var sessionDelete = new PermissionManifest.ResourceDefinition(TianjiAgentPermissions.SESSION_DELETE_API, ResourceType.API,
                Map.of("methods", List.of("DELETE"), "paths", List.of("/api/tianji/v1/agent-sessions")),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL,
                        List.of(TianjiAgentPermissions.READ, TianjiAgentPermissions.SESSION_DELETE)), null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                List.of(TianjiAgentPermissions.READ_SCOPE, TianjiAgentPermissions.SESSION_DELETE_SCOPE),
                new PermissionManifest.I18nReference("tianji.permissions", "api.session.delete"), DefinitionLifecycle.ACTIVE);
        var linkedSubmit = linkedResource(TianjiAgentPermissions.LINKED_SUBMIT_API, "submit", TianjiAgentPermissions.LINKED_SUBMIT,
                TianjiAgentPermissions.LINKED_SUBMIT_SCOPE);
        var linkedResolve = linkedResource(TianjiAgentPermissions.LINKED_RESOLVE_API, "resolve", TianjiAgentPermissions.LINKED_RESOLVE,
                TianjiAgentPermissions.LINKED_RESOLVE_SCOPE);
        var linkedClose = linkedResource(TianjiAgentPermissions.LINKED_CLOSE_API, "close", TianjiAgentPermissions.LINKED_CLOSE,
                TianjiAgentPermissions.LINKED_CLOSE_SCOPE);
        return new PermissionManifest("1.0", new PermissionManifest.Application("xingyuan", "tianji"),
                new PermissionManifest.Producer("tianji-java", PermissionManifest.ProducerKind.JAVA,
                        PermissionManifest.SnapshotMode.FULL, List.of("tianji."), List.of("agent-server"),
                        List.of("agent-server"), false),
                new PermissionManifest.Artifact("tianji-service", "a".repeat(40), "build-1", "1"),
                List.of(), List.of(TianjiAgentPermissions.READ, TianjiAgentPermissions.START, TianjiAgentPermissions.SESSION_DELETE,
                        TianjiAgentPermissions.LINKED_SUBMIT, TianjiAgentPermissions.LINKED_RESOLVE, TianjiAgentPermissions.LINKED_CLOSE),
                List.of(resource, events, list, current, start, sessionGet, sessionStart, sessionList, sessionDelete,
                        linkedSubmit, linkedResolve, linkedClose), List.of(), List.of());
    }

    private static PermissionManifest.ResourceDefinition linkedResource(String key, String operation, String permission, String scope) {
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API,
                Map.of("methods", List.of("POST"), "paths", List.of("/api/tianji/v1/agent-process-dispatches/" + operation)),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of(permission)), null,
                List.of(PermissionManifest.PrincipalType.SERVICE), List.of(scope),
                new PermissionManifest.I18nReference("tianji.permissions", "api.linked." + operation), DefinitionLifecycle.ACTIVE);
    }

    private RuntimePolicyBinding binding() {
        var digests = new PermissionManifestCanonicalizer().digests(manifest);
        return new RuntimePolicyBinding("tianji-java", "1", DIGEST, digests.policyDigest(), digests.manifestDigest());
    }

    private SystemAuthorizationSnapshot snapshot(Set<String> permissions) {
        var view = new EffectivePermissionSnapshot(snapshotIssuer.get(), USER.subject(), SCOPE,
                "1", "2", "3", new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of("50"), List.of("50")),
                permissions.stream().sorted().toList(), NOW.plusSeconds(30));
        return new SystemAuthorizationSnapshot("20", USER.subject(), "60", "tianji", 2, 3, List.of("50"),
                null, null, permissions, Set.of(TianjiAgentPermissions.GET_API, TianjiAgentPermissions.LIST_API,
                        TianjiAgentPermissions.EVENTS_API, TianjiAgentPermissions.CURRENT_AUTH_API,
                        TianjiAgentPermissions.START_API, TianjiAgentPermissions.SESSION_GET_API, TianjiAgentPermissions.SESSION_START_API,
                        TianjiAgentPermissions.SESSION_LIST_API, TianjiAgentPermissions.SESSION_DELETE_API,
                        TianjiAgentPermissions.LINKED_SUBMIT_API, TianjiAgentPermissions.LINKED_RESOLVE_API, TianjiAgentPermissions.LINKED_CLOSE_API),
                Map.of(), Map.of(), "checksum",
                NOW, view.snapshotExpiresAt(), view, binding());
    }
}
