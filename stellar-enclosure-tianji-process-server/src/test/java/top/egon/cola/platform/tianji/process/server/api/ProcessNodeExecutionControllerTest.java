package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionConverter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore.NodePageKeyVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess.AutomationInstanceAccessVO;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessNodeExecutionControllerTest {
    private static final String INSTANCE = "/api/tianji/v2/process-instances/instance-1";
    private static final String NODES = INSTANCE + "/node-executions";
    private static final String HASH = "a".repeat(64);
    private static final Instant OBSERVED = Instant.parse("2026-11-14T08:35:00.123456Z");
    private static final Instant LATEST = OBSERVED.minusSeconds(5);
    private static final ProcessActor OBSERVER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "observer", "https://idp.test/tenant");
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner", "https://idp.test/tenant");
    private static final ProcessActor FOREIGN = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "observer", "https://idp.test/tenant");
    private static final AutomationObservationPermitVO OBSERVE_ONLY = new AutomationObservationPermitVO(false, true, false, false, false);
    private static final AutomationObservationPermitVO OWNER_READ_ONLY = new AutomationObservationPermitVO(true, false, false, false, false);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessInstanceBindingStore bindings = mock(ProcessInstanceBindingStore.class);
    private final ProcessNodeExecutionStore nodes = mock(ProcessNodeExecutionStore.class);
    private final ProcessExecutionService instances = mock(ProcessExecutionService.class);
    private MockMvc mvc;
    private ProcessNodeExecutionController controller;

    @BeforeEach
    void configure() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        controller = new ProcessNodeExecutionController(access, instances, bindings, nodes,
                Mappers.getMapper(ProcessNodeExecutionConverter.class), new TransactionTemplate(manager));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProcessApiErrors()).build();
    }

    @Test
    void springRegistersTheGeneratedConverterByItsDeclaredNameAndDefaultProjectionIsRedacted() {
        var generated = Mappers.getMapper(ProcessNodeExecutionConverter.class).getClass();
        try (var context = new AnnotationConfigApplicationContext(generated)) {
            var mapper = context.getBean("processNodeExecutionConverter", ProcessNodeExecutionConverter.class);
            assertEquals(generated, mapper.getClass());
            var component = generated.getAnnotation(Component.class);
            assertNotNull(component);
            assertEquals("processNodeExecutionConverter", component.value());
            var projected = mapper.toTarget(row("occ-2", 2, "run-2", LATEST));
            assertNull(projected.getOutput());
            assertNull(projected.getAgentRunId());
            assertEquals(List.of(), projected.getEventReceiptIds());
            assertEquals("occ-2", projected.getOccurrenceId());
        }
    }

    @Test
    void readsAnExactNodePageWithoutUnauthorizedBusinessOrDiagnosticData() throws Exception {
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OBSERVER, OBSERVE_ONLY));
        when(instances.observeAutomation(OBSERVER, "instance-1", OBSERVE_ONLY)).thenReturn(pinned());
        when(instances.observeAutomation(OWNER, "instance-1", OWNER_READ_ONLY)).thenReturn(pinned());
        when(bindings.find(20, "instance-1")).thenReturn(Optional.of(binding()));
        when(nodes.now()).thenReturn(OBSERVED, OBSERVED.plusSeconds(1));
        var newest = row("occ-2", 2, "run-2", LATEST);
        var second = row("occ-2", 1, "run-1", LATEST);
        var oldest = row("occ-1", 1, "run-0", LATEST.minusSeconds(1));
        when(nodes.list(20, "instance-1", "extract", OBSERVED, null, 3)).thenReturn(List.of(newest, second, oldest));
        var after = new NodePageKeyVO(LATEST, "occ-2", 1);
        when(nodes.list(20, "instance-1", "extract", OBSERVED, after, 3)).thenReturn(List.of(oldest));
        var response = mvc.perform(get(NODES).param("nodeId", "extract").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.releaseVersion").value("fixed-1"))
                .andExpect(jsonPath("$.items[0].occurrenceId").value("occ-2"))
                .andExpect(jsonPath("$.items[0].attempt").value(2))
                .andExpect(jsonPath("$.items[1].occurrenceId").value("occ-2"))
                .andExpect(jsonPath("$.items[1].attempt").value(1))
                .andExpect(jsonPath("$.items[0].output").value(nullValue()))
                .andExpect(jsonPath("$.items[0].agentRunId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].nativeJobId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString()).andReturn().getResponse();
        String cursor = JsonMapper.builder().build().readTree(response.getContentAsByteArray()).path("nextCursor").asString();
        mvc.perform(get(NODES).param("nodeId", "extract").param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].occurrenceId").value("occ-1"))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        verify(nodes).list(20, "instance-1", "extract", OBSERVED, after, 3);
        mvc.perform(get(NODES).param("nodeId", "other").param("cursor", cursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OWNER,
                new AutomationObservationPermitVO(true, false, false, true, false, true)));
        mvc.perform(get(NODES).param("nodeId", "extract").param("cursor", cursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        when(nodes.list(20, "instance-1", "extract", OBSERVED.plusSeconds(1), null, 3))
                .thenReturn(List.of(newest, second, oldest));
        mvc.perform(get(NODES).param("nodeId", "extract").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].agentRunId").value("run-2"))
                .andExpect(jsonPath("$.items[0].output.approved").value(true));
    }

    @Test
    void queryRejectsUnknownDuplicateOrUnboundedValuesAndBody() throws Exception {
        for (var request : List.of(get(NODES).param("cursor", "bad=="), get(NODES).param("cursor", "x".repeat(2049)),
                get(NODES).param("nodeId", "1wrong"), get(NODES).param("limit", "0"), get(NODES).param("limit", "101"),
                get(NODES).param("limit", "01"), get(NODES).param("limit", "2", "2"), get(NODES).param("role", "observer"),
                get(NODES).contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        verifyNoInteractions(access, instances, bindings, nodes);
    }

    @Test
    void proxiedPathValidationUsesTheClosedBadRequestEnvelope() throws Exception {
        var proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor());
        var validated = MockMvcBuilders.standaloneSetup(proxy.getProxy()).setControllerAdvice(new ProcessApiErrors()).build();
        validated.perform(get("/api/tianji/v2/process-instances/{instanceId}/node-executions", "i".repeat(256)))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        verifyNoInteractions(access, instances, bindings, nodes);
    }

    @Test
    void hidesForeignInstanceAndDoesNotMountRecoveryMutation() throws Exception {
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(FOREIGN, OBSERVE_ONLY));
        when(instances.observeAutomation(FOREIGN, "foreign-instance", OBSERVE_ONLY)).thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(NODES.replace("instance-1", "foreign-instance")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        verifyNoInteractions(bindings, nodes);
        mvc.perform(post(INSTANCE + "/node-retries").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthorizedAndConsistentImmutableInstanceBeforeNodeSql() throws Exception {
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OBSERVER, OWNER_READ_ONLY));
        when(instances.observeAutomation(OBSERVER, "instance-1", OWNER_READ_ONLY))
                .thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(NODES)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        verifyNoInteractions(bindings, nodes);

        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OBSERVER, OBSERVE_ONLY));
        when(instances.observeAutomation(OBSERVER, "instance-1", OBSERVE_ONLY))
                .thenThrow(new IllegalStateException("native evidence mismatch"))
                .thenReturn(pinned());
        mvc.perform(get(NODES)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verifyNoInteractions(bindings, nodes);
        when(bindings.find(20, "instance-1")).thenReturn(Optional.of(binding().setReleaseKey("mismatched")));
        mvc.perform(get(NODES)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        verifyNoInteractions(nodes);
        when(bindings.find(20, "instance-1")).thenReturn(Optional.of(binding()));
        when(nodes.now()).thenReturn(OBSERVED);
        when(nodes.list(20, "instance-1", null, OBSERVED, null, 21))
                .thenReturn(List.of(row("occ-2", 2, "run-2", LATEST).setConfigurationSha256("b".repeat(64))));
        mvc.perform(get(NODES)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
    }

    private static AutomationInstanceBindingBO binding() {
        return new AutomationInstanceBindingBO().setTenantId(20).setInstanceId("instance-1").setOwner(OWNER)
                .setReleaseKey("fixed-1").setDefinitionKey("review").setActivationRevision(4).setConfigurationSha256(HASH);
    }

    private static AutomationInstanceDTO pinned() {
        return new AutomationInstanceDTO().setInstanceId("instance-1").setReleaseVersion("fixed-1")
                .setDefinitionKey("review").setActivationRevision(4).setConfigurationSha256(HASH)
                .setProfile(AutomationInstanceDTO.ProfileEnum.AUTOMATION_V2);
    }

    private static ProcessNodeExecutionBO row(String occurrence, int attempt, String runId, Instant started) {
        return new ProcessNodeExecutionBO().setTenantId(20).setInstanceId("instance-1").setNodeId("extract")
                .setOccurrenceId(occurrence).setAttempt(attempt).setNodeExecutionRef("exec-" + occurrence + "-" + attempt)
                .setKind(KindEnum.AGENT).setState(StateEnum.COMPLETED).setRevision(1).setSideEffectOutcome(SideEffectOutcomeEnum.NONE)
                .setStartedAt(started).setFinishedAt(started.plusSeconds(1)).setNodeDeadlineAt(started.plusSeconds(60))
                .setAttemptDeadlineAt(started.plusSeconds(20)).setInputSha256(HASH).setOutputSchemaSha256(HASH).setConfigurationSha256(HASH)
                .setOutput(Map.of("approved", true)).setAgentRunId(runId).setNativeJobId("private-native");
    }

}
