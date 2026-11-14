package top.egon.cola.platform.tianji.process.server.event;

import org.flywaydb.core.Flyway;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO.ScheduleVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceConverter;
import top.egon.cola.platform.tianji.process.server.execution.GovernedStartPilotProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCancellationReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationConverter;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.InvalidAutomationModelException;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels.VerifiedAutomationModelsVO;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftBO;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
class ProcessEventIntegrationTest {
    private static final String SCHEMA = "tianji_process";
    private static final String SOURCE_ID = "process-main";
    private static final String DESTINATION = "lifecycle-test";
    private static final long TENANT = 20;
    private static final String DEFINITION = "lifecycleWait";
    private static final String RELEASE = "lifecycle-v1";
    private static final String WAIT_NODE = "wait";
    private static final String MESSAGE = "tianji-v2:" + WAIT_NODE;
    private static final String WAIT_ROUTE = "test-signal-route";
    private static final String WAIT_SOURCE = "test-signal-source";
    private static final String COMPILED_DEFINITION = "compiledWait";
    private static final String COMPILED_RELEASE = "compiled-wait-v2";
    private static final String SCHEDULE_ROUTE = "registered-start";
    private static final String SIGNAL_DEFINITION = "callbackWait";
    private static final String SIGNAL_RELEASE = "callback-wait-release";
    private static final String OUTBOUND_NODE = "outbound";
    private static final String CALLBACK_CAPABILITY = "requestApproval";
    private static final String TOKEN_FIELD = "callbackToken";
    private static final ProcessActor PRODUCER = ProcessActor.verified(TENANT, ProcessActor.SubjectType.SERVICE,
            "schedule-source", "https://idp.test/main");
    private static final String COMPILED_XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:tianji:native-wait-test">
              <message id="incoming" name="incomingMessage"/>
              <process id="compiledWait" isExecutable="true">
                <startEvent id="start"/>
                <intermediateCatchEvent id="park"><timerEventDefinition><timeDuration>PT10S</timeDuration></timerEventDefinition></intermediateCatchEvent>
                <intermediateCatchEvent id="wait"><messageEventDefinition messageRef="incoming"/></intermediateCatchEvent>
                <endEvent id="end"/>
                <sequenceFlow id="first" sourceRef="start" targetRef="park"/>
                <sequenceFlow id="second" sourceRef="park" targetRef="wait"/>
                <sequenceFlow id="third" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor OWNER = ProcessActor.verified(TENANT, ProcessActor.SubjectType.USER, "lifecycle-owner",
            "https://idp.test/main");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROCESS = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:tianji:outbox-test">
              <message id="incoming" name="tianji-v2:wait"/>
              <process id="lifecycleWait" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="beforeWait"/>
                <intermediateCatchEvent id="wait"><messageEventDefinition messageRef="incoming"/></intermediateCatchEvent>
                <endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="beforeWait"/>
                <sequenceFlow id="b" sourceRef="beforeWait" targetRef="wait"/>
                <sequenceFlow id="c" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void utc_schedule_skips_old_slots_and_repeats_only_the_same_stable_key_after_restart() {
        var start = Instant.parse("2026-03-29T00:59:59.123456Z");
        var interval = Duration.ofMinutes(30);
        var schedule = new ScheduleVO(start, interval, start.plus(interval.multipliedBy(5)), 6);
        var activated = start.plus(interval);
        var afterDowntime = start.plus(interval.multipliedBy(3)).plusSeconds(1);
        var next = ProcessEventSchedule.nextFire(activated, schedule, afterDowntime).orElseThrow();
        assertEquals(Instant.parse("2026-03-29T02:59:59.123456Z"), next);
        var restart = new ScheduleVO(start, interval, schedule.endAt(), schedule.maxOccurrences());
        var sameSlot = ProcessEventSchedule.nextFire(activated, restart, next.minusNanos(1_000)).orElseThrow();
        var fireKey = ProcessEventSchedule.fireKey(TENANT, DEFINITION, RELEASE, HASH, next);
        assertEquals(fireKey, ProcessEventSchedule.fireKey(TENANT, DEFINITION, RELEASE, HASH, sameSlot));
        assertNotEquals(fireKey, ProcessEventSchedule.fireKey(TENANT + 1, DEFINITION, RELEASE, HASH, next));
        assertNotEquals(fireKey, ProcessEventSchedule.fireKey(TENANT, DEFINITION, "lifecycle-v2", HASH, next));
        assertNotEquals(fireKey, ProcessEventSchedule.fireKey(TENANT, DEFINITION, RELEASE, "b".repeat(64), next));
        assertNotEquals(fireKey, ProcessEventSchedule.fireKey(TENANT, DEFINITION, RELEASE, HASH, next.plus(interval)));
        assertEquals(next.plus(interval), ProcessEventSchedule.nextFire(activated, restart, next).orElseThrow());
        assertTrue(ProcessEventSchedule.nextFire(activated, restart, next.plus(interval)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ProcessEventSchedule.nextFire(activated, restart, next.plusNanos(1)));
        var unsupported = new ScheduleVO(start, Duration.ofNanos(1), start.plusSeconds(1), 1);
        assertThrows(IllegalArgumentException.class, () -> ProcessEventSchedule.nextFire(activated, unsupported, afterDowntime));
    }

    @Test
    void lifecycle_outbox_uses_the_native_transaction_and_replays_the_exact_transition() throws Exception {
        var database = provision();
        try (var app = running(database)) {
            var infrastructure = app.getBean(OutboxInfrastructure.class);
            var jdbc = new JdbcTemplate(infrastructure.dataSource());
            var transaction = new TransactionTemplate(infrastructure.transactionManager());
            var runtime = app.getBean(RuntimeService.class);
            var tasks = app.getBean(TaskService.class);
            var repository = app.getBean(RepositoryService.class);
            var bindings = app.getBean(ProcessInstanceBindingStore.class);
            var nodes = app.getBean(ProcessNodeExecutionStore.class);
            var rootGuard = app.getBean(AutomationInstanceGuardCommandInterceptor.class);
            var nativeAccess = new ProcessControlNativeAccess(infrastructure.dataSource(), app.getBean(SpringProcessEngineConfiguration.class));
            var reservations = new ProcessEventSubscriptionStore(jdbc, SCHEMA);
            var publisher = app.getBean(ProcessLifecycleEventPublisher.class);
            seedRelease(database.owner());
            var deployment = repository.createDeployment().tenantId(Long.toString(TENANT))
                    .addString("lifecycle-wait.bpmn20.xml", PROCESS).deploy();
            var definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
            database.owner().update("""
                    INSERT INTO tianji_process.tianji_process_release_definition
                    (tenant_id,release_key,model_type,definition_key,engine_definition_id,engine_deployment_id,
                     engine_version,model_sha256,input_schema)
                    VALUES (?,?,'BPMN',?,?,?,?,?,'{}')
                    """, TENANT, RELEASE, DEFINITION, definition.getId(), deployment.getId(), definition.getVersion(), HASH);
            var instance = runtime.createProcessInstanceBuilder().processDefinitionId(definition.getId()).tenantId(Long.toString(TENANT))
                    .businessKey("outbox-transition").start().getId();
            seedBinding(transaction, jdbc, bindings, instance);

            assertEquals(0, runtime.createEventSubscriptionQuery().processInstanceId(instance).eventName(MESSAGE).count());
            var reserved = reserveAndCheck(transaction, rootGuard, reservations, database.owner(), instance);
            var task = tasks.createTaskQuery().processInstanceId(instance).taskDefinitionKey("beforeWait").singleResult();
            assertNotNull(task);
            var bound = transaction.execute(status -> {
                var binding = rootGuard.lockKnownRoot(TENANT, instance);
                tasks.complete(task.getId());
                var nativeWait = runtime.createEventSubscriptionQuery().tenantId(Long.toString(TENANT)).processInstanceId(instance)
                        .eventType("message").eventName(MESSAGE).singleResult();
                assertNotNull(nativeWait);
                var nativeExecutionId = nativeWait.getExecutionId();
                var nativeSubscriptionId = nativeWait.getId();
                var nativeDefinitionId = definition.getId();
                assertEquals(nativeSubscriptionId, nativeAccess.requireEventWait(binding, nativeDefinitionId, WAIT_NODE,
                        nativeExecutionId, nativeSubscriptionId, instance).getId());
                assertThrows(IllegalStateException.class, () -> nativeAccess.requireEventWait(binding, nativeDefinitionId, "foreign-node",
                        nativeExecutionId, nativeSubscriptionId, instance));
                assertThrows(IllegalStateException.class, () -> nativeAccess.requireEventWait(binding, nativeDefinitionId, WAIT_NODE,
                        nativeExecutionId, nativeSubscriptionId, "foreign-attempt"));
                assertThrows(IllegalStateException.class, () -> nativeAccess.requireEventWait(binding, "foreign-definition", WAIT_NODE,
                        nativeExecutionId, nativeSubscriptionId, instance));
                assertThrows(IllegalStateException.class, () -> nativeAccess.requireEventWait(binding, nativeDefinitionId, WAIT_NODE,
                        nativeExecutionId, "foreign-subscription", instance));
                var node = storeWaitNode(nodes, binding, reserved, instance, nativeSubscriptionId);
                var evidence = new ProcessEventSubscriptionStore.NativeWaitVO(nativeExecutionId,
                        nativeSubscriptionId, node.getNodeExecutionRef());
                var result = reservations.bindExact(reserved, evidence);
                assertThrows(IllegalStateException.class, () -> reservations.bindExact(reserved, evidence));
                return result;
            });
            assertEquals(EventSubscriptionBO.State.BOUND, bound.getState());
            assertEquals(2, bound.getRevision());
            assertEquals(0, outboxCount(database.owner()));
            assertThrows(IllegalStateException.class, () -> publisher.publish(new AutomationInstanceBindingBO()
                    .setTenantId(TENANT).setInstanceId(instance).setReleaseKey(RELEASE),
                    "INSTANCE_COMPLETED", database.now(), null, null, null, null));
            var occurredAt = database.now();
            assertThrows(InjectedRollback.class, () -> transaction.executeWithoutResult(status -> {
                var binding = rootGuard.lockKnownRoot(TENANT, instance);
                assertEquals(EventSubscriptionBO.State.CONSUMED, reservations.consume(bound).getState());
                runtime.messageEventReceived(MESSAGE, bound.getNativeExecutionId());
                publisher.publish(binding, "INSTANCE_COMPLETED", occurredAt, null, null, null, null);
                assertEquals(1, outboxCount(jdbc));
                assertThrows(IllegalArgumentException.class, () -> publisher.publish(binding, "UNKNOWN", occurredAt,
                        null, null, null, null));
                throw new InjectedRollback();
            }));
            assertEquals(0, outboxCount(database.owner()));
            assertEquals(bound, reservations.find(TENANT, bound.getTokenSha256()).orElseThrow());
            assertEquals(1, runtime.createExecutionQuery().processInstanceId(instance).messageEventSubscriptionName(MESSAGE).count());
            transaction.executeWithoutResult(status -> {
                var binding = rootGuard.lockKnownRoot(TENANT, instance);
                var consumed = reservations.consume(bound);
                assertEquals(EventSubscriptionBO.State.CONSUMED, consumed.getState());
                assertEquals(3, consumed.getRevision());
                assertNotNull(consumed.getClosedAt());
                assertThrows(IllegalStateException.class, () -> reservations.consume(bound));
                assertEquals(0, reservations.closeOpen(TENANT, instance));
                runtime.messageEventReceived(MESSAGE, bound.getNativeExecutionId());
                publisher.publish(binding, "INSTANCE_COMPLETED", occurredAt, null, null, null, null);
                publisher.publish(binding, "INSTANCE_COMPLETED", occurredAt, null, null, null, null);
            });
            assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(instance).count());
            assertEquals(EventSubscriptionBO.State.CONSUMED,
                    reservations.find(TENANT, bound.getTokenSha256()).orElseThrow().getState());
            assertEquals(1, outboxCount(database.owner()));
            var stored = database.owner().queryForMap("""
                    SELECT message_id,idempotency_key,schema_version,destination,payload
                    FROM tianji_process.egon_cola_outbox_message WHERE channel=?
                    """, ProcessLifecycleEventPublisher.CHANNEL);
            assertEquals(stored.get("message_id"), stored.get("idempotency_key"));
            assertTrue(stored.get("message_id").toString().matches("pl-[0-9a-f]{61}"));
            assertEquals("2", stored.get("schema_version"));
            assertEquals(DESTINATION, stored.get("destination"));
            var payload = JSON.readTree(stored.get("payload").toString());
            assertEquals(Set.of("schemaVersion", "eventId", "tenantId", "sourceId", "instanceId", "releaseVersion", "type", "occurredAt"),
                    Set.copyOf(payload.propertyNames()));
            assertEquals(stored.get("message_id"), payload.path("eventId").asString());
            assertEquals(TENANT, payload.path("tenantId").asLong());
            assertEquals(SOURCE_ID, payload.path("sourceId").asString());
            assertEquals(instance, payload.path("instanceId").asString());
            assertEquals(RELEASE, payload.path("releaseVersion").asString());
            assertEquals("INSTANCE_COMPLETED", payload.path("type").asString());
            assertEquals(occurredAt, Instant.parse(payload.path("occurredAt").asString()));
            assertFalse(payload.has("payload"));
            verifyCompiledWaitHooks(app, database, jdbc, transaction);
        }
    }

