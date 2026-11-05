package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessWorkerRecoveryBlockedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessWorkerRecoveryService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_FAILURES_RESOURCE_KEY;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_RECOVERIES_RESOURCE_KEY;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_RECOVER_SCOPE;

class ProcessWorkerRecoveryControllerTest {
    private static final String INSTANCE = "proc-42";
    private static final String BASE_PATH = "/api/tianji/v1/process-instances/" + INSTANCE;
    private static final String GET_PATH = BASE_PATH + "/worker-failures";
    private static final String POST_PATH = BASE_PATH + "/worker-recoveries";
    private static final String VERSION = "a".repeat(64);
    private static final String RECOVER_PERMISSION = "TIANJI_PROCESS_WORKER_RECOVER";
    private static final Instant NOW = Instant.parse("2026-11-05T05:50:01.000001Z");
    private static final ProcessActor ACTOR = ProcessActor.verified(42, ProcessActor.SubjectType.USER, "owner",
            "https://idp.example/tenants/42");
    private static final RecoverProcessWorkerCommand COMMAND = new RecoverProcessWorkerCommand(
            INSTANCE, "job-18", "restore-18", VERSION, "只读核验 🛠 complete");

    private final ProcessWorkerRecoveryService service = mock(ProcessWorkerRecoveryService.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(access.require(WORKER_FAILURES_RESOURCE_KEY)).thenReturn(ACTOR);
        when(access.require(WORKER_RECOVERIES_RESOURCE_KEY)).thenReturn(ACTOR);
        var controller = assertDoesNotThrow(() -> new ProcessWorkerRecoveryController(service, access));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @Test
    void both_operations_declare_read_and_recover_for_user_and_service_only() throws Exception {
        var failures = ProcessWorkerRecoveryController.class.getMethod("failures", String.class);
        var recover = ProcessWorkerRecoveryController.class.getMethod("recover", String.class, RecoverProcessWorkerCommand.class);
        for (var method : List.of(failures, recover)) {
            var resource = method.getAnnotation(PermissionResource.class);
            var permissions = method.getAnnotation(RequiresPermissions.class);
            assertNotNull(resource);
            assertNotNull(permissions);
            assertArrayEquals(new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER,
                    PermissionManifest.PrincipalType.SERVICE}, resource.allowedPrincipals());
            assertArrayEquals(new String[]{TianjiProcessPermissions.READ_SCOPE, WORKER_RECOVER_SCOPE}, resource.oauthScopes());
            assertArrayEquals(new String[]{TianjiProcessPermissions.READ, RECOVER_PERMISSION}, permissions.codes());
        }
        assertEquals(WORKER_FAILURES_RESOURCE_KEY, failures.getAnnotation(PermissionResource.class).key());
        assertEquals(WORKER_RECOVERIES_RESOURCE_KEY, recover.getAnnotation(PermissionResource.class).key());
    }

