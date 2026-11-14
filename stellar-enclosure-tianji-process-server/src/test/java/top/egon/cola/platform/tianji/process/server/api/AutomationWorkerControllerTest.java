package top.egon.cola.platform.tianji.process.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.ProcessSecurityConfiguration;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ServicePolicySnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.DefinitionLifecycle;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestCanonicalizer;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.cache.AuthorizationSnapshotLoader;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedTenantContextFilter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT;

class AutomationWorkerControllerTest {
    private static final String BASE = "/api/tianji/v2/worker-tasks";
    private static final String ACQUIRE = BASE + "/acquire";
    private static final String COMPLETE = BASE + "/complete";
    private static final String FAIL = BASE + "/fail";
    private static final String ACQUIRE_KEY = "tianji.api.process.worker.acquire.v2";
    private static final String COMPLETE_KEY = "tianji.api.process.worker.complete.v2";
    private static final String FAIL_KEY = "tianji.api.process.worker.fail.v2";
    private static final String ACQUIRE_SCOPE = "tianji:worker:acquire";
    private static final String REPORT_SCOPE = "tianji:worker:report";
    private static final URI ISSUER = URI.create("https://idp.test/tenant");
    private static final String LEASE = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-14T08:35:00.123456Z");
    private static final String ACQUIRE_BODY = "{\"capabilityKey\":\"riskCheck\",\"capabilityVersion\":1}";
    private static final String REPORT_PREFIX = """
            {"taskId":"task-1","activityOccurrenceId":"occ-1","nodeExecutionRef":"node-1","leaseToken":"%s",
             "commandId":"%s",
            """;
    private static final String COMPLETE_BODY = REPORT_PREFIX.formatted(LEASE, "complete-1") + """
            "effectKey":"effect-1","businessReceiptId":"receipt-1","output":{"approved":true}}
            """;
    private static final String FAIL_BODY = REPORT_PREFIX.formatted(LEASE, "fail-1") + """
            "kind":"UNKNOWN","errorCode":"WORKER_EXECUTION_FAILED","businessReceiptId":null}
            """;

    private static final CatalogScope CATALOG = new CatalogScope("10", "20", "xingyuan", "tianji", "default", "test");
    private static final ProcessActor WORKER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", ISSUER.toString());
    private final FlowableWorkerService workers = mock(FlowableWorkerService.class);
    private final AtomicReference<Set<String>> currentResources = new AtomicReference<>(Set.of(ACQUIRE_KEY, COMPLETE_KEY, FAIL_KEY));

