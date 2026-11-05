package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessStartReceiptControllerTest {
    private static final String PATH = "/api/tianji/v1/process-start-receipts";
    private static final String RESOURCE = "tianji.api.process.startReceipt.get";
    private static final String COMMAND = "intent:123";
    private static final String SHA = "a".repeat(64);
    private static final Instant RECORDED = Instant.parse("2026-11-05T10:20:30.123456Z");
    private static final ProcessActor OWNER = new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "service-1", "b".repeat(64));
    private static final GetProcessStartReceiptQuery QUERY = new GetProcessStartReceiptQuery(COMMAND, SHA);
    private final ProcessExecutionService processes = mock(ProcessExecutionService.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProcessStartReceiptController(processes, access))
            .setControllerAdvice(new ProcessApiErrors()).build();

    @Test
    void reads_only_the_applied_receipt_for_an_exact_owned_request() throws Exception {
        when(access.require(RESOURCE)).thenReturn(OWNER);
        when(processes.getStartReceipt(OWNER, QUERY)).thenReturn(new ProcessStartReceiptDTO(
                "instance-1", ProcessStartReceiptDTO.StatusEnum.APPLIED, RECORDED));
        mvc.perform(get(PATH).param("commandId", COMMAND).param("requestSha256", SHA))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"instanceId":"instance-1","status":"APPLIED","recordedAt":"2026-11-05T10:20:30.123456Z"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        var ordered = inOrder(access, processes);
        ordered.verify(access).require(RESOURCE);
        ordered.verify(processes).getStartReceipt(OWNER, QUERY);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void authorizes_before_validating_the_exact_query() throws Exception {
        when(access.require(RESOURCE)).thenThrow(new AccessDeniedException("private"));
        mvc.perform(get(PATH).param("bad", "private")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(processes);
        doReturn(OWNER).when(access).require(RESOURCE);
        for (var request : Set.of(get(PATH).param("commandId", COMMAND),
                get(PATH).param("commandId", COMMAND, COMMAND).param("requestSha256", SHA),
                get(PATH).param("commandId", COMMAND).param("requestSha256", SHA, SHA),
                get(PATH).param("commandId", COMMAND).param("requestSha256", SHA.toUpperCase(java.util.Locale.ROOT)),
                get(PATH).param("commandId", " " + COMMAND).param("requestSha256", SHA),
                get(PATH).param("commandId", COMMAND).param("requestSha256", SHA).param("owner", "private"))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(processes);
    }

    @Test
    void keeps_no_exact_match_and_source_unavailable_distinct_without_identifiers() throws Exception {
        when(access.require(RESOURCE)).thenReturn(OWNER);
        when(processes.getStartReceipt(OWNER, QUERY)).thenThrow(new ProcessResourceNotFoundException())
                .thenThrow(new IllegalStateException("private"));
        mvc.perform(get(PATH).param("commandId", COMMAND).param("requestSha256", SHA))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"code":"TIANJI_RESOURCE_NOT_FOUND","correlationId":null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
        mvc.perform(get(PATH).param("commandId", COMMAND).param("requestSha256", SHA))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"TIANJI_DEPENDENCY_UNAVAILABLE","correlationId":null}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void declares_only_existing_read_authority_and_needs_both_flags() throws Exception {
        var method = ProcessStartReceiptController.class.getMethod("receipt", MultiValueMap.class);
        var permission = method.getAnnotation(PermissionResource.class);
        assertEquals(RESOURCE, permission.key());
        assertArrayEquals(new String[]{TianjiProcessPermissions.READ_SCOPE}, permission.oauthScopes());
        assertEquals(Set.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                Set.of(permission.allowedPrincipals()));
        assertArrayEquals(new String[]{TianjiProcessPermissions.READ}, method.getAnnotation(RequiresPermissions.class).codes());

        var runner = new WebApplicationContextRunner().withUserConfiguration(ProcessStartReceiptController.class)
                .withBean(ProcessExecutionService.class, () -> processes).withBean(TianjiProcessAccess.class, () -> access);
        String base = "stellar.tianji.process.api.";
        runner.run(context -> assertEquals(0, context.getBeansOfType(ProcessStartReceiptController.class).size()));
        runner.withPropertyValues(base + "enabled=true")
                .run(context -> assertEquals(0, context.getBeansOfType(ProcessStartReceiptController.class).size()));
        runner.withPropertyValues(base + "start-receipt-read-enabled=true")
                .run(context -> assertEquals(0, context.getBeansOfType(ProcessStartReceiptController.class).size()));
        runner.withPropertyValues(base + "enabled=true", base + "start-receipt-read-enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(ProcessStartReceiptController.class).size()));
    }
}
