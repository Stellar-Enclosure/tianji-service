package top.egon.cola.platform.tianji.process.server.execution;

import org.flywaydb.core.Flyway;
import org.flowable.common.engine.impl.interceptor.AbstractCommandInterceptor;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationConverter;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;

import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;

import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;

import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;

import top.egon.cola.platform.tianji.process.server.task.HumanTaskCompletionObservation;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
class AutomationConcurrencyIT {
    private static final String SCHEMA = "tianji_process";
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final String DEFINITION = "review";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", "https://idp.test/main");
    private static final String XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step25">
              <process id="review" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="done" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String INPUT = """
            {"type":"object","properties":{"amount":{"type":"string","format":"decimal:2"}},
            "required":["amount"],"additionalProperties":false}
            """;
    private static final String EMPTY = """
            {"type":"object","properties":{},"required":[],"additionalProperties":false}
            """;

    @Test
    void native_cancel_and_async_execution_cannot_enter_before_the_same_root_guard() throws Exception {
        var database = provision();
        var environment = new MockEnvironment().withProperty(ENABLED, "true");
        var authority = new FixtureAuthority(database.owner());
        try (var application = context(database.url(), database.role(), false)) {
            var services = components(application, environment, authority);
            publish(services.drafts(), "review-v2");
            services.activation().activate(OWNER, DEFINITION, new ActivationCommand("review-v2", 0L, "activate-review"));
            var first = services.execution().startAutomation(OWNER, start("start-cancel"));
            var second = services.execution().startAutomation(OWNER, start("start-job"));
            var process = application.getBean(SpringProcessEngineConfiguration.class);
            var nativeProbe = application.getBean(NativeEntryProbe.class);
            var job = process.getManagementService().createJobQuery().processInstanceId(second.getInstanceId()).singleResult();
            assertNotNull(job);
            var transaction = new TransactionTemplate(process.getTransactionManager());
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                assertWaitsBeforeNativeCommand(database, transaction, pool, nativeProbe, first.getInstanceId(),
                        "DeleteProcessInstanceCmd", () -> process.getRuntimeService().deleteProcessInstance(first.getInstanceId(), "fixture cancel"));
                assertWaitsBeforeNativeCommand(database, transaction, pool, nativeProbe, second.getInstanceId(),
                        "ExecuteJobCmd", () -> process.getManagementService().executeJob(job.getId()));
            }
            publishHuman(services.drafts());
            services.activation().activate(OWNER, "approval", new ActivationCommand("human-v2", 0L, "activate-human"));
            var wiring = application.getBean(NativeWiring.class);
            verifyFailedJobAccounting(database, services.execution(), process, wiring);
            for (boolean attempt : List.of(true, false)) {
                verifyRealGeneratedTimeout(database, services.execution(), process, attempt);
            }
            var taskRoot = services.execution().startAutomation(OWNER, new AutomationStartCommand("approval", "approval-1",
                    "start-human-race", "trace-human", Map.of(), Map.of()));
            var bootstrap = process.getManagementService().createJobQuery().processInstanceId(taskRoot.getInstanceId()).singleResult();
            process.getManagementService().executeJob(bootstrap.getId());
            var task = process.getTaskService().createTaskQuery().processInstanceId(taskRoot.getInstanceId()).singleResult();
            assertNotNull(task);
            assertTrue(process.getTaskService().getIdentityLinksForTask(task.getId()).stream().anyMatch(link -> "role:901".equals(link.getGroupId())));
            var timer = process.getManagementService().createTimerJobQuery().processInstanceId(taskRoot.getInstanceId()).list().getFirst();
            assertNotNull(timer);
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                assertWaitsBeforeNativeCommand(database, transaction, pool, nativeProbe, taskRoot.getInstanceId(),
                        "MoveTimerToExecutableJobCmd", () -> process.getManagementService().moveTimerToExecutableJob(timer.getId()));
                assertWaitsBeforeNativeCommand(database, transaction, pool, nativeProbe, taskRoot.getInstanceId(),
                        "CompleteTaskCmd", () -> assertThrows(RuntimeException.class, () -> process.getTaskService().complete(task.getId())));
            }
            assertNotNull(process.getTaskService().createTaskQuery().taskId(task.getId()).singleResult(),
                    "Raw Flowable completion cannot bypass typed ACTION");
            var humanReceipts = new HumanTaskReceiptStore(((DataSourceTransactionManager) process.getTransactionManager()).getDataSource(), SCHEMA);
            humanReceipts.verifyStorage();
            var human = new FlowableHumanTaskService(process, null, application.getBean(SpringDmnEngineConfiguration.class),
                    new ProcessModelValidator(), humanReceipts, null, HumanTaskCompletionObservation.disabled(),
                    new FlowableHumanTaskService.AutomationTaskParticipants(wiring.bindings, wiring.releases, wiring.guard, wiring.nodes, wiring.lifecycle));
            var expires = Instant.now().plusSeconds(60);
            var reader = new HumanTaskContext(OWNER, "tianji.api.process.task.get.v2", Set.of("901"), expires, 1, 1);
            var claimant = new HumanTaskContext(OWNER, "tianji.api.process.task.claim", Set.of("901"), expires, 1, 1);
            var completer = new HumanTaskContext(OWNER, "tianji.api.process.task.complete.v2", Set.of("901"), expires, 1, 1);
            var wrong = new HumanTaskContext(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "wrong", "https://idp.test/main"),
                    "tianji.api.process.task.complete.v2", Set.of("902"), expires, 1, 1);
            var initial = human.observeAction(reader, task.getId());
            assertEquals(List.of("CLAIM"), initial.getAvailableActions());
            human.claim(claimant, new ClaimHumanTaskCommand("claim-human", task.getId(), initial.getVersion()));
            var current = human.observeAction(reader, task.getId());
            var complete = new CompleteEngineHumanTaskCommand("complete-human", current.getVersion(), Map.of("resultRef", "accepted"));
            assertThrows(ProcessResourceNotFoundException.class, () -> human.completeAction(wrong, task.getId(), complete));
            assertThrows(ProcessVersionConflictException.class, () -> human.completeAction(completer, task.getId(),
                    new CompleteEngineHumanTaskCommand("stale-human", initial.getVersion(), Map.of("resultRef", "accepted"))));
            var applied = human.completeAction(completer, task.getId(), complete);
            assertEquals(applied, human.completeAction(completer, task.getId(), complete));
            assertThrows(ProcessResourceNotFoundException.class, () -> human.completeAction(wrong, task.getId(), complete));
            var revoked = new HumanTaskContext(OWNER, "tianji.api.process.task.complete.v2", Set.of("902"), expires, 2, 1);
            assertThrows(ProcessResourceNotFoundException.class, () -> human.completeAction(revoked, task.getId(), complete));
            assertNull(process.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
            assertEquals(1, database.owner().queryForObject("""
                    SELECT count(*) FROM tianji_process.tianji_process_node_execution
                    WHERE instance_id=? AND state='COMPLETED' AND output_sha256 IS NOT NULL AND output_json->>'resultRef'='accepted'
                    """, Integer.class, taskRoot.getInstanceId()));
            verifyTypedCancellation(database, services.execution(), process, application.getBean(SpringDmnEngineConfiguration.class),
                    wiring, nativeProbe, human, reader, claimant, completer);
            assertNotNull(process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(first.getInstanceId())
                    .singleResult().getDeleteReason());
            assertNotNull(process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(second.getInstanceId())
                    .singleResult().getEndTime());
        }
    }

    private static void verifyTypedCancellation(DatabaseVO database, ProcessExecutionService execution,
            SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn, NativeWiring wiring,
            NativeEntryProbe probe, FlowableHumanTaskService human, HumanTaskContext reader,
            HumanTaskContext claimant, HumanTaskContext completer) throws Exception {
        var root = execution.startAutomation(OWNER, new AutomationStartCommand("approval", "approval-cancel",
                "start-human-cancel", "trace-human-cancel", Map.of(), Map.of()));
        var jobs = process.getManagementService();
        jobs.executeJob(jobs.createJobQuery().processInstanceId(root.getInstanceId()).singleResult().getId());
        var nativeTask = process.getTaskService().createTaskQuery().processInstanceId(root.getInstanceId()).singleResult();
        var unclaimed = human.observeAction(reader, nativeTask.getId());
        human.claim(claimant, new ClaimHumanTaskCommand("claim-for-cancel", nativeTask.getId(), unclaimed.getVersion()));
        var claimed = human.observeAction(reader, nativeTask.getId());
        var transactions = (DataSourceTransactionManager) process.getTransactionManager();
        var source = transactions.getDataSource();
        var receiptStore = new ProcessCancellationReceiptStore(source, SCHEMA);
        receiptStore.verifyStorage();
        var validator = new ProcessModelValidator();
        var releaseService = new ProcessReleaseService(process, dmn, validator);
        var nativeAccess = new ProcessControlNativeAccess(source, process);
        var dispatch = new AgentDispatchStore(source, transactions, SCHEMA);
        var control = new ProcessControlService(process, dmn, validator, releaseService, nativeAccess, receiptStore, dispatch,
                new ProcessControlService.AutomationControlParticipants(wiring.bindings, wiring.releases, wiring.guard, wiring.nodes));
        var snapshot = control.control(OWNER, new GetProcessInstanceQuery(root.getInstanceId()));
        var cancel = new CancelProcessCommand(root.getInstanceId(), "cancel-human", snapshot.version(), "fixture cancellation");
        var worker = ProcessActor.verified(OWNER.tenantId(), ProcessActor.SubjectType.SERVICE, "legacy-dispatch", "https://idp.test/main");
        var route = new AgentDispatchBinding(worker, "legacy-process", "agent", "1", "legacy-agent", 1,
                "5".repeat(64), "Fixture legacy intent", Duration.ofMinutes(5));
        var first = new TransactionTemplate(transactions).execute(status -> {
            var bound = wiring.guard.lockKnownRoot(OWNER.tenantId(), root.getInstanceId());
            var definition = nativeAccess.automationDefinition(bound);
            AgentDispatchStore.Intent singleton = null;
            for (int index = 0; index < 257; index++) {
                var intent = dispatch.prepare(new AgentDispatchStore.Seed(route, root.getInstanceId(), "intent-" + index,
                        root.getReleaseVersion(), definition.engineDefinitionId(), definition.modelSha256(), "approve",
                        "6".repeat(64), root.getBusinessKey(), root.getCorrelationId(), Map.of()));
                if (index == 0) singleton = intent;
            }
            return singleton;
        });
        assertNotNull(first);
        assertThrows(IllegalStateException.class, () -> control.cancel(OWNER, cancel), "The owned cancel rejects 257 open V5 intents");
        assertNotNull(process.getTaskService().createTaskQuery().taskId(nativeTask.getId()).singleResult(),
                "An over-capacity cancellation must roll back the native deletion");
        assertEquals(257, openAgentDispatch(database, root.getInstanceId(), "PENDING"));
        assertEquals(AgentDispatchStore.State.CLOSED, new TransactionTemplate(transactions).execute(status -> {
            wiring.guard.lockKnownRoot(OWNER.tenantId(), root.getInstanceId());
            return dispatch.record(first.seed().key(), first.query(), new LinkedAgentSubmissionDTO(true, null)).state();
        }));
        var complete = new CompleteEngineHumanTaskCommand("complete-after-cancel", claimed.getVersion(), Map.of("resultRef", "late"));
        var cancelInNative = new CountDownLatch(1);
        var allowCancel = new CountDownLatch(1);
        CancelProcessResult applied;
        probe.observe("DeleteProcessInstanceCmd", cancelInNative, allowCancel);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var cancellation = pool.submit(() -> control.cancel(OWNER, cancel));
            try {
                assertTrue(cancelInNative.await(5, TimeUnit.SECONDS), "Owned cancellation must reach the native command");
                var completion = pool.submit(() -> assertThrows(ProcessResourceNotFoundException.class,
                        () -> human.completeAction(completer, nativeTask.getId(), complete)));
                waitForNativeOrLock(database, new CountDownLatch(1));
                assertFalse(completion.isDone(), "Concurrent ACTION must wait while the cancellation owns the root");
                allowCancel.countDown();
                applied = cancellation.get(10, TimeUnit.SECONDS);
                completion.get(10, TimeUnit.SECONDS);
            } finally {
                allowCancel.countDown();
            }
        } finally {
            probe.stop();
        }
        assertTrue(applied.accepted());
        assertTrue(applied.linkedAgentClosureScheduled());
        assertEquals(applied, control.cancel(OWNER, cancel));
        assertEquals(0, openAgentDispatch(database, root.getInstanceId(), "PENDING"));
        assertEquals(256, openAgentDispatch(database, root.getInstanceId(), "CLOSING"));
        assertEquals(1, openAgentDispatch(database, root.getInstanceId(), "CLOSED"));
        assertThrows(ProcessResourceNotFoundException.class, () -> human.completeAction(completer, nativeTask.getId(), complete));
        assertEquals("CANCELLED", database.owner().queryForObject("""
                SELECT state FROM tianji_process.tianji_process_node_execution WHERE tenant_id=? AND instance_id=?
                """, String.class, OWNER.tenantId(), root.getInstanceId()));
        assertNull(process.getTaskService().createTaskQuery().taskId(nativeTask.getId()).singleResult());
    }

    private static int openAgentDispatch(DatabaseVO database, String instanceId, String state) {
        return database.owner().queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_agent_dispatch
                WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND state=?
                """, Integer.class, OWNER.tenantId(), instanceId, state);
    }

    private static void verifyFailedJobAccounting(DatabaseVO database, ProcessExecutionService execution,
                                                  SpringProcessEngineConfiguration process, NativeWiring wiring) {
        var root = execution.startAutomation(OWNER, new AutomationStartCommand("approval", "approval-failure",
                "start-failure", "trace-failure", Map.of(), Map.of()));
        var jobs = process.getManagementService();
        var job = jobs.createJobQuery().processInstanceId(root.getInstanceId()).singleResult();
        assertNotNull(job);
        wiring.rejectFixtureCandidate.set(true);
        try {
            assertThrows(RuntimeException.class, () -> jobs.executeJob(job.getId()));
        } finally {
            wiring.rejectFixtureCandidate.set(false);
        }
        var retry = jobs.createTimerJobQuery().processInstanceId(root.getInstanceId()).singleResult();
        assertNotNull(retry, "Flowable must account for the original native failure after releasing the root lock");
        assertEquals(job.getRetries() - 1, retry.getRetries());
        assertNotNull(retry.getExceptionMessage());
        assertEquals(0, database.owner().queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_node_execution WHERE tenant_id=? AND instance_id=?
                """, Integer.class, OWNER.tenantId(), root.getInstanceId()));
        assertNull(process.getTaskService().createTaskQuery().processInstanceId(root.getInstanceId()).singleResult());
    }

    private static void verifyRealGeneratedTimeout(DatabaseVO database, ProcessExecutionService execution,
                                                   SpringProcessEngineConfiguration process, boolean attempt) {
        String suffix = attempt ? "attempt" : "node";
        var root = execution.startAutomation(OWNER, new AutomationStartCommand("approval", "approval-" + suffix,
                "start-" + suffix, "trace-" + suffix, Map.of(), Map.of()));
        var jobs = process.getManagementService();
        jobs.executeJob(jobs.createJobQuery().processInstanceId(root.getInstanceId()).singleResult().getId());
        var ordered = jobs.createTimerJobQuery().processInstanceId(root.getInstanceId()).list().stream()
                .sorted(java.util.Comparator.comparing(org.flowable.job.api.Job::getDuedate)).toList();
        assertEquals(2, ordered.size());
        var timer = attempt ? ordered.getFirst() : ordered.getLast();
        // Advance the SQL fixture's stored deadlines without waiting minutes. The production timer
        // still executes the real nested or outer BPMN boundary and reads its strict database clock.
        assertEquals(1, database.owner().update("""
                UPDATE tianji_process.tianji_process_node_execution
                   SET started_at=clock_timestamp()-interval '5 seconds',
                       attempt_deadline_at=clock_timestamp()-interval '2 seconds',
                       node_deadline_at=clock_timestamp()-interval '1 second'
                 WHERE tenant_id=? AND instance_id=? AND state='WAITING'
                """, OWNER.tenantId(), root.getInstanceId()));
        var executable = jobs.moveTimerToExecutableJob(timer.getId());
        jobs.executeJob(executable.getId());
        assertEquals("TIMED_OUT", database.owner().queryForObject("""
                SELECT state FROM tianji_process.tianji_process_node_execution WHERE tenant_id=? AND instance_id=?
                """, String.class, OWNER.tenantId(), root.getInstanceId()));
        assertNull(process.getTaskService().createTaskQuery().processInstanceId(root.getInstanceId()).singleResult());
        assertNotNull(process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root.getInstanceId())
                .singleResult().getEndTime());
    }

    private static void assertWaitsBeforeNativeCommand(DatabaseVO database, TransactionTemplate transaction,
            java.util.concurrent.ExecutorService pool, NativeEntryProbe probe, String instanceId, String command, Runnable operation)
            throws Exception {
        var reached = new CountDownLatch(1);
        var result = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        probe.observe(command, reached);
        transaction.executeWithoutResult(status -> {
            var source = ((DataSourceTransactionManager) transaction.getTransactionManager()).getDataSource();
            assertEquals(instanceId, new JdbcTemplate(source).queryForObject("""
                    SELECT instance_id FROM tianji_process.tianji_process_instance_binding WHERE tenant_id=? AND instance_id=? FOR UPDATE
                    """, String.class, OWNER.tenantId(), instanceId));
            result.set(pool.submit(operation));
            waitForNativeOrLock(database, reached);
            assertEquals(1L, reached.getCount(), "A V2 native command reached Flowable mutation before the root guard");
        });
        result.get().get(10, TimeUnit.SECONDS);
        assertEquals(0L, reached.getCount(), "The actual native command must advance once the root lock releases");
        probe.stop();
    }

    private static void waitForNativeOrLock(DatabaseVO database, CountDownLatch nativeEntry) {
        Instant until = Instant.now().plusSeconds(6);
        while (nativeEntry.getCount() == 1L && Instant.now().isBefore(until)) {
            if (Boolean.TRUE.equals(database.owner().queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_locks WHERE NOT granted AND locktype='transactionid'
                      AND pid IN (SELECT pid FROM pg_stat_activity WHERE datname=current_database()))
                    """, Boolean.class))) return;
            try {
                Thread.sleep(15);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
        assertEquals(0L, nativeEntry.getCount(), "Neither the actual native command nor its database lock was reached");
    }

    private static AutomationStartCommand start(String commandId) {
        return new AutomationStartCommand(DEFINITION, "order-42", commandId, "correlation-42", Map.of("amount", "1.25"), Map.of());
    }

    private static void publish(ProcessDraftService drafts, String version) {
        var draft = drafts.create(OWNER, DEFINITION, new CreateProcessDraftCommand("Review", null, "draft-" + version));
        var content = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", DEFINITION, XML)), INPUT, EMPTY, "[]", "[]");
        var ready = drafts.save(OWNER, draft.getDraftId(), new SaveProcessDraftCommand("save-" + version, 1, "Review", content));
        var request = new PublishProcessDraftCommand(ready.getDraftId(), ready.getRevision(), ready.getContentSha256(), version, "publish-" + version);
        assertEquals(version, drafts.publish(OWNER, request, Instant.now().plusSeconds(60)).releaseVersion());
    }

    private static void publishHuman(ProcessDraftService drafts) {
        String xml = XML.replace("id=\"review\"", "id=\"approval\"").replace("<endEvent id=\"end\"/>", """
                <userTask id="approve"/>
                <endEvent id="end"/>
                <sequenceFlow id="approved" sourceRef="approve" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"approve\"");
        var empty = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(), "required", List.of(),
                "additionalProperties", false));
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30),
                end, end, end, new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var result = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("resultRef", Map.of("type", "string")),
                "required", List.of("resultRef"), "additionalProperties", false));
        var human = new AutomationNodeBindingVO("approve", "HUMAN", empty, result, List.of(),
                List.of(new AutomationNodeBindingVO.MappingVO("resultRef", "nodes.approve.output.resultRef")), policy,
                null, null, null, new AutomationNodeBindingVO.HumanBindingVO("approvers", result, Duration.ofMinutes(1)), null, null);
        var model = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "approval", xml)), empty, result, List.of(human), List.of());
        var draft = drafts.create(OWNER, "approval", new CreateProcessDraftCommand("Approval", null, "draft-human"));
        var saved = drafts.save(OWNER, draft.getDraftId(),
                new SaveProcessDraftCommand("save-human", 1, "Approval", AutomationReleaseStore.copyContent(model)));
        assertEquals("human-v2", drafts.publish(OWNER, new PublishProcessDraftCommand(saved.getDraftId(), saved.getRevision(),
                saved.getContentSha256(), "human-v2", "publish-human"), Instant.now().plusSeconds(60)).releaseVersion());
    }

    private static ComponentsVO components(ConfigurableApplicationContext context, MockEnvironment environment, FixtureAuthority fixture) {
        var process = context.getBean(SpringProcessEngineConfiguration.class);
        var dmn = context.getBean(SpringDmnEngineConfiguration.class);
        process.setBeans(Map.of());
        dmn.setBeans(Map.of());
        var manager = (DataSourceTransactionManager) process.getTransactionManager();
        var source = manager.getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var releases = new ProcessReleaseService(process, dmn, new ProcessModelValidator());
        var sealed = new AutomationReleaseStore(jdbc, SCHEMA);
        var ver = new ProcessApiSchemaVerifier(source, SCHEMA);
        var activations = new AutomationActivationStore(jdbc, SCHEMA);
        var provider = new StaticListableBeanFactory(Map.of("admission", (AutomationAdmissionAuthority) fixture::proof,
                "trust", (AutomationReleaseTrustVerifier.TrustAuthority) fixture::signed));
        var permission = provider.getBeanProvider(AutomationAdmissionAuthority.class);
        var trust = provider.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
        var drafts = new ProcessDraftService(new ProcessDraftStore(jdbc, SCHEMA), new ProcessDraftCommandReceiptStore(jdbc, SCHEMA),
                new ProcessDraftContentCodec(), Mappers.getMapper(ProcessDraftConverter.class), ver, transaction, environment, releases,
                sealed, new AutomationModelValidator(), new AutomationModelCompiler(),
                new AutomationReleaseStore.DependencyResolver() {
                    @Override
                    public AutomationModelValidator.DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO model) {
                        return new AutomationModelValidator.DependencySnapshotVO(actor.tenantId(), Map.of(), Map.of(), Set.of(),
                                Map.of("approvers", "a".repeat(64)), Map.of());
                    }

                    @Override
                    public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO model,
                                             AutomationModelValidator.DependencySnapshotVO observed) {
                        return actor.tenantId() == observed.tenantId();
                    }
                });
        var activation = new AutomationActivationService(activations, sealed, releases, Mappers.getMapper(AutomationActivationConverter.class),
                ver, transaction, environment, permission, trust);
        var bindingStore = new ProcessInstanceBindingStore(jdbc, SCHEMA);
        var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activations, sealed, bindingStore,
                Mappers.getMapper(AutomationInstanceConverter.class), ver, environment, permission, trust);
        var execution = new ProcessExecutionService(process, dmn, new ProcessModelValidator(), new GovernedStartPilotProperties(List.of()), null, dependencies);
        fixture.releases = sealed;
        return new ComponentsVO(drafts, activation, execution);
    }

    private record ComponentsVO(ProcessDraftService drafts, AutomationActivationService activation, ProcessExecutionService execution) { }
    private record DatabaseVO(JdbcTemplate owner, String url, String role) { }

    private static final class FixtureAuthority {
        private final JdbcTemplate observer;
        private final java.security.KeyPair key;
        private final Instant keyIssued = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(30);
        private AutomationReleaseStore releases;

        private FixtureAuthority(JdbcTemplate observer) throws Exception {
            this.observer = observer;
            key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }

        private AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            Instant now = observer.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
            if (query.getPurpose() == AutomationAdmissionQuery.Purpose.REPLAY_START) {
                return new AutomationAdmissionProofVO(query.sha256(), "replay-permit", "a".repeat(64), now, now.plusSeconds(25),
                        1L, "b".repeat(64), "c".repeat(64), "d".repeat(64), Map.of());
            }
            var material = signed(query, releases.find(query.getActor().tenantId(), query.getReleaseVersion()).orElseThrow());
            return new AutomationAdmissionProofVO(query.sha256(), "start-permit", "a".repeat(64), now, now.plusSeconds(25),
                    material.currentGeneration(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of());
        }

        private AutomationReleaseTrustVerifier.TrustObservationVO signed(
                AutomationAdmissionQuery query, AutomationReleaseStore.ImmutableReleaseVO release) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try {
                byte[] encoded = key.getPublic().getEncoded();
                String keyId = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded));
                Instant expires = keyIssued.plusSeconds(300);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, release.tenantId(), 1,
                        Base64.getEncoder().encodeToString(encoded), keyIssued.minusSeconds(1), expires);
                var signer = Signature.getInstance("Ed25519");
                signer.initSign(key.getPrivate());
                signer.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, keyIssued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, keyIssued, expires,
                        Base64.getEncoder().encodeToString(signer.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (java.security.GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static DatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Automation concurrency requires an empty loopback database with a dedicated role");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        ownerSource.setConnectionProperties(properties);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT current_database() LIKE 'tianji_it_%' AND NOT pg_is_in_recovery()
                   AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                """, Boolean.class)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb
                    AND NOT rolreplication AND NOT rolbypassrls
                    AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                    AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        assertEquals(0, owner.queryForObject("""
                SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=?)
                """, Integer.class, role));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        var flyway = Flyway.configure().dataSource(ownerSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target("14").cleanDisabled(true).load();
        assertEquals(14, flyway.migrate().migrationsExecuted);
        flyway.validate();
        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = context(scoped, System.getProperty("user.name"), true)) {
            assertNotNull(installing.getBean(org.flowable.engine.RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation", "tianji_case_start_provenance");
        grant(owner, quoted, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution", "tianji_process_agent_dispatch");
        grant(owner, quoted, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding",
                "tianji_human_task_receipt", "tianji_process_cancel_receipt");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(nativeTables.contains("act_re_deployment"));
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(owner, quoted, "SELECT,INSERT,UPDATE,DELETE", table);
        }
        return new DatabaseVO(owner, scoped, role);
    }

    private static void grant(JdbcTemplate jdbc, String role, String privilege, String... names) {
        for (String name : names) jdbc.execute("GRANT " + privilege + " ON " + SCHEMA + "." + name + " TO " + role);
    }

    private static ConfigurableApplicationContext context(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false");
    }

    static final class NativeEntryProbe extends AbstractCommandInterceptor {
        private volatile String expected;
        private volatile CountDownLatch entered;
        private volatile CountDownLatch proceed;

        void observe(String command, CountDownLatch reached) {
            observe(command, reached, null);
        }

        void observe(String command, CountDownLatch reached, CountDownLatch continueNative) {
            entered = reached;
            proceed = continueNative;
            expected = command;
        }

        void stop() {
            expected = null;
            entered = null;
            proceed = null;
        }

        @Override
        public <T> T execute(CommandConfig config, Command<T> command, CommandExecutor executor) {
            if (command.getClass().getSimpleName().equals(expected)) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                entered.countDown();
                if (proceed != null) {
                    try {
                        assertTrue(proceed.await(8, TimeUnit.SECONDS), "Native fixture command was not released");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
            }
            return next.execute(config, command, executor);
        }
    }

    static final class NativeWiring {
        final java.util.concurrent.atomic.AtomicBoolean rejectFixtureCandidate = new java.util.concurrent.atomic.AtomicBoolean();
        ProcessInstanceBindingStore bindings;
        AutomationReleaseStore releases;
        AutomationInstanceGuardCommandInterceptor guard;
        ProcessNodeExecutionStore nodes;
        ProcessNodeLifecycleListener lifecycle;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestConfiguration {
        @Bean
        NativeEntryProbe nativeEntryProbe() {
            return new NativeEntryProbe();
        }

        @Bean
        NativeWiring nativeWiring() {
            return new NativeWiring();
        }

        @Bean
        org.flowable.spring.boot.EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bootstrap(NativeEntryProbe probe,
                                                                                              NativeWiring wiring) {
            return config -> {
                var source = ((DataSourceTransactionManager) config.getTransactionManager()).getDataSource();
                var jdbc = new JdbcTemplate(source);
                var bindings = new ProcessInstanceBindingStore(jdbc, SCHEMA);
                var releaseStore = new AutomationReleaseStore(jdbc, SCHEMA);
                AutomationBootstrapBehavior.register(config, () -> new AutomationBootstrapBehavior(bindings, releaseStore));
                var fixtureDirectory = new StaticListableBeanFactory(Map.of("automationHumanCandidateDirectory",
                        (ProcessNodeLifecycleListener.HumanCandidateDirectory) (tenant, ref, hash) ->
                                !wiring.rejectFixtureCandidate.get() && tenant == OWNER.tenantId()
                                        && "approvers".equals(ref) && "a".repeat(64).equals(hash)
                                        ? Optional.of(new ProcessNodeLifecycleListener.HumanCandidatePolicyVO(Set.of("901"), hash))
                                        : Optional.empty()));
                var nodeStore = new ProcessNodeExecutionStore(jdbc, SCHEMA);
                wiring.bindings = bindings;
                wiring.releases = releaseStore;
                wiring.nodes = nodeStore;
                wiring.guard = new AutomationInstanceGuardCommandInterceptor(jdbc, SCHEMA, bindings);
                wiring.lifecycle = new ProcessNodeLifecycleListener(bindings, nodeStore, releaseStore,
                        fixtureDirectory.getBeanProvider(ProcessNodeLifecycleListener.HumanCandidateDirectory.class));
                ProcessNodeLifecycleListener.register(config, () -> wiring.lifecycle);
                config.setCustomPostCommandInterceptors(List.of(wiring.guard, probe));
                AutomationInstanceGuardCommandInterceptor.registerFailedJobHandling(config);
            };
        }
    }
}
