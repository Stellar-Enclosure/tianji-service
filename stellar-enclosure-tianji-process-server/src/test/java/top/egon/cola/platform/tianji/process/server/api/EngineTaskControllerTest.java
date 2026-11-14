package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskCompletionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EngineTaskControllerTest {
    private static final String TASK = "/api/tianji/v2/process-tasks/task-101";
    private static final String COMPLETIONS = TASK + "/completions";
    private static final String READ_RESOURCE = "tianji.api.process.task.get.v2";
    private static final String COMPLETE_RESOURCE = "tianji.api.process.task.complete.v2";
    private static final Instant NOW = Instant.parse("2026-11-14T08:35:00.123456Z");
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://idp.test/tenant");
    private static final String COMMAND = """
            {"commandId":"complete-101","expectedVersion":"version-1","result":{"resultRef":"business-result-101"}}
            """.strip();
    private MockMvc mvc;
    private final TianjiHumanTaskAccess access = mock(TianjiHumanTaskAccess.class);
    private final FlowableHumanTaskService tasks = mock(FlowableHumanTaskService.class);

    @BeforeEach
    void configure() {
        mvc = MockMvcBuilders.standaloneSetup(new EngineTaskController(access, tasks))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @Test
    void candidateReadsActionAndSameCommandReturnsOriginalCompletion() throws Exception {
        var reader = context(READ_RESOURCE);
        var writer = context(COMPLETE_RESOURCE);
        var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false,
                "required", List.of("resultRef"), "properties", Map.of("resultRef", Map.of("type", "string"))));
        when(access.require(READ_RESOURCE)).thenReturn(reader);
        when(access.require(COMPLETE_RESOURCE)).thenReturn(writer);
        when(tasks.observeAction(reader, "task-101")).thenReturn(new EngineHumanTaskDTO("task-101", "instance-1", "review",
                EngineHumanTaskDTO.StateEnum.CLAIMED, true, "version-1", schema, List.of("COMPLETE"), null));
        var command = new CompleteEngineHumanTaskCommand("complete-101", "version-1", Map.of("resultRef", "business-result-101"));
        when(tasks.completeAction(writer, "task-101", command)).thenReturn(new EngineHumanTaskCompletionDTO(
                "task-101", "complete-101", EngineHumanTaskCompletionDTO.StateEnum.COMPLETED, NOW));
        mvc.perform(get(TASK)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.taskId").value("task-101"))
                .andExpect(jsonPath("$.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.resultSchema.properties.resultRef.type").value("string"))
                .andExpect(jsonPath("$.form").doesNotExist()).andExpect(jsonPath("$.approvalRef").doesNotExist());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON).content(COMMAND))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.state").value("COMPLETED"))
                    .andExpect(jsonPath("$.commandId").value("complete-101"))
                    .andExpect(jsonPath("$.completedAt").value("2026-11-14T08:35:00.123456Z"))
                    .andExpect(jsonPath("$.result").doesNotExist());
        }
        verify(tasks).observeAction(reader, "task-101");
        verify(tasks, times(2)).completeAction(writer, "task-101", command);
    }

    @Test
    void rejectsUnknownOrNonScalarCompletionBeforeAction() throws Exception {
        for (String invalid : List.of(COMMAND + "{}", COMMAND.replace(
                "\"resultRef\":\"business-result-101\"", "\"resultRef\":{\"nested\":true}"),
                COMMAND.replace("\"resultRef\":\"business-result-101\"", "\"resultRef\":1e2"),
                COMMAND.replace("\"resultRef\":\"business-result-101\"", "\"resultRef\":null"),
                COMMAND.replace("\"resultRef\":\"business-result-101\"", "\"resultRef\":1000000000000000"),
                COMMAND.replace("\"resultRef\":\"business-result-101\"",
                        "\"resultRef\":\"business-result-101\",\"resultRef\":\"other\""),
                COMMAND.replace("\"expectedVersion\"", "\"actor\":{\"tenantId\":20},\"expectedVersion\""))) {
            mvc.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        mvc.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON).content(COMMAND + " ".repeat(68 * 1024)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        mvc.perform(get(TASK).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        mvc.perform(post(COMPLETIONS).contentType(MediaType.TEXT_PLAIN).content(COMMAND))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(access, tasks);
    }

    @Test
    void beanAndProxiedMethodValidationUseTheClosedBadRequestEnvelope() throws Exception {
        var proxy = new ProxyFactory(new EngineTaskController(access, tasks));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor());
        // Omit wire advice so an invalid command reaches MVC bean validation.
        var validated = MockMvcBuilders.standaloneSetup(proxy.getProxy()).setControllerAdvice(new ProcessApiErrors()).build();
        validated.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON).content(COMMAND.replace("version-1", " ")))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        validated.perform(get("/api/tianji/v2/process-tasks/{taskId}", "t".repeat(256)))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        verifyNoInteractions(access, tasks);
    }

    @Test
    void hidesNonCandidateAndApprovalAndReportsStaleOrRevokedCompletion() throws Exception {
        var reader = context(READ_RESOURCE);
        var writer = context(COMPLETE_RESOURCE);
        when(access.require(READ_RESOURCE)).thenReturn(reader);
        when(access.require(COMPLETE_RESOURCE)).thenReturn(writer);
        when(tasks.observeAction(eq(reader), any())).thenThrow(new ProcessResourceNotFoundException());
        when(tasks.completeAction(writer, "task-101", new CompleteEngineHumanTaskCommand(
                "complete-101", "stale-version", Map.of("resultRef", "business-result-101"))))
                .thenThrow(new ProcessVersionConflictException());
        when(tasks.completeAction(writer, "task-101", new CompleteEngineHumanTaskCommand(
                "revoked-command", "version-1", Map.of("resultRef", "business-result-101"))))
                .thenThrow(new AccessDeniedException("Private task role"));
        mvc.perform(get(TASK.replace("task-101", "approval-101"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        mvc.perform(get(TASK.replace("task-101", "other-tenant"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        mvc.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(COMMAND.replace("version-1", "stale-version")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_PROCESS_VERSION_CONFLICT"));
        mvc.perform(post(COMPLETIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(COMMAND.replace("complete-101", "revoked-command")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
    }

    private static HumanTaskContext context(String resource) {
        return new HumanTaskContext(ACTOR, resource, Set.of("42"), NOW.plusSeconds(15), 1, 2);
    }
}
