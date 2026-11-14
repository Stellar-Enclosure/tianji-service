package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryResultDTO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionConverter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore.NodePageKeyVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeRetryService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess.AutomationInstanceAccessVO;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED;

class ProcessNodeExecutionControllerTest {
    private static final String INSTANCE = "/api/tianji/v2/process-instances/instance-1";
    private static final String NODES = INSTANCE + "/node-executions";
    private static final String RETRIES = INSTANCE + "/node-retries";
    private static final String RETRY_RESOURCE = "tianji.api.process.node.retry.v2";
    private static final String HASH = "a".repeat(64);
    private static final String RETRY_REASON = "Confirm the outcome before retrying";
    private static final String RETRY_BODY = """
            {"commandId":"retry-1","nodeExecutionRef":"nx-1","expectedVersion":"%s",
             "reason":"%s","businessReceiptRef":null}
            """.formatted(HASH, RETRY_REASON);
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
    private final ProcessNodeRetryService retries = mock(ProcessNodeRetryService.class);
    private final ProcessNodeExecutionController.NodeRetryAuthorization retryAuthorization =
            mock(ProcessNodeExecutionController.NodeRetryAuthorization.class);
    private TransactionTemplate transaction;
    private MockMvc mvc;
    private ProcessNodeExecutionController controller;

    @BeforeEach
    void configure() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        transaction = new TransactionTemplate(manager);
        controller = new ProcessNodeExecutionController(access, instances, bindings, nodes,
                Mappers.getMapper(ProcessNodeExecutionConverter.class), transaction);
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
    void hidesForeignInstance() throws Exception {
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(FOREIGN, OBSERVE_ONLY));
        when(instances.observeAutomation(FOREIGN, "foreign-instance", OBSERVE_ONLY)).thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(NODES.replace("instance-1", "foreign-instance")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        verifyNoInteractions(bindings, nodes);
    }

    @Test
    void blocksRetryWhenRecoveryProofIsUnavailable() throws Exception {
        when(access.require(RETRY_RESOURCE)).thenReturn(OWNER);
        when(retries.retry(eq(OWNER), eq("instance-1"), eq(retryCommand(HASH, null)), any()))
                .thenThrow(new AutomationOperationException(TIANJI_NODE_RETRY_BLOCKED));
        retryMvc().perform(retry(RETRY_BODY)).andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_NODE_RETRY_BLOCKED"))
                .andExpect(jsonPath("$.correlationId").value(nullValue()));
        verify(retries).retry(eq(OWNER), eq("instance-1"), eq(retryCommand(HASH, null)), any());
        verifyNoInteractions(instances, bindings, nodes);
    }

    @Test
    void exactRetryReturnsOriginalScheduledReferenceWithOmittedOrNullReceiptHint() throws Exception {
        when(access.require(RETRY_RESOURCE)).thenReturn(OWNER);
        Instant validUntil = OBSERVED.plusSeconds(20);
        when(retryAuthorization.refresh(OWNER)).thenReturn(validUntil);
        var expected = new NodeRetryResultDTO("retry-1", "instance-1", "occ-1", 2, "nx-2", "SCHEDULED");
        when(retries.retry(eq(OWNER), eq("instance-1"), eq(retryCommand(HASH, null)), any())).thenAnswer(call -> {
            Supplier<Instant> refresh = call.getArgument(3);
            assertEquals(validUntil, refresh.get());
            return expected;
        });
        var retryMvc = retryMvc();
        var original = retryMvc.perform(retry(RETRY_BODY)).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.commandId").value("retry-1"))
                .andExpect(jsonPath("$.instanceId").value("instance-1"))
                .andExpect(jsonPath("$.occurrenceId").value("occ-1"))
                .andExpect(jsonPath("$.attempt").value(2))
                .andExpect(jsonPath("$.nodeExecutionRef").value("nx-2"))
                .andExpect(jsonPath("$.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.businessReceiptRef").doesNotExist())
                .andExpect(jsonPath("$.proof").doesNotExist()).andReturn().getResponse();
        var replay = retryMvc.perform(retry(RETRY_BODY.replace(",\"businessReceiptRef\":null", "")))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        assertEquals(original.getContentAsString(), replay.getContentAsString());
        verify(retries, times(2)).retry(eq(OWNER), eq("instance-1"), eq(retryCommand(HASH, null)), any());
        verify(retryAuthorization, times(2)).refresh(OWNER);
        verifyNoInteractions(instances, bindings, nodes);
    }

