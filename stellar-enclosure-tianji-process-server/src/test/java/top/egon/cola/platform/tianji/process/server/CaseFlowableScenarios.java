package top.egon.cola.platform.tianji.process.server;

import org.flowable.cmmn.engine.impl.agenda.operation.TerminateCaseInstanceOperation;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionCorrelationService;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionPoller;
import top.egon.cola.platform.tianji.process.server.bridge.CaseSourceProperties;
import top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableCaseWaitInspector;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.CasePlanItemReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Actual native/SQL scenarios, called only by FormalCasePostgresTest. Synthetic IAM inputs do not prove real IAM acceptance. */
final class CaseFlowableScenarios {
    private static final String SCHEMA = "tianji_process", LINKED_RELEASE = "formal-linked-release";
    private static final ProcessActor OWNER = actor("case-owner"), OTHER = actor("case-other");
    private static final URI SERVICE_ISSUER = URI.create("https://service.example/20"), RESOURCE = URI.create("https://process.example/api/tianji");
    private static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "case-native-service", SERVICE_ISSUER.toASCIIString());
    private static final Set<String> ROLES = Set.of("7", "8", "9");
    private CaseFlowableScenarios() { }

    static void verify(ConfigurableApplicationContext app, JdbcTemplate bootstrap) throws Exception {
        var h = new NativeCases(app, bootstrap);
        h.releases.publish(OWNER, "formal-direct-release", List.of(new ProcessReleaseService.ModelResource(Type.CMMN, "governedCase", bytes("governed-case.cmmn"), Map.of())));
        lifecycle(h, "complete", false); lifecycle(h, "terminate-plan-item", true);
        namespacesAndRollback(h); competingPlanAndTask(h);
        h.releases.publish(OWNER, LINKED_RELEASE, List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "linkedProcess", bytes("formal-case-linked.bpmn20.xml"), Map.of()),
                new ProcessReleaseService.ModelResource(Type.CMMN, "governedCase", bytes("governed-case.cmmn"), Map.of())));
        linkedNormalAndRollback(h); parentTimerWinsWhileChildActive(h); wholeCaseTermination(h); forcedCompletionWithoutPositiveTraces(h); withdrawnSource(h);
    }

    private static void lifecycle(NativeCases h, String suffix, boolean terminateSupplement) throws Exception {
        var start = h.startCommand(suffix); var active = h.cases.start(h.context("start", OWNER, Set.of()), start);
        var primary = item(active, "primaryTask"); assertEquals(CasePlanItemStateEnum.ENABLED, primary.state());
        var disabled = h.act(active.caseInstanceId(), primary, "disable-" + suffix, CasePlanItemActionEnum.DISABLE);
        assertEquals(CasePlanItemStateEnum.DISABLED, disabled.resultingState());
        var enabled = h.act(active.caseInstanceId(), h.item(active.caseInstanceId(), "primaryTask"), "enable-" + suffix, CasePlanItemActionEnum.ENABLE);
        assertNotEquals(primary.version(), enabled.resultingVersion());
        assertThrows(ProcessConflictException.class, () -> h.cases.actOnPlanItem(h.context("actPlanItem", OWNER, ROLES),
                new ActOnPlanItemCommand("stale-" + suffix, active.caseInstanceId(), primary.planItemInstanceId(), primary.version(), CasePlanItemActionEnum.START)));
        h.startItem(active.caseInstanceId(), "primaryTask", "primary-start-" + suffix);
        String taskId = h.taskId(active.caseInstanceId(), "primaryTask");
        assertEquals(Set.of("role:7", "role:8"), h.cmmn.getCmmnTaskService().getIdentityLinksForTask(taskId).stream()
                .filter(link -> "candidate".equals(link.getType())).map(link -> link.getGroupId()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var before = h.tasks.get(h.taskContext("get", OWNER, ROLES), new GetHumanTaskQuery(taskId));
        h.tasks.claim(h.taskContext("claim", OWNER, Set.of("7")), new ClaimHumanTaskCommand("primary-claim-" + suffix, taskId, before.version()));
        var claimed = h.tasks.get(h.taskContext("get", OWNER, ROLES), new GetHumanTaskQuery(taskId));
        var complete = new CompleteHumanTaskCommand("primary-complete-" + suffix, taskId, claimed.version(), Map.of());
        var receipt = h.tasks.complete(h.taskContext("complete", OWNER, Set.of("7")), complete);
        assertEquals(receipt, h.tasks.complete(h.taskContext("complete", OWNER, Set.of("8")), complete));
        assertThrows(ProcessResourceNotFoundException.class, () -> h.tasks.complete(h.taskContext("complete", OWNER, Set.of()), complete));
        var expired = new top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext(OWNER, "tianji.api.process.task.complete", Set.of("7"), h.now().minusSeconds(1), 2, 3);
        assertThrows(IllegalStateException.class, () -> h.tasks.complete(expired, complete));
        assertEquals(CasePlanItemStateEnum.ENABLED, h.item(active.caseInstanceId(), "supplementalTask").state());
        h.startItem(active.caseInstanceId(), "supplementalTask", "supplement-start-" + suffix);
        if (terminateSupplement) {
            var result = h.act(active.caseInstanceId(), h.item(active.caseInstanceId(), "supplementalTask"), "supplement-terminate-" + suffix, CasePlanItemActionEnum.TERMINATE);
            assertEquals(CasePlanItemStateEnum.TERMINATED, result.resultingState());
            assertNotEquals(result.previousVersion(), result.resultingVersion());
            assertThrows(RuntimeException.class, () -> h.cases.actOnPlanItem(h.context("actPlanItem", OWNER, ROLES),
                    new ActOnPlanItemCommand("history-is-not-runtime-" + suffix, active.caseInstanceId(), result.planItemInstanceId(), result.resultingVersion(), CasePlanItemActionEnum.START)));
        } else h.completeTask(active.caseInstanceId(), "supplementalTask", "supplement-" + suffix, OWNER, Set.of("8"));
        var finished = h.get(active.caseInstanceId()); assertEquals(CaseInstanceStatusEnum.COMPLETED, finished.status()); assertNotNull(finished.finishedAt());
        assertTrue(finished.planItems().stream().allMatch(value -> value.availableActions().isEmpty()));
        assertEquals("completed", h.cmmn.getCmmnHistoryService().createHistoricCaseInstanceQuery().caseInstanceId(active.caseInstanceId()).singleResult().getState());
        assertEquals(1, h.control.queryForObject("SELECT count(*) FROM tianji_process.act_cmmn_hi_mil_inst WHERE case_inst_id_=? AND element_id_='milestone'", Integer.class, active.caseInstanceId()));
        var stopped = new FlowableCaseService(h.process, h.cmmn, h.dmn, new ProcessModelValidator(), h.provenance, h.planReceipts, false);
        assertEquals(finished, stopped.get(h.context("get", OWNER, ROLES), new CaseInstanceQuery(active.caseInstanceId())));
        assertEquals(finished, stopped.start(h.context("start", OWNER, Set.of()), start));
        assertEquals(disabled, stopped.actOnPlanItem(h.context("actPlanItem", OWNER, Set.of("8")), new ActOnPlanItemCommand(disabled.commandId(), disabled.caseInstanceId(),
                disabled.planItemInstanceId(), disabled.previousVersion(), disabled.action())));
        assertThrows(IllegalStateException.class, () -> stopped.start(h.context("start", OWNER, Set.of()), h.startCommand("stopped-" + suffix)));
    }

    private static void namespacesAndRollback(NativeCases h) throws Exception {
        var first = h.cases.start(h.context("start", OWNER, ROLES), h.startCommand("affinity-one"));
        var second = h.cases.start(h.context("start", OWNER, ROLES), h.startCommand("affinity-two"));
        h.startItem(first.caseInstanceId(), "primaryTask", "affinity-start-one"); h.startItem(second.caseInstanceId(), "primaryTask", "affinity-start-two");
        String task = h.taskId(first.caseInstanceId(), "primaryTask");
        var nativeTask = h.cmmn.getCmmnTaskService().createTaskQuery().taskId(task).singleResult(); String correct = nativeTask.getSubScopeId();
        String other = item(second, "primaryTask").planItemInstanceId();
        h.control.update("UPDATE tianji_process.act_ru_task SET sub_scope_id_=? WHERE id_=?", other, task);
        try { assertThrows(ProcessResourceNotFoundException.class, () -> h.tasks.get(h.taskContext("get", OWNER, ROLES), new GetHumanTaskQuery(task))); }
        finally { h.control.update("UPDATE tianji_process.act_ru_task SET sub_scope_id_=? WHERE id_=?", correct, task); }
        assertThrows(ProcessResourceNotFoundException.class, () -> h.cases.actOnPlanItem(h.context("actPlanItem", OWNER, ROLES),
                new ActOnPlanItemCommand("foreign-pi", first.caseInstanceId(), other, item(second, "primaryTask").version(), CasePlanItemActionEnum.DISABLE)));
        var foreign = ProcessActor.verified(21, ProcessActor.SubjectType.USER, OWNER.subjectId(), "https://idp.example/21");
        assertThrows(ProcessResourceNotFoundException.class, () -> h.cases.get(h.context("get", foreign, ROLES), new CaseInstanceQuery(first.caseInstanceId())));
        var realm = ProcessActor.verified(20, ProcessActor.SubjectType.USER, OWNER.subjectId(), "https://other.example/20");
        assertThrows(ProcessResourceNotFoundException.class, () -> h.cases.get(h.context("get", realm, ROLES), new CaseInstanceQuery(first.caseInstanceId())));
        var service = h.cases.start(h.context("start", SERVICE, Set.of()), h.startCommand("service-owner"));
        assertEquals(service.caseInstanceId(), h.cases.get(h.context("get", SERVICE, Set.of()), new CaseInstanceQuery(service.caseInstanceId())).caseInstanceId());
        assertThrows(ProcessResourceNotFoundException.class, () -> h.cases.get(h.context("get", OTHER, ROLES), new CaseInstanceQuery(service.caseInstanceId())));
        var rollback = h.cases.start(h.context("start", OWNER, ROLES), h.startCommand("rollback")); var primary = item(rollback, "primaryTask");
        h.control.execute("CREATE FUNCTION tianji_process.formal_case_action_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.command_id='formal-action-rollback' THEN RAISE EXCEPTION 'test action rollback'; END IF; RETURN NEW; END $$");
        h.control.execute("CREATE TRIGGER formal_case_action_failure BEFORE INSERT ON tianji_process.tianji_case_plan_item_receipt FOR EACH ROW EXECUTE FUNCTION tianji_process.formal_case_action_failure()");
        try {
            assertThrows(RuntimeException.class, () -> h.cases.actOnPlanItem(h.context("actPlanItem", OWNER, ROLES), new ActOnPlanItemCommand("formal-action-rollback", rollback.caseInstanceId(), primary.planItemInstanceId(), primary.version(), CasePlanItemActionEnum.DISABLE)));
            assertEquals(primary.version(), h.item(rollback.caseInstanceId(), "primaryTask").version());
            assertEquals(0, h.control.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_id='formal-action-rollback'", Integer.class));
        } finally { h.control.execute("DROP TRIGGER formal_case_action_failure ON tianji_process.tianji_case_plan_item_receipt"); h.control.execute("DROP FUNCTION tianji_process.formal_case_action_failure()"); }
    }

    private static void competingPlanAndTask(NativeCases h) throws Exception {
        var created = h.cases.start(h.context("start", OWNER, ROLES), h.startCommand("competing")); h.finishPrimary(created.caseInstanceId(), "competing");
        h.startItem(created.caseInstanceId(), "supplementalTask", "competing-supplement");
        String task = h.taskId(created.caseInstanceId(), "supplementalTask"); var view = h.tasks.get(h.taskContext("get", OTHER, Set.of("8")), new GetHumanTaskQuery(task));
        h.tasks.claim(h.taskContext("claim", OTHER, Set.of("8")), new ClaimHumanTaskCommand("competing-other-claim", task, view.version()));
        assertThrows(ProcessResourceNotFoundException.class, () -> h.act(created.caseInstanceId(), h.item(created.caseInstanceId(), "supplementalTask"), "cannot-cancel-other", CasePlanItemActionEnum.TERMINATE));
        var claimed = h.tasks.get(h.taskContext("get", OTHER, Set.of("8")), new GetHumanTaskQuery(task));
        h.tasks.unclaim(h.taskContext("unclaim", OTHER, Set.of()), new top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand("competing-unclaim", task, claimed.version()));
        var active = h.item(created.caseInstanceId(), "supplementalTask");
        var gate = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var terminate = pool.submit(() -> { gate.await(); return accepted(() -> h.act(created.caseInstanceId(), active, "competing-terminate", CasePlanItemActionEnum.TERMINATE)); });
            var complete = pool.submit(() -> { gate.await(); return accepted(() -> h.completeTask(created.caseInstanceId(), "supplementalTask", "competing-complete", OWNER, Set.of("8"))); });
            gate.countDown(); boolean first = terminate.get(15, TimeUnit.SECONDS), second = complete.get(15, TimeUnit.SECONDS); assertTrue(first || second);
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
        assertEquals(CaseInstanceStatusEnum.COMPLETED, h.get(created.caseInstanceId()).status());
    }

    private static void linkedNormalAndRollback(NativeCases h) throws Exception {
        var linked = h.linked("normal"); var source = linked.origin().seed();
        assertEquals(OWNER, source.owner()); assertEquals(SERVICE, source.executor()); assertEquals(LINKED_RELEASE, source.releaseKey());
        assertEquals("linked-business-normal", source.businessKey()); assertEquals("linked-correlation-normal", source.correlationId());
        var historical = h.process.getHistoryService().createHistoricActivityInstanceQuery().activityInstanceId(source.sourceOccurrenceId()).singleResult();
        assertNotNull(historical); assertNotNull(historical.getEndTime()); assertNull(historical.getDeleteReason()); assertEquals("caseSource", historical.getActivityId());
        String waitExecution = h.control.queryForObject("SELECT execution_id_ FROM tianji_process.act_ru_event_subscr WHERE proc_inst_id_=? AND event_type_='message' AND event_name_='TianjiCaseCompletedV1'", String.class, linked.parentId());
        assertNotEquals(source.sourceOccurrenceId(), waitExecution);
        assertThrows(ProcessResourceNotFoundException.class, () -> h.cases.get(h.context("get", SERVICE, Set.of()), new CaseInstanceQuery(source.caseInstanceId())));
        h.finishCase(source.caseInstanceId(), "linked-normal");
        h.control.execute("CREATE FUNCTION tianji_process.formal_case_message_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state='APPLIED' THEN RAISE EXCEPTION 'test Message rollback'; END IF; RETURN NEW; END $$");
        h.control.execute("CREATE TRIGGER formal_case_message_failure BEFORE UPDATE ON tianji_process.tianji_case_completion_inbox FOR EACH ROW EXECUTE FUNCTION tianji_process.formal_case_message_failure()");
        try {
            assertThrows(IllegalStateException.class, () -> h.completions.apply(h.registration, linked.key()));
            assertEquals(1, h.process.getRuntimeService().createProcessInstanceQuery().processInstanceId(linked.parentId()).count());
            assertEquals("PENDING", h.inboxState(linked.key()));
            assertTrue(h.control.queryForObject("SELECT failure_count FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", Integer.class, linked.key()) > 0);
            assertEquals(0, h.control.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_type='CASE_COMPLETION_SERVICE' AND command_id=?", Integer.class, linked.key()));
        } finally { h.control.execute("DROP TRIGGER formal_case_message_failure ON tianji_process.tianji_case_completion_inbox"); h.control.execute("DROP FUNCTION tianji_process.formal_case_message_failure()"); }
        h.completions.apply(h.registration, linked.key()); h.completions.apply(h.registration, linked.key());
        assertEquals("APPLIED", h.inboxState(linked.key()));
        assertEquals(waitExecution, h.control.queryForObject("SELECT resource_id FROM tianji_process.tianji_process_command_receipt WHERE command_type='CASE_COMPLETION_SERVICE' AND command_id=?", String.class, linked.key()));
        assertEquals(1, h.control.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_type='CASE_COMPLETION_SERVICE' AND command_id=?", Integer.class, linked.key()));
        assertEquals("caseCompleted", h.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(linked.parentId()).singleResult().getEndActivityId());
    }

    private static void parentTimerWinsWhileChildActive(NativeCases h) throws Exception {
        var linked = h.linked("timer"); String caseId = linked.origin().seed().caseInstanceId();
        assertEquals(CaseInstanceStatusEnum.ACTIVE, h.get(caseId).status());
        var management = h.process.getManagementService(); var timer = management.createTimerJobQuery().processInstanceId(linked.parentId()).singleResult(); assertNotNull(timer);
        management.moveTimerToExecutableJob(timer.getId()); management.executeJob(timer.getId());
        h.completions.apply(h.registration, linked.key()); assertEquals("IGNORED_LATE", h.inboxState(linked.key()));
        assertNull(h.control.queryForObject("SELECT terminal_status FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", String.class, linked.key()));
        assertEquals(CaseInstanceStatusEnum.ACTIVE, h.get(caseId).status()); h.finishCase(caseId, "late"); h.completions.apply(h.registration, linked.key());
        assertEquals("caseTimeout", h.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(linked.parentId()).singleResult().getEndActivityId());
        assertEquals("IGNORED_LATE", h.inboxState(linked.key()));
    }

    private static void wholeCaseTermination(NativeCases h) throws Exception {
        var linked = h.linked("terminated"); h.cmmn.getCmmnRuntimeService().terminateCaseInstance(linked.origin().seed().caseInstanceId());
        h.completions.apply(h.registration, linked.key()); assertEquals("DEAD", h.inboxState(linked.key()));
        assertEquals("CASE_TERMINATED", h.control.queryForObject("SELECT decision_code FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", String.class, linked.key()));
        assertEquals(1, h.process.getRuntimeService().createProcessInstanceQuery().processInstanceId(linked.parentId()).count());
        assertEquals(0, h.control.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_type='CASE_COMPLETION_SERVICE' AND command_id=?", Integer.class, linked.key()));
    }

    private static void forcedCompletionWithoutPositiveTraces(NativeCases h) throws Exception {
        var linked = h.linked("force-complete"); String caseId = linked.origin().seed().caseInstanceId();
        h.cmmn.getCommandExecutor().execute(context -> { new TerminateCaseInstanceOperation(context, caseId, true, null, null, "forceComplete").run(); return null; });
        assertEquals("completed", h.cmmn.getCmmnHistoryService().createHistoricCaseInstanceQuery().caseInstanceId(caseId).singleResult().getState());
        h.completions.apply(h.registration, linked.key()); assertEquals("DEAD", h.inboxState(linked.key()));
        assertEquals("INCONSISTENT_NATIVE_FACT", h.control.queryForObject("SELECT decision_code FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", String.class, linked.key()));
        assertEquals(1, h.process.getRuntimeService().createProcessInstanceQuery().processInstanceId(linked.parentId()).count());
    }

    private static void withdrawnSource(NativeCases h) throws Exception {
        var linked = h.linked("withdrawn");
        var authority = h.app.getBean(LocalIdentity.class); when(authority.issuers().findByIssuer(SERVICE_ISSUER.toASCIIString())).thenReturn(Optional.empty());
        assertTrue(h.registry.activeDueKeys().isEmpty()); h.completions.reconcileOrphans(h.registry.activeDueKeys(), 32);
        assertEquals("DEAD", h.inboxState(linked.key()));
        assertEquals("SOURCE_UNAVAILABLE", h.control.queryForObject("SELECT decision_code FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", String.class, linked.key()));
    }

    private static boolean accepted(Callable<?> action) throws Exception { try { action.call(); return true; } catch (ProcessConflictException | ProcessResourceNotFoundException rejected) { return false; } }
    private static CasePlanItemDTO item(CaseInstanceDTO value, String definition) { return value.planItems().stream().filter(item -> definition.equals(item.planItemDefinitionKey())).findFirst().orElseThrow(); }
    private static ProcessActor actor(String name) { return ProcessActor.verified(20, ProcessActor.SubjectType.USER, name, "https://idp.example/20"); }
    private static byte[] bytes(String name) throws Exception { return new ClassPathResource("fixtures/" + name).getContentAsByteArray(); }
    private static String hash(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }

    private record Linked(String parentId, CaseStartProvenanceStore.Entry origin, String key) { }
    private static final class NativeCases {
        final ConfigurableApplicationContext app; final JdbcTemplate control; final SpringProcessEngineConfiguration process; final SpringCmmnEngineConfiguration cmmn; final SpringDmnEngineConfiguration dmn;
        final FlowableCaseService cases; final FlowableHumanTaskService tasks; final ProcessReleaseService releases; final CaseStartProvenanceStore provenance; final CasePlanItemReceiptStore planReceipts;
        final CaseCompletionCorrelationService completions; final CaseSourceRegistry registry; final CaseSourceRegistry.Registration registration;
        NativeCases(ConfigurableApplicationContext app, JdbcTemplate control) {
            this.app = app; this.control = control; process = app.getBean(SpringProcessEngineConfiguration.class); cmmn = app.getBean(SpringCmmnEngineConfiguration.class); dmn = app.getBean(SpringDmnEngineConfiguration.class);
            cases = app.getBean(FlowableCaseService.class); tasks = app.getBean(FlowableHumanTaskService.class); releases = app.getBean(ProcessReleaseService.class);
            provenance = app.getBean(CaseStartProvenanceStore.class); planReceipts = app.getBean(CasePlanItemReceiptStore.class); completions = app.getBean(CaseCompletionCorrelationService.class);
            registry = app.getBean(CaseSourceRegistry.class); registration = registry.registrations().getFirst();
            assertTrue(app.getBean(CaseCompletionPoller.class).isRunning());
        }
        java.time.Instant now() { return control.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
        CaseContext context(String operation, ProcessActor actor, Set<String> roles) { return new CaseContext(actor, "tianji.api.process.case." + operation, roles, now().plusSeconds(30), 2, 3); }
        top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext taskContext(String operation, ProcessActor actor, Set<String> roles) { return HumanTaskFlowableScenarios.context(operation, actor, roles, control); }
        StartCaseCommand startCommand(String suffix) { return new StartCaseCommand("governedCase", "formal-direct-release", "case-business-" + suffix, "case-start-" + suffix, "case-correlation-" + suffix); }
        CaseInstanceDTO get(String id) { return cases.get(context("get", OWNER, ROLES), new CaseInstanceQuery(id)); }
        CasePlanItemDTO item(String id, String definition) { return CaseFlowableScenarios.item(get(id), definition); }
        top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult act(String id, CasePlanItemDTO item, String command, CasePlanItemActionEnum action) { return cases.actOnPlanItem(context("actPlanItem", OWNER, ROLES), new ActOnPlanItemCommand(command, id, item.planItemInstanceId(), item.version(), action)); }
        void startItem(String id, String definition, String command) { act(id, item(id, definition), command, CasePlanItemActionEnum.START); }
        String taskId(String caseId, String definition) { return control.queryForObject("SELECT id_ FROM tianji_process.act_ru_task WHERE scope_type_='cmmn' AND scope_id_=? AND task_def_key_=?", String.class, caseId, definition); }
        Object completeTask(String caseId, String definition, String suffix, ProcessActor actor, Set<String> roles) {
            String id = taskId(caseId, definition); var task = tasks.get(taskContext("get", actor, roles), new GetHumanTaskQuery(id));
            tasks.claim(taskContext("claim", actor, roles), new ClaimHumanTaskCommand("claim-" + suffix, id, task.version()));
            var claimed = tasks.get(taskContext("get", actor, roles), new GetHumanTaskQuery(id));
            return tasks.complete(taskContext("complete", actor, roles), new CompleteHumanTaskCommand("complete-" + suffix, id, claimed.version(), Map.of()));
        }
        void finishPrimary(String caseId, String suffix) { startItem(caseId, "primaryTask", "start-primary-" + suffix); completeTask(caseId, "primaryTask", "primary-" + suffix, OWNER, Set.of("7")); assertEquals(CasePlanItemStateEnum.ENABLED, item(caseId, "supplementalTask").state()); }
        void finishCase(String caseId, String suffix) { finishPrimary(caseId, suffix); startItem(caseId, "supplementalTask", "start-supplement-" + suffix); completeTask(caseId, "supplementalTask", "supplement-" + suffix, OWNER, Set.of("8")); }
        Linked linked(String suffix) throws Exception {
            var runs = new ProcessExecutionService(process, dmn, new ProcessModelValidator());
            var run = runs.start(OWNER, new ProcessExecutionService.StartCommand(LINKED_RELEASE, "linkedProcess", "linked-business-" + suffix, "linked-start-" + suffix, "linked-correlation-" + suffix, Map.of()));
            var task = process.getTaskService().createTaskQuery().processInstanceId(run.instanceId()).singleResult(); assertNotNull(task);
            var before = tasks.get(taskContext("get", OWNER, Set.of("7")), new GetHumanTaskQuery(task.getId()));
            tasks.claim(taskContext("claim", OWNER, Set.of("7")), new ClaimHumanTaskCommand("linked-prior-claim-" + suffix, task.getId(), before.version()));
            var claimed = tasks.get(taskContext("get", OWNER, Set.of("7")), new GetHumanTaskQuery(task.getId()));
            tasks.complete(taskContext("complete", OWNER, Set.of("7")), new CompleteHumanTaskCommand("linked-prior-complete-" + suffix, task.getId(), claimed.version(), Map.of()));
            String id = control.queryForObject("SELECT case_instance_id FROM tianji_process.tianji_case_start_provenance WHERE parent_process_instance_id=?", String.class, run.instanceId());
            var tx = new org.springframework.transaction.support.TransactionTemplate((DataSourceTransactionManager) process.getTransactionManager());
            var origin = tx.execute(status -> provenance.find(20, id).orElseThrow());
            String key = control.queryForObject("SELECT completion_command_id FROM tianji_process.tianji_case_completion_inbox WHERE case_instance_id=?", String.class, id);
            assertEquals(1, control.queryForObject("SELECT count(*) FROM tianji_process.tianji_case_start_provenance WHERE parent_process_instance_id=?", Integer.class, run.instanceId()));
            return new Linked(run.instanceId(), origin, key);
        }
        String inboxState(String id) { return control.queryForObject("SELECT state FROM tianji_process.tianji_case_completion_inbox WHERE completion_command_id=?", String.class, id); }
    }

    /** Native engines and adapters are real; only local IAM metadata is synthetic. Global HTTP APIs stay off and no server is opened. */
    @TestConfiguration(proxyBeanMethods = false)
    static class NativeCaseWiring {
        @Bean static BeanFactoryPostProcessor replaceOnlyTheTwoEngineGuards() {
            return factory -> { var registry = (BeanDefinitionRegistry) factory; for (String name : List.of("processStorageGuard", "caseStorageGuard")) if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name); };
        }
        @Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> formalProcessGuard(OutboxInfrastructure outbox, TransactionalOutboxProperties properties,
                ObjectProvider<FlowableCaseService> services, ObjectProvider<CaseSourceRegistry> registries) {
            return new ProcessStorageConfiguration().processStorageGuard(outbox, properties, new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true"), services, registries);
        }
        @Bean EngineConfigurationConfigurer<SpringCmmnEngineConfiguration> formalCaseGuard(OutboxInfrastructure outbox, TransactionalOutboxProperties properties, ObjectProvider<FlowableCaseService> services) {
            return new ProcessStorageConfiguration().caseStorageGuard(outbox, properties, new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true"), services);
        }
        @Bean LocalIdentity formalCaseLocalIdentity() {
            var starter = new IdpStarterProperties(); starter.getServiceClient().setAppId("process-app");
            var issuers = mock(TrustedIssuerRegistry.class); var oauth = mock(IdpServiceOAuth2Client.class);
            var trusted = new TrustedIssuerRegistry.TrustedIssuerVO(SERVICE_ISSUER, URI.create(SERVICE_ISSUER + "/jwks"), "case-native-20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1);
            when(issuers.findByIssuer(SERVICE_ISSUER.toASCIIString())).thenReturn(Optional.of(trusted));
            var key = new ServiceAuthorizationKey(SERVICE_ISSUER.toASCIIString(), "local-case-native", SERVICE.subjectId(), 1, "process-app", RESOURCE, ServiceTokenContext.TENANT, "20", Set.of("tianji:case:start"));
            when(oauth.authorizationKey(any())).thenReturn(key); return new LocalIdentity(starter, issuers, oauth);
        }
        @Bean CaseSourceRegistry caseSourceRegistry(LocalIdentity identity) throws Exception {
            var source = new CaseSourceProperties.Source(20, "native-case-source", LINKED_RELEASE, "linkedProcess", hash(bytes("formal-case-linked.bpmn20.xml")), "caseSource", "governedCase", hash(bytes("governed-case.cmmn")), SERVICE_ISSUER, "local-case-native", SERVICE.subjectId(), "process-app", RESOURCE);
            return new CaseSourceRegistry(new CaseSourceProperties(true, true, List.of(source), Duration.ofHours(1), Duration.ofSeconds(1), 32, 10), identity.starter(), identity.issuers(), identity::oauth);
        }
        @Bean ProcessModelValidator nativeCaseValidator() { return new ProcessModelValidator(); }
        @Bean CaseStartProvenanceStore nativeCaseProvenance(SpringProcessEngineConfiguration process) { var manager = (DataSourceTransactionManager) process.getTransactionManager(); var store = new CaseStartProvenanceStore(manager.getDataSource(), SCHEMA); store.verifyStorage(); return store; }
        @Bean CasePlanItemReceiptStore nativeCasePlanReceipts(SpringProcessEngineConfiguration process) { var manager = (DataSourceTransactionManager) process.getTransactionManager(); var store = new CasePlanItemReceiptStore(manager.getDataSource(), SCHEMA); store.verifyStorage(); return store; }
        @Bean CaseCompletionInbox nativeCaseInbox(SpringProcessEngineConfiguration process) { var manager = (DataSourceTransactionManager) process.getTransactionManager(); return new CaseCompletionInbox(manager.getDataSource(), manager, SCHEMA); }
        @Bean FlowableCaseService nativeCaseService(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn,
                ProcessModelValidator validator, CaseStartProvenanceStore provenance, CasePlanItemReceiptStore plans, CaseSourceRegistry registry, CaseCompletionInbox inbox, ObjectProvider<CaseCompletionPoller> pollers) {
            return new FlowableCaseService(process, cmmn, dmn, validator, provenance, plans, true, registry, inbox, () -> { var poller = pollers.getIfAvailable(); return poller != null && poller.isRunning(); });
        }
        @Bean HumanTaskReceiptStore nativeHumanReceipts(SpringProcessEngineConfiguration process, CaseStartProvenanceStore provenance) { var manager = (DataSourceTransactionManager) process.getTransactionManager(); var store = new HumanTaskReceiptStore(manager.getDataSource(), SCHEMA, provenance); store.verifyStorage(); return store; }
        @Bean FlowableHumanTaskService nativeHumanTasks(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts, CaseStartProvenanceStore provenance) { return new FlowableHumanTaskService(process, cmmn, dmn, validator, receipts, provenance); }
        @Bean ProcessReleaseService nativeReleases(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn,
                ProcessModelValidator validator, CaseSourceRegistry registry, ObjectProvider<CaseCompletionPoller> pollers) {
            return new ProcessReleaseService(process, cmmn, dmn, validator, false, null, false, () -> true, true, true, registry,
                    () -> { var poller = pollers.getIfAvailable(); return poller != null && poller.isRunning(); }, true);
        }
        @Bean VerifiedProcessModels nativeModels(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, SpringDmnEngineConfiguration dmn, ProcessModelValidator validator) { return new VerifiedProcessModels(process, cmmn, dmn, validator); }
        @Bean FlowableCaseWaitInspector nativeCaseWaits(SpringProcessEngineConfiguration process, ProcessReleaseService releases, VerifiedProcessModels models, ProcessModelValidator validator) { return new FlowableCaseWaitInspector(process, releases, models, validator); }
        @Bean CaseCompletionCorrelationService nativeCaseCompletion(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn, ProcessReleaseService releases,
                VerifiedProcessModels models, CaseStartProvenanceStore provenance, CaseCompletionInbox inbox, CaseSourceRegistry registry, FlowableCaseWaitInspector waits) { return new CaseCompletionCorrelationService(process, cmmn, releases, models, provenance, inbox, registry, waits, Duration.ofSeconds(1), 10); }
        @Bean(destroyMethod = "stop") CaseCompletionPoller nativeCasePoller(CaseSourceRegistry registry, CaseCompletionCorrelationService completion) { return new CaseCompletionPoller(registry, completion, Duration.ofHours(1), 32); }
    }
    record LocalIdentity(IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) { }
}