    private static void verifyCompiledWaitHooks(ConfigurableApplicationContext app, DatabaseVO database, JdbcTemplate jdbc,
            TransactionTemplate transaction) throws Exception {
        var engine = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var releaseService = new ProcessReleaseService(engine, dmn, new ProcessModelValidator());
        var releaseStore = new AutomationReleaseStore(jdbc, SCHEMA);
        var scheduleStart = database.now().minusSeconds(60);
        var release = publishCompiledWait(releaseService, releaseStore, jdbc, transaction, COMPILED_RELEASE, scheduleStart);
        var verified = transaction.execute(status -> releaseService.verifiedAutomation(release));
        var bindings = app.getBean(ProcessInstanceBindingStore.class);
        var activationClock = new AtomicReference<Instant>();
        var activationStore = new AutomationActivationStore(jdbc, SCHEMA);
        var schedulerActivationStore = new AutomationActivationStore(jdbc, SCHEMA) {
            @Override public Instant now() { return activationClock.get(); }
        };
        var environment = new MockEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true")
                .withProperty("stellar.tianji.process.events.enabled", "true");
        var authority = new TestReleaseAuthority(database, releaseStore);
        var trust = new StaticListableBeanFactory(Map.of("proof", (AutomationAdmissionAuthority) authority::proof,
                "trust", (AutomationReleaseTrustVerifier.TrustAuthority) authority::signed));
        var verification = new ProcessApiSchemaVerifier(jdbc.getDataSource(), SCHEMA);
        var activations = new AutomationActivationService(activationStore, releaseStore, releaseService,
                Mappers.getMapper(AutomationActivationConverter.class), verification, transaction, environment,
                trust.getBeanProvider(AutomationAdmissionAuthority.class),
                trust.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class));
        assertEquals(1, activations.activate(OWNER, COMPILED_DEFINITION, new ActivationCommand(COMPILED_RELEASE, 0L,
                "activate-compiled-wait")).getRevision());
        var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activationStore, releaseStore, bindings,
                Mappers.getMapper(AutomationInstanceConverter.class), verification, environment,
                trust.getBeanProvider(AutomationAdmissionAuthority.class),
                trust.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class));
        var execution = new ProcessExecutionService(engine, dmn, new ProcessModelValidator(), new GovernedStartPilotProperties(List.of()),
                null, dependencies);
        var inbox = new ProcessEventInboxStore(jdbc, SCHEMA);
        var runtime = engine.getRuntimeService();
        var guard = app.getBean(AutomationInstanceGuardCommandInterceptor.class);
        var reservations = app.getBean(ProcessEventSubscriptionStore.class);
        var nodes = app.getBean(ProcessNodeExecutionStore.class);
        var lifecycle = app.getBean(ProcessNodeLifecycleListener.class);
        var nativeAccess = new ProcessControlNativeAccess(jdbc.getDataSource(), engine);
        var failAfterMessage = new AtomicBoolean(false);
        var messageDelivery = mock(RuntimeService.class, delegatesTo(runtime));
        doAnswer(call -> {
            runtime.messageEventReceived(call.getArgument(0), call.getArgument(1));
            if (failAfterMessage.compareAndSet(true, false)) throw new InjectedRollback();
            return null;
        }).when(messageDelivery).messageEventReceived(anyString(), anyString());
        var events = new ProcessEventApplicationService(inbox, execution, transaction, environment,
                (actor, definition, version, route, revision, sha) -> PRODUCER.equals(actor) && revision == 1 && HASH.equals(sha)
                        && (COMPILED_DEFINITION.equals(definition) && Set.of(SCHEDULE_ROUTE, WAIT_ROUTE).contains(route)
                        || SIGNAL_DEFINITION.equals(definition) && SIGNAL_RELEASE.equals(version) && WAIT_ROUTE.equals(route)),
                new ProcessEventApplicationService.SignalParticipantsVO(reservations, bindings, releaseStore, releaseService, guard, nodes,
                        nativeAccess, lifecycle, messageDelivery));
        var jobs = engine.getManagementService();
        var instance = startCompiledWait(app, engine, jdbc, transaction, verified.root().engineDefinitionId());
        var bootstrap = jobs.createJobQuery().processInstanceId(instance).singleResult();
        assertNotNull(bootstrap);
        jobs.executeJob(bootstrap.getId());
        var parkActivity = verified.controlBindings().occurrenceSlots().get("park").activityId();
        var park = jobs.createTimerJobQuery().processInstanceId(instance).list().stream()
                .filter(job -> parkActivity.equals(job.getElementId())).findFirst().orElseThrow();
        var reserved = transaction.execute(status -> {
            guard.lockKnownRoot(TENANT, instance);
            return reserve(reservations, instance, WAIT_NODE);
        });
        assertEquals(EventSubscriptionBO.State.RESERVED, reserved.subscription().getState());
        var earlySignal = new ProcessEventCommand("early-timeout-signal", COMPILED_DEFINITION, WAIT_ROUTE, 1, "Received",
                database.now(), "fixture-timeout-signal", reserved.token(), Map.of());
        var earlyReceipt = events.receive(PRODUCER, earlySignal);
        assertEquals("RECEIVED", earlyReceipt.getState());
        assertEquals("RECEIVED", events.advance(TENANT, earlyReceipt.getReceiptId()).getState());
        assertEquals(0, runtime.createEventSubscriptionQuery().processInstanceId(instance).count());
        jobs.executeJob(jobs.moveTimerToExecutableJob(park.getId()).getId());

        var bound = reservations.find(TENANT, reserved.subscription().getTokenSha256()).orElseThrow();
        assertEquals(EventSubscriptionBO.State.BOUND, bound.getState());
        assertEquals(2, bound.getRevision());
        var slot = verified.controlBindings().occurrenceSlots().get(WAIT_NODE);
        String message = "tianji-v2:" + slot.activityId();
        assertThrows(RuntimeException.class, () -> runtime.messageEventReceived(message, bound.getNativeExecutionId()));
        assertEquals(bound, reservations.find(TENANT, bound.getTokenSha256()).orElseThrow());
        assertEquals(1, runtime.createEventSubscriptionQuery().id(bound.getNativeSubscriptionId()).eventName(message).count());
        var node = transaction.execute(status -> {
            var binding = guard.lockKnownRoot(TENANT, instance);
            var row = nodes.find(TENANT, instance, bound.getNodeExecutionRef()).orElseThrow();
            assertEquals(StateEnum.WAITING, row.getState());
            assertEquals(WAIT_NODE, row.getNodeId());
            assertEquals(bound.getNativeSubscriptionId(), nativeAccess.requireEventWait(binding, verified.root().engineDefinitionId(),
                    slot.activityId(), bound.getNativeExecutionId(), bound.getNativeSubscriptionId(), row.getAttemptScopeId()).getId());
            return row;
        });
        var timeoutActivity = verified.controlBindings().policySlots().get(WAIT_NODE).attemptTimeoutBoundaryId();
        var timeout = jobs.createTimerJobQuery().processInstanceId(instance).list().stream()
                .filter(job -> timeoutActivity.equals(job.getElementId())).findFirst().orElseThrow();
        await().atMost(Duration.ofSeconds(8)).until(() -> !database.now().isBefore(node.getAttemptDeadlineAt()));
        jobs.executeJob(jobs.moveTimerToExecutableJob(timeout.getId()).getId());
        assertEquals(EventSubscriptionBO.State.CLOSED, reservations.find(TENANT, bound.getTokenSha256()).orElseThrow().getState());
        assertEquals(StateEnum.TIMED_OUT, transaction.execute(status ->
                nodes.find(TENANT, instance, bound.getNodeExecutionRef()).orElseThrow().getState()));
        assertEquals(0, runtime.createEventSubscriptionQuery().processInstanceId(instance).eventName(message).count());
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(instance).count());
        assertEquals(1, cancellationEventCount(jdbc, instance));
        assertEquals("IGNORED_LATE", events.advance(TENANT, earlyReceipt.getReceiptId()).getState());

        verifyExplicitCancellation(app, jdbc, transaction, engine, dmn, releaseService, execution, verified);
        verifyWorkerSignal(app, database, jdbc, transaction, engine, dmn, releaseService, releaseStore, activations, execution, events,
                failAfterMessage);

        verifyScheduledAdmission(new ProcessEventSchedule(schedulerActivationStore, releaseStore, events), activationClock, inbox, events,
                activationStore, releaseService, releaseStore, activations, jdbc, transaction, scheduleStart);
    }

    private static void verifyWorkerSignal(ConfigurableApplicationContext app, DatabaseVO database, JdbcTemplate jdbc,
            TransactionTemplate transaction, SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
            ProcessReleaseService releaseService, AutomationReleaseStore releaseStore, AutomationActivationService activations,
            ProcessExecutionService execution, ProcessEventApplicationService events, AtomicBoolean failAfterMessage) throws Exception {
        var empty = fixtureSchema(Map.of(), List.of());
        var workerInput = fixtureSchema(Map.of(TOKEN_FIELD, Map.of("type", "string")), List.of());
        var text = Map.of("type", "string");
        var flag = Map.of("type", "boolean");
        var payloadSchema = fixtureSchema(Map.of("approved", flag, "comment", text), List.of("approved", "comment"));
        var rootInput = fixtureSchema(Map.of("caseRef", text), List.of("caseRef"));
        var outputFields = Map.<String, Object>of("approved", flag, "comment", text, "caseRef", text);
        var rootOutput = fixtureSchema(outputFields, List.of());
        var waitOutput = fixtureSchema(outputFields, List.of("approved", "comment", "caseRef"));
        var registration = new WorkerTopicRegistry.AutomationRegistrationVO(TENANT, "https://idp.test/main", "callback-worker",
                "worker-source", CALLBACK_CAPABILITY, 1, ProcessNodeLifecycleListener.schemaSha256(workerInput),
                ProcessNodeLifecycleListener.schemaSha256(empty), WorkerEffectSafetyEnum.READ_ONLY, "callback-handler-1",
                "callback-backend", 2, Duration.ofSeconds(60));
        var registry = new WorkerTopicRegistry(List.of(), List.of(registration));
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofSeconds(30), end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var outbound = new AutomationNodeBindingVO(OUTBOUND_NODE, "SERVICE", workerInput, empty, List.of(), List.of(), policy,
                registration.capability(), null, null, null, null, null);
        var wait = new AutomationNodeBindingVO(WAIT_NODE, "EVENT_WAIT", rootOutput, waitOutput,
                List.of(new MappingVO("comment", "event.comment"), new MappingVO("caseRef", "input.caseRef")),
                List.of(new MappingVO("approved", "nodes.wait.output.approved"), new MappingVO("comment", "nodes.wait.output.comment"),
                        new MappingVO("caseRef", "nodes.wait.output.caseRef")), policy, null, null, null, null, null, null);
        var route = new EventRouteVO(WAIT_ROUTE, 1, "Received", "SIGNAL", WAIT_SOURCE, payloadSchema,
                List.of(new MappingVO("approved", "event.approved")), Duration.ofSeconds(60), null, WAIT_NODE, null);
        var workerXml = COMPILED_XML.replace(COMPILED_DEFINITION, SIGNAL_DEFINITION).replace("""
                <intermediateCatchEvent id="park"><timerEventDefinition><timeDuration>PT10S</timeDuration></timerEventDefinition></intermediateCatchEvent>
                """.strip(), "<serviceTask id=\"outbound\"/>").replace("\"park\"", "\"outbound\"");
        var source = new AutomationModelSourceVO("AUTOMATION_V2", List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN",
                SIGNAL_DEFINITION, workerXml)), rootInput, rootOutput, List.of(outbound, wait), List.of(route));
        var dependencies = new AutomationModelValidator.DependencySnapshotVO(TENANT, registry.automationReleaseRegistrations(TENANT),
                Map.of(), Set.of(), Map.of(), Map.of(new AutomationModelValidator.VersionedRefVO(WAIT_ROUTE, 1),
                        new AutomationModelValidator.EventRegistrationVO(route, HASH)));
        verifyRejectedSignalMappings(source, dependencies, outbound, wait);
        var release = publishCompiled(releaseService, releaseStore, jdbc, transaction, SIGNAL_RELEASE, source, dependencies);
        app.getBean(TestReservationDirectory.class).compiledSha.set(release.compiledSha256());
        assertEquals(1, activations.activate(OWNER, SIGNAL_DEFINITION,
                new ActivationCommand(SIGNAL_RELEASE, 0L, "activate-callback-fixture")).getRevision());
        var reservations = app.getBean(ProcessEventSubscriptionStore.class);
        var bindings = app.getBean(ProcessInstanceBindingStore.class);
        var nodes = app.getBean(ProcessNodeExecutionStore.class);
        var guard = app.getBean(AutomationInstanceGuardCommandInterceptor.class);
        var lifecycle = app.getBean(ProcessNodeLifecycleListener.class);
        var worker = new FlowableWorkerService(engine, dmn, new ProcessModelValidator(), registry,
                new FlowableWorkerService.AutomationWorkerParticipants(bindings, releaseStore, guard, nodes, lifecycle));
        var root = execution.startAutomation(OWNER, new AutomationStartCommand(SIGNAL_DEFINITION, "callback-business", "start-callback",
                "callback-correlation", Map.of("caseRef", "case-101"), Map.of())).getInstanceId();
        var jobs = engine.getManagementService();
        var runtime = engine.getRuntimeService();
        jobs.executeJob(jobs.createJobQuery().processInstanceId(root).singleResult().getId());
        var executor = engine.getAsyncExecutor();
        int longWait = (int) Duration.ofMinutes(5).toMillis();
        engine.setAsyncExecutorActivate(true);
        executor.setDefaultAsyncJobAcquireWaitTimeInMillis(longWait);
        executor.setDefaultTimerJobAcquireWaitTimeInMillis(longWait);
        executor.setResetExpiredJobsInterval(longWait);
        executor.start();
        var lease = worker.acquireAutomation(registration.actor(), new AutomationWorkerAcquireCommand(CALLBACK_CAPABILITY, 1));
        assertEquals(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, lease.getStatus());
        var task = lease.getTask();
        var token = (String) task.getInput().get(TOKEN_FIELD);
        assertNotNull(token);
        var digest = ProcessEventSubscriptionStore.tokenSha256(token);
        var reserved = reservations.find(TENANT, digest).orElseThrow();
        assertEquals(EventSubscriptionBO.State.RESERVED, reserved.getState());
        assertEquals(root, reserved.getInstanceId());
        assertEquals(root, reserved.getParentScopeId());
        assertEquals(WAIT_ROUTE, reserved.getRouteKey());
        assertEquals(WAIT_SOURCE, reserved.getSourceRef());
        assertEquals(0, runtime.createEventSubscriptionQuery().processInstanceId(root).count());

        var signal = signal("worker-callback", database.now(), token, true);
        var foreignIssuer = ProcessActor.verified(TENANT, ProcessActor.SubjectType.SERVICE, PRODUCER.subjectId(), "https://idp.test/other");
        var foreignTenant = ProcessActor.verified(TENANT + 1, ProcessActor.SubjectType.SERVICE, PRODUCER.subjectId(), "https://idp.test/main");
        assertThrows(AccessDeniedException.class, () -> events.receive(foreignIssuer, signal));
        assertThrows(ProcessResourceNotFoundException.class, () -> events.receive(foreignTenant, signal));
        String unknownToken = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        assertThrows(ProcessResourceNotFoundException.class, () -> events.receive(PRODUCER, signal("unknown-callback", signal.getOccurredAt(), unknownToken, true)));
        var received = events.receive(PRODUCER, signal);
        assertEquals("RECEIVED", received.getState());
        assertEquals(received, events.receive(PRODUCER, signal));
        assertEquals(received, events.advance(TENANT, received.getReceiptId()));
        assertEquals(AutomationOperationException.Code.TIANJI_EVENT_CONFLICT, assertThrows(AutomationOperationException.class,
                () -> events.receive(PRODUCER, signal(signal.getEventId(), signal.getOccurredAt(), token, false))).code());
        assertEquals(reserved, reservations.find(TENANT, digest).orElseThrow());
        var competing = events.receive(PRODUCER, signal("worker-callback-competing", database.now(), token, false));
        assertEquals("RECEIVED", competing.getState());
        assertTrue(received.getReceivedAt().isBefore(competing.getReceivedAt()));

        var completion = new AutomationWorkerCompletionCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), "complete-callback-outbound", task.getEffectKey(), "callback-business-receipt", Map.of());
        assertEquals(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, worker.completeAutomation(registration.actor(), completion).getOutcome());
        await().atMost(Duration.ofSeconds(10)).until(() -> reservations.find(TENANT, digest).orElseThrow().getState()
                == EventSubscriptionBO.State.BOUND);
        var bound = reservations.find(TENANT, digest).orElseThrow();
        assertEquals(EventSubscriptionBO.State.BOUND, bound.getState());
        assertEquals(1, runtime.createEventSubscriptionQuery().id(bound.getNativeSubscriptionId()).count());
        int eventsBefore = outboxCount(jdbc);
        failAfterMessage.set(true);
        assertThrows(InjectedRollback.class, () -> events.advance(TENANT, received.getReceiptId()));
        assertFalse(failAfterMessage.get());
        assertEquals(bound, reservations.find(TENANT, digest).orElseThrow());
        assertEquals(1, runtime.createEventSubscriptionQuery().id(bound.getNativeSubscriptionId()).count());
        assertEquals(1, runtime.createProcessInstanceQuery().processInstanceId(root).count());
        assertEquals(received, events.receive(PRODUCER, signal));
        var stillWaiting = transaction.execute(status -> nodes.find(TENANT, root, bound.getNodeExecutionRef()).orElseThrow());
        assertEquals(StateEnum.WAITING, stillWaiting.getState());
        assertEquals(Map.of("caseRef", "case-101"), stillWaiting.getInput());
        assertNull(stillWaiting.getOutput());
        assertEquals(eventsBefore, outboxCount(jdbc));
        var bothReady = new CountDownLatch(2);
        var advanceTogether = new CountDownLatch(1);
        ProcessEventReceiptDTO applied;
        try (var competingWorkers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = competingWorkers.submit(() -> {
                bothReady.countDown();
                assertTrue(advanceTogether.await(5, TimeUnit.SECONDS));
                return events.advance(TENANT, received.getReceiptId());
            });
            var second = competingWorkers.submit(() -> {
                bothReady.countDown();
                assertTrue(advanceTogether.await(5, TimeUnit.SECONDS));
                return events.advance(TENANT, competing.getReceiptId());
            });
            assertTrue(bothReady.await(5, TimeUnit.SECONDS));
            advanceTogether.countDown();
            applied = first.get(10, TimeUnit.SECONDS);
            assertTrue(Set.of("IGNORED_LATE", "RECEIVED").contains(second.get(10, TimeUnit.SECONDS).getState()));
        }
        assertEquals("APPLIED", applied.getState());
        assertEquals(root, applied.getInstanceId());
        assertEquals(bound.getNodeExecutionRef(), applied.getNodeExecutionRef());
        assertEquals(EventSubscriptionBO.State.CONSUMED, reservations.find(TENANT, digest).orElseThrow().getState());
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(root).count());
        var finished = transaction.execute(status -> nodes.find(TENANT, root, bound.getNodeExecutionRef()).orElseThrow());
        assertEquals(StateEnum.COMPLETED, finished.getState());
        var expectedOutput = Map.of("approved", true, "comment", "producer-note", "caseRef", "case-101");
        assertEquals(expectedOutput, finished.getOutput());
        var permit = new ProcessExecutionService.AutomationObservationPermitVO(true, false, true, false, false);
        assertEquals(expectedOutput, execution.observeAutomation(OWNER, root, permit).getOutput());
        assertEquals(applied, events.receive(PRODUCER, signal));
        assertEquals(applied, events.advance(TENANT, applied.getReceiptId()));
        assertEquals("IGNORED_LATE", events.advance(TENANT, competing.getReceiptId()).getState());
        var late = events.receive(PRODUCER, signal("late-worker-callback", database.now(), token, false));
        assertEquals("IGNORED_LATE", events.advance(TENANT, late.getReceiptId()).getState());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_event_inbox WHERE tenant_id=? AND wait_token_sha256=? AND state='APPLIED'
                """, Integer.class, TENANT, digest));
        assertTrue(jdbc.queryForList("""
                SELECT to_jsonb(s)::text FROM tianji_process.tianji_process_event_subscription s WHERE token_sha256=?
                UNION ALL
                SELECT to_jsonb(i)::text FROM tianji_process.tianji_process_event_inbox i WHERE wait_token_sha256=?
                UNION ALL
                SELECT payload FROM tianji_process.egon_cola_outbox_message WHERE channel=? AND payload::jsonb->>'instanceId'=?
                """, String.class, digest, digest, ProcessLifecycleEventPublisher.CHANNEL, root).stream().noneMatch(value -> value.contains(token)));
    }

    private static ProcessEventCommand signal(String id, Instant occurredAt, String token, boolean approved) {
        return new ProcessEventCommand(id, SIGNAL_DEFINITION, WAIT_ROUTE, 1, "Received", occurredAt, "callback-correlation",
                token, Map.of("approved", approved, "comment", "producer-note"));
    }

    private static void verifyRejectedSignalMappings(AutomationModelSourceVO source,
            AutomationModelValidator.DependencySnapshotVO dependencies, AutomationNodeBindingVO outbound, AutomationNodeBindingVO wait) {
        assertRejectedSignalMappings("OVERLAPPING_MAPPING_TARGET", source, dependencies, outbound, wait,
                List.of(new MappingVO("approved", "event.approved"), new MappingVO("comment", "event.comment"),
                        new MappingVO("caseRef", "input.caseRef")));
        assertRejectedSignalMappings("EVENT_WAIT_OUTPUT_NOT_CONSTRUCTIBLE", source, dependencies, outbound, wait,
                List.of(new MappingVO("caseRef", "input.caseRef")));
    }

    private static void assertRejectedSignalMappings(String code, AutomationModelSourceVO source,
            AutomationModelValidator.DependencySnapshotVO dependencies, AutomationNodeBindingVO outbound, AutomationNodeBindingVO wait,
            List<MappingVO> proposedMappings) {
        var badWait = new AutomationNodeBindingVO(WAIT_NODE, wait.kind(), wait.inputSchema(), wait.outputSchema(), proposedMappings,
                wait.outputMapping(), wait.policy(), null, null, null, null, null, null);
        var bad = new AutomationModelSourceVO(source.profile(), source.models(), source.inputSchema(), source.outputSchema(),
                List.of(outbound, badWait), source.eventRoutes());
        var failure = assertThrows(InvalidAutomationModelException.class, () -> new AutomationModelValidator().validate(bad, dependencies));
        assertEquals(List.of(new AutomationModelValidator.ModelIssueVO(WAIT_ROUTE, code)), failure.issues());
    }

    private static ProcessSchemaVO fixtureSchema(Map<String, Object> fields, List<String> required) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", fields, "required", required, "additionalProperties", false));
    }

    private static void verifyExplicitCancellation(ConfigurableApplicationContext app, JdbcTemplate jdbc, TransactionTemplate transaction,
            SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn, ProcessReleaseService releases,
            ProcessExecutionService execution, VerifiedAutomationModelsVO verified) {
        var source = jdbc.getDataSource();
        var manager = (DataSourceTransactionManager) transaction.getTransactionManager();
        var cancellationReceipts = new ProcessCancellationReceiptStore(source, SCHEMA);
        cancellationReceipts.verifyStorage();
        var control = new ProcessControlService(engine, dmn, new ProcessModelValidator(), releases,
                new ProcessControlNativeAccess(source, engine), cancellationReceipts, new AgentDispatchStore(source, manager, SCHEMA),
                app.getBean(ProcessControlService.AutomationControlParticipants.class));
        var guard = app.getBean(AutomationInstanceGuardCommandInterceptor.class);
        var reservations = app.getBean(ProcessEventSubscriptionStore.class);
        var nodes = app.getBean(ProcessNodeExecutionStore.class);
        var jobs = engine.getManagementService();
        var runtime = engine.getRuntimeService();
        var first = startForCancellation(execution, "first");
        var second = startForCancellation(execution, "second");
        jobs.executeJob(jobs.createJobQuery().processInstanceId(first).singleResult().getId());
        jobs.executeJob(jobs.createJobQuery().processInstanceId(second).singleResult().getId());
        var firstToken = transaction.execute(status -> {
            guard.lockKnownRoot(TENANT, first);
            return reserve(reservations, first, WAIT_NODE).subscription();
        });
        var secondToken = transaction.execute(status -> {
            guard.lockKnownRoot(TENANT, second);
            return reserve(reservations, second, WAIT_NODE).subscription();
        });
        var parkId = verified.controlBindings().occurrenceSlots().get("park").activityId();
        var park = jobs.createTimerJobQuery().processInstanceId(first).list().stream()
                .filter(job -> parkId.equals(job.getElementId())).findFirst().orElseThrow();
        jobs.executeJob(jobs.moveTimerToExecutableJob(park.getId()).getId());
        var bound = reservations.find(TENANT, firstToken.getTokenSha256()).orElseThrow();
        assertEquals(EventSubscriptionBO.State.BOUND, bound.getState());
        assertEquals(EventSubscriptionBO.State.RESERVED, secondToken.getState());

        transaction.executeWithoutResult(status -> {
            guard.lockKnownRoot(TENANT, first);
            var attempt = nodes.find(TENANT, first, bound.getNodeExecutionRef()).orElseThrow().getAttemptScopeId();
            assertEquals(1, runtime.createExecutionQuery().processInstanceId(first).executionId(attempt).count());
            var nested = reservations.reserve(TENANT, first, attempt, WAIT_NODE, UUID.randomUUID(), WAIT_ROUTE, 1,
                    WAIT_SOURCE, reservations.now().plusSeconds(60)).subscription();
            assertEquals(1, reservations.closeScopes(TENANT, first, Set.of(attempt)));
            assertEquals(0, reservations.closeScopes(TENANT, first, Set.of(attempt)));
            assertEquals(EventSubscriptionBO.State.CLOSED,
                    reservations.find(TENANT, nested.getTokenSha256()).orElseThrow().getState());
            assertEquals(bound, reservations.find(TENANT, bound.getTokenSha256()).orElseThrow());
        });

        var query = new GetProcessInstanceQuery(first);
        var before = control.control(OWNER, query);
        assertEquals(0, cancellationEventCount(jdbc, first));
        var command = new CancelProcessCommand(first, "cancel-compiled-wait", before.version(), "Fixture cancellation");
        var canceled = control.cancel(OWNER, command);
        assertTrue(canceled.accepted());
        assertEquals(ProcessInstanceStatusEnum.CANCELLED, canceled.status());
        assertEquals(canceled, control.cancel(OWNER, command));
        assertEquals(canceled.resultingVersion(), control.control(OWNER, query).version());
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(first).count());
        assertEquals(0, runtime.createEventSubscriptionQuery().id(bound.getNativeSubscriptionId()).count());
        var closed = reservations.find(TENANT, bound.getTokenSha256()).orElseThrow();
        assertEquals(EventSubscriptionBO.State.CLOSED, closed.getState());
        assertEquals(bound.getRevision() + 1, closed.getRevision());
        assertNotNull(closed.getClosedAt());
        assertEquals(StateEnum.CANCELLED, transaction.execute(status ->
                nodes.find(TENANT, first, bound.getNodeExecutionRef()).orElseThrow().getState()));
        assertEquals(1, cancellationEventCount(jdbc, first));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_cancel_receipt detail
                JOIN tianji_process.tianji_process_command_receipt base
                  USING (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
                WHERE detail.tenant_id=? AND detail.command_id=? AND detail.process_instance_id=?
                    AND detail.accepted AND detail.result_status='CANCELLED' AND base.state='APPLIED' AND base.resource_id=?
                """, Integer.class, TENANT, command.commandId(), first, first));
        assertEquals(secondToken, reservations.find(TENANT, secondToken.getTokenSha256()).orElseThrow());
        assertEquals(1, runtime.createProcessInstanceQuery().processInstanceId(second).count());
        assertEquals(0, cancellationEventCount(jdbc, second));
    }

    private static String startForCancellation(ProcessExecutionService execution, String suffix) {
        return execution.startAutomation(OWNER, new AutomationStartCommand(COMPILED_DEFINITION, "cancel-business-" + suffix,
                "cancel-start-" + suffix, "cancel-correlation-" + suffix, Map.of(), Map.of())).getInstanceId();
    }

    private static int cancellationEventCount(JdbcTemplate jdbc, String instance) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM tianji_process.egon_cola_outbox_message
                WHERE channel=? AND payload::jsonb->>'type'='INSTANCE_CANCELLED' AND payload::jsonb->>'instanceId'=?
                """, Integer.class, ProcessLifecycleEventPublisher.CHANNEL, instance);
    }

    private static void verifyScheduledAdmission(ProcessEventSchedule schedule, AtomicReference<Instant> clock, ProcessEventInboxStore inbox,
            ProcessEventApplicationService events, AutomationActivationStore activationStore, ProcessReleaseService releaseService,
            AutomationReleaseStore releaseStore, AutomationActivationService activations, JdbcTemplate jdbc, TransactionTemplate transaction,
            Instant scheduleStart) {
        var active = activationStore.current(TENANT, COMPILED_DEFINITION).orElseThrow();
        clock.set(active.getActivatedAt().plusSeconds(2));
        var fire = schedule.next(TENANT, COMPILED_DEFINITION, SCHEDULE_ROUTE, 1).orElseThrow();
        var source = new ProcessEventSchedule.ScheduledSourceVO(PRODUCER, COMPILED_DEFINITION, SCHEDULE_ROUTE, 1, ignored -> Map.of());
        ProcessEventSchedule.ScheduleRegistration registration = () -> List.of(source);
        schedule.advanceRegistered(registration);
        assertTrue(inbox.findOriginal(PRODUCER, fire.fireKey()).isEmpty());
        assertTrue(schedule.next(TENANT, COMPILED_DEFINITION, SCHEDULE_ROUTE, 2).isEmpty());
        clock.set(fire.scheduledAt());
        assertThrows(AccessDeniedException.class, () -> schedule.admit(fire, OWNER, Map.of()));
        schedule.advanceRegistered(registration);
        var row = inbox.findOriginal(PRODUCER, fire.fireKey()).orElseThrow();
        assertEquals("RECEIVED", row.getState());
        assertEquals(fire.scheduledAt(), row.getOccurredAt());
        assertEquals(COMPILED_RELEASE, row.getReleaseKey());
        assertEquals(1, row.getActivationRevision());
        assertEquals(HASH, row.getRouteSha256());
        assertNull(row.getInstanceId());
        assertEquals(row.getReceiptId(), schedule.admit(fire, PRODUCER, Map.of()).getReceiptId());
        schedule.advanceRegistered(registration);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_event_inbox WHERE tenant_id=? AND route_key=?",
                Integer.class, TENANT, SCHEDULE_ROUTE));

        var future = schedule.next(TENANT, COMPILED_DEFINITION, SCHEDULE_ROUTE, 1).orElseThrow();
        var replacement = "compiled-wait-cutover";
        publishCompiledWait(releaseService, releaseStore, jdbc, transaction, replacement, scheduleStart);
        assertEquals(2, activations.activate(OWNER, COMPILED_DEFINITION, new ActivationCommand(replacement, 1L,
                "activate-compiled-cutover")).getRevision());
        clock.set(future.scheduledAt());
        assertEquals(AutomationOperationException.Code.TIANJI_PROCESS_ACTIVATION_CONFLICT,
                assertThrows(AutomationOperationException.class, () -> schedule.admit(future, PRODUCER, Map.of())).code());
        var stale = new ProcessEventCommand(future.fireKey(), COMPILED_DEFINITION, SCHEDULE_ROUTE, 1, "Scheduled", future.scheduledAt(),
                future.fireKey(), null, Map.of());
        assertEquals(AutomationOperationException.Code.TIANJI_EVENT_ROUTE_CHANGED, assertThrows(AutomationOperationException.class,
                () -> events.receiveScheduled(PRODUCER, stale, COMPILED_RELEASE, 1, HASH)).code());
        assertTrue(inbox.findOriginal(PRODUCER, future.fireKey()).isEmpty());
        schedule.advanceRegistered(registration);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_event_inbox WHERE tenant_id=? AND route_key=?",
                Integer.class, TENANT, SCHEDULE_ROUTE));
    }

    private static AutomationReleaseStore.ImmutableReleaseVO publishCompiledWait(ProcessReleaseService releaseService,
            AutomationReleaseStore releaseStore, JdbcTemplate jdbc, TransactionTemplate transaction, String releaseVersion, Instant scheduleStart) {
        var empty = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));
        var route = new EventRouteVO(WAIT_ROUTE, 1, "Received", "SIGNAL", WAIT_SOURCE, empty, List.of(),
                Duration.ofSeconds(60), null, WAIT_NODE, null);
        var scheduled = new EventRouteVO(SCHEDULE_ROUTE, 1, "Scheduled", "START", WAIT_SOURCE, empty, List.of(), Duration.ofSeconds(60),
                COMPILED_DEFINITION, null, new ScheduleVO(scheduleStart, Duration.ofSeconds(15), null, 30));
        var source = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", COMPILED_DEFINITION, COMPILED_XML)), empty, empty,
                List.of(waitBinding("park", "TIMER", empty, Duration.ofSeconds(30), Duration.ofSeconds(45)),
                        waitBinding(WAIT_NODE, "EVENT_WAIT", empty, Duration.ofSeconds(2), Duration.ofSeconds(20))), List.of(route, scheduled));
        var registration = new AutomationModelValidator.EventRegistrationVO(route, HASH);
        var dependencies = new AutomationModelValidator.DependencySnapshotVO(TENANT, Map.of(), Map.of(), Set.of(), Map.of(),
                Map.of(new AutomationModelValidator.VersionedRefVO(WAIT_ROUTE, 1), registration,
                        new AutomationModelValidator.VersionedRefVO(SCHEDULE_ROUTE, 1), new AutomationModelValidator.EventRegistrationVO(scheduled, HASH)));
        return publishCompiled(releaseService, releaseStore, jdbc, transaction, releaseVersion, source, dependencies);
    }

    private static AutomationReleaseStore.ImmutableReleaseVO publishCompiled(ProcessReleaseService releaseService,
            AutomationReleaseStore releaseStore, JdbcTemplate jdbc, TransactionTemplate transaction, String releaseVersion,
            AutomationModelSourceVO source, AutomationModelValidator.DependencySnapshotVO dependencies) {
        var compiled = new AutomationModelCompiler().compile(new AutomationModelValidator().validate(source, dependencies));
        var prepared = releaseStore.prepare(compiled);
        var draftStore = new ProcessDraftStore(jdbc, SCHEMA);
        return transaction.execute(status -> {
            var now = draftStore.now();
            var content = AutomationReleaseStore.copyContent(source);
            byte[] canonical = new ProcessDraftContentCodec().canonicalContent(content);
            var draft = new ProcessDraftBO().setOwner(OWNER).setDraftId("pd-" + releaseVersion).setDefinitionKey(compiled.rootDefinitionKey())
                    .setTitle("Compiled wait fixture").setState(DraftSummaryDTO.StateEnum.EDITING).setRevision(1).setContent(content)
                    .setContentSha256(ProcessTypedJson.sha256("AUTOMATION_DRAFT_CONTENT_V2", canonical)).setCreatedAt(now).setUpdatedAt(now)
                    .setLastCommandId("create-compiled-wait");
            draftStore.insert(draft, new String(canonical, StandardCharsets.UTF_8));
            releaseService.publishCompiled(OWNER, releaseVersion, prepared);
            releaseStore.insert(draft, releaseVersion, prepared, now);
            releaseStore.seal(draft, releaseVersion, "publish-" + releaseVersion, now);
            return releaseStore.find(TENANT, releaseVersion).orElseThrow();
        });
    }

    private static AutomationNodeBindingVO waitBinding(String node, String kind, ProcessSchemaVO schema, Duration attempt, Duration hard) {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(hard, attempt, Duration.ofSeconds(1), end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        return new AutomationNodeBindingVO(node, kind, schema, schema, List.of(), List.of(), policy, null, null, null, null, null, null);
    }

    private static String startCompiledWait(ConfigurableApplicationContext app, SpringProcessEngineConfiguration engine,
            JdbcTemplate jdbc, TransactionTemplate transaction, String nativeDefinitionId) {
        var bindings = app.getBean(ProcessInstanceBindingStore.class);
        var receipts = new ProcessCommandReceiptStore(jdbc, SCHEMA);
        var manifest = Map.<String, Object>of();
        var configSha = ProcessTypedJson.sha256("AUTOMATION_EFFECTIVE_CONFIGURATION_V2", ProcessTypedJson.canonicalBytes(manifest));
        var inputSha = ProcessTypedJson.sha256("AUTOMATION_ROOT_INPUT_V2", ProcessTypedJson.canonicalBytes(Map.of()));
        String business = "compiled-wait-business";
        String correlation = "compiled-wait-correlation";
        String command = "start-compiled-wait";
        var resolved = ProcessTypedJson.sha256("AUTOMATION_RESOLVED_START_V2", ProcessTypedJson.canonicalBytes(Map.of(
                "rawRequestSha256", HASH, "tenantId", TENANT, "definitionKey", COMPILED_DEFINITION, "releaseVersion", COMPILED_RELEASE,
                "activationRevision", 1, "configurationSha256", configSha, "businessKey", business, "correlationId", correlation,
                "normalizedInputSha256", inputSha)));
        return transaction.execute(status -> {
            assertTrue(receipts.insert(OWNER, ProcessCommandReceiptStore.Kind.START, command, resolved, COMPILED_RELEASE, correlation));
            var runtime = engine.getRuntimeService();
            String root = engine.getIdGenerator().getNextId();
            assertEquals(root, runtime.createProcessInstanceBuilder().predefineProcessInstanceId(root).processDefinitionId(nativeDefinitionId)
                    .tenantId(Long.toString(TENANT)).businessKey(business).variables(Map.of(ProcessReleaseService.AUTOMATION_BINDING_REF, root))
                    .start().getId());
            receipts.apply(OWNER, ProcessCommandReceiptStore.Kind.START, command, root, null);
            var submitted = receipts.lock(OWNER, ProcessCommandReceiptStore.Kind.START, command).createdAt();
            bindings.insert(new AutomationInstanceBindingBO().setTenantId(TENANT).setInstanceId(root).setReleaseKey(COMPILED_RELEASE)
                    .setDefinitionKey(COMPILED_DEFINITION).setOwner(OWNER).setEffectiveManifest(manifest).setConfigurationSha256(configSha)
                    .setStartRequestSha256(HASH).setSubmittedAt(submitted).setActivationRevision(1).setSourceCommandId(command)
                    .setResolvedStartSha256(resolved).setAdmissionKind(AutomationInstanceBindingBO.AdmissionKind.DIRECT).setAdmittedAt(submitted)
                    .setProof(new AutomationAdmissionProofVO(HASH, "compiled-fixture", HASH, submitted, submitted.plusSeconds(20),
                            1, HASH, HASH, HASH, Map.of())));
            runtime.setVariableLocal(root, "tianjiAutomationRootInputs", engine.getVariableJsonMapper().readTree("{}"));
            return root;
        });
    }

    private static EventSubscriptionBO reserveAndCheck(TransactionTemplate transaction, AutomationInstanceGuardCommandInterceptor guard,
            ProcessEventSubscriptionStore reservations, JdbcTemplate owner, String instance) {
        var rolledBack = new AtomicReference<String>();
        assertThrows(InjectedRollback.class, () -> transaction.executeWithoutResult(status -> {
            guard.lockKnownRoot(TENANT, instance);
            var wait = reserve(reservations, instance, WAIT_NODE);
            rolledBack.set(wait.subscription().getTokenSha256());
            throw new InjectedRollback();
        }));
        assertTrue(reservations.find(TENANT, rolledBack.get()).isEmpty());

        var wait = transaction.execute(status -> {
            var deadline = reservations.now().plusSeconds(60);
            assertThrows(IllegalStateException.class, () -> reservations.reserve(TENANT, instance, instance, WAIT_NODE,
                    UUID.randomUUID(), WAIT_ROUTE, 1, WAIT_SOURCE, deadline));
            guard.lockKnownRoot(TENANT, instance);
            var open = reserve(reservations, instance, WAIT_NODE);
            assertEquals(open.subscription(), reservations.findOpen(TENANT, instance, instance, WAIT_NODE).orElseThrow());
            assertTrue(reservations.findOpen(TENANT, instance, "foreign-parent", WAIT_NODE).isEmpty());
            assertThrows(IllegalStateException.class, () -> reservations.findOpen(TENANT + 1, instance, instance, WAIT_NODE));
            assertThrows(IllegalStateException.class, () -> reserve(reservations, instance, WAIT_NODE));
            var other = reserve(reservations, instance, "other-wait").subscription();
            var closed = reservations.close(other);
            assertEquals(EventSubscriptionBO.State.CLOSED, closed.getState());
            assertEquals(2, closed.getRevision());
            assertNotNull(closed.getClosedAt());
            assertNull(closed.getNativeSubscriptionId());
            assertTrue(reservations.findOpen(TENANT, instance, instance, "other-wait").isEmpty());
            assertThrows(IllegalStateException.class, () -> reservations.consume(closed));
            return open;
        });
        var row = wait.subscription();
        assertEquals(EventSubscriptionBO.State.RESERVED, row.getState());
        assertEquals(1, row.getRevision());
        assertNull(row.getNativeSubscriptionId());
        assertTrue(wait.token().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(row.getTokenSha256(), ProcessEventSubscriptionStore.tokenSha256(wait.token()));
        assertTrue(reservations.find(TENANT + 1, row.getTokenSha256()).isEmpty());
        var storedRow = owner.queryForObject("""
                SELECT to_jsonb(s)::text FROM tianji_process.tianji_process_event_subscription s
                WHERE tenant_id=? AND token_sha256=?
                """, String.class, TENANT, row.getTokenSha256());
        assertFalse(storedRow.contains(wait.token()), "Only the token digest is persisted");
        return row;
    }

    private static ProcessEventSubscriptionStore.ReservedEventWaitVO reserve(ProcessEventSubscriptionStore reservations,
            String instance, String target) {
        return reservations.reserve(TENANT, instance, instance, target, UUID.randomUUID(), WAIT_ROUTE, 1, WAIT_SOURCE,
                reservations.now().plusSeconds(60));
    }

    private static ProcessNodeExecutionBO storeWaitNode(ProcessNodeExecutionStore nodes, AutomationInstanceBindingBO binding,
            EventSubscriptionBO reservation, String attemptScopeId, String subscriptionId) {
        var input = Map.<String, Object>of();
        var digest = ProcessTypedJson.sha256("AUTOMATION_NODE_INPUT_V2", HASH.getBytes(StandardCharsets.UTF_8),
                ProcessTypedJson.canonicalBytes(input));
        var row = new ProcessNodeExecutionBO().setTenantId(TENANT).setInstanceId(binding.getInstanceId())
                .setOccurrenceId("wait-occurrence").setAttempt(1).setNodeExecutionRef("test-signal-node").setNodeId(WAIT_NODE)
                .setKind(KindEnum.EVENT_WAIT).setParentScopeId(reservation.getParentScopeId()).setAttemptScopeId(attemptScopeId)
                .setNativeSubscriptionId(subscriptionId).setInput(input).setInputSha256(digest).setInputSchemaSha256(HASH)
                .setOutputSchemaSha256(HASH).setConfigurationSha256(binding.getConfigurationSha256()).setState(StateEnum.WAITING)
                .setRevision(1).setSideEffectOutcome(SideEffectOutcomeEnum.NONE).setStartedAt(nodes.now())
                .setNodeDeadlineAt(reservation.getDeadlineAt()).setAttemptDeadlineAt(reservation.getDeadlineAt());
        nodes.insertOccurrence(row);
        return nodes.find(TENANT, binding.getInstanceId(), row.getNodeExecutionRef()).orElseThrow();
    }

    private static void seedRelease(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO tianji_process.tianji_process_draft
                    (tenant_id,draft_id,definition_key,title,owner_type,owner_issuer,owner_id,state,revision,content_json,
                     content_sha256,last_command_id,created_at,updated_at)
                VALUES (?,'pd-outbox',?,'Outbox fixture','USER',?,?,'EDITING',1,
                    '{"models":null,"inputSchemaSource":null,"outputSchemaSource":null,"nodeBindingsSource":null,"eventRoutesSource":null}',
                    ?,'create-draft',statement_timestamp(),statement_timestamp())
                """, TENANT, DEFINITION, OWNER.issuerFingerprint(), OWNER.subjectId(), HASH);
        jdbc.update("""
                INSERT INTO tianji_process.tianji_process_release
                    (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at)
                VALUES (?,?,'ACTIVE',?,'USER',?,statement_timestamp())
                """, TENANT, RELEASE, HASH, OWNER.subjectId());
        jdbc.update("""
                INSERT INTO tianji_process.tianji_process_automation_release
                    (tenant_id,release_key,root_definition_key,source_descriptor,source_sha256,compiler_version,compiled_sha256,
                     created_at,source_draft_id,source_draft_revision,draft_content_sha256,dependency_manifest,dependency_sha256)
                VALUES (?,?,?,'{}',?,'test-compiler',?,statement_timestamp(),'pd-outbox',1,?,'{}',?)
                """, TENANT, RELEASE, DEFINITION, HASH, HASH, HASH, HASH);
        jdbc.update("""
                INSERT INTO tianji_process.tianji_process_automation_activation
                    (tenant_id,definition_key,revision,release_key,actor_type,actor_issuer,actor_id,command_id,request_sha256,
                     source_sha256,compiled_sha256,dependency_sha256,authorization_sha256,dependency_evidence_sha256,
                     release_signature_sha256,trust_generation,admission_proof,observed_at,proof_valid_until,activated_at)
                VALUES (?,?,1,?,'USER',?,?,'activate-outbox',?,?,?,?,?,?,?,1,'{}',statement_timestamp(),
                     statement_timestamp()+interval '1 minute',statement_timestamp())
                """, TENANT, DEFINITION, RELEASE, OWNER.issuerFingerprint(), OWNER.subjectId(), HASH, HASH, HASH, HASH, HASH, HASH, HASH);
    }

    private static void seedBinding(TransactionTemplate transaction, JdbcTemplate jdbc, ProcessInstanceBindingStore bindings, String instance) {
        transaction.executeWithoutResult(status -> {
            var now = jdbc.queryForObject("SELECT statement_timestamp()", OffsetDateTime.class).toInstant();
            jdbc.update("""
                    INSERT INTO tianji_process.tianji_process_command_receipt
                        (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id,request_sha256,release_key,
                         correlation_id,state,resource_id,created_at)
                    VALUES (?,'USER',?,?,'START','outbox-start',?,?,'outbox-correlation','APPLIED',?,statement_timestamp())
                    """, TENANT, OWNER.issuerFingerprint(), OWNER.subjectId(), HASH, RELEASE, instance);
            var manifest = Map.<String, Object>of();
            var configHash = ProcessTypedJson.sha256("AUTOMATION_EFFECTIVE_CONFIGURATION_V2", ProcessTypedJson.canonicalBytes(manifest));
            bindings.insert(new AutomationInstanceBindingBO().setTenantId(TENANT).setInstanceId(instance).setReleaseKey(RELEASE)
                    .setDefinitionKey(DEFINITION).setOwner(OWNER).setEffectiveManifest(manifest).setConfigurationSha256(configHash)
                    .setStartRequestSha256(HASH).setSubmittedAt(now).setActivationRevision(1).setSourceCommandId("outbox-start")
                    .setResolvedStartSha256(HASH).setAdmissionKind(AutomationInstanceBindingBO.AdmissionKind.DIRECT).setAdmittedAt(now)
                    .setProof(new AutomationAdmissionProofVO(HASH, "fixture", HASH, now, now.plusSeconds(20), 1,
                            HASH, HASH, HASH, Map.of())));
        });
    }

    private static int outboxCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM tianji_process.egon_cola_outbox_message WHERE channel=?",
                Integer.class, ProcessLifecycleEventPublisher.CHANNEL);
    }

    private static DatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Process lifecycle testing requires an empty loopback database and a dedicated role");
        }
        var source = new DriverManagerDataSource(url);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        source.setConnectionProperties(properties);
        var owner = new JdbcTemplate(source);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb
                    AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        var process = Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(false)
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").target("15").cleanDisabled(true).load();
        assertEquals(15, process.migrate().migrationsExecuted);
        process.validate();
        var outbox = Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(false)
                .locations("classpath:db/transactional-outbox/postgresql").baselineVersion("0").cleanDisabled(true).load();
        outbox.baseline();
        assertTrue(outbox.migrate().migrationsExecuted > 0);
        outbox.validate();
        var scopedUrl = url + "?currentSchema=" + SCHEMA;
        try (var provisioner = new SpringApplicationBuilder(Provisioner.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + scopedUrl, "--spring.datasource.username=" + System.getProperty("user.name"),
                "--flowable.database-schema-update=true", "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(provisioner.getBean(RepositoryService.class));
        }
        var quotedRole = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".egon_cola_outbox_message TO " + quotedRole);
        owner.execute("GRANT USAGE ON ALL SEQUENCES IN SCHEMA " + SCHEMA + " TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_command_receipt TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT ON " + SCHEMA + ".tianji_process_instance_binding TO " + quotedRole);
        owner.execute("GRANT UPDATE(created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_event_subscription TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_event_inbox TO " + quotedRole);
        owner.execute("GRANT SELECT ON " + SCHEMA + ".tianji_process_schema_history TO " + quotedRole);
        owner.execute("GRANT SELECT ON " + SCHEMA + ".tianji_case_start_provenance TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT ON " + SCHEMA + ".tianji_process_cancel_receipt TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_agent_dispatch TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_worker_lease_receipt TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT ON " + SCHEMA + ".tianji_process_draft_command TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_node_execution TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT, UPDATE ON " + SCHEMA + ".tianji_process_release_definition, "
                + SCHEMA + ".tianji_process_release, " + SCHEMA + ".tianji_process_draft TO " + quotedRole);
        owner.execute("GRANT SELECT, INSERT ON " + SCHEMA + ".tianji_process_automation_release, "
                + SCHEMA + ".tianji_process_automation_activation TO " + quotedRole);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertFalse(nativeTables.isEmpty());
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            owner.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + SCHEMA + "." + table + " TO " + quotedRole);
        }
        return new DatabaseVO(owner, scopedUrl, role);
    }

    private static ConfigurableApplicationContext running(DatabaseVO database) {
        return new SpringApplicationBuilder(TianjiProcessServerApplication.class, LifecycleDelivery.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + database.url(), "--spring.datasource.username=" + database.role(),
                "--spring.main.banner-mode=off", "--stellar.tianji.process.events.lifecycle.enabled=true",
                "--stellar.tianji.process.events.lifecycle.source-id=" + SOURCE_ID,
                "--stellar.tianji.process.events.lifecycle.destination=" + DESTINATION);
    }

    private record DatabaseVO(JdbcTemplate owner, String url, String role) {
        Instant now() { return owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant(); }
    }

    private static final class TestReleaseAuthority {
        private final DatabaseVO database;
        private final AutomationReleaseStore releases;
        private final KeyPair key;
        private final Instant issued;

        private TestReleaseAuthority(DatabaseVO database, AutomationReleaseStore releases) throws GeneralSecurityException {
            this.database = database;
            this.releases = releases;
            this.key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            this.issued = database.now().minusSeconds(1);
        }

        AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            var material = signed(query, releases.find(TENANT, query.getReleaseVersion()).orElseThrow());
            var observed = database.now();
            return new AutomationAdmissionProofVO(query.sha256(), "scheduled-fixture", HASH, observed, observed.plusSeconds(25),
                    material.currentGeneration(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of());
        }

        AutomationReleaseTrustVerifier.TrustObservationVO signed(AutomationAdmissionQuery query,
                AutomationReleaseStore.ImmutableReleaseVO release) {
            assertEquals(query.getReleaseVersion(), release.releaseVersion());
            try {
                var publicKey = key.getPublic().getEncoded();
                var keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey));
                var expires = issued.plusSeconds(180);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, TENANT, 1,
                        Base64.getEncoder().encodeToString(publicKey), issued.minusSeconds(1), expires);
                var signature = Signature.getInstance("Ed25519");
                signature.initSign(key.getPrivate());
                signature.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, issued, expires,
                        Base64.getEncoder().encodeToString(signature.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class InjectedRollback extends RuntimeException { }

    private static final class TestReservationDirectory implements ProcessNodeLifecycleListener.EventReservationDirectory {
        private final AtomicReference<String> compiledSha = new AtomicReference<>();

        @Override
        public Optional<ProcessNodeLifecycleListener.OutboundEventReservationVO> find(long tenantId, String compiledManifestSha256,
                String workerNodeId, String capabilityKey, long capabilityVersion) {
            if (tenantId != TENANT || !compiledManifestSha256.equals(compiledSha.get()) || !OUTBOUND_NODE.equals(workerNodeId)
                    || !CALLBACK_CAPABILITY.equals(capabilityKey) || capabilityVersion != 1) return Optional.empty();
            return Optional.of(new ProcessNodeLifecycleListener.OutboundEventReservationVO(WAIT_ROUTE, 1, WAIT_NODE, TOKEN_FIELD));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Provisioner { }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleDelivery {
        @Bean("automationEventReservationDirectory")
        TestReservationDirectory fixtureEventReservationDirectory() {
            return new TestReservationDirectory();
        }

        @Bean("processEventSubscriptionStore")
        ProcessEventSubscriptionStore testSubscriptions(OutboxInfrastructure infrastructure) {
            return new ProcessEventSubscriptionStore(new JdbcTemplate(infrastructure.dataSource()), SCHEMA);
        }

        @Bean
        DeliveryHandler testLifecycleDelivery() {
            return new DeliveryHandler() {
                @Override public String channel() { return ProcessLifecycleEventPublisher.CHANNEL; }
                @Override public void validateDestination(String destination) { assertEquals(DESTINATION, destination); }
                @Override public DeliveryResult deliver(DeliveryContext context) {
                    return DeliveryResult.success();
                }
            };
        }
    }
}
