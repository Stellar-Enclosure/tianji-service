package top.egon.cola.platform.tianji.process.server;

import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Called only by the existing explicit fresh-loopback PostgreSQL test, after native engine provisioning and V7. */
final class HumanTaskFlowableScenarios {
    private static final ProcessActor APPLICANT = actor("human-applicant"), REVIEWER = actor("human-reviewer"), OTHER = actor("human-other");
    private HumanTaskFlowableScenarios() { }

    static void verify(ConfigurableApplicationContext app, JdbcTemplate bootstrap) throws Exception {
        var process = app.getBean(SpringProcessEngineConfiguration.class); var dmn = app.getBean(SpringDmnEngineConfiguration.class); var validator = new ProcessModelValidator();
        var manager = (DataSourceTransactionManager) process.getTransactionManager(); var store = new HumanTaskReceiptStore(manager.getDataSource(), "tianji_process"); store.verifyStorage();
        var service = new FlowableHumanTaskService(process, dmn, validator, store); var releases = new ProcessReleaseService(process, dmn, validator, false, null, false, () -> true, true);
        var runs = new ProcessExecutionService(process, dmn, validator); var tasks = app.getBean(TaskService.class);
        byte[] model = new ClassPathResource("fixtures/governed-human-task.bpmn20.xml").getContentAsByteArray();
        releases.publish(APPLICANT, "native-human-release", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "humanReview", model, Map.of())));
        String instance = start(runs, "native-human-release", "humanReview", "life"); var task = task(service, tasks, instance, bootstrap);
        assertEquals(Set.of("finance", "manager"), Set.copyOf(task.availableLanes())); assertEquals("approved", task.approvalField());
        assertThrows(ProcessResourceNotFoundException.class, () -> service.get(context("get", APPLICANT, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId())));
        assertThrows(ProcessResourceNotFoundException.class, () -> service.claim(context("claim", APPLICANT, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-applicant", task.taskId(), task.version())));
        var foreign = ProcessActor.verified(20, ProcessActor.SubjectType.USER, REVIEWER.subjectId(), "https://other.example/20");
        assertThrows(ProcessResourceNotFoundException.class, () -> service.get(context("get", foreign, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId())));
        var foreignTenant = ProcessActor.verified(21, ProcessActor.SubjectType.USER, REVIEWER.subjectId(), "https://idp.example/21");
        assertThrows(ProcessResourceNotFoundException.class, () -> service.get(context("get", foreignTenant, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId())));
        assertThrows(ProcessResourceNotFoundException.class, () -> service.claim(context("claim", OTHER, Set.of("999"), bootstrap), new ClaimHumanTaskCommand("native-human-denied", task.taskId(), task.version())));
        var page = service.query(context("query", REVIEWER, Set.of("50"), bootstrap), new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE, null, instance, null, null, 10));
        assertEquals(List.of(task.taskId()), page.items().stream().map(HumanTaskDTO::taskId).toList());
        service.claim(context("claim", REVIEWER, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-claim", task.taskId(), task.version()));
        var claimed = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId())); assertTrue(claimed.assignedToMe());
        assertThrows(ProcessConflictException.class, () -> service.unclaim(context("unclaim", REVIEWER, Set.of(), bootstrap), new UnclaimHumanTaskCommand("native-human-stale", task.taskId(), task.version())));
        var runtime = app.getBean(RuntimeService.class); runtime.suspendProcessInstanceById(instance);
        assertThrows(ProcessConflictException.class, () -> service.complete(context("complete", REVIEWER, Set.of("50"), bootstrap), new CompleteHumanTaskCommand("native-human-suspended", task.taskId(), claimed.version(), Map.of("approved", true, "comment", "suspended form"))));
        runtime.activateProcessInstanceById(instance); var active = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        service.unclaim(context("unclaim", REVIEWER, Set.of("999"), bootstrap), new UnclaimHumanTaskCommand("native-human-unclaim", task.taskId(), active.version()));
        var unclaimed = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        assertNotEquals(task.version(), unclaimed.version());
        var admin = context("adminQuery", OTHER, Set.of(), bootstrap);
        assertEquals(1, service.query(admin, new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, null, instance, null, null, 10)).items().size());
        service.transfer(context("transfer", OTHER, Set.of(), bootstrap), new TransferHumanTaskCommand("native-human-transfer", task.taskId(), unclaimed.version(), "manager"));
        assertThrows(ProcessResourceNotFoundException.class, () -> service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId())));
        var moved = service.get(context("get", OTHER, Set.of("60"), bootstrap), new GetHumanTaskQuery(task.taskId())); assertEquals("manager", moved.lane());
        service.claim(context("claim", OTHER, Set.of("60"), bootstrap), new ClaimHumanTaskCommand("native-human-manager-claim", task.taskId(), moved.version()));
        var finalTask = service.get(context("get", OTHER, Set.of("60"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        var complete = new CompleteHumanTaskCommand("native-human-complete", task.taskId(), finalTask.version(), Map.of("approved", false, "comment", "private human form"));
        var receipt = service.complete(context("complete", OTHER, Set.of("60"), bootstrap), complete); assertEquals(Boolean.FALSE, receipt.approved());
        assertEquals(receipt, service.complete(context("complete", OTHER, Set.of(), bootstrap), complete));
        assertThrows(ProcessConflictException.class, () -> service.complete(context("complete", OTHER, Set.of("60"), bootstrap), new CompleteHumanTaskCommand(complete.commandId(), complete.taskId(), complete.expectedVersion(), Map.of("approved", true, "comment", "private human form"))));
        assertEquals("rejected", app.getBean(HistoryService.class).createHistoricProcessInstanceQuery().processInstanceId(instance).singleResult().getEndActivityId());
        String stored = bootstrap.queryForObject("SELECT row_to_json(h)::text FROM tianji_process.tianji_human_task_receipt h WHERE command_id='native-human-complete'", String.class); assertFalse(stored.contains("private human form"));
        verifyRollback(service, runs, tasks, bootstrap);
        verifyClaimAndCompletionContention(service, runs, tasks, bootstrap);
        verifyDmnRoutes(app, runs, service, tasks, bootstrap);
        verifyTimers(app, releases, runs, service, tasks, bootstrap);
    }

    private static void verifyRollback(FlowableHumanTaskService service, ProcessExecutionService runs, TaskService tasks, JdbcTemplate bootstrap) {
        String instance = start(runs, "native-human-release", "humanReview", "rollback"); var task = task(service, tasks, instance, bootstrap);
        service.claim(context("claim", REVIEWER, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-rollback-claim", task.taskId(), task.version()));
        var claimed = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        var command = new CompleteHumanTaskCommand("native-human-rollback", task.taskId(), claimed.version(), Map.of("approved", true, "comment", "private rollback form"));
        bootstrap.execute("CREATE FUNCTION tianji_process.test_human_action_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.command_id='native-human-rollback' THEN RAISE EXCEPTION 'forced human receipt failure'; END IF; RETURN NEW; END $$");
        bootstrap.execute("CREATE TRIGGER test_human_action_failure BEFORE INSERT ON tianji_process.tianji_human_task_receipt FOR EACH ROW EXECUTE FUNCTION tianji_process.test_human_action_failure()");
        try {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> service.complete(context("complete", REVIEWER, Set.of("50"), bootstrap), command));
            assertTrue(hasMessage(failure, "forced human receipt failure"));
            assertEquals(1, tasks.createTaskQuery().taskId(task.taskId()).count()); assertNull(tasks.getVariable(task.taskId(), "reviewApproved"));
            assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_id='native-human-rollback'", Integer.class));
            assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt WHERE command_id='native-human-rollback'", Integer.class));
        } finally { bootstrap.execute("DROP TRIGGER test_human_action_failure ON tianji_process.tianji_human_task_receipt"); bootstrap.execute("DROP FUNCTION tianji_process.test_human_action_failure()"); }
        assertEquals(Boolean.TRUE, service.complete(context("complete", REVIEWER, Set.of("50"), bootstrap), command).approved());
    }

    private static void verifyClaimAndCompletionContention(FlowableHumanTaskService service, ProcessExecutionService runs, TaskService tasks, JdbcTemplate bootstrap) throws Exception {
        String instance = start(runs, "native-human-release", "humanReview", "contention"); var task = task(service, tasks, instance, bootstrap); var ready = new CountDownLatch(1);
        ProcessActor winner;
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Future<ProcessActor>> claims = IntStream.range(0, 20).mapToObj(number -> pool.submit(() -> {
                ready.await(); var actor = actor("human-racer-" + number);
                try { service.claim(context("claim", actor, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-race", task.taskId(), task.version())); return actor; }
                catch (ProcessConflictException expected) { return null; }
            })).toList(); ready.countDown(); var winners = new java.util.ArrayList<ProcessActor>(); for (var claim : claims) { var actor = claim.get(20, TimeUnit.SECONDS); if (actor != null) winners.add(actor); }
            assertEquals(1, winners.size()); winner = winners.getFirst();
        }
        assertEquals(1, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt WHERE task_id=? AND command_type='TASK_CLAIM'", Integer.class, task.taskId()));
        var claimed = service.get(context("get", winner, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        var command = new CompleteHumanTaskCommand("native-human-repeated-complete", task.taskId(), claimed.version(), Map.of("approved", true, "comment", "concurrent form")); var complete = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Future<HumanTaskActionReceiptDTO>> results = IntStream.range(0, 20).mapToObj(number -> pool.submit(() -> { complete.await(); return service.complete(context("complete", winner, Set.of("50"), bootstrap), command); })).toList();
            complete.countDown(); var first = results.getFirst().get(20, TimeUnit.SECONDS); for (var result : results) assertEquals(first, result.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt WHERE task_id=? AND command_type='TASK_COMPLETE'", Integer.class, task.taskId()));
    }

    private static void verifyDmnRoutes(ConfigurableApplicationContext app, ProcessExecutionService runs, FlowableHumanTaskService service, TaskService tasks, JdbcTemplate bootstrap) throws Exception {
        String bpmn = new String(new ClassPathResource("fixtures/governed-human-task.bpmn20.xml").getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("humanReview", "humanRoutingReview").replace("task:initialLane=\"finance\"", "task:laneVariable=\"queue\"")
                .replace("<startEvent id=\"start\"/>", "<startEvent id=\"start\"/><serviceTask id=\"decision\" xmlns:flowable=\"http://flowable.org/bpmn\" flowable:type=\"dmn\"><extensionElements><flowable:field name=\"decisionTableReferenceKey\"><flowable:string>humanRouting</flowable:string></flowable:field></extensionElements></serviceTask>")
                .replace("sourceRef=\"start\" targetRef=\"review\"", "sourceRef=\"start\" targetRef=\"decision\"")
                .replace("<sequenceFlow id=\"reviewRoute\"", "<sequenceFlow id=\"decisionReview\" sourceRef=\"decision\" targetRef=\"review\"/><sequenceFlow id=\"reviewRoute\"");
        String dmn = new String(new ClassPathResource("fixtures/baseline.dmn").getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("baselineDecision", "humanRouting").replace("name=\"route\"", "name=\"queue\"").replace("'standard'", "'finance'").replace("'review'", "'manager'").replace("&gt; 10", "&gt; 20");
        var releases = new ProcessReleaseService(app.getBean(SpringProcessEngineConfiguration.class), app.getBean(SpringDmnEngineConfiguration.class), new ProcessModelValidator(), true, null, false, () -> true, true);
        releases.publish(APPLICANT, "native-human-dmn", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "humanRoutingReview", bpmn.getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of("amount", ValueType.INTEGER)),
                new ProcessReleaseService.ModelResource(Type.DMN, "humanRouting", dmn.getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of())));
        for (long amount : List.of(5L, 25L)) {
            String suffix = "dmn-" + amount; var run = runs.start(APPLICANT, new ProcessExecutionService.StartCommand("native-human-dmn", "humanRoutingReview", "native-human-" + suffix, "native-human-start-" + suffix, "native-human-correlation-" + suffix, Map.of("amount", amount)));
            var nativeTask = tasks.createTaskQuery().processInstanceId(run.instanceId()).singleResult(); assertNotNull(nativeTask);
            boolean finance = amount == 5; var reviewer = finance ? REVIEWER : OTHER; var roles = finance ? Set.of("50") : Set.of("60");
            var task = service.get(context("get", reviewer, roles, bootstrap), new GetHumanTaskQuery(nativeTask.getId())); assertEquals(finance ? "finance" : "manager", task.lane());
            if (!finance) assertThrows(ProcessResourceNotFoundException.class, () -> service.claim(context("claim", REVIEWER, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-dmn-denied", task.taskId(), task.version())));
            service.claim(context("claim", reviewer, roles, bootstrap), new ClaimHumanTaskCommand("native-human-dmn-claim-" + amount, task.taskId(), task.version()));
            var claimed = service.get(context("get", reviewer, roles, bootstrap), new GetHumanTaskQuery(task.taskId()));
            assertEquals(Boolean.TRUE, service.complete(context("complete", reviewer, roles, bootstrap), new CompleteHumanTaskCommand("native-human-dmn-complete-" + amount, task.taskId(), claimed.version(), Map.of("approved", true, "comment", "routed form"))).approved());
        }
        RuntimeException missing = assertThrows(RuntimeException.class, () -> runs.start(APPLICANT, new ProcessExecutionService.StartCommand("native-human-dmn", "humanRoutingReview", "native-human-dmn-no-hit", "native-human-start-dmn-no-hit", "native-human-correlation-dmn-no-hit", Map.of("amount", 15L))));
        assertTrue(hasMessage(missing, "Human task assignment does not match its fixed released policy"));
        assertEquals(0, app.getBean(RuntimeService.class).createProcessInstanceQuery().processInstanceBusinessKey("native-human-dmn-no-hit").count());
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_command_receipt WHERE command_id='native-human-start-dmn-no-hit'", Integer.class));
    }

    private static void verifyTimers(ConfigurableApplicationContext app, ProcessReleaseService releases, ProcessExecutionService runs, FlowableHumanTaskService service, TaskService tasks, JdbcTemplate bootstrap) throws Exception {
        byte[] timed = new ClassPathResource("fixtures/governed-human-task-timer.bpmn20.xml").getContentAsByteArray();
        releases.publish(APPLICANT, "native-human-timer", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "timedHumanReview", timed, Map.of())));
        byte[] shortTimer = new String(timed, java.nio.charset.StandardCharsets.UTF_8).replace("PT5M", "PT3S").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        releases.publish(APPLICANT, "native-human-short-timer", List.of(new ProcessReleaseService.ModelResource(Type.BPMN, "timedHumanReview", shortTimer, Map.of())));
        String timeout = start(runs, "native-human-short-timer", "timedHumanReview", "automatic-timeout"); var runtime = app.getBean(RuntimeService.class);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> runtime.createProcessInstanceQuery().processInstanceId(timeout).count() == 0);
        assertEquals("timedOut", app.getBean(HistoryService.class).createHistoricProcessInstanceQuery().processInstanceId(timeout).singleResult().getEndActivityId());
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt WHERE process_instance_id=? AND approval_receipt_id IS NOT NULL", Integer.class, timeout));

        String completed = start(runs, "native-human-timer", "timedHumanReview", "before-timer"); var pending = task(service, tasks, completed, bootstrap);
        service.claim(context("claim", REVIEWER, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-before-timer-claim", pending.taskId(), pending.version()));
        var assigned = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(pending.taskId()));
        var result = service.complete(context("complete", REVIEWER, Set.of("50"), bootstrap), new CompleteHumanTaskCommand("native-human-before-timer-complete", pending.taskId(), assigned.version(), Map.of("approved", true)));
        assertEquals(Boolean.TRUE, result.approved()); assertEquals(0, app.getBean(ManagementService.class).createTimerJobQuery().processInstanceId(completed).count());
        assertEquals("completed", app.getBean(HistoryService.class).createHistoricProcessInstanceQuery().processInstanceId(completed).singleResult().getEndActivityId());

        // Last fixture in the owning test: stop acquisition so the following race is driven by one explicit native job execution.
        app.getBean(SpringProcessEngineConfiguration.class).getAsyncExecutor().shutdown();
        var jobs = app.getBean(ManagementService.class); var start = new CountDownLatch(1);
        String instance = start(runs, "native-human-timer", "timedHumanReview", "timer-race"); var task = task(service, tasks, instance, bootstrap);
        service.claim(context("claim", REVIEWER, Set.of("50"), bootstrap), new ClaimHumanTaskCommand("native-human-timer-claim", task.taskId(), task.version()));
        var claimed = service.get(context("get", REVIEWER, Set.of("50"), bootstrap), new GetHumanTaskQuery(task.taskId()));
        var timer = jobs.createTimerJobQuery().processInstanceId(instance).singleResult(); assertNotNull(timer); String executable = jobs.moveTimerToExecutableJob(timer.getId()).getId();
        var command = new CompleteHumanTaskCommand("native-human-timer-complete", task.taskId(), claimed.version(), Map.of("approved", true));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var completion = pool.submit(() -> { start.await(); try { return service.complete(context("complete", REVIEWER, Set.of("50"), bootstrap), command); } catch (RuntimeException loser) { return null; } });
            var timerAttempt = pool.submit(() -> { start.await(); try { jobs.executeJob(executable); return true; } catch (RuntimeException loser) { return false; } });
            start.countDown(); var receipt = completion.get(20, TimeUnit.SECONDS); boolean timerWon = timerAttempt.get(20, TimeUnit.SECONDS);
            assertEquals(receipt == null, timerWon);
            assertEquals(receipt == null ? 0 : 1, bootstrap.queryForObject("SELECT count(*) FROM tianji_process.tianji_human_task_receipt WHERE task_id=? AND command_type='TASK_COMPLETE'", Integer.class, task.taskId()));
            assertEquals(0, tasks.createTaskQuery().taskId(task.taskId()).count());
            String end = app.getBean(HistoryService.class).createHistoricProcessInstanceQuery().processInstanceId(instance).singleResult().getEndActivityId(); assertEquals(timerWon ? "timedOut" : "completed", end);
            if (receipt != null) assertEquals(receipt, service.complete(context("complete", REVIEWER, Set.of(), bootstrap), command));
        }
    }

    private static String start(ProcessExecutionService runs, String release, String definition, String suffix) { return runs.start(APPLICANT, new ProcessExecutionService.StartCommand(release, definition, "native-human-" + suffix, "native-human-start-" + suffix, "native-human-correlation-" + suffix, Map.of())).instanceId(); }
    private static HumanTaskDTO task(FlowableHumanTaskService service, TaskService tasks, String instance, JdbcTemplate jdbc) { var task = tasks.createTaskQuery().processInstanceId(instance).singleResult(); assertNotNull(task); return service.get(context("get", REVIEWER, Set.of("50"), jdbc), new GetHumanTaskQuery(task.getId())); }
    private static HumanTaskContext context(String operation, ProcessActor actor, Set<String> roles, JdbcTemplate jdbc) { return new HumanTaskContext(actor, "tianji.api.process.task." + operation, roles, jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant().plusSeconds(30), 2, 3); }
    private static ProcessActor actor(String subject) { return ProcessActor.verified(20, ProcessActor.SubjectType.USER, subject, "https://idp.example/20"); }
    private static boolean hasMessage(Throwable failure, String text) { for (var current = failure; current != null; current = current.getCause()) if (current.getMessage() != null && current.getMessage().contains(text)) return true; return false; }
}