    @Test
    void verified_worker_uses_the_typed_acquisition_route() throws Exception {
        var command = new AutomationWorkerAcquireCommand("riskCheck", 1);
        when(workers.acquireAutomation(WORKER, command)).thenReturn(
                new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, null),
                new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, task()));
        withMvc(mvc -> {
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.status").value("EMPTY")).andExpect(jsonPath("$.task").value(nullValue()));
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACQUIRED"))
                    .andExpect(jsonPath("$.task.effectRecovery").value(nullValue()))
                    .andExpect(jsonPath("$.task.taskContractSha256").value(task().getTaskContractSha256()))
                    .andExpect(jsonPath("$.task.attemptDeadlineAt").value(NOW.plusSeconds(60).toString()))
                    .andExpect(jsonPath("$.task.leaseExpiresAt").value(NOW.plusSeconds(30).toString()))
                    .andExpect(jsonPath("$.task.targetId").value(nullValue()))
                    .andExpect(jsonPath("$.task.tenantId").doesNotExist());
        });
        verify(workers, times(2)).acquireAutomation(WORKER, command);
    }

    @Test
    void requires_current_exact_service_scope_and_catalog_resource_for_each_endpoint() throws Exception {
        withMvc(mvc -> {
            mvc.perform(typed(ACQUIRE, null, ACQUIRE_BODY)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
            mvc.perform(typed(ACQUIRE, "user-token", ACQUIRE_BODY)).andExpect(status().isUnauthorized());
            mvc.perform(typed(ACQUIRE, "report-token", ACQUIRE_BODY)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
            mvc.perform(typed(COMPLETE, "acquire-token", COMPLETE_BODY)).andExpect(status().isForbidden());
            mvc.perform(typed(FAIL, "acquire-token", FAIL_BODY)).andExpect(status().isForbidden());
            mvc.perform(get(ACQUIRE).header("Authorization", "Bearer acquire-token")).andExpect(status().isForbidden());
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY).header("X-Egon-Tianji-Tenant", "21"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
            currentResources.set(Set.of("tianji.api.process.worker.acquire", COMPLETE_KEY));
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY)).andExpect(status().isForbidden());
            mvc.perform(typed(FAIL, "report-token", FAIL_BODY)).andExpect(status().isForbidden());
        });
        verifyNoInteractions(workers);
    }

    @Test
    void strict_acquisition_and_report_wire_reject_fields_types_tokens_and_cross_profile_bodies() throws Exception {
        withMvc(mvc -> {
            for (String invalid : List.of(ACQUIRE_BODY + "{}", ACQUIRE_BODY.replace(":1}", ":1.0}"),
                    ACQUIRE_BODY.replace(":1}", ":\"1\"}"), ACQUIRE_BODY.replace(":1}", ":1000000000000000}"),
                    ACQUIRE_BODY.replace(":1}", ":1,\"capabilityVersion\":1}"),
                    ACQUIRE_BODY.replace(":1}", ":1,\"tenantId\":20}"),
                    "{\"topic\":\"riskCheck\",\"schemaVersion\":\"1\"}")) {
                mvc.perform(typed(ACQUIRE, "acquire-token", invalid)).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
            }
            for (String invalid : List.of(COMPLETE_BODY + "{}", COMPLETE_BODY.replace(LEASE, LEASE.substring(0, 42) + "B"),
                    COMPLETE_BODY.replace("\"approved\":true", "\"approved\":true,\"approved\":false"),
                    COMPLETE_BODY.replace("\"approved\":true", "\"approved\":1e2"),
                    COMPLETE_BODY.replace("\"approved\":true", "\"approved\":1000000000000000"),
                    COMPLETE_BODY.replace("\"effectKey\"", "\"taskContractSha256\":\"" + HASH + "\",\"effectKey\""))) {
                mvc.perform(typed(COMPLETE, "report-token", invalid)).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
            }
            for (String invalid : List.of(FAIL_BODY.replace(",\"businessReceiptId\":null", ""),
                    FAIL_BODY.replace("\"UNKNOWN\"", "0"), FAIL_BODY.replace("\"UNKNOWN\"", "\"BUSINESS_REJECTED\""),
                    FAIL_BODY.replace("\"errorCode\"", "\"effectRecovery\":{},\"errorCode\""))) {
                mvc.perform(typed(FAIL, "report-token", invalid)).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
            }
            mvc.perform(typed(COMPLETE, "report-token", COMPLETE_BODY + " ".repeat(256 * 1024)))
                    .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY).param("actor", "worker"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY).contentType(MediaType.TEXT_PLAIN))
                    .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("TIANJI_UNSUPPORTED_MEDIA_TYPE"));
            byte[] invalidUtf8 = ACQUIRE_BODY.getBytes(StandardCharsets.UTF_8);
            invalidUtf8[invalidUtf8.length - 1] = (byte) 0xFF;
            mvc.perform(typed(ACQUIRE, "acquire-token", ACQUIRE_BODY).content(invalidUtf8))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        });
        verifyNoInteractions(workers);
    }

    @Test
    void report_replay_keeps_original_response_and_mismatch_does_not_disclose_lease_material() throws Exception {
        var complete = new AutomationWorkerCompletionCommand("task-1", "occ-1", "node-1", LEASE, "complete-1",
                "effect-1", "receipt-1", Map.of("approved", true));
        var fail = new AutomationWorkerFailureCommand("task-1", "occ-1", "node-1", LEASE, "fail-1",
                AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "WORKER_EXECUTION_FAILED", null);
        when(workers.completeAutomation(WORKER, complete)).thenReturn(
                new AutomationWorkerReportDTO("task-1", "occ-1", AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, NOW));
        when(workers.completeAutomation(WORKER, new AutomationWorkerCompletionCommand("task-1", "occ-1", "node-1", LEASE,
                "complete-1", "effect-1", "receipt-1", Map.of("approved", false)))).thenThrow(new ProcessConflictException());
        when(workers.failAutomation(WORKER, fail)).thenReturn(
                new AutomationWorkerReportDTO("task-1", "occ-1", AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN, NOW));
        when(workers.failAutomation(WORKER, new AutomationWorkerFailureCommand("task-1", "occ-1", "node-1", LEASE,
                "complete-1", AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "WORKER_EXECUTION_FAILED", null)))
                .thenThrow(new ProcessConflictException());
        when(workers.failAutomation(WORKER, new AutomationWorkerFailureCommand("task-1", "occ-1", "node-1", LEASE,
                "lost-lease", AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "WORKER_EXECUTION_FAILED", null)))
                .thenThrow(new WorkerLeaseLostException());
        withMvc(mvc -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                mvc.perform(typed(COMPLETE, "report-token", COMPLETE_BODY))
                        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.outcome").value("COMPLETED")).andExpect(jsonPath("$.decidedAt").value(NOW.toString()))
                        .andExpect(jsonPath("$.leaseToken").doesNotExist()).andExpect(jsonPath("$.output").doesNotExist());
                mvc.perform(typed(FAIL, "report-token", FAIL_BODY))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("UNKNOWN"))
                        .andExpect(jsonPath("$.decidedAt").value(NOW.toString()));
            }
            mvc.perform(typed(COMPLETE, "report-token", COMPLETE_BODY.replace("true", "false")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
            mvc.perform(typed(FAIL, "report-token", FAIL_BODY.replace("fail-1", "complete-1")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
            mvc.perform(typed(FAIL, "report-token", FAIL_BODY.replace("fail-1", "lost-lease")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_WORKER_LEASE_LOST"))
                    .andExpect(jsonPath("$.leaseToken").doesNotExist()).andExpect(jsonPath("$.correlationId").value(nullValue()));
        });
        verify(workers, times(2)).completeAutomation(WORKER, complete);
        verify(workers, times(2)).failAutomation(WORKER, fail);
    }

    private void withMvc(CheckedMvc checks) throws Exception {
        var jwt = mock(IdpJwtVerifier.class);
        Instant now = Instant.now();
        serviceToken(jwt, "acquire-token", ACQUIRE_SCOPE, now);
        serviceToken(jwt, "report-token", REPORT_SCOPE, now);
        when(jwt.verifyService("user-token")).thenReturn(new AccessTokenVerification.Invalid<>("PRINCIPAL_TYPE_MISMATCH"));
        var client = mock(Rbac3AuthorizationClient.class);
        var guard = permissions(client, now);
        var mapper = new ObjectMapper();
        var authorization = new Rbac3BearerAuthenticationFilter(new AuthorizationSnapshotLoader(client, "tianji", Clock.systemUTC()), mapper);
        new WebApplicationContextRunner().withUserConfiguration(WorkerSecurityWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(IdpJwtVerifier.class, () -> jwt)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .withBean(PermissionExecutionGuard.class, () -> guard)
                .withBean("flowableWorkerService", FlowableWorkerService.class, () -> workers)
                .withBean(WorkerTopicRegistry.class, () -> mock(WorkerTopicRegistry.class))
                .withBean("processApiSchemaVerifier", ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean("processReleaseService", ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean("processExecutionService", ProcessExecutionService.class, () -> mock(ProcessExecutionService.class))
                .withBean(ProcessDecisionService.class, () -> mock(ProcessDecisionService.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    checks.run(MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).apply(springSecurity()).build());
                });
    }

    private static void serviceToken(IdpJwtVerifier jwt, String token, String scope, Instant now) {
        var worker = new ServiceIdentityPrincipal("worker", "20", "worker", token,
                URI.create("https://api.test/tianji"), 1, Set.of(scope), "xingyuan", "worker", "test", "credential",
                now.minusSeconds(10), now.plusSeconds(120));
        when(jwt.verifyService(token)).thenReturn(new AccessTokenVerification.Valid<>(worker, Set.of(scope), null, ISSUER));
    }

    private PermissionExecutionGuard permissions(Rbac3AuthorizationClient client, Instant now) throws InterruptedException {
        var manifest = new PermissionManifest("1.0", new PermissionManifest.Application("xingyuan", "tianji"),
                new PermissionManifest.Producer("tianji-process-java", PermissionManifest.ProducerKind.JAVA,
                        PermissionManifest.SnapshotMode.FULL, List.of("tianji.api.process."), List.of("process-server"),
                        List.of("process-server"), false),
                new PermissionManifest.Artifact("tianji-service", "a".repeat(40), "build-1", "1"),
                List.of(), List.of("TIANJI_WORKER_ACQUIRE", "TIANJI_WORKER_REPORT"),
                List.of(resource(ACQUIRE_KEY, ACQUIRE, "TIANJI_WORKER_ACQUIRE", ACQUIRE_SCOPE),
                        resource(COMPLETE_KEY, COMPLETE, "TIANJI_WORKER_REPORT", REPORT_SCOPE),
                        resource(FAIL_KEY, FAIL, "TIANJI_WORKER_REPORT", REPORT_SCOPE)), List.of(), List.of());
        var digests = new PermissionManifestCanonicalizer().digests(manifest);
        var binding = new RuntimePolicyBinding("tianji-process-java", "1", HASH, digests.policyDigest(), digests.manifestDigest());
        when(client.fetchPolicy(eq("tianji"), any(ServiceIdentityPrincipal.class)))
                .thenAnswer(call -> new ServicePolicySnapshot(CATALOG, "1", "3", binding, currentResources.get(), now.plusSeconds(90)));
        var clock = Clock.systemUTC();
        var registry = new PermissionPolicyRegistry(manifest, HASH, clock, List.of(CATALOG));
        return new PermissionExecutionGuard(registry, new CurrentRbac3User(), new AuthorizationSnapshotLoader(client, "tianji", clock),
                new PermissionServicePolicyGuard(registry, client, "tianji", clock));
    }

    private static PermissionManifest.ResourceDefinition resource(String key, String path, String permission, String scope) {
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API, Map.of("methods", List.of("POST"), "paths", List.of(path)),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of(permission)), null,
                List.of(PermissionManifest.PrincipalType.SERVICE), List.of(scope), new PermissionManifest.I18nReference("tianji.process.permissions",
                "api.worker"), DefinitionLifecycle.ACTIVE);
    }

    private static AutomationWorkerTaskDTO task() {
        var task = new AutomationWorkerTaskDTO().setTaskId("task-1").setActivityOccurrenceId("occ-1").setNodeExecutionRef("node-1")
                .setLeaseToken(LEASE).setCapabilityKey("riskCheck").setCapabilityVersion(1).setInput(Map.of("amount", 10))
                .setInputSha256(HASH).setEffectKey("effect-1").setAttemptDeadlineAt(NOW.plusSeconds(60)).setLeaseExpiresAt(NOW.plusSeconds(30))
                .setHandlerVersion("worker-1").setEffectMode(TRANSACTIONAL_RECEIPT).setInputSchemaSha256(HASH).setOutputSchemaSha256(HASH)
                .setEffectFingerprint(HASH);
        return task.setTaskContractSha256(task.contractSha256(20, "process-source"));
    }

    private static MockHttpServletRequestBuilder typed(String path, String token, String body) {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @FunctionalInterface
    private interface CheckedMvc {
        void run(MockMvc mvc) throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessSecurityConfiguration.class, WorkerController.class, ProcessApiErrors.class, ProcessRequestBodyAdvice.class})
    static class WorkerSecurityWeb {
        @Bean("idpBearerAuthenticationFilter")
        IdpBearerAuthenticationFilter idpBearerAuthenticationFilter(IdpJwtVerifier jwt, IdpEndpointAuthenticationPolicy endpointPolicy) {
            return IdpBearerAuthenticationFilter.create(new UserAccessTokenVerifier(jwt), new ServiceAccessTokenVerifier(jwt),
                    endpointPolicy, new ObjectMapper(), new VerifiedTenantContextFilter(), jwt);
        }
    }
}
