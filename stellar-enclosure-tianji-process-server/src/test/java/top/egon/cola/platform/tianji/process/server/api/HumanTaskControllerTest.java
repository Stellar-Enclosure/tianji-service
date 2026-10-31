package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskClient;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Private HTTP shapes and dispatch markers, without a server, IAM connection or native task engine. */
class HumanTaskControllerTest {
    private static final String HASH = "a".repeat(64), AFTER = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();
    private final FlowableHumanTaskService tasks = mock(FlowableHumanTaskService.class);
    private final TianjiHumanTaskAccess access = mock(TianjiHumanTaskAccess.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new HumanTaskController(tasks, access))
            .setControllerAdvice(new HumanTaskRequestBodyAdvice(), new ProcessApiErrors()).build();

    @Test void each_private_operation_obtains_its_exact_current_context_and_returns_noncacheable_reference_data() throws Exception {
        when(access.require(anyString())).thenAnswer(call -> context(call.getArgument(0)));
        when(tasks.get(any(), any())).thenReturn(task()); when(tasks.query(any(), any())).thenReturn(new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, List.of(task()), null));
        when(tasks.claim(any(), any())).thenReturn(receipt(HumanTaskActionEnum.CLAIM)); when(tasks.unclaim(any(), any())).thenReturn(receipt(HumanTaskActionEnum.UNCLAIM));
        when(tasks.transfer(any(), any())).thenReturn(receipt(HumanTaskActionEnum.TRANSFER)); when(tasks.complete(any(), any())).thenReturn(receipt(HumanTaskActionEnum.COMPLETE));
        var get = new GetHumanTaskQuery("task"); var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, null, null, null, null, 10);
        var claim = new ClaimHumanTaskCommand("cmd", "task", HASH); var unclaim = new UnclaimHumanTaskCommand("cmd", "task", HASH);
        var transfer = new TransferHumanTaskCommand("cmd", "task", HASH, "finance"); var complete = new CompleteHumanTaskCommand("cmd", "task", HASH, Map.of("approved", false));
        for (var item : Map.of("get", get, "query", query, "admin/query", query, "claim", claim, "unclaim", unclaim, "transfer", transfer, "complete", complete).entrySet()) {
            mvc.perform(post(HumanTaskClient.PATH + '/' + item.getKey()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(item.getValue())))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        verify(tasks).get(context(TianjiProcessPermissions.TASK_GET_API), get); verify(tasks).query(context(TianjiProcessPermissions.TASK_QUERY_API), query);
        verify(tasks).query(context(TianjiProcessPermissions.TASK_ADMIN_QUERY_API), query); verify(tasks).claim(context(TianjiProcessPermissions.TASK_CLAIM_API), claim);
        verify(tasks).unclaim(context(TianjiProcessPermissions.TASK_UNCLAIM_API), unclaim); verify(tasks).transfer(context(TianjiProcessPermissions.TASK_TRANSFER_API), transfer); verify(tasks).complete(context(TianjiProcessPermissions.TASK_COMPLETE_API), complete);
    }

    @Test void malformed_or_identity_bearing_task_bodies_fail_before_authorization_and_native_dispatch() throws Exception {
        String valid = json.writeValueAsString(new ClaimHumanTaskCommand("cmd", "task", HASH));
        for (var invalid : List.of(valid.replace("\"taskId\":\"task\"", "\"taskId\":42"), valid.substring(0, valid.length() - 1) + ",\"tenantId\":20}",
                valid.substring(0, valid.length() - 1) + ",\"candidateRoles\":[\"50\"]}", valid.substring(0, valid.length() - 1) + ",\"taskId\":\"task\"}", valid + " {}", "[]")) {
            mvc.perform(post(HumanTaskClient.PATH + "/claim").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(HumanTaskClient.PATH + "/query").contentType(MediaType.APPLICATION_JSON).content("{\"scopeType\":0,\"selection\":\"ALL\",\"limit\":10}" )).andExpect(status().isBadRequest());
        mvc.perform(post(HumanTaskClient.PATH + "/complete").contentType(MediaType.APPLICATION_JSON).content("{\"commandId\":\"cmd\",\"taskId\":\"task\",\"expectedVersion\":\"" + HASH + "\",\"form\":{\"approved\":{\"value\":true}}}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(access, tasks);
    }

    @Test void fixed_and_streamed_body_limits_are_enforced_before_private_content_is_deserialized() throws Exception {
        byte[] oversized = new byte[HumanTaskRequestBodyAdvice.MAX_BYTES + 1];
        mvc.perform(post(HumanTaskClient.PATH + "/complete").contentType(MediaType.APPLICATION_JSON).content(oversized)).andExpect(status().isPayloadTooLarge());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(oversized); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class, () -> new HumanTaskRequestBodyAdvice().beforeBodyRead(streamed, null, CompleteHumanTaskCommand.class, null)); verifyNoInteractions(access, tasks);
    }

    @Test void authorization_and_native_errors_return_safe_noncacheable_codes_without_private_form_text() throws Exception {
        var command = new CompleteHumanTaskCommand("cmd", "task", HASH, Map.of("approved", false, "comment", "private form"));
        when(access.require(TianjiProcessPermissions.TASK_COMPLETE_API)).thenThrow(new AccessDeniedException("private form"));
        mvc.perform(post(HumanTaskClient.PATH + "/complete").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED")); verifyNoInteractions(tasks);
        doReturn(context(TianjiProcessPermissions.TASK_COMPLETE_API)).when(access).require(TianjiProcessPermissions.TASK_COMPLETE_API); when(tasks.complete(any(), any())).thenThrow(new ProcessConflictException());
        mvc.perform(post(HumanTaskClient.PATH + "/complete").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"));
        doThrow(new IllegalStateException("private form")).when(tasks).complete(any(), any());
        String response = mvc.perform(post(HumanTaskClient.PATH + "/complete").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString(); assertFalse(response.contains("private form"));
    }

    @Test void administrator_query_requires_both_permissions_and_scopes_and_only_current_users() throws Exception {
        var method = HumanTaskController.class.getMethod("adminQuery", ListHumanTasksQuery.class); var resource = method.getAnnotation(PermissionResource.class);
        assertEquals(Set.of(HumanTaskClient.READ_SCOPE, HumanTaskClient.TRANSFER_SCOPE), Set.of(resource.oauthScopes()));
        assertEquals(Set.of(TianjiProcessPermissions.TASK_READ, TianjiProcessPermissions.TASK_TRANSFER), Set.of(method.getAnnotation(RequiresPermissions.class).codes()));
        for (var candidate : HumanTaskController.class.getDeclaredMethods()) {
            var declaration = candidate.getAnnotation(PermissionResource.class); if (declaration != null) assertArrayEquals(new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER}, declaration.allowedPrincipals());
        }
    }
    private static HumanTaskContext context(String resource) { return new HumanTaskContext(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20"), resource, Set.of("50"), NOW.plusSeconds(30), 2, 3); }
    private static HumanTaskDTO task() { return new HumanTaskDTO("task", HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "workflow", "review", "Review", HumanTaskKindEnum.APPROVAL, "approved", HASH, "finance", List.of("finance"), false, false, false, Map.of("approved", ProcessValueTypeEnum.BOOLEAN), NOW, null); }
    private static HumanTaskActionReceiptDTO receipt(HumanTaskActionEnum action) { return new HumanTaskActionReceiptDTO("cmd", "task", action, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "workflow", "review", HumanTaskKindEnum.APPROVAL, "finance", HASH, action == HumanTaskActionEnum.COMPLETE ? null : AFTER, action == HumanTaskActionEnum.COMPLETE ? "approval-" + HASH : null, action == HumanTaskActionEnum.COMPLETE ? false : null, NOW); }
}