    @Test
    void offersRetryOnlyForActiveOwnerWithCurrentScopeAndEligibleNode() throws Exception {
        var node = row("occ-1", 1, null, LATEST).setKind(KindEnum.SERVICE).setState(StateEnum.WAIT_RECOVERY)
                .setSideEffectOutcome(SideEffectOutcomeEnum.UNKNOWN);
        var binding = binding();
        var active = pinned().setState(AutomationInstanceDTO.StateEnum.ACTIVE);
        when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OWNER, OWNER_READ_ONLY));
        when(instances.observeAutomation(OWNER, "instance-1", OWNER_READ_ONLY)).thenReturn(active);
        when(instances.observeAutomation(OBSERVER, "instance-1", OBSERVE_ONLY)).thenReturn(active);
        when(bindings.find(20, "instance-1")).thenReturn(Optional.of(binding));
        when(nodes.now()).thenReturn(OBSERVED);
        when(nodes.list(20, "instance-1", null, OBSERVED, null, 21)).thenReturn(List.of(node));
        when(retryAuthorization.refresh(OWNER)).thenReturn(OBSERVED.plusSeconds(1), OBSERVED.plusSeconds(1), OBSERVED);
        when(retryAuthorization.refresh(OBSERVER)).thenReturn(OBSERVED.plusSeconds(1));
        when(retries.canOfferRetry(OWNER, binding, node, OBSERVED)).thenReturn(true, false);
        var retryMvc = retryMvc();
        var scope = List.of(new SimpleGrantedAuthority("SCOPE_tianji:process:node:retry"));

        try {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(OWNER, null, scope));
            retryMvc.perform(get(NODES)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].availableActions.length()").value(1))
                    .andExpect(jsonPath("$.items[0].availableActions[0]").value("RETRY"));
            // A current grant alone does not offer retry when the node is ineligible.
            retryMvc.perform(get(NODES)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].availableActions").isEmpty());
            // The authorization deadline is exclusive at the database observation time.
            retryMvc.perform(get(NODES)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].availableActions").isEmpty());

            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(OWNER, null, List.of()));
            retryMvc.perform(get(NODES)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].availableActions").isEmpty());

            when(access.observeAutomationNodes()).thenReturn(new AutomationInstanceAccessVO(OBSERVER, OBSERVE_ONLY));
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(OBSERVER, null, scope));
            retryMvc.perform(get(NODES)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].availableActions").isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(retryAuthorization, times(3)).refresh(OWNER);
        verify(retryAuthorization).refresh(OBSERVER);
        verify(retries, times(2)).canOfferRetry(OWNER, binding, node, OBSERVED);
        verifyNoMoreInteractions(retries);
    }

    @Test
    void retryUsesCurrentResourceAuthorityAndMapsExactServiceConflicts() throws Exception {
        var retryMvc = retryMvc();
        when(access.require(RETRY_RESOURCE)).thenThrow(new AccessDeniedException("revoked")).thenReturn(OWNER);
        retryMvc.perform(retry(RETRY_BODY)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(retries, retryAuthorization, instances, bindings, nodes);

        var version = "b".repeat(64);
        when(retries.retry(eq(OWNER), eq("instance-1"), eq(retryCommand(version, null)), any()))
                .thenThrow(new ProcessVersionConflictException());
        when(retries.retry(eq(OWNER), eq("instance-1"), eq(retryCommand(HASH, "business-1")), any()))
                .thenThrow(new ProcessConflictException());
        when(retries.retry(eq(OWNER), eq("foreign-instance"), eq(retryCommand(HASH, null)), any()))
                .thenThrow(new ProcessResourceNotFoundException());
        retryMvc.perform(retry(RETRY_BODY.replace(HASH, version))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_PROCESS_VERSION_CONFLICT"));
        retryMvc.perform(retry(RETRY_BODY.replace("null", "\"business-1\""))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIANJI_IDEMPOTENCY_CONFLICT"));
        retryMvc.perform(post(RETRIES.replace("instance-1", "foreign-instance"))
                        .contentType(MediaType.APPLICATION_JSON).content(RETRY_BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
    }

    @Test
    void retryRejectsOpenWireAndUnboundedBodiesBeforeScheduling() throws Exception {
        when(access.require(RETRY_RESOURCE)).thenReturn(OWNER);
        var retryMvc = retryMvc();
        for (String invalid : List.of("{}", RETRY_BODY + "{}", RETRY_BODY.replace("\"retry-1\"", "1"),
                RETRY_BODY.replace("null", "{}"), RETRY_BODY.replace("null", "\"" + "x".repeat(256) + "\""),
                RETRY_BODY.replace(RETRY_REASON, " "), RETRY_BODY.replace(RETRY_REASON, "x".repeat(513)),
                RETRY_BODY.replace("\"reason\":", "\"generation\":1,\"reason\":"),
                RETRY_BODY.replace("\"reason\":", "\"proof\":{},\"reason\":"),
                RETRY_BODY.replace("\"reason\":", "\"targetUrl\":\"https://external.test\",\"reason\":"),
                RETRY_BODY.replace("\"reason\":", "\"reason\":\"duplicate\",\"reason\":"))) {
            retryMvc.perform(retry(invalid)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        retryMvc.perform(retry(RETRY_BODY).param("tenantId", "21")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        retryMvc.perform(retry(RETRY_BODY).contentType(MediaType.TEXT_PLAIN)).andExpect(status().isUnsupportedMediaType());
        retryMvc.perform(retry(RETRY_BODY + " ".repeat(32 * 1024))).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        byte[] invalidUtf8 = RETRY_BODY.getBytes(StandardCharsets.UTF_8);
        invalidUtf8[invalidUtf8.length - 1] = (byte) 0xFF;
        retryMvc.perform(retry(RETRY_BODY).content(invalidUtf8)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        verifyNoInteractions(retries, retryAuthorization, instances, bindings, nodes);
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

    private static MockHttpServletRequestBuilder retry(String command) {
        return post(RETRIES).contentType(MediaType.APPLICATION_JSON).content(command);
    }

    private MockMvc retryMvc() {
        var beans = new StaticListableBeanFactory(Map.of("processNodeRetryService", retries,
                "processNodeRetryAuthorization", retryAuthorization));
        var retryController = new ProcessNodeExecutionController(access, instances, bindings, nodes,
                Mappers.getMapper(ProcessNodeExecutionConverter.class), transaction,
                beans.getBeanProvider(ProcessNodeRetryService.class),
                beans.getBeanProvider(ProcessNodeExecutionController.NodeRetryAuthorization.class));
        return MockMvcBuilders.standaloneSetup(retryController)
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    private static NodeRetryCommand retryCommand(String version, String receiptHint) {
        return new NodeRetryCommand("retry-1", "nx-1", version, RETRY_REASON, receiptHint);
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
