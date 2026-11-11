package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionQuery;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ProcessLink;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskStorageVerifier;

import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.repository.CmmnDeploymentBuilder;
import org.flowable.cmmn.api.repository.CmmnDeployment;
import org.flowable.cmmn.api.repository.CaseDefinitionQuery;
import org.flowable.cmmn.api.repository.CaseDefinition;
import org.flowable.cmmn.engine.impl.parser.CmmnParserImpl;
import org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory;
import org.flowable.cmmn.engine.impl.delegate.CmmnClassDelegateFactory;
import org.flowable.common.engine.impl.el.ExpressionManager;
import top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.MockedConstruction;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.security.MessageDigest;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class ProcessReleaseServiceTest {
    @Test
    void requires_a_well_formed_trusted_actor() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessActor(0, ProcessActor.SubjectType.USER, "alice"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessActor(20, ProcessActor.SubjectType.USER, " alice"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessActor(20, ProcessActor.SubjectType.USER, "alice" + (char) 0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessActor(20, ProcessActor.SubjectType.USER, "alice" + (char) 0xd800));
        var first = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://idp-a.example/20");
        var second = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://idp-b.example/20");
        assertNotEquals(first, second);
        assertNotEquals(first, new ProcessActor(20, ProcessActor.SubjectType.USER, "alice"));
        assertThrows(IllegalArgumentException.class, () -> ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "http://idp.example/20"));
    }

    @Test
    void rejects_different_datasources_or_nonisolated_engine_beans() {
        DataSource source = mock(DataSource.class);
        var process = process(source);
        var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        when(decision.getBeans()).thenReturn(Map.of("unsafe", new Object()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator()));
        when(decision.getBeans()).thenReturn(Map.of());
        when(decision.getDataSource()).thenReturn(mock(DataSource.class));
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator()));
    }

    @Test
    void validates_the_complete_bundle_before_using_the_database() throws Exception {
        DataSource source = mock(DataSource.class);
        var process = process(source);
        var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        var service = new ProcessReleaseService(process, decision, new ProcessModelValidator());
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "alice");
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/fixtures/baseline.dmn")) {
            assertNotNull(input);
            xml = input.readAllBytes();
        }
        var model = new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", xml, Map.of());
        byte[] expected = xml.clone();
        xml[0] = 'x';
        byte[] returned = model.xml();
        returned[0] = 'y';
        assertArrayEquals(expected, model.xml());
        assertThrows(UnsupportedOperationException.class, () -> model.inputs().put("x", ValueType.STRING));
        assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "../release", List.of(model)));
        assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(model, model)));
        assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(
                new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", expected, Map.of("amount", ValueType.STRING)))));
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService.ModelResource(Type.BPMN, "baseline",
                new byte[] {1}, Map.of("execution", ValueType.STRING)));
        assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(
                new ProcessReleaseService.ModelResource(Type.DMN, "wrong-key", expected, Map.of()))));
        try (var worker = getClass().getResourceAsStream("/fixtures/worker-mapping.bpmn20.xml")) {
            assertNotNull(worker);
            var workerModel = new ProcessReleaseService.ModelResource(Type.BPMN, "workerMapping", worker.readAllBytes(), Map.of("amount", ValueType.INTEGER));
            assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(workerModel)));
        }
        try (var wait = getClass().getResourceAsStream("/fixtures/agent-dispatch-wait.bpmn20.xml")) {
            assertNotNull(wait);
            var modelWithWait = new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch", wait.readAllBytes(), Map.of("amount", ValueType.INTEGER));
            assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(modelWithWait)));
            assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator(), false, null, true));
        }
        try (var human = getClass().getResourceAsStream("/fixtures/governed-human-task.bpmn20.xml")) {
            assertNotNull(human);
            var governed = new ProcessReleaseService.ModelResource(Type.BPMN, "humanReview", human.readAllBytes(), Map.of());
            assertThrows(IllegalArgumentException.class, () -> service.publish(actor, "valid", List.of(governed)));
        }
        verifyNoInteractions(source, process.getRepositoryService(), decision.getDmnRepositoryService());
    }

    @Test
    void decision_tasks_require_opt_in_pinned_behavior_and_a_compatible_decision_in_the_same_release() throws Exception {
        DataSource source = mock(DataSource.class);
        var process = process(source);
        var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator(), true));
        when(process.getActivityBehaviorFactory()).thenReturn(new PinnedDecisionActivityBehaviorFactory());
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator(), true));
        when(decision.isHistoryEnabled()).thenReturn(true);
        when(decision.getDmnHistoryService()).thenReturn(mock(DmnHistoryService.class));
        var disabled = new ProcessReleaseService(process, decision, new ProcessModelValidator());
        var enabled = new ProcessReleaseService(process, decision, new ProcessModelValidator(), true);
        var actor = new ProcessActor(20, ProcessActor.SubjectType.USER, "alice");
        byte[] bpmn;
        byte[] dmn;
        try (var bpmnInput = getClass().getResourceAsStream("/fixtures/approval-routing.bpmn20.xml");
             var dmnInput = getClass().getResourceAsStream("/fixtures/baseline.dmn")) {
            assertNotNull(bpmnInput);
            assertNotNull(dmnInput);
            bpmn = bpmnInput.readAllBytes();
            dmn = dmnInput.readAllBytes();
        }
        var routing = new ProcessReleaseService.ModelResource(Type.BPMN, "approvalRouting", bpmn, Map.of("amount", ValueType.INTEGER));
        var rules = new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", dmn, Map.of());
        assertThrows(IllegalArgumentException.class, () -> disabled.publish(actor, "release", List.of(routing, rules)));
        assertThrows(IllegalArgumentException.class, () -> enabled.publish(actor, "release", List.of(routing)));
        var wrongInputs = new ProcessReleaseService.ModelResource(Type.BPMN, "approvalRouting", bpmn, Map.of("amount", ValueType.STRING));
        assertThrows(IllegalArgumentException.class, () -> enabled.publish(actor, "release", List.of(wrongInputs, rules)));
        verifyNoInteractions(source, process.getRepositoryService(), decision.getDmnRepositoryService());
    }

    @Test
    void human_task_publication_requires_its_explicit_gate_pinned_factory_and_verified_v7_before_bundle_admission() throws Exception {
        DataSource source = mock(DataSource.class); var process = process(source); var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        assertThrows(IllegalArgumentException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator(), false, null, false, () -> true, true));
        when(process.getActivityBehaviorFactory()).thenReturn(new PinnedDecisionActivityBehaviorFactory());
        byte[] xml; try (var input = getClass().getResourceAsStream("/fixtures/governed-human-task.bpmn20.xml")) { assertNotNull(input); xml = input.readAllBytes(); }
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "publisher", "https://idp.example/20");
        var resource = new ProcessReleaseService.ModelResource(Type.BPMN, "humanReview", xml, Map.of());
        try (var verification = mockConstruction(HumanTaskStorageVerifier.class)) {
            var disabled = new ProcessReleaseService(process, decision, new ProcessModelValidator());
            assertThrows(IllegalArgumentException.class, () -> disabled.publish(actor, "release", List.of(resource))); assertTrue(verification.constructed().isEmpty());
            var enabled = new ProcessReleaseService(process, decision, new ProcessModelValidator(), false, null, false, () -> true, true);
            assertEquals(1, verification.constructed().size()); verify(verification.constructed().getFirst()).verify();
            var collision = new ProcessReleaseService.ModelResource(Type.BPMN, "humanReview", xml, Map.of("reviewApproved", ValueType.BOOLEAN));
            assertThrows(IllegalArgumentException.class, () -> enabled.publish(actor, "release", List.of(collision))); verifyNoInteractions(source);
            // A failed connection proves admission reached the existing publication transaction, not a deployment or commit.
            assertThrows(org.springframework.transaction.CannotCreateTransactionException.class, () -> enabled.publish(actor, "release", List.of(resource)));
            verify(source).getConnection(); verifyNoInteractions(process.getRepositoryService());
        }
    }

    @Test
    void timed_human_models_require_live_native_timer_job_acquisition_before_publication() throws Exception {
        DataSource source = mock(DataSource.class); var process = process(source); var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        when(process.getActivityBehaviorFactory()).thenReturn(new PinnedDecisionActivityBehaviorFactory());
        try (var verification = mockConstruction(HumanTaskStorageVerifier.class); var input = getClass().getResourceAsStream("/fixtures/governed-human-task-timer.bpmn20.xml")) {
            assertNotNull(input); var resource = new ProcessReleaseService.ModelResource(Type.BPMN, "timedHumanReview", input.readAllBytes(), Map.of());
            var service = new ProcessReleaseService(process, decision, new ProcessModelValidator(), false, null, false, () -> true, true);
            var actor = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "publisher", "https://idp.example/20");
            assertThrows(IllegalStateException.class, () -> service.publish(actor, "release", List.of(resource))); verifyNoInteractions(source);
            var jobs = mock(AsyncJobExecutorConfiguration.class); var executor = mock(AsyncExecutor.class);
            when(process.isAsyncExecutorActivate()).thenReturn(true); when(process.getAsyncExecutorConfiguration()).thenReturn(jobs); when(process.getAsyncExecutor()).thenReturn(executor); when(executor.isActive()).thenReturn(true);
            when(jobs.isTimerJobAcquisitionEnabled()).thenReturn(true); when(jobs.isAsyncJobAcquisitionEnabled()).thenReturn(true); when(jobs.isResetExpiredJobEnabled()).thenReturn(true);
            assertThrows(org.springframework.transaction.CannotCreateTransactionException.class, () -> service.publish(actor, "release", List.of(resource)));
            verify(source).getConnection(); verifyNoInteractions(process.getRepositoryService());
        }
    }

    @Test
    void failed_human_storage_verification_cannot_construct_an_enabled_publisher() {
        DataSource source = mock(DataSource.class); var process = process(source); var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        when(process.getActivityBehaviorFactory()).thenReturn(new PinnedDecisionActivityBehaviorFactory());
        try (var verification = mockConstruction(HumanTaskStorageVerifier.class, (value, ignored) -> doThrow(new IllegalStateException("unverified V7")).when(value).verify())) {
            assertThrows(IllegalStateException.class, () -> new ProcessReleaseService(process, decision, new ProcessModelValidator(), false, null, false, () -> true, true));
            verifyNoInteractions(source, process.getRepositoryService());
        }
    }

    @Test
    void agent_wait_publication_requires_current_maintenance_readiness_but_not_new_dispatch_admission() throws Exception {
        DataSource source = mock(DataSource.class);
        var process = process(source);
        var decision = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        var executor = mock(AsyncExecutor.class);
        var jobs = mock(AsyncJobExecutorConfiguration.class);
        when(process.isAsyncExecutorActivate()).thenReturn(true);
        when(process.getAsyncExecutor()).thenReturn(executor);
        when(executor.isActive()).thenReturn(true);
        when(process.getAsyncExecutorConfiguration()).thenReturn(jobs);
        when(jobs.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(jobs.isResetExpiredJobEnabled()).thenReturn(true);
        when(process.getHistoryService()).thenReturn(mock(HistoryService.class));
        when(process.isDbHistoryUsed()).thenReturn(true);
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
        var registration = new WorkerTopicRegistry.Registration(20, "https://idp.example/20", "worker", "agentStart", "1",
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("runId", ValueType.STRING), Map.of("requestedAmount", Set.of("amount")),
                Map.of("runId", Set.of("agentRunId")), 1, Duration.ofSeconds(15), Duration.ZERO, WorkerEffect.EXTERNAL_IDEMPOTENT, true);
        var ready = new AtomicBoolean();
        try (var verification = mockConstruction(ProcessApiSchemaVerifier.class);
             var input = getClass().getResourceAsStream("/fixtures/agent-dispatch-wait.bpmn20.xml")) {
            assertNotNull(input);
            var service = new ProcessReleaseService(process, decision, new ProcessModelValidator(), false,
                    new WorkerTopicRegistry(List.of(registration)), true, ready::get);
            var model = new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch", input.readAllBytes(), Map.of("amount", ValueType.INTEGER));
            var actor = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "alice", "https://idp.example/20");
            assertThrows(IllegalStateException.class, () -> service.publish(actor, "release", List.of(model)));
            verifyNoInteractions(source, process.getRepositoryService());
            ready.set(true);
            assertThrows(org.springframework.transaction.CannotCreateTransactionException.class, () -> service.publish(actor, "release", List.of(model)));
            verify(source).getConnection();
            verifyNoInteractions(process.getRepositoryService());
        }
    }

    @Test
    void cmmn_publication_deploys_the_closed_bytes_and_returns_the_exact_case_binding() throws Exception {
        try (var f = new CaseFixture()) {
            var release = assertDoesNotThrow(() -> f.publisher(true, true).publish(f.actor, "release", List.of(f.resource(Map.of()))));
            assertEquals(1, release.definitions().size()); var binding = release.definitions().getFirst();
            assertEquals(Type.CMMN, binding.type()); assertEquals("governedCase", binding.definitionKey());
            assertEquals("case-definition", binding.engineDefinitionId()); assertEquals("case-deployment", binding.engineDeploymentId());
            assertEquals(3, binding.engineVersion()); assertEquals(f.hash, binding.modelSha256()); assertTrue(binding.inputs().isEmpty());
            verify(f.builder).addBytes(eq("governedCase.cmmn"), aryEq(f.xml));
            verify(f.builder).tenantId("20"); verify(f.caseQuery, atLeastOnce()).deploymentId("case-deployment");
            verify(f.caseQuery).listPage(0, 2);
            verify(f.caseQuery, atLeastOnce()).caseDefinitionTenantId("20");
            verify(f.connection).commit(); verifyNoInteractions(f.process.getRepositoryService(), f.dmn.getDmnRepositoryService());
        }
    }

    @Test
    void cmmn_requires_case_and_human_publication_flags_and_real_isolated_native_readiness() throws Exception {
        try (var f = new CaseFixture()) {
            assertThrows(IllegalArgumentException.class, () -> f.publisher(false, false).publish(f.actor, "release", List.of(f.resource(Map.of()))));
            assertThrows(IllegalArgumentException.class, () -> f.publisher(true, false).publish(f.actor, "release", List.of(f.resource(Map.of()))));
            when(f.cmmn.getDataSource()).thenReturn(mock(DataSource.class));
            assertThrows(IllegalArgumentException.class, () -> f.publisher(true, true));
            when(f.cmmn.getDataSource()).thenReturn(f.source);
            when(f.cmmn.getBeans()).thenReturn(Map.of("unsafe", new Object()));
            assertThrows(IllegalArgumentException.class, () -> f.publisher(true, true));
            when(f.cmmn.getBeans()).thenReturn(Map.of());
            when(f.cmmn.getActivityBehaviorFactory()).thenReturn(new DefaultCmmnActivityBehaviorFactory());
            assertThrows(IllegalStateException.class, () -> f.publisher(true, true));
            verifyNoInteractions(f.source, f.repository);
        }
    }

    @Test
    void cmmn_input_schema_and_ungoverned_models_are_rejected_before_any_publication_transaction() throws Exception {
        try (var f = new CaseFixture()) {
            var service = f.publisher(true, true);
            assertThrows(IllegalArgumentException.class, () -> service.publish(f.actor, "release", List.of(f.resource(Map.of("amount", ValueType.INTEGER)))));
            assertThrows(IllegalArgumentException.class, () -> service.publish(f.actor, "release", List.of(new ProcessReleaseService.ModelResource(Type.CMMN, "formalCase", fixture("formal-case.cmmn"), Map.of()))));
            assertThrows(IllegalArgumentException.class, () -> service.publish(f.actor, "release", List.of(new ProcessReleaseService.ModelResource(Type.CMMN, "wrongKey", f.xml, Map.of()))));
            verifyNoInteractions(f.source, f.repository);
        }
    }

    @Test
    void cmmn_deployment_mismatch_rolls_back_and_never_becomes_an_applied_release() throws Exception {
        for (String corruption : List.of("key", "tenant", "deployment", "version", "resource", "hash")) {
            try (var f = new CaseFixture()) {
                switch (corruption) {
                    case "key" -> when(f.definition.getKey()).thenReturn("otherCase");
                    case "tenant" -> when(f.definition.getTenantId()).thenReturn("21");
                    case "deployment" -> when(f.definition.getDeploymentId()).thenReturn("other-deployment");
                    case "version" -> when(f.definition.getVersion()).thenReturn(0);
                    case "resource" -> when(f.definition.getResourceName()).thenReturn("other.cmmn");
                    case "hash" -> when(f.repository.getResourceAsStream(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1}));
                    default -> throw new AssertionError(corruption);
                }
                assertThrows(IllegalStateException.class, () -> f.publisher(true, true).publish(f.actor, "release", List.of(f.resource(Map.of()))), corruption);
                verify(f.connection).rollback();
            }
        }
    }

    @Test
    void verified_case_models_recheck_every_native_binding_and_the_immutable_bytes() throws Exception {
        try (var f = new CaseFixture()) {
            var models = new VerifiedProcessModels(f.process, f.cmmn, f.dmn, new ProcessModelValidator());
            var binding = new ProcessReleaseService.Definition(Type.CMMN, "governedCase", "case-definition", "case-deployment", 3, f.hash, Map.of());
            var model = models.caseModel(20, binding); assertNotNull(model); assertEquals("governedCase", model.definitionKey());
            verify(f.caseQuery).caseDefinitionId("case-definition"); verify(f.caseQuery).deploymentId("case-deployment");
            verify(f.caseQuery).caseDefinitionTenantId("20"); verify(f.caseQuery).caseDefinitionKey("governedCase"); verify(f.caseQuery).caseDefinitionVersion(3);
            when(f.definition.getTenantId()).thenReturn("21");
            assertThrows(ProcessResourceNotFoundException.class, () -> models.caseModel(20, binding));
            when(f.definition.getTenantId()).thenReturn("20");
            when(f.repository.getResourceAsStream(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1}));
            assertThrows(ProcessResourceNotFoundException.class, () -> models.caseModel(20, binding));
        }
    }

    @Test
    void manifest_digest_has_package_scope() {
        var method = assertDoesNotThrow(() -> ProcessReleaseService.class.getDeclaredMethod("manifestHash", List.class));
        int modifiers = method.getModifiers();
        assertEquals(String.class, method.getReturnType());
        assertTrue(java.lang.reflect.Modifier.isStatic(modifiers));
        assertTrue(method.canAccess(null), "manifest digest is callable within the release package");
        assertEquals(0, modifiers & (java.lang.reflect.Modifier.PRIVATE | java.lang.reflect.Modifier.PROTECTED
                | java.lang.reflect.Modifier.PUBLIC));
    }

    @Test
    void legacy_constructor_overloads_and_two_type_manifest_bytes_remain_compatible() throws Exception {
        var source = mock(DataSource.class); var process = process(source); var dmn = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        var validator = new ProcessModelValidator();
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator));
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator, false));
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator, false, null));
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator, false, null, false));
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator, false, null, false, () -> true));
        assertDoesNotThrow(() -> new ProcessReleaseService(process, dmn, validator, false, null, false, () -> true, false));
        var dmnModel = validator.validateDmn(fixture("baseline.dmn"), "baselineDecision");
        var bpmnModel = validator.validateBpmn(fixture("baseline.bpmn20.xml"), "baseline");
        var models = List.of(new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", dmnModel.xml(), dmnModel.inputs()),
                new ProcessReleaseService.ModelResource(Type.BPMN, "baseline", bpmnModel.xml(), Map.of()));
        String expected = "2633baa271f32928b180858f00c49923f813011e00a00aa4fc3df162cfcc1049";
        assertEquals(expected, ProcessReleaseService.manifestHash(models));
        assertEquals(expected, ProcessReleaseService.manifestHash(models.reversed()));
        verifyNoInteractions(source);
    }

    @Test
    void linked_source_publication_binds_both_models_and_the_source_node_in_one_release() throws Exception {
        try (var f = new CaseFixture(); var pinned = mockStatic(PinnedDecisionActivityBehaviorFactory.class)) {
            var registry = mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.class);
            var registration = mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration.class);
            var key = sourceBinding(f, "release"); when(registry.require(key)).thenReturn(registration); when(registry.current(registration)).thenReturn(true);
            configureLinkedProcess(f);
            var service = sourcePublisher(f, registry, () -> true, true);
            var release = assertDoesNotThrow(() -> service.publish(f.actor, "release", sourceBundle(f)));
            assertEquals("cf7819f270487cd29941ee68d1c3c067ca0335e57e6e09b32556186a0b9bfee3", release.manifestSha256());
            assertEquals(Set.of(Type.BPMN, Type.CMMN), release.definitions().stream().map(ProcessReleaseService.Definition::type).collect(java.util.stream.Collectors.toSet()));
            verify(registry).require(key); verify(registry).current(registration); verify(f.connection).commit();
        }
    }

    @Test
    void linked_source_requires_new_start_and_live_maintenance_before_database_or_native_deployment() throws Exception {
        for (boolean starts : List.of(false, true)) {
            try (var f = new CaseFixture(); var pinned = mockStatic(PinnedDecisionActivityBehaviorFactory.class)) {
                var registry = mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.class);
                var service = sourcePublisher(f, registry, () -> false, starts);
                assertThrows(IllegalArgumentException.class, () -> service.publish(f.actor, "release", sourceBundle(f)));
                verifyNoInteractions(f.source, f.repository, registry);
            }
        }
    }

    @Test
    void linked_source_cannot_fetch_a_case_from_another_release_or_skip_the_current_source_registration() throws Exception {
        try (var f = new CaseFixture(); var pinned = mockStatic(PinnedDecisionActivityBehaviorFactory.class)) {
            var registry = mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.class);
            var service = sourcePublisher(f, registry, () -> true, true);
            assertThrows(IllegalArgumentException.class, () -> service.publish(f.actor, "release", List.of(sourceBundle(f).getFirst())));
            verifyNoInteractions(registry, f.source, f.repository);
            var checked = new SourceChecked(); when(registry.require(sourceBinding(f, "release"))).thenThrow(checked);
            assertSame(checked, assertThrows(SourceChecked.class, () -> service.publish(f.actor, "release", sourceBundle(f))));
            verifyNoInteractions(f.source, f.repository);
        }
    }

    private static final class SourceChecked extends RuntimeException { }
    @Test
    void activation_checks_the_owning_primary_the_full_native_bundle_and_the_link() throws Exception {
        try (var fixture = new CaseFixture()) {
            byte[] bpmn = fixture("agent-dispatch-wait.bpmn20.xml");
            byte[] dmn = fixture("baseline.dmn");
            var processRepository = fixture.process.getRepositoryService();
            var processQuery = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
            var processDefinition = mock(ProcessDefinition.class);
            when(processRepository.createProcessDefinitionQuery()).thenReturn(processQuery);
            when(processQuery.singleResult()).thenReturn(processDefinition);
            when(processDefinition.getKey()).thenReturn("agentDispatch");
            when(processDefinition.getResourceName()).thenReturn("agentDispatch.bpmn20.xml");
            when(processDefinition.getVersion()).thenReturn(3);
            when(processRepository.getResourceAsStream("bpmn-deployment", "agentDispatch.bpmn20.xml"))
                    .thenAnswer(i -> new ByteArrayInputStream(bpmn));
            var decisionRepository = fixture.dmn.getDmnRepositoryService();
            var decisionQuery = mock(DmnDecisionQuery.class, RETURNS_SELF);
            when(decisionRepository.createDecisionQuery()).thenReturn(decisionQuery);
            when(decisionQuery.singleResult()).thenReturn(mock(DmnDecision.class));
            when(decisionRepository.getDmnResource("dmn-definition")).thenAnswer(i -> new ByteArrayInputStream(dmn));

            var inputs = Map.of("amount", ValueType.INTEGER);
            var expected = List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "agentDispatch", bpmn, inputs),
                    new ProcessReleaseService.ModelResource(Type.DMN, "baselineDecision", dmn, inputs),
                    fixture.resource(Map.of()));
            fixture.manifest = ProcessReleaseService.manifestHash(expected);
            for (var resource : expected) {
                var type = resource.type().name().toLowerCase(Locale.ROOT);
                fixture.definitions.add(Map.of("model_type", resource.type().name(), "definition_key", resource.definitionKey(),
                        "engine_definition_id", type.equals("cmmn") ? "case-definition" : type + "-definition",
                        "engine_deployment_id", type.equals("cmmn") ? "case-deployment" : type + "-deployment",
                        "engine_version", 3, "model_sha256",
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.xml())),
                        "input_schema", resource.inputs().isEmpty() ? "{}" : "{\"amount\":\"INTEGER\"}"));
            }
            var service = fixture.publisher(false, false);
            var jdbc = fixture.jdbc.constructed().getFirst();
            var primarySql = "SELECT NOT pg_catalog.pg_is_in_recovery()";
            when(jdbc.queryForObject(primarySql, Boolean.class)).thenReturn(true);
            var link = new ProcessLink("agentDispatch", "worker", "agentStart", "1");
            assertEquals(fixture.manifest, service.getForActivation(fixture.actor, "release", link).manifestSha256());
            verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(processQuery).processDefinitionTenantId("20");
            verify(decisionQuery).decisionTenantId("20");
            verify(fixture.caseQuery).caseDefinitionTenantId("20");
            var wrongLink = new ProcessLink("agentDispatch", "worker", "another", "1");
            assertThrows(ProcessResourceNotFoundException.class, () -> service.getForActivation(fixture.actor, "release", wrongLink));
            String correct = fixture.manifest;
            fixture.manifest = "0".repeat(64);
            assertThrows(ProcessResourceNotFoundException.class, () -> service.getForActivation(fixture.actor, "release", link));
            fixture.manifest = correct;
            dmn[0] = 'x';
            assertThrows(ProcessResourceNotFoundException.class, () -> service.getForActivation(fixture.actor, "release", link));
            when(jdbc.queryForObject(primarySql, Boolean.class)).thenReturn(false);
            clearInvocations(processRepository, decisionRepository, fixture.repository);
            assertThrows(ProcessResourceNotFoundException.class, () -> service.getForActivation(fixture.actor, "release", link));
            verifyNoInteractions(processRepository, decisionRepository, fixture.repository);
        }
    }

    private static List<ProcessReleaseService.ModelResource> sourceBundle(CaseFixture f) throws Exception {
        return List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "linkedProcess", fixture("formal-case-linked.bpmn20.xml"), Map.of()), f.resource(Map.of()));
    }
    private static top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.BindingKey sourceBinding(CaseFixture f, String release) throws Exception {
        String parentHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture("formal-case-linked.bpmn20.xml")));
        return new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.BindingKey(20, release, "linkedProcess", parentHash, "caseSource", "governedCase", f.hash);
    }
    private static ProcessReleaseService sourcePublisher(CaseFixture f, top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry registry,
                                                         java.util.function.BooleanSupplier ready, boolean starts) {
        when(f.process.getHistoryService()).thenReturn(mock(HistoryService.class)); when(f.process.isDbHistoryUsed()).thenReturn(true); when(f.process.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT);
        return new ProcessReleaseService(f.process, f.cmmn, f.dmn, new ProcessModelValidator(), false, null, false, () -> true, true, true, registry, ready, starts);
    }
    private static void configureLinkedProcess(CaseFixture f) {
        var repository = f.process.getRepositoryService();
        var builder = mock(org.flowable.engine.repository.DeploymentBuilder.class, RETURNS_SELF); var deployment = mock(org.flowable.engine.repository.Deployment.class);
        var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF); var definition = mock(ProcessDefinition.class);
        when(repository.createDeployment()).thenReturn(builder); when(builder.deploy()).thenReturn(deployment); when(deployment.getId()).thenReturn("parent-deployment");
        when(repository.createProcessDefinitionQuery()).thenReturn(query); when(query.list()).thenReturn(List.of(definition));
        when(definition.getId()).thenReturn("parent-definition"); when(definition.getKey()).thenReturn("linkedProcess"); when(definition.getVersion()).thenReturn(1);
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = ProcessReleaseServiceTest.class.getResourceAsStream("/fixtures/" + name)) { assertNotNull(input); return input.readAllBytes(); }
    }
    private static final class CaseFixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class);
        final SpringProcessEngineConfiguration process = process(source);
        final SpringDmnEngineConfiguration dmn = decision(source, (DataSourceTransactionManager) process.getTransactionManager());
        final SpringCmmnEngineConfiguration cmmn = mock(SpringCmmnEngineConfiguration.class);
        final CmmnRepositoryService repository = mock(CmmnRepositoryService.class);
        final CmmnDeploymentBuilder builder = mock(CmmnDeploymentBuilder.class, RETURNS_SELF);
        final CmmnDeployment deployment = mock(CmmnDeployment.class);
        final CaseDefinitionQuery caseQuery = mock(CaseDefinitionQuery.class, RETURNS_SELF);
        final CaseDefinition definition = mock(CaseDefinition.class);
        final ProcessActor actor = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "publisher", "https://idp.example/20");
        final byte[] xml = fixture("governed-case.cmmn"); final String hash;
        final MockedConstruction<ProcessApiSchemaVerifier> schema;
        final MockedConstruction<HumanTaskStorageVerifier> humanSchema;
        final MockedConstruction<JdbcTemplate> jdbc;
        String manifest; final List<Map<String,Object>> definitions = new ArrayList<>();
        CaseFixture() throws Exception {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml));
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            when(process.getActivityBehaviorFactory()).thenReturn(new PinnedDecisionActivityBehaviorFactory());
            var manager = process.getTransactionManager();
            when(cmmn.getDataSource()).thenReturn(source); when(cmmn.getTransactionManager()).thenReturn(manager);
            when(cmmn.getDatabaseSchema()).thenReturn("tianji_process"); when(cmmn.getBeans()).thenReturn(Map.of()); when(cmmn.getDatabaseSchemaUpdate()).thenReturn("false");
            when(cmmn.getCmmnRepositoryService()).thenReturn(repository);
            when(cmmn.getCmmnRuntimeService()).thenReturn(mock(CmmnRuntimeService.class));
            when(cmmn.getCmmnTaskService()).thenReturn(mock(CmmnTaskService.class));
            when(cmmn.getCmmnHistoryService()).thenReturn(mock(CmmnHistoryService.class)); when(cmmn.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT);
            var expressions = mock(ExpressionManager.class);
            var delegates = mock(CmmnClassDelegateFactory.class);
            var factory = new PinnedCaseActivityBehaviorFactory(() -> null);
            factory.setExpressionManager(expressions); factory.setClassDelegateFactory(delegates);
            var parser = new CmmnParserImpl(); parser.setActivityBehaviorFactory(factory);
            when(cmmn.getActivityBehaviorFactory()).thenReturn(factory); when(cmmn.getCmmnParser()).thenReturn(parser);
            when(cmmn.getExpressionManager()).thenReturn(expressions); when(cmmn.getClassDelegateFactory()).thenReturn(delegates);
            when(repository.createDeployment()).thenReturn(builder); when(builder.deploy()).thenReturn(deployment); when(deployment.getId()).thenReturn("case-deployment");
            when(repository.createCaseDefinitionQuery()).thenReturn(caseQuery); when(caseQuery.list()).thenReturn(List.of(definition)); when(caseQuery.listPage(0, 2)).thenReturn(List.of(definition)); when(caseQuery.singleResult()).thenReturn(definition);
            when(definition.getId()).thenReturn("case-definition"); when(definition.getKey()).thenReturn("governedCase"); when(definition.getTenantId()).thenReturn("20");
            when(definition.getDeploymentId()).thenReturn("case-deployment"); when(definition.getVersion()).thenReturn(3); when(definition.getResourceName()).thenReturn("governedCase.cmmn");
            when(repository.getResourceAsStream(anyString(), anyString())).thenAnswer(i -> new ByteArrayInputStream(xml));
            schema = mockConstruction(ProcessApiSchemaVerifier.class); humanSchema = mockConstruction(HumanTaskStorageVerifier.class);
            jdbc = mockConstruction(JdbcTemplate.class, (mock, context) -> {
                when(mock.update(anyString(), any(Object[].class))).thenAnswer(i -> {
                    String sql = i.getArgument(0); Object[] args = (Object[]) i.getRawArguments()[1];
                    if (sql.contains("INSERT INTO") && sql.contains("tianji_process_release_definition")) {
                        definitions.add(Map.of("model_type", args[2], "definition_key", args[3], "engine_definition_id", args[4], "engine_deployment_id", args[5],
                                "engine_version", args[6], "model_sha256", args[7], "input_schema", args[8]));
                    } else if (sql.contains("INSERT INTO") && sql.contains("tianji_process_release")) manifest = (String) args[2];
                    return 1;
                });
                when(mock.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class))).thenAnswer(i -> {
                    String sql = i.getArgument(0); RowMapper<?> mapper = i.getArgument(1);
                    var rows = sql.contains("SELECT model_type") ? definitions : List.of(Map.<String,Object>of("manifest_sha256", manifest, "state", "ACTIVE", "created_at", Timestamp.from(Instant.parse("2026-11-04T08:00:00Z"))));
                    var result = new ArrayList<>(); for (var row : rows) result.add(mapper.mapRow(resultSet(row), result.size())); return result;
                });
            });
            clearInvocations(source, repository, process.getRepositoryService(), dmn.getDmnRepositoryService());
        }
        ProcessReleaseService publisher(boolean cases, boolean human) { return new ProcessReleaseService(process, cmmn, dmn, new ProcessModelValidator(), false, null, false, () -> true, human, cases); }
        ProcessReleaseService.ModelResource resource(Map<String,ValueType> inputs) { return new ProcessReleaseService.ModelResource(Type.CMMN, "governedCase", xml, inputs); }
        private static ResultSet resultSet(Map<String,Object> row) throws Exception {
            var rs = mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(i -> { var value = row.get(i.getArgument(0)); return value == null ? null : value.toString(); });
            when(rs.getInt(anyString())).thenAnswer(i -> ((Number) row.get(i.getArgument(0))).intValue());
            when(rs.getTimestamp(anyString())).thenAnswer(i -> row.get(i.getArgument(0))); return rs;
        }
        @Override public void close() { jdbc.close(); humanSchema.close(); schema.close(); }
    }

    private static SpringProcessEngineConfiguration process(DataSource source) {
        var engine = mock(SpringProcessEngineConfiguration.class);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(new DataSourceTransactionManager(source));
        when(engine.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(engine.getDatabaseSchema()).thenReturn("tianji_process");
        when(engine.getBeans()).thenReturn(Map.of());
        return engine;
    }

    private static SpringDmnEngineConfiguration decision(DataSource source, DataSourceTransactionManager manager) {
        var engine = mock(SpringDmnEngineConfiguration.class);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(manager);
        when(engine.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(engine.getDatabaseSchema()).thenReturn("tianji_process");
        when(engine.getBeans()).thenReturn(Map.of());
        return engine;
    }
}
