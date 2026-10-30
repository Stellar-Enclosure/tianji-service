package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.HistoryService;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
