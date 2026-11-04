package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlBlockReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessCancellationBlockedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessControlControllerTest {
    private static final String INSTANCE = "process-one";
    private static final String PATH = "/api/tianji/v1/process-instances/" + INSTANCE;
    private static final String HASH = "a".repeat(64);
    private static final String RESULT_HASH = "b".repeat(64);
    private static final Instant STARTED = Instant.parse("2026-11-05T01:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-11-05T01:01:00Z");
    private static final Instant FIRST = Instant.parse("2026-11-05T01:02:00.123456Z");
    private static final Instant SECOND = Instant.parse("2026-11-05T01:03:00.654321Z");
    private static final ProcessActor ACTOR = ProcessActor.verified(
            20, ProcessActor.SubjectType.USER, "owner", "https://idp.example/tenants/20");

    private final ProcessControlService controls = mock(ProcessControlService.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(access.require(any())).thenReturn(ACTOR);
        mvc = MockMvcBuilders.standaloneSetup(new ProcessControlController(controls, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @Test
    void both_routes_reauthorize_before_service_and_preserve_independent_clock_orders() throws Exception {
        when(controls.control(eq(ACTOR), any())).thenReturn(snapshot());
        when(controls.cancel(eq(ACTOR), any())).thenAnswer(call -> {
            CancelProcessCommand command = call.getArgument(1);
            return "cancel-one".equals(command.commandId())
                    ? result(command.commandId(), SECOND, FIRST)
                    : result(command.commandId(), FIRST, SECOND);
        });

        mvc.perform(get(PATH + "/control")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.process.instanceId").value(INSTANCE))
                .andExpect(jsonPath("$.activeWaits[0].kind").value("HUMAN_TASK"))
                .andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"))
                .andExpect(jsonPath("$.version").value(HASH));
        mvc.perform(post(PATH + "/cancellations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(command("cancel-one"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.finishedAt").value(SECOND.toString()))
                .andExpect(jsonPath("$.decidedAt").value(FIRST.toString()));
        mvc.perform(post(PATH + "/cancellations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(command("cancel-two"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").value(FIRST.toString()))
                .andExpect(jsonPath("$.decidedAt").value(SECOND.toString()));

        var ordered = inOrder(access, controls);
        ordered.verify(access).require(TianjiProcessPermissions.CONTROL_API);
        ordered.verify(controls).control(eq(ACTOR), any());
        ordered.verify(access).require(TianjiProcessPermissions.CANCEL_API);
        ordered.verify(controls).cancel(eq(ACTOR), any());
        ordered.verify(access).require(TianjiProcessPermissions.CANCEL_API);
        ordered.verify(controls).cancel(eq(ACTOR), any());
    }

    @Test
    void cancellation_body_is_exactly_four_fields_bounded_to_4k_and_matches_the_path() throws Exception {
        for (String invalid : List.of(
                "{}",
                "[]",
                "{\"instanceId\":null,\"commandId\":\"cancel\",\"expectedVersion\":\"" + HASH + "\",\"reason\":\"reason\"}",
                "{\"instanceId\":7,\"commandId\":\"cancel\",\"expectedVersion\":\"" + HASH + "\",\"reason\":\"reason\"}",
                "{\"instanceId\":\"process-one\",\"commandId\":\"cancel\",\"expectedVersion\":\"" + HASH + "\",\"reason\":\"reason\",\"extra\":\"x\"}",
                "{\"instanceId\":\"process-one\",\"instanceId\":\"process-one\",\"commandId\":\"cancel\",\"expectedVersion\":\"" + HASH + "\",\"reason\":\"reason\"}",
                "{\"instanceId\":\"process-one\",\"commandId\":\"cancel\",\"expectedVersion\":\"" + HASH + "\",\"reason\":\"reason\"}{}")) {
            mvc.perform(post(PATH + "/cancellations").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        mvc.perform(post("/api/tianji/v1/process-instances/another/cancellations")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command("cancel-mismatch"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        String oversized = "{\"instanceId\":\"process-one\",\"commandId\":\"cancel\",\"expectedVersion\":\""
                + HASH + "\",\"reason\":\"" + "x".repeat(4096) + "\"}";
        mvc.perform(post(PATH + "/cancellations").contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        verifyNoInteractions(controls);
    }

    @Test
    void cancellation_errors_are_closed_noncacheable_and_do_not_expose_causes() throws Exception {
        var errors = List.of(
                new Error(new IllegalArgumentException("private-invalid"), 400, "TIANJI_INVALID_COMMAND"),
                new Error(new AuthenticationCredentialsNotFoundException("private-auth"), 401, "TIANJI_UNAUTHENTICATED"),
                new Error(new AccessDeniedException("private-denied"), 403, "TIANJI_PERMISSION_DENIED"),
                new Error(new ProcessResourceNotFoundException(), 404, "TIANJI_RESOURCE_NOT_FOUND"),
                new Error(new ProcessConflictException(), 409, "TIANJI_IDEMPOTENCY_CONFLICT"),
                new Error(new ProcessVersionConflictException(), 409, "TIANJI_PROCESS_VERSION_CONFLICT"),
                new Error(new ProcessCancellationBlockedException(), 409, "TIANJI_PROCESS_CANCEL_BLOCKED"),
                new Error(new ProcessTenantChangedException(), 409, "TIANJI_TENANT_CONTEXT_CHANGED"),
                new Error(new IllegalStateException("private-dependency"), 503, "TIANJI_DEPENDENCY_UNAVAILABLE"),
                new Error(new RuntimeException("private-runtime"), 500, "TIANJI_INTERNAL_ERROR"));
        for (var error : errors) {
            doThrow(error.failure()).when(controls).cancel(any(), any());
            var response = mvc.perform(post(PATH + "/cancellations").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(command("cancel-error"))))
                    .andExpect(status().is(error.status()))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(error.code())).andReturn().getResponse();
            var body = json.readTree(response.getContentAsString());
            assertEquals(2, body.size());
            assertFalse(response.getContentAsString().contains("private"));
        }
    }

    @Test
    void both_operations_declare_read_and_cancel_for_user_and_service_only() throws Exception {
        for (var method : List.of(
                ProcessControlController.class.getMethod("control", String.class),
                ProcessControlController.class.getMethod("cancel", String.class, CancelProcessCommand.class))) {
            PermissionResource resource = method.getAnnotation(PermissionResource.class);
            RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
            assertNotNull(resource);
            assertNotNull(permissions);
            assertArrayEquals(new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER,
                    PermissionManifest.PrincipalType.SERVICE}, resource.allowedPrincipals());
            assertArrayEquals(new String[]{TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.CANCEL_SCOPE},
                    resource.oauthScopes());
            assertArrayEquals(new String[]{TianjiProcessPermissions.READ, TianjiProcessPermissions.CANCEL},
                    permissions.codes());
        }
        assertEquals(TianjiProcessPermissions.CONTROL_API,
                ProcessControlController.class.getMethod("control", String.class)
                        .getAnnotation(PermissionResource.class).key());
        assertEquals(TianjiProcessPermissions.CANCEL_API,
                ProcessControlController.class.getMethod("cancel", String.class, CancelProcessCommand.class)
                        .getAnnotation(PermissionResource.class).key());
    }

    private static CancelProcessCommand command(String commandId) {
        return new CancelProcessCommand(INSTANCE, commandId, HASH, "owner requested cancellation");
    }

    private static ProcessControlSnapshotDTO snapshot() {
        var process = new ProcessInstanceDTO(INSTANCE, "invoice", "release-one", "business-one", "correlation-one",
                ProcessInstanceStatusEnum.ACTIVE, STARTED, null);
        return new ProcessControlSnapshotDTO(process,
                List.of(new ProcessActiveWaitDTO("reviewTask", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                Set.of(ProcessControlActionEnum.CANCEL), ProcessControlBlockReasonEnum.NONE, HASH, OBSERVED);
    }

    private static CancelProcessResult result(String commandId, Instant finishedAt, Instant decidedAt) {
        return new CancelProcessResult(INSTANCE, commandId, true, ProcessInstanceStatusEnum.CANCELLED,
                RESULT_HASH, finishedAt, decidedAt, true);
    }

    private record Error(RuntimeException failure, int status, String code) { }
}