    @Test
    void controller_is_enabled_only_with_all_three_flags() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(ProcessWorkerRecoveryController.class)
                .withBean(ProcessWorkerRecoveryService.class, () -> service)
                .withBean(TianjiProcessAccess.class, () -> access);
        for (var missing : List.of("api", "worker", "worker-recovery")) {
            var remaining = List.of("api", "worker", "worker-recovery").stream().filter(flag -> !missing.equals(flag))
                    .map(flag -> "stellar.tianji.process." + flag + ".enabled=true").toArray(String[]::new);
            runner.withPropertyValues(remaining).run(context ->
                    assertTrue(context.getBeansOfType(ProcessWorkerRecoveryController.class).isEmpty(), missing));
        }
        runner.withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true",
                "stellar.tianji.process.worker-recovery.enabled=true").run(context ->
                assertEquals(1, context.getBeansOfType(ProcessWorkerRecoveryController.class).size()));
        verifyNoInteractions(service, access);
    }

    @Test
    void both_routes_reauthorize_and_return_only_the_closed_no_store_results() throws Exception {
        var query = new GetProcessInstanceQuery(INSTANCE);
        var eligible = new ProcessWorkerFailureDTO("job-18", "check", true, ProcessWorkerFailureDTO.StatusEnum.RETRY_AVAILABLE,
                ProcessWorkerFailureDTO.ReasonEnum.NONE, ProcessWorkerFailureDTO.ReportedFailureEnum.RETRYABLE, VERSION);
        var historical = new ProcessWorkerFailureDTO("job-20", null, false, ProcessWorkerFailureDTO.StatusEnum.HISTORICAL_FAILURE,
                ProcessWorkerFailureDTO.ReasonEnum.UNKNOWN_OUTCOME, ProcessWorkerFailureDTO.ReportedFailureEnum.UNKNOWN, null);
        when(service.workerFailures(ACTOR, query)).thenReturn(new ProcessWorkerFailuresDTO(INSTANCE, NOW, List.of(eligible, historical)));
        when(service.recoverWorker(ACTOR, COMMAND)).thenReturn(result());

        var listing = mvc.perform(get(GET_PATH))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instanceId").value(INSTANCE)).andExpect(jsonPath("$.observedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.failures[0].jobId").value("job-18"))
                .andExpect(jsonPath("$.failures[0].activityId").value("check"))
                .andExpect(jsonPath("$.failures[0].nativeDeadletter").value(true))
                .andExpect(jsonPath("$.failures[0].status").value("RETRY_AVAILABLE"))
                .andExpect(jsonPath("$.failures[0].reason").value("NONE"))
                .andExpect(jsonPath("$.failures[0].reportedFailure").value("RETRYABLE"))
                .andExpect(jsonPath("$.failures[0].expectedVersion").value(VERSION))
                .andExpect(jsonPath("$.failures[1].jobId").value("job-20"))
                .andExpect(jsonPath("$.failures[1].nativeDeadletter").value(false))
                .andExpect(jsonPath("$.failures[1].status").value("HISTORICAL_FAILURE"))
                .andExpect(jsonPath("$.failures[1].reason").value("UNKNOWN_OUTCOME"))
                .andExpect(jsonPath("$.failures[1].reportedFailure").value("UNKNOWN"))
                .andReturn();
        var listingBody = json.readTree(listing.getResponse().getContentAsByteArray());
        assertEquals(Set.of("instanceId", "observedAt", "failures"), Set.copyOf(listingBody.propertyNames()));
        assertEquals(Set.of("jobId", "activityId", "nativeDeadletter", "status", "reason", "reportedFailure", "expectedVersion"),
                Set.copyOf(listingBody.get("failures").get(1).propertyNames()));
        assertTrue(listingBody.get("failures").get(1).get("activityId").isNull());
        assertTrue(listingBody.get("failures").get(1).get("expectedVersion").isNull());

        var first = performSuccessfulRecovery();
        var replay = performSuccessfulRecovery();
        assertArrayEquals(first.getResponse().getContentAsByteArray(), replay.getResponse().getContentAsByteArray());
        assertEquals(Set.of("instanceId", "jobId", "occurrenceId", "commandId", "status", "retries", "decidedAt"),
                Set.copyOf(json.readTree(first.getResponse().getContentAsByteArray()).propertyNames()));

        var ordered = inOrder(access, service);
        ordered.verify(access).require(WORKER_FAILURES_RESOURCE_KEY);
        ordered.verify(service).workerFailures(ACTOR, query);
        ordered.verify(access).require(WORKER_RECOVERIES_RESOURCE_KEY);
        ordered.verify(service).recoverWorker(ACTOR, COMMAND);
        ordered.verify(access).require(WORKER_RECOVERIES_RESOURCE_KEY);
        ordered.verify(service).recoverWorker(ACTOR, COMMAND);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void malformed_or_nonaffine_commands_never_invoke_the_service() throws Exception {
        String body = json.writeValueAsString(new RecoverProcessWorkerCommand(INSTANCE, "job-18", "restore-18",
                VERSION, "safe-marker"));
        for (String invalid : List.of("{}", "[]",
                body.replace("\"job-18\"", "null"), body.replace("\"job-18\"", "123"),
                body.replace("\"job-18\"", "true"), body.replace("\"job-18\"", "{}"),
                body.replace("\"job-18\"", "[]"), body.replace("\"job-18\"", "\" job-18\""),
                body.replace(VERSION, VERSION.toUpperCase(java.util.Locale.ROOT)),
                body.replace("safe-marker", " safe-marker"),
                body.substring(0, body.length() - 1) + ",\"extra\":\"secret\"}",
                body.substring(0, body.length() - 1) + ",\"jobId\":\"job-19\"}",
                body + " {}", body.replace("safe-marker", "\\" + "uD800"))) {
            var response = mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andReturn();
            assertError(response, "TIANJI_INVALID_COMMAND");
        }

        byte[] invalidUtf8 = body.getBytes(StandardCharsets.UTF_8);
        int marker = body.indexOf("safe-marker");
        invalidUtf8[marker] = (byte) 0xC3;
        invalidUtf8[marker + 1] = '(';
        assertError(mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).content(invalidUtf8))
                .andExpect(status().isBadRequest()).andReturn(), "TIANJI_INVALID_COMMAND");
        assertError(mvc.perform(post("/api/tianji/v1/process-instances/another/worker-recoveries")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(COMMAND)))
                .andExpect(status().isBadRequest()).andReturn(), "TIANJI_INVALID_COMMAND");
        verifyNoInteractions(service);
    }

    @Test
    void recovery_body_is_capped_independently_of_cancellation_for_declared_and_streamed_lengths() throws Exception {
        var body = json.writeValueAsBytes(COMMAND);
        when(service.recoverWorker(ACTOR, COMMAND)).thenReturn(result());
        mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_LENGTH, "8192")
                        .content(padded(body, 8192)))
                .andExpect(status().isOk());

        var oversized = mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_LENGTH, "8193").content(body))
                .andExpect(status().isContentTooLarge()).andReturn();
        assertError(oversized, "TIANJI_REQUEST_TOO_LARGE");

        var advice = new ProcessRequestBodyAdvice();
        var chunked = new MockHttpInputMessage(padded(body, 8193));
        chunked.getHeaders().set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        assertEquals(-1, chunked.getHeaders().getContentLength());
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class, () ->
                advice.beforeBodyRead(chunked, null, RecoverProcessWorkerCommand.class, null));

        var cancel = new CancelProcessCommand(INSTANCE, "cancel-18", VERSION, "manual");
        var oldChunked = new MockHttpInputMessage(padded(json.writeValueAsBytes(cancel), 4097));
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class, () ->
                advice.beforeBodyRead(oldChunked, null, CancelProcessCommand.class, null));
    }

    @Test
    void permission_and_tenant_errors_prevent_both_operations_from_using_the_service() throws Exception {
        for (var error : List.of(
                new Error(new AuthenticationCredentialsNotFoundException("private-auth"), 401, "TIANJI_UNAUTHENTICATED"),
                new Error(new AccessDeniedException("private-denial"), 403, "TIANJI_PERMISSION_DENIED"),
                new Error(new ProcessTenantChangedException(), 409, "TIANJI_TENANT_CONTEXT_CHANGED"))) {
            doThrow(error.failure()).when(access).require(WORKER_FAILURES_RESOURCE_KEY);
            doThrow(error.failure()).when(access).require(WORKER_RECOVERIES_RESOURCE_KEY);
            assertError(mvc.perform(get(GET_PATH)).andExpect(status().is(error.status())).andReturn(), error.code());
            assertError(mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(COMMAND)))
                    .andExpect(status().is(error.status())).andReturn(), error.code());
        }
        verifyNoInteractions(service);
    }

    @Test
    void service_errors_keep_fixed_statuses_and_never_echo_the_request_or_causes() throws Exception {
        for (var error : List.of(
                new Error(new ProcessResourceNotFoundException(), 404, "TIANJI_RESOURCE_NOT_FOUND"),
                new Error(new ProcessWorkerRecoveryBlockedException(), 409, "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED"),
                new Error(new ProcessVersionConflictException(), 409, "TIANJI_PROCESS_VERSION_CONFLICT"),
                new Error(new ProcessConflictException(), 409, "TIANJI_IDEMPOTENCY_CONFLICT"),
                new Error(new IllegalStateException("private-dependency"), 503, "TIANJI_DEPENDENCY_UNAVAILABLE"),
                new Error(new RuntimeException("private-unexpected"), 500, "TIANJI_INTERNAL_ERROR"))) {
            doThrow(error.failure()).when(service).recoverWorker(any(), any());
            assertError(mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(COMMAND)))
                    .andExpect(status().is(error.status())).andReturn(), error.code());
        }
    }

    private MvcResult performSuccessfulRecovery() throws Exception {
        return mvc.perform(post(POST_PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(COMMAND)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instanceId").value(INSTANCE)).andExpect(jsonPath("$.jobId").value("job-18"))
                .andExpect(jsonPath("$.occurrenceId").value("job-18")).andExpect(jsonPath("$.commandId").value("restore-18"))
                .andExpect(jsonPath("$.status").value("RETRY_SCHEDULED")).andExpect(jsonPath("$.retries").value(1))
                .andExpect(jsonPath("$.decidedAt").value(NOW.toString())).andReturn();
    }

    private void assertError(MvcResult result, String code) throws Exception {
        var response = result.getResponse();
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals(Set.of("code", "correlationId"), Set.copyOf(body.propertyNames()));
        assertEquals(code, body.get("code").asString());
        assertTrue(body.get("correlationId").isNull());
        var rawBody = response.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(rawBody.contains(COMMAND.reason()));
        assertFalse(rawBody.contains("private"));
    }

    private static byte[] padded(byte[] body, int size) {
        var bytes = Arrays.copyOf(body, size);
        Arrays.fill(bytes, body.length, size, (byte) ' ');
        return bytes;
    }

    private static RecoverProcessWorkerResult result() {
        return new RecoverProcessWorkerResult(INSTANCE, "job-18", "job-18", "restore-18",
                RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED, 1, NOW);
    }

    private record Error(RuntimeException failure, int status, String code) { }
}
