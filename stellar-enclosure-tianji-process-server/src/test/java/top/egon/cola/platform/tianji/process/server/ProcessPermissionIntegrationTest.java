package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.process.server.api.ProcessApiErrors;
import top.egon.cola.platform.tianji.process.server.api.ProcessController;
import top.egon.cola.platform.tianji.process.server.api.ProcessRequestBodyAdvice;
import top.egon.cola.platform.tianji.process.server.api.WorkerController;
import top.egon.cola.platform.tianji.process.server.api.AgentResultInboxController;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistry;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiCaseAccess;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskClient;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.process.server.security.TianjiApprovalVerificationAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerAcquireStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
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
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3MethodAuthorizationManager;
import top.egon.cola.platform.tianquan.jianshen.starter.authorization.AuthorizationService;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedTenantContextFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real identity filters and Jianshen guard with controlled verifier and authorization responses. */
class ProcessPermissionIntegrationTest {
    private static final Instant NOW = Instant.now();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String DIGEST = "a".repeat(64);
    private static final CatalogScope SCOPE = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
    private static final IdentityPrincipal USER = new IdentityPrincipal("alice", "20", "token-id",
            Set.of("https://api.example/tianji"), NOW.minusSeconds(60), NOW.plusSeconds(300),
            AuthenticationContext.of("PASSWORD", NOW.minusSeconds(60)));
    private final AtomicReference<Set<String>> granted = new AtomicReference<>(Set.of(TianjiProcessPermissions.READ));
    private final AtomicReference<String> snapshotIssuer = new AtomicReference<>("https://idp.example/tenants/20");
    private final AtomicReference<Set<String>> serviceGranted = new AtomicReference<>(resources());
    private final AtomicBoolean serviceAvailable = new AtomicBoolean(true);
    private final PermissionManifest manifest = manifest();
    private final Rbac3AuthorizationClient client = new Rbac3AuthorizationClient() {
        @Override public SystemAuthorizationSnapshot fetch(String system, IdentityPrincipal principal) { return snapshot(granted.get()); }
        @Override public ServicePolicySnapshot fetchPolicy(String system, ServiceIdentityPrincipal principal) {
            if (!serviceAvailable.get()) throw new AuthorizationUnavailableException("private-backend-details");
            return new ServicePolicySnapshot(SCOPE, "1", "3", binding(), serviceGranted.get(), NOW.plusSeconds(30));
        }
    };

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void servlet_chain_checks_current_user_permission_scope_and_trusted_tenant() {
        var verifier = mock(IdpJwtVerifier.class);
        when(verifier.verifyAny("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiProcessPermissions.READ_SCOPE), null, URI.create("https://idp.example/tenants/20")));
        when(verifier.verifyUser("verified-test-token")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(TianjiProcessPermissions.READ_SCOPE), null, URI.create("https://idp.example/tenants/20")));
        when(verifier.verifyAny("without-scope")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(), null, URI.create("https://idp.example/tenants/20")));
        when(verifier.verifyUser("without-scope")).thenReturn(new AccessTokenVerification.Valid<>(USER,
                Set.of(), null, URI.create("https://idp.example/tenants/20")));
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier),
                new ServiceAccessTokenVerifier(verifier), policy, new com.fasterxml.jackson.databind.ObjectMapper(),
                new VerifiedTenantContextFilter(), verifier);
        var authorization = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", CLOCK),
                new com.fasterxml.jackson.databind.ObjectMapper());
        var processes = mock(ProcessExecutionService.class);
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://idp.example/tenants/20");
        when(processes.get(actor, "instance-1")).thenReturn(new ProcessExecutionService.Snapshot("instance-1", "v1", "invoice",
                "order", "trace", ProcessExecutionService.State.ACTIVE, NOW, null));
        new WebApplicationContextRunner().withUserConfiguration(TestWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> processes)
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(PermissionExecutionGuard.class, this::guard)
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    mvc.perform(get("/api/tianji/v1/process-instances/instance-1")).andExpect(status().isUnauthorized());
                    var response = mvc.perform(get("/api/tianji/v1/process-instances/instance-1")
                                    .header("Authorization", "Bearer verified-test-token").header("X-Tenant-ID", "21"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.instanceId").value("instance-1")).andReturn();
                    assertThrows(IllegalStateException.class, () -> VerifiedUserTokenCarrier.current(response.getRequest()));
                    assertNull(response.getRequest().getAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer"));
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                    mvc.perform(get("/api/tianji/v1/process-instances/instance-1")
                                    .header("Authorization", "Bearer verified-test-token").header("X-Egon-Tianji-Tenant", "21"))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
                    granted.set(Set.of());
                    mvc.perform(get("/api/tianji/v1/process-instances/instance-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    granted.set(Set.of(TianjiProcessPermissions.READ));
                    mvc.perform(get("/api/tianji/v1/process-instances/instance-1")
                                    .header("Authorization", "Bearer without-scope"))
                            .andExpect(status().isForbidden());
                    snapshotIssuer.set("https://another-idp.example/tenants/20");
                    mvc.perform(get("/api/tianji/v1/process-instances/instance-1")
                                    .header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    mvc.perform(get("/unclassified").header("Authorization", "Bearer verified-test-token"))
                            .andExpect(status().isForbidden());
                    verify(processes, times(1)).get(actor, "instance-1");
                }));
    }

    @Test
    void service_requires_its_own_scope_and_policy() {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.READ_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(),
                URI.create("https://idp.example/tenants/20")));
        assertEquals(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/20"),
                new TianjiProcessAccess(guard(), CLOCK).require(TianjiProcessPermissions.GET_API));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal));
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> new TianjiProcessAccess(guard(), CLOCK).require(TianjiProcessPermissions.GET_API));
    }

    @Test
    void worker_http_requires_service_identity_scope_and_current_resource_policy() {
        var verifier = mock(IdpJwtVerifier.class);
        URI issuer = URI.create("https://idp.example/tenants/20");
        var service = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        var noScope = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.READ_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        var reporter = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.WORKER_REPORT_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        when(verifier.verifyService("worker-token")).thenReturn(new AccessTokenVerification.Valid<>(service,
                Set.of(TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE), null, issuer));
        when(verifier.verifyService("no-scope")).thenReturn(new AccessTokenVerification.Valid<>(noScope,
                Set.of(TianjiProcessPermissions.READ_SCOPE), null, issuer));
        when(verifier.verifyService("report-token")).thenReturn(new AccessTokenVerification.Valid<>(reporter,
                Set.of(TianjiProcessPermissions.WORKER_REPORT_SCOPE), null, issuer));
        when(verifier.verifyService("user-token")).thenReturn(new AccessTokenVerification.Invalid<>("PRINCIPAL_TYPE_MISMATCH"));
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier),
                new ServiceAccessTokenVerifier(verifier), policy, new com.fasterxml.jackson.databind.ObjectMapper(),
                new VerifiedTenantContextFilter(), verifier);
        var authorization = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", CLOCK),
                new com.fasterxml.jackson.databind.ObjectMapper());
        var workers = mock(FlowableWorkerService.class);
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", issuer.toString());
        var command = new AcquireWorkerTaskCommand("riskCheck", "1");
        when(workers.acquire(actor, command)).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var complete = new CompleteWorkerTaskCommand("task", "occurrence", lease, "receipt", Map.of("approved", true));
        when(workers.complete(actor, complete)).thenReturn(new WorkerReceiptDTO("task", "occurrence", WorkerReceiptOutcomeEnum.COMPLETED, NOW));
        new WebApplicationContextRunner().withUserConfiguration(TestWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(WorkerTopicRegistry.class, () -> mock(WorkerTopicRegistry.class))
                .withBean(FlowableWorkerService.class, () -> workers)
                .withBean(PermissionExecutionGuard.class, this::guard)
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .apply(springSecurity()).build();
                    String body = "{\"topic\":\"riskCheck\",\"schemaVersion\":\"1\"}";
                    mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body))
                            .andExpect(status().isUnauthorized());
                    var response = mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body)
                                    .header("Authorization", "Bearer worker-token"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EMPTY")).andReturn();
                    assertNull(response.getRequest().getAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer"));
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                    mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body)
                                    .header("Authorization", "Bearer user-token"))
                            .andExpect(status().isUnauthorized());
                    mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body)
                                    .header("Authorization", "Bearer no-scope"))
                            .andExpect(status().isForbidden());
                    byte[] completion = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(complete);
                    mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType("application/json").content(completion)
                                    .header("Authorization", "Bearer worker-token"))
                            .andExpect(status().isForbidden());
                    mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType("application/json").content(completion)
                                    .header("Authorization", "Bearer report-token"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("COMPLETED"));
                    mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body)
                                    .header("Authorization", "Bearer worker-token").header("X-Egon-Tianji-Tenant", "21"))
                            .andExpect(status().isConflict());
                    var reduced = new java.util.HashSet<>(resources());
                    reduced.remove(TianjiProcessPermissions.WORKER_ACQUIRE_API);
                    serviceGranted.set(Set.copyOf(reduced));
                    mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType("application/json").content(body)
                                    .header("Authorization", "Bearer worker-token"))
                            .andExpect(status().isForbidden());
                    verify(workers, times(1)).acquire(actor, command);
                    verify(workers, times(1)).complete(actor, complete);
                }));
    }

    @Test
    void inbox_http_requires_current_service_scope_policy_and_the_registered_dispatch_owner() {
        var verifier = mock(IdpJwtVerifier.class);
        URI issuer = URI.create("https://idp.example/tenants/20");
        var service = new ServiceIdentityPrincipal("agent-result-service", "20", "agent-result-service", "result-token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_SCOPE),
                "xingyuan", "agent", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        var noScope = new ServiceIdentityPrincipal("agent-result-service", "20", "agent-result-service", "result-token-id",
                URI.create("https://api.example/tianji"), 1, Set.of(TianjiProcessPermissions.READ_SCOPE),
                "xingyuan", "agent", "test", "credential", NOW.minusSeconds(60), NOW.plusSeconds(300));
        when(verifier.verifyService("result-token")).thenReturn(new AccessTokenVerification.Valid<>(service,
                Set.of(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_SCOPE), null, issuer));
        when(verifier.verifyService("no-result-scope")).thenReturn(new AccessTokenVerification.Valid<>(noScope,
                Set.of(TianjiProcessPermissions.READ_SCOPE), null, issuer));
        when(verifier.verifyService("result-user-token")).thenReturn(new AccessTokenVerification.Invalid<>("PRINCIPAL_TYPE_MISMATCH"));
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        var identity = IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(verifier), new ServiceAccessTokenVerifier(verifier), policy,
                new com.fasterxml.jackson.databind.ObjectMapper(), new VerifiedTenantContextFilter(), verifier);
        var authorization = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", CLOCK), new com.fasterxml.jackson.databind.ObjectMapper());
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-result-service", issuer.toString());
        var owner = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-worker", issuer.toString());
        var producerRegistry = new AgentResultProducerRegistry(List.of(new AgentResultProducerRegistry.Registration(actor, "agent-east", "process-east", owner)));
        var inbox = mock(AgentResultInbox.class);
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", DIGEST,
                NOW.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        var event = new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, owner.issuerFingerprint(), owner.subjectId(), "run-one", 3),
                20, owner.issuerFingerprint(), owner.subjectId(), "intent", link, "review", 1, "run-one", 3, AgentRunStatusEnum.COMPLETED,
                NOW.truncatedTo(java.time.temporal.ChronoUnit.MICROS), DIGEST, null, "UNKNOWN");
        when(inbox.receive(eq(actor), any(AgentTerminalEvent.class))).thenAnswer(call -> {
            AgentTerminalEvent accepted = call.getArgument(1);
            producerRegistry.require(actor, accepted);
            return new AgentResultReceipt(accepted.eventId(), accepted.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, accepted.finishedAt());
        });
        new WebApplicationContextRunner().withUserConfiguration(TestWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.agent-results.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false", "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean(ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .withBean(ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean(AgentDispatchStore.class, () -> mock(AgentDispatchStore.class))
                .withBean(AgentResultProducerRegistry.class, () -> producerRegistry)
                .withBean(AgentResultInbox.class, () -> inbox)
                .withBean(PermissionExecutionGuard.class, this::guard)
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext()).apply(springSecurity()).build();
                    byte[] body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(event);
                    String path = "/api/tianji/v1/agent-result-inbox";
                    mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
                    var response = mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer result-token"))
                            .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("RECEIVED")).andReturn();
                    assertNull(response.getRequest().getAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer"));
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                    mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer result-user-token"))
                            .andExpect(status().isUnauthorized());
                    mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer no-result-scope"))
                            .andExpect(status().isForbidden());
                    mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer result-token")
                            .header("X-Egon-Tianji-Tenant", "21")).andExpect(status().isConflict());
                    var otherOwner = new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, owner.issuerFingerprint(), "another-worker", "run-one", 3),
                            20, owner.issuerFingerprint(), "another-worker", "intent", link, "review", 1, "run-one", 3, event.status(), event.finishedAt(), DIGEST, null, "UNKNOWN");
                    mvc.perform(post(path).contentType("application/json").content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(otherOwner))
                            .header("Authorization", "Bearer result-token")).andExpect(status().isNotFound());
                    var otherTenant = new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 21, owner.issuerFingerprint(), owner.subjectId(), "run-one", 3),
                            21, owner.issuerFingerprint(), owner.subjectId(), "intent", link, "review", 1, "run-one", 3, event.status(), event.finishedAt(), DIGEST, null, "UNKNOWN");
                    mvc.perform(post(path).contentType("application/json").content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(otherTenant))
                            .header("Authorization", "Bearer result-token")).andExpect(status().isNotFound());
                    serviceAvailable.set(false);
                    var unavailable = mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer result-token"))
                            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE")).andReturn();
                    assertFalse(unavailable.getResponse().getContentAsString().contains("private-backend-details"));
                    serviceAvailable.set(true);
                    var reduced = new java.util.HashSet<>(resources()); reduced.remove(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API);
                    serviceGranted.set(Set.copyOf(reduced));
                    mvc.perform(post(path).contentType("application/json").content(body).header("Authorization", "Bearer result-token"))
                            .andExpect(status().isForbidden());
                    verify(inbox, times(1)).receive(actor, event);
                }));
    }

    @Test
    void each_resource_requires_its_current_permission_and_matching_user_scope() {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Map<String, String> required = Map.of(
                TianjiProcessPermissions.RELEASE_PUBLISH_API, TianjiProcessPermissions.RELEASE_PUBLISH,
                TianjiProcessPermissions.RELEASE_GET_API, TianjiProcessPermissions.RELEASE_READ,
                TianjiProcessPermissions.START_API, TianjiProcessPermissions.START,
                TianjiProcessPermissions.GET_API, TianjiProcessPermissions.READ,
                TianjiProcessPermissions.DECISION_API, TianjiProcessPermissions.DECISION_EXECUTE);
        Set<String> all = Set.copyOf(required.values());
        granted.set(all);
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(all)), List.of(
                new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.RELEASE_READ_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.START_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.READ_SCOPE),
                new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.DECISION_SCOPE))));
        var access = new TianjiProcessAccess(guard(), CLOCK);
        for (String resource : required.keySet()) assertEquals(20, access.require(resource).tenantId());
        required.forEach((resource, permission) -> {
            var reduced = new java.util.HashSet<>(all);
            reduced.remove(permission);
            granted.set(Set.copyOf(reduced));
            assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class, () -> access.require(resource));
        });
        granted.set(all);
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(all)), List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.READ_SCOPE))));
        assertEquals(20, access.require(TianjiProcessPermissions.GET_API).tenantId());
        assertThrows(org.springframework.security.authorization.AuthorizationDeniedException.class,
                () -> access.require(TianjiProcessPermissions.START_API));
    }

    @Test
    void administrator_task_context_requires_both_current_permissions_and_both_user_scopes() {
        var request = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(request, "verified-test-token");
        request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20"); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Set<String> both = Set.of(TianjiProcessPermissions.TASK_READ, TianjiProcessPermissions.TASK_TRANSFER); granted.set(both);
        var scopes = List.of(new SimpleGrantedAuthority("SCOPE_" + HumanTaskClient.READ_SCOPE), new SimpleGrantedAuthority("SCOPE_" + HumanTaskClient.TRANSFER_SCOPE));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(both)), scopes));
        var access = new TianjiHumanTaskAccess(new TianjiProcessAccess(guard(), CLOCK), CLOCK);
        assertEquals(Set.of("50"), access.require(TianjiProcessPermissions.TASK_ADMIN_QUERY_API).roleIds());
        for (var missing : both) {
            granted.set(both.stream().filter(code -> !code.equals(missing)).collect(java.util.stream.Collectors.toUnmodifiableSet()));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.TASK_ADMIN_QUERY_API));
        }
        granted.set(both);
        for (var scope : List.of(HumanTaskClient.READ_SCOPE, HumanTaskClient.TRANSFER_SCOPE)) {
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(both)), List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.TASK_ADMIN_QUERY_API));
        }
        granted.set(Set.of(TianjiProcessPermissions.TASK_READ));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(granted.get())), List.of(new SimpleGrantedAuthority("SCOPE_" + HumanTaskClient.READ_SCOPE))));
        assertEquals(TianjiProcessPermissions.TASK_QUERY_API, access.require(TianjiProcessPermissions.TASK_QUERY_API).resourceKey());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.TASK_ADMIN_QUERY_API));
    }

    @Test
    void approval_verification_requires_a_current_service_resource_grant_and_exact_scope_and_never_accepts_user_authority() {
        var request = new MockHttpServletRequest(); request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var service = new ServiceIdentityPrincipal("approval-consumer", "20", "approval-consumer", "service-token", URI.create("https://api.example/tianji"), 1, Set.of(ApprovalClient.VERIFY_SCOPE),
                "xingyuan", "tianji", "test", "credential", NOW.minusSeconds(1), NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), URI.create("https://idp.example/tenants/20")));
        var access = new TianjiApprovalVerificationAccess(new TianjiProcessAccess(guard(), CLOCK), CLOCK);
        assertEquals("approval-consumer", access.require().actor().subjectId());
        var reduced = new java.util.HashSet<>(resources()); reduced.remove(TianjiProcessPermissions.APPROVAL_VERIFY_API); serviceGranted.set(Set.copyOf(reduced));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, access::require);
        serviceGranted.set(resources());
        var withoutScope = new ServiceIdentityPrincipal("approval-consumer", "20", "approval-consumer", "other-token", URI.create("https://api.example/tianji"), 1, Set.of(HumanTaskClient.READ_SCOPE),
                "xingyuan", "tianji", "test", "credential", NOW.minusSeconds(1), NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(withoutScope, List.of(), URI.create("https://idp.example/tenants/20")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, access::require);
        granted.set(Set.of(TianjiProcessPermissions.APPROVAL_VERIFY));
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(granted.get())), List.of(new SimpleGrantedAuthority("SCOPE_" + ApprovalClient.VERIFY_SCOPE))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, access::require);
    }

    @Test
    void method_permission_annotation_matches_the_runtime_resource_policy() throws Exception {
        var request = new MockHttpServletRequest();
        VerifiedUserTokenCarrier.set(request, "verified-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(
                new Rbac3UserDetails(USER, snapshot(granted.get())),
                List.of(new SimpleGrantedAuthority("SCOPE_" + TianjiProcessPermissions.READ_SCOPE))));
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(ProcessController.class.getMethod("instance", String.class));
        var manager = new Rbac3MethodAuthorizationManager(mock(AuthorizationService.class), guard());
        assertTrue(manager.check(() -> SecurityContextHolder.getContext().getAuthentication(), invocation).isGranted());
    }

    @Test
    void case_user_resources_require_current_permission_and_scope_for_each_operation() {
        var request = new MockHttpServletRequest();
        request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20");
        VerifiedUserTokenCarrier.set(request, "verified-test-token"); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var permissions = Set.of(TianjiProcessPermissions.CASE_START, TianjiProcessPermissions.CASE_READ, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT);
        var scopeCodes = Set.of(TianjiProcessPermissions.CASE_START_SCOPE, TianjiProcessPermissions.CASE_READ_SCOPE, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE);
        var operations = Map.of(TianjiProcessPermissions.CASE_START_API, TianjiProcessPermissions.CASE_START,
                TianjiProcessPermissions.CASE_GET_API, TianjiProcessPermissions.CASE_READ,
                TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT);
        var operationScopes = Map.of(TianjiProcessPermissions.CASE_START_API, TianjiProcessPermissions.CASE_START_SCOPE,
                TianjiProcessPermissions.CASE_GET_API, TianjiProcessPermissions.CASE_READ_SCOPE,
                TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE);
        var access = new TianjiCaseAccess(new TianjiProcessAccess(guard(), CLOCK), CLOCK);
        for (var operation : operations.entrySet()) {
            granted.set(permissions);
            var authorities = scopeCodes.stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(permissions)), authorities));
            var allowed = access.require(operation.getKey());
            assertEquals(ProcessActor.SubjectType.USER, allowed.actor().subjectType()); assertEquals(Set.of("50"), allowed.roleIds());
            assertEquals(operation.getKey(), allowed.resourceKey()); assertEquals(2, allowed.authVersion()); assertEquals(3, allowed.policyVersion());
            granted.set(permissions.stream().filter(permission -> !permission.equals(operation.getValue())).collect(java.util.stream.Collectors.toUnmodifiableSet()));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(operation.getKey()));
            granted.set(permissions);
            var reducedScopes = scopeCodes.stream().filter(scope -> !scope.equals(operationScopes.get(operation.getKey())))
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
            SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot(permissions)), reducedScopes));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(operation.getKey()));
        }
    }

    @Test
    void case_service_start_and_get_require_current_resource_and_scope_and_never_gain_operator_authority() {
        var request = new MockHttpServletRequest(); request.setAttribute(VerifiedProcessIssuerFilter.class.getName() + ".issuer", "https://idp.example/tenants/20");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var scopes = Set.of(TianjiProcessPermissions.CASE_START_SCOPE, TianjiProcessPermissions.CASE_READ_SCOPE, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE);
        var service = new ServiceIdentityPrincipal("case-consumer", "20", "case-consumer", "case-token", URI.create("https://api.example/tianji"), 1, scopes,
                "xingyuan", "tianji", "test", "credential", NOW.minusSeconds(1), NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), URI.create("https://idp.example/tenants/20")));
        var access = new TianjiCaseAccess(new TianjiProcessAccess(guard(), CLOCK), CLOCK);
        for (var resource : List.of(TianjiProcessPermissions.CASE_START_API, TianjiProcessPermissions.CASE_GET_API)) {
            var context = access.require(resource); assertEquals(ProcessActor.SubjectType.SERVICE, context.actor().subjectType());
            assertTrue(context.roleIds().isEmpty()); assertEquals("case-consumer", context.actor().subjectId());
        }
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API));
        var reduced = new java.util.HashSet<>(resources()); reduced.remove(TianjiProcessPermissions.CASE_GET_API); serviceGranted.set(Set.copyOf(reduced));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.CASE_GET_API));
        serviceGranted.set(resources());
        var wrongScope = new ServiceIdentityPrincipal("case-consumer", "20", "case-consumer", "case-other-token", URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiProcessPermissions.CASE_START_SCOPE), "xingyuan", "tianji", "test", "credential", NOW.minusSeconds(1), NOW.plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(wrongScope, List.of(), URI.create("https://idp.example/tenants/20")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> access.require(TianjiProcessPermissions.CASE_GET_API));
    }

    private PermissionExecutionGuard guard() {
        var registry = new PermissionPolicyRegistry(manifest, DIGEST, CLOCK, List.of(SCOPE));
        var scopes = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of(TianjiProcessPermissions.READ, Set.of(TianjiProcessPermissions.READ_SCOPE),
                        TianjiProcessPermissions.START, Set.of(TianjiProcessPermissions.START_SCOPE),
                        TianjiProcessPermissions.RELEASE_PUBLISH, Set.of(TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE),
                        TianjiProcessPermissions.RELEASE_READ, Set.of(TianjiProcessPermissions.RELEASE_READ_SCOPE),
                        TianjiProcessPermissions.DECISION_EXECUTE, Set.of(TianjiProcessPermissions.DECISION_SCOPE),
                        TianjiProcessPermissions.WORKER_ACQUIRE, Set.of(TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE),
                        TianjiProcessPermissions.WORKER_REPORT, Set.of(TianjiProcessPermissions.WORKER_REPORT_SCOPE)));
        var taskScopes = new java.util.HashMap<>(scopes.getPermissionScopes());
        taskScopes.put(TianjiProcessPermissions.TASK_READ, Set.of(HumanTaskClient.READ_SCOPE)); taskScopes.put(TianjiProcessPermissions.TASK_TRANSFER, Set.of(HumanTaskClient.TRANSFER_SCOPE));
        taskScopes.put(TianjiProcessPermissions.TASK_CLAIM, Set.of(HumanTaskClient.CLAIM_SCOPE)); taskScopes.put(TianjiProcessPermissions.TASK_COMPLETE, Set.of(HumanTaskClient.COMPLETE_SCOPE)); scopes.setPermissionScopes(taskScopes);
        taskScopes.put(TianjiProcessPermissions.CASE_START, Set.of(TianjiProcessPermissions.CASE_START_SCOPE));
        taskScopes.put(TianjiProcessPermissions.CASE_READ, Set.of(TianjiProcessPermissions.CASE_READ_SCOPE));
        taskScopes.put(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT, Set.of(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE)); scopes.setPermissionScopes(taskScopes);
        return new PermissionExecutionGuard(registry, new CurrentRbac3User(), new AuthorizationSnapshotLoader(client, "tianji", CLOCK),
                new PermissionServicePolicyGuard(registry, client, "tianji", CLOCK), scopes);
    }

    private PermissionManifest manifest() {
        var resources = List.of(
                resource(TianjiProcessPermissions.CASE_START_API, TianjiProcessPermissions.CASE_START, TianjiProcessPermissions.CASE_START_SCOPE,
                        "POST", "/api/tianji/v1/cases/start", "api.case.start"),
                resource(TianjiProcessPermissions.CASE_GET_API, TianjiProcessPermissions.CASE_READ, TianjiProcessPermissions.CASE_READ_SCOPE,
                        "POST", "/api/tianji/v1/cases/get", "api.case.get"),
                new PermissionManifest.ResourceDefinition(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API, ResourceType.API,
                        Map.of("methods", List.of("POST"), "paths", List.of("/api/tianji/v1/cases/act-plan-item")), ResourceAccess.PROTECTED,
                        new PermissionRequirement(PermissionMode.ALL, List.of(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT)), null,
                        List.of(PermissionManifest.PrincipalType.USER), List.of(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE),
                        new PermissionManifest.I18nReference("tianji.process.permissions", "api.case.actPlanItem"), DefinitionLifecycle.ACTIVE),
                resource(TianjiProcessPermissions.GET_API, TianjiProcessPermissions.READ, TianjiProcessPermissions.READ_SCOPE,
                        "GET", "/api/tianji/v1/process-instances/{instanceId}", "api.process.get"),
                resource(TianjiProcessPermissions.START_API, TianjiProcessPermissions.START, TianjiProcessPermissions.START_SCOPE,
                        "POST", "/api/tianji/v1/process-instances", "api.process.start"),
                resource(TianjiProcessPermissions.RELEASE_GET_API, TianjiProcessPermissions.RELEASE_READ, TianjiProcessPermissions.RELEASE_READ_SCOPE,
                        "GET", "/api/tianji/v1/process-releases/{releaseVersion}", "api.release.get"),
                resource(TianjiProcessPermissions.RELEASE_PUBLISH_API, TianjiProcessPermissions.RELEASE_PUBLISH, TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE,
                        "POST", "/api/tianji/v1/process-releases", "api.release.publish"),
                resource(TianjiProcessPermissions.DECISION_API, TianjiProcessPermissions.DECISION_EXECUTE, TianjiProcessPermissions.DECISION_SCOPE,
                        "POST", "/api/tianji/v1/decisions/evaluate", "api.decision.evaluate"),
                workerResource(TianjiProcessPermissions.WORKER_ACQUIRE_API, TianjiProcessPermissions.WORKER_ACQUIRE,
                        TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE, "/api/tianji/v1/worker-tasks/acquire", "api.worker.acquire"),
                workerResource(TianjiProcessPermissions.WORKER_COMPLETE_API, TianjiProcessPermissions.WORKER_REPORT,
                        TianjiProcessPermissions.WORKER_REPORT_SCOPE, "/api/tianji/v1/worker-tasks/complete", "api.worker.complete"),
                workerResource(TianjiProcessPermissions.WORKER_FAIL_API, TianjiProcessPermissions.WORKER_REPORT,
                        TianjiProcessPermissions.WORKER_REPORT_SCOPE, "/api/tianji/v1/worker-tasks/fail", "api.worker.fail"),
                workerResource(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API, TianjiProcessPermissions.AGENT_RESULT_RECEIVE,
                        TianjiProcessPermissions.AGENT_RESULT_RECEIVE_SCOPE, "/api/tianji/v1/agent-result-inbox", "api.agent.result.receive"),
                taskResource(TianjiProcessPermissions.TASK_GET_API, List.of(TianjiProcessPermissions.TASK_READ), List.of(HumanTaskClient.READ_SCOPE), "get"),
                taskResource(TianjiProcessPermissions.TASK_QUERY_API, List.of(TianjiProcessPermissions.TASK_READ), List.of(HumanTaskClient.READ_SCOPE), "query"),
                taskResource(TianjiProcessPermissions.TASK_CLAIM_API, List.of(TianjiProcessPermissions.TASK_CLAIM), List.of(HumanTaskClient.CLAIM_SCOPE), "claim"),
                taskResource(TianjiProcessPermissions.TASK_UNCLAIM_API, List.of(TianjiProcessPermissions.TASK_CLAIM), List.of(HumanTaskClient.CLAIM_SCOPE), "unclaim"),
                taskResource(TianjiProcessPermissions.TASK_TRANSFER_API, List.of(TianjiProcessPermissions.TASK_TRANSFER), List.of(HumanTaskClient.TRANSFER_SCOPE), "transfer"),
                taskResource(TianjiProcessPermissions.TASK_COMPLETE_API, List.of(TianjiProcessPermissions.TASK_COMPLETE), List.of(HumanTaskClient.COMPLETE_SCOPE), "complete"),
                taskResource(TianjiProcessPermissions.TASK_ADMIN_QUERY_API, List.of(TianjiProcessPermissions.TASK_READ, TianjiProcessPermissions.TASK_TRANSFER), List.of(HumanTaskClient.READ_SCOPE, HumanTaskClient.TRANSFER_SCOPE), "admin/query"),
                workerResource(TianjiProcessPermissions.APPROVAL_VERIFY_API, TianjiProcessPermissions.APPROVAL_VERIFY, ApprovalClient.VERIFY_SCOPE, ApprovalClient.PATH, "api.task.approval.verify"));
        return new PermissionManifest("1.0", new PermissionManifest.Application("xingyuan", "tianji"),
                new PermissionManifest.Producer("tianji-process-java", PermissionManifest.ProducerKind.JAVA,
                        PermissionManifest.SnapshotMode.FULL, List.of("tianji.api.process."), List.of("process-server"),
                        List.of("process-server"), false),
                new PermissionManifest.Artifact("tianji-service", "a".repeat(40), "build-1", "1"),
                List.of(), List.of(TianjiProcessPermissions.CASE_START, TianjiProcessPermissions.CASE_READ, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT, TianjiProcessPermissions.READ, TianjiProcessPermissions.START, TianjiProcessPermissions.RELEASE_READ,
                TianjiProcessPermissions.RELEASE_PUBLISH, TianjiProcessPermissions.DECISION_EXECUTE,
                TianjiProcessPermissions.WORKER_ACQUIRE, TianjiProcessPermissions.WORKER_REPORT, TianjiProcessPermissions.AGENT_RESULT_RECEIVE,
                TianjiProcessPermissions.TASK_READ, TianjiProcessPermissions.TASK_CLAIM, TianjiProcessPermissions.TASK_COMPLETE, TianjiProcessPermissions.TASK_TRANSFER, TianjiProcessPermissions.APPROVAL_VERIFY), resources, List.of(), List.of());
    }

    private static PermissionManifest.ResourceDefinition resource(String key, String permission, String scope,
                                                                  String method, String path, String label) {
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API, Map.of("methods", List.of(method), "paths", List.of(path)),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of(permission)), null,
                List.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE), List.of(scope),
                new PermissionManifest.I18nReference("tianji.process.permissions", label), DefinitionLifecycle.ACTIVE);
    }

    private static PermissionManifest.ResourceDefinition workerResource(String key, String permission, String scope,
                                                                        String path, String label) {
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API, Map.of("methods", List.of("POST"), "paths", List.of(path)),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of(permission)), null,
                List.of(PermissionManifest.PrincipalType.SERVICE), List.of(scope),
                new PermissionManifest.I18nReference("tianji.process.permissions", label), DefinitionLifecycle.ACTIVE);
    }

    private static PermissionManifest.ResourceDefinition taskResource(String key, List<String> permissions, List<String> scopes, String path) {
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API, Map.of("methods", List.of("POST"), "paths", List.of(HumanTaskClient.PATH + '/' + path)),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, permissions), null, List.of(PermissionManifest.PrincipalType.USER), scopes,
                new PermissionManifest.I18nReference("tianji.process.permissions", "api.task." + ("admin/query".equals(path) ? "adminQuery" : path)), DefinitionLifecycle.ACTIVE);
    }

    private static Set<String> resources() {
        return Set.of(TianjiProcessPermissions.CASE_START_API, TianjiProcessPermissions.CASE_GET_API, TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API, TianjiProcessPermissions.GET_API, TianjiProcessPermissions.START_API, TianjiProcessPermissions.RELEASE_GET_API,
                TianjiProcessPermissions.RELEASE_PUBLISH_API, TianjiProcessPermissions.DECISION_API,
                TianjiProcessPermissions.WORKER_ACQUIRE_API, TianjiProcessPermissions.WORKER_COMPLETE_API, TianjiProcessPermissions.WORKER_FAIL_API,
                TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API, TianjiProcessPermissions.TASK_GET_API, TianjiProcessPermissions.TASK_QUERY_API, TianjiProcessPermissions.TASK_ADMIN_QUERY_API,
                TianjiProcessPermissions.TASK_CLAIM_API, TianjiProcessPermissions.TASK_UNCLAIM_API, TianjiProcessPermissions.TASK_TRANSFER_API, TianjiProcessPermissions.TASK_COMPLETE_API, TianjiProcessPermissions.APPROVAL_VERIFY_API);
    }

    private RuntimePolicyBinding binding() {
        var hashes = new PermissionManifestCanonicalizer().digests(manifest);
        return new RuntimePolicyBinding("tianji-process-java", "1", DIGEST, hashes.policyDigest(), hashes.manifestDigest());
    }

    private SystemAuthorizationSnapshot snapshot(Set<String> permissions) {
        var view = new EffectivePermissionSnapshot(snapshotIssuer.get(), USER.subject(), SCOPE,
                "1", "2", "3", new EffectivePermissionSnapshot.ActiveRoleContext("tianji", List.of("50"), List.of("50")),
                permissions.stream().sorted().toList(), NOW.plusSeconds(30));
        return new SystemAuthorizationSnapshot("20", USER.subject(), "60", "tianji", 2, 3, List.of("50"), null, null,
                permissions, resources(), Map.of(), Map.of(), "checksum", NOW,
                view.snapshotExpiresAt(), view, binding());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, ProcessController.class, WorkerController.class, AgentResultInboxController.class, ProcessApiErrors.class, ProcessRequestBodyAdvice.class})
    static class TestWeb { }
}
