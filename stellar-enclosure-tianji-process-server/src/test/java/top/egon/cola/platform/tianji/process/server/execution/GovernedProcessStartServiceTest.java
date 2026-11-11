package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceBuilder;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Pointer;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Provenance;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartAuthority;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartCommand;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartAuthorizationQuery;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartCommand;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartGrant;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.WorkerRouteProof;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.WorkerRouteQuery;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GovernedProcessStartServiceTest {
    private static final ProcessActor CALLER = new ProcessActor(20, ProcessActor.SubjectType.USER, "operator", "1".repeat(64));
    private static final ProcessActor BRIDGE = new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "worker", "2".repeat(64));
    private static final String CANDIDATE = "3".repeat(64);
    private static final String MANIFEST = "4".repeat(64);
    private static final String MODEL = "5".repeat(64);
    private static final String REGISTRATION = "6".repeat(64);
    private static final String AUTHORIZATION = "7".repeat(64);
    private static final String CHANNEL = "stable";
    private static final String DEFINITION = "example";
    private static final String BUSINESS = "business";
    private static final String COMMAND = "start-1";
    private static final String CORRELATION = "corr-1";
    private static final String RELEASE = "ps-1";
    private static final String INSTANCE = "instance-1";
    private static final GovernedStartPilotProperties NO_PILOTS = new GovernedStartPilotProperties(List.of());
    private static final GovernedStartPilotProperties PILOT = new GovernedStartPilotProperties(List.of(
            new GovernedStartPilotProperties.Pilot(CALLER.tenantId(), DEFINITION, CHANNEL)));
    private static final Instant NOW = Instant.parse("2026-11-11T04:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(10);
    private static final Map<String, Object> INPUTS = Map.of("amount", 7L);
    private static final String RESOLVED = ProcessRequestDigest.hash(List.of(RELEASE, DEFINITION, BUSINESS, CORRELATION), INPUTS);
    private static final String GOVERNED = ProcessRequestDigest.hash(
            List.of("TIANJI_GOVERNED_PROCESS_START_V1", CHANNEL, DEFINITION, BUSINESS, CORRELATION), INPUTS);

    @Test
    void existing_constructor_does_not_initialize_governed_start() {
        try (var fixture = new Fixture(false)) {
            assertTrue(fixture.storeConstruction.constructed().isEmpty());
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.startGoverned(CALLER, command(7)));
            verify(fixture.authority, never()).authorize(any());
            verify(fixture.transactionManager, never()).getTransaction(any());
        }
    }

    @Test
    void exact_pointer_and_worker_commit_native_start_receipt_and_provenance_in_one_transaction() {
        try (var fixture = new Fixture(true)) {
            var result = fixture.service.startGoverned(CALLER, command(7));
            assertEquals(INSTANCE, result.instance().instanceId());
            assertEquals(CHANNEL, result.channel());
            assertEquals(1, result.activationRevision());
            assertEquals(CANDIDATE, result.candidateSha256());

            var order = inOrder(fixture.authority, fixture.transactionManager, fixture.store, fixture.receipts, fixture.builder);
            order.verify(fixture.authority).currentWorker(fixture.workerQuery);
            order.verify(fixture.transactionManager).getTransaction(any(TransactionDefinition.class));
            order.verify(fixture.store).begin(CALLER.tenantId());
            order.verify(fixture.receipts).insert(CALLER, Kind.START, COMMAND, RESOLVED, RELEASE, CORRELATION);
            order.verify(fixture.builder).start();
            order.verify(fixture.receipts).apply(CALLER, Kind.START, COMMAND, INSTANCE, null);
            order.verify(fixture.store).append(CALLER, COMMAND, CORRELATION, fixture.pointer, GOVERNED,
                    RESOLVED, AUTHORIZATION, INSTANCE);
            order.verify(fixture.transactionManager).commit(fixture.status);
            verify(fixture.builder).processDefinitionId(fixture.definition.engineDefinitionId());
            verify(fixture.builder).tenantId(Long.toString(CALLER.tenantId()));
            verify(fixture.builder).variables(INPUTS);
        }
    }

    @Test
    void replay_uses_original_revision_and_rejects_changed_body_without_current_worker() {
        try (var fixture = new Fixture(true)) {
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));
            when(fixture.store.find(CALLER, COMMAND)).thenReturn(Optional.of(
                    new Provenance(fixture.pointer, GOVERNED, RESOLVED, INSTANCE)));
            when(fixture.store.current(CALLER.tenantId(), CHANNEL)).thenReturn(Optional.of(fixture.pointer(2)));

            var result = fixture.service.startGoverned(CALLER, command(7));
            assertEquals(1, result.activationRevision());
            assertEquals(INSTANCE, result.instance().instanceId());
            assertThrows(ProcessConflictException.class, () -> fixture.service.startGoverned(CALLER, command(8)));
            verify(fixture.authority, times(2)).authorize(new StartAuthorizationQuery(CALLER, CHANNEL, DEFINITION, COMMAND));
            verify(fixture.authority, never()).currentWorker(any());
            verify(fixture.store, never()).current(anyLong(), anyString());
            verify(fixture.builder, never()).start();
        }
    }

    @Test
    void explicit_receipt_replays_explicit_start_but_cannot_replay_governed_start() {
        try (var fixture = new Fixture(true)) {
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));
            when(fixture.receipts.insert(CALLER, Kind.START, COMMAND, RESOLVED, RELEASE, CORRELATION)).thenReturn(false);

            assertThrows(ProcessConflictException.class, () -> fixture.service.startGoverned(CALLER, command(7)));
            var explicit = new StartCommand(RELEASE, DEFINITION, BUSINESS, COMMAND, CORRELATION, INPUTS);
            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit).instanceId());
            verify(fixture.builder, never()).start();
        }
    }

    @Test
    void pointer_movement_after_worker_observation_prevents_start() {
        try (var fixture = new Fixture(true)) {
            when(fixture.store.current(CALLER.tenantId(), CHANNEL)).thenReturn(Optional.of(fixture.pointer),
                    Optional.of(fixture.pointer(2)));

            assertThrows(ProcessVersionConflictException.class, () -> fixture.service.startGoverned(CALLER, command(7)));
            verify(fixture.receipts, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
            verify(fixture.builder, never()).start();
            verify(fixture.transactionManager).rollback(fixture.status);
        }
    }

    @Test
    void current_worker_must_match_the_activated_fingerprint() {
        try (var fixture = new Fixture(true)) {
            when(fixture.authority.currentWorker(fixture.workerQuery)).thenReturn(
                    new WorkerRouteProof(fixture.workerQuery, fixture.binding, "8".repeat(64), NOW, EXPIRES));

            assertThrows(AccessDeniedException.class, () -> fixture.service.startGoverned(CALLER, command(7)));
            verify(fixture.receipts, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
            verify(fixture.builder, never()).start();
        }
    }

    @Test
    void expired_admission_after_provenance_rolls_back_native_start_and_receipt() {
        try (var fixture = new Fixture(true)) {
            when(fixture.store.transactionTime()).thenReturn(NOW, NOW, EXPIRES);

            assertThrows(AccessDeniedException.class, () -> fixture.service.startGoverned(CALLER, command(7)));
            var order = inOrder(fixture.builder, fixture.receipts, fixture.store, fixture.transactionManager);
            order.verify(fixture.builder).start();
            order.verify(fixture.receipts).apply(CALLER, Kind.START, COMMAND, INSTANCE, null);
            order.verify(fixture.store).append(CALLER, COMMAND, CORRELATION, fixture.pointer, GOVERNED,
                    RESOLVED, AUTHORIZATION, INSTANCE);
            order.verify(fixture.transactionManager).rollback(fixture.status);
            verify(fixture.transactionManager, never()).commit(fixture.status);
        }
    }

    @Test
    void protected_start_uses_the_same_receipt_digest_and_commits_governed_provenance() {
        try (var fixture = new Fixture(true, PILOT)) {
            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit(RELEASE, 7)).instanceId());
            var order = inOrder(fixture.authority, fixture.store, fixture.receipts, fixture.builder, fixture.transactionManager);
            order.verify(fixture.store).find(CALLER, COMMAND);
            order.verify(fixture.authority).authorize(new StartAuthorizationQuery(CALLER, CHANNEL, DEFINITION, COMMAND));
            order.verify(fixture.store).hasReceipt(CALLER, COMMAND);
            order.verify(fixture.authority).currentWorker(fixture.workerQuery);
            order.verify(fixture.store).begin(CALLER.tenantId());
            order.verify(fixture.receipts).insert(CALLER, Kind.START, COMMAND, RESOLVED, RELEASE, CORRELATION);
            order.verify(fixture.builder).start();
            order.verify(fixture.receipts).apply(CALLER, Kind.START, COMMAND, INSTANCE, null);
            order.verify(fixture.store).append(CALLER, COMMAND, CORRELATION, fixture.pointer, GOVERNED,
                    RESOLVED, AUTHORIZATION, INSTANCE);
            order.verify(fixture.transactionManager).commit(fixture.status);
        }
    }

    @Test
    void protected_start_rejects_an_explicit_release_mismatch_at_the_locked_pointer() {
        try (var fixture = new Fixture(true, PILOT)) {
            assertThrows(ProcessVersionConflictException.class, () -> fixture.service.start(CALLER, explicit("other-release", 7)));
            verify(fixture.store).begin(CALLER.tenantId());
            verify(fixture.store, times(2)).current(CALLER.tenantId(), CHANNEL);
            verify(fixture.authority, never()).currentWorker(any());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_replays_the_exact_historical_governed_release_after_pointer_movement() {
        try (var fixture = new Fixture(true, PILOT)) {
            when(fixture.store.hasReceipt(CALLER, COMMAND)).thenReturn(true);
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));
            when(fixture.store.find(CALLER, COMMAND)).thenReturn(Optional.of(
                    new Provenance(fixture.pointer, GOVERNED, RESOLVED, INSTANCE)));
            when(fixture.store.current(CALLER.tenantId(), CHANNEL)).thenReturn(Optional.of(fixture.pointer(2)));

            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit(RELEASE, 7)).instanceId());
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit("other-release", 7)));
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 8)));
            verify(fixture.authority, never()).currentWorker(any());
            verify(fixture.store, never()).current(anyLong(), anyString());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_authorizes_the_original_channel_after_a_pilot_remap() {
        var remapped = new GovernedStartPilotProperties(List.of(
                new GovernedStartPilotProperties.Pilot(CALLER.tenantId(), DEFINITION, "canary")));
        try (var fixture = new Fixture(true, remapped)) {
            var prior = new Provenance(fixture.pointer, GOVERNED, RESOLVED, INSTANCE);
            when(fixture.store.find(CALLER, COMMAND)).thenReturn(Optional.of(prior));
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));
            var historicalQuery = new StartAuthorizationQuery(CALLER, CHANNEL, DEFINITION, COMMAND);
            when(fixture.authority.authorize(historicalQuery)).thenReturn(
                    new StartGrant(historicalQuery, AUTHORIZATION, NOW, EXPIRES));

            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit(RELEASE, 7)).instanceId());
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 8)));
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit("other-release", 7)));
            verify(fixture.authority, times(3)).authorize(historicalQuery);
            verify(fixture.authority, never()).authorize(new StartAuthorizationQuery(CALLER, "canary", DEFINITION, COMMAND));
            verify(fixture.authority, never()).currentWorker(any());
            verify(fixture.store, never()).current(anyLong(), anyString());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_fails_closed_if_historical_provenance_changes_under_the_fence() {
        var remapped = new GovernedStartPilotProperties(List.of(
                new GovernedStartPilotProperties.Pilot(CALLER.tenantId(), DEFINITION, "canary")));
        try (var fixture = new Fixture(true, remapped)) {
            var prior = new Provenance(fixture.pointer, GOVERNED, RESOLVED, INSTANCE);
            var changed = new Provenance(fixture.pointer(2, "canary"), GOVERNED, RESOLVED, INSTANCE);
            when(fixture.store.find(CALLER, COMMAND)).thenReturn(Optional.of(prior), Optional.of(changed));
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));

            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 7)));
            verify(fixture.authority).authorize(new StartAuthorizationQuery(CALLER, CHANNEL, DEFINITION, COMMAND));
            verify(fixture.store).begin(CALLER.tenantId());
            verify(fixture.authority, never()).currentWorker(any());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_execution_requires_the_exact_assembled_authority_instance() {
        try (var fixture = new Fixture(true, PILOT)) {
            assertTrue(fixture.service.enforcesGovernedStartPilots(PILOT, fixture.authority));
            assertFalse(fixture.service.enforcesGovernedStartPilots(PILOT, mock(GovernedStartAuthority.class)));
            assertFalse(fixture.service.enforcesGovernedStartPilots(NO_PILOTS, fixture.authority));
        }
    }

    @Test
    void protected_start_replays_exact_existing_applied_explicit_receipt_without_writing_or_loading_worker() {
        try (var fixture = new Fixture(true, PILOT)) {
            when(fixture.store.hasReceipt(CALLER, COMMAND)).thenReturn(true);
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(fixture.applied));

            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit(RELEASE, 7)).instanceId());
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 8)));
            assertThrows(ProcessConflictException.class, () -> fixture.service.start(CALLER, explicit("other-release", 7)));
            verify(fixture.store, never()).current(anyLong(), anyString());
            verify(fixture.authority, never()).currentWorker(any());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_never_replays_unapplied_receipt_or_skips_current_caller_admission() {
        try (var fixture = new Fixture(true, PILOT)) {
            when(fixture.store.hasReceipt(CALLER, COMMAND)).thenReturn(true);
            when(fixture.receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.of(
                    new Receipt(RESOLVED, RELEASE, CORRELATION, "PENDING", null, NOW, null)));
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 7)));

            doReturn(null).when(fixture.authority).authorize(any());
            assertThrows(AccessDeniedException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 7)));
            verify(fixture.store).hasReceipt(CALLER, COMMAND);
            verify(fixture.receipts).lockIfPresent(CALLER, Kind.START, COMMAND);
            verify(fixture.authority, never()).currentWorker(any());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_refuses_missing_worker_without_falling_back_to_explicit_start() {
        try (var fixture = new Fixture(true, PILOT)) {
            when(fixture.authority.currentWorker(fixture.workerQuery)).thenReturn(null);
            assertThrows(AccessDeniedException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 7)));
            verify(fixture.authority).currentWorker(fixture.workerQuery);
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_refuses_missing_activation_without_falling_back_to_explicit_start() {
        try (var fixture = new Fixture(true, PILOT)) {
            when(fixture.store.current(CALLER.tenantId(), CHANNEL)).thenReturn(Optional.empty());
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.start(CALLER, explicit(RELEASE, 7)));
            verify(fixture.authority, never()).currentWorker(any());
            verifyNoStartWrites(fixture);
        }
    }

    @Test
    void protected_start_routes_only_the_exact_tenant_and_definition_pair() {
        var otherScopes = new GovernedStartPilotProperties(List.of(
                new GovernedStartPilotProperties.Pilot(CALLER.tenantId() + 1, DEFINITION, CHANNEL),
                new GovernedStartPilotProperties.Pilot(CALLER.tenantId(), "anotherDefinition", CHANNEL)));
        try (var fixture = new Fixture(true, otherScopes)) {
            assertEquals(INSTANCE, fixture.service.start(CALLER, explicit(RELEASE, 7)).instanceId());
            verify(fixture.receipts).insert(CALLER, Kind.START, COMMAND, RESOLVED, RELEASE, CORRELATION);
            verify(fixture.builder).start();
            verifyNoInteractions(fixture.authority, fixture.store);
        }
    }

    private static void verifyNoStartWrites(Fixture fixture) {
        verify(fixture.receipts, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.receipts, never()).apply(any(), any(), anyString(), anyString(), any());
        verify(fixture.store, never()).append(any(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.builder, never()).start();
    }

    private static StartCommand explicit(String release, long amount) {
        return new StartCommand(release, DEFINITION, BUSINESS, COMMAND, CORRELATION, Map.of("amount", amount));
    }

    private static GovernedStartCommand command(long amount) {
        return new GovernedStartCommand(CHANNEL, DEFINITION, BUSINESS, COMMAND, CORRELATION, Map.of("amount", amount));
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedConstruction<ProcessReleaseService> releaseConstruction = mockConstruction(ProcessReleaseService.class);
        private final MockedConstruction<VerifiedProcessModels> modelConstruction = mockConstruction(VerifiedProcessModels.class);
        private final MockedConstruction<ProcessCommandReceiptStore> receiptConstruction = mockConstruction(ProcessCommandReceiptStore.class);
        private final MockedConstruction<GovernedProcessStartStore> storeConstruction = mockConstruction(GovernedProcessStartStore.class);
        private final GovernedStartAuthority authority = mock(GovernedStartAuthority.class);
        private final DataSourceTransactionManager transactionManager = mock(DataSourceTransactionManager.class);
        private final TransactionStatus status = mock(TransactionStatus.class);
        private final ProcessInstanceBuilder builder = mock(ProcessInstanceBuilder.class, RETURNS_SELF);
        private final AgentDispatchBinding binding = new AgentDispatchBinding(BRIDGE, "release-bridge", "agentStart", "1",
                "agent", 1, "9".repeat(64), "Handle the work.", Duration.ofSeconds(5));
        private final Pointer pointer = pointer(1);
        private final WorkerRouteQuery workerQuery = new WorkerRouteQuery(CALLER.tenantId(), CHANNEL,
                pointer.revision(), CANDIDATE, binding.topic(), binding.schemaVersion());
        private final Definition definition = new Definition(Type.BPMN, DEFINITION, "definition-1", "deployment-1", 1,
                MODEL, Map.of("amount", ValueType.INTEGER));
        private final Receipt applied = new Receipt(RESOLVED, RELEASE, CORRELATION, "APPLIED", INSTANCE, NOW, null);
        private final ProcessExecutionService service;
        private final ProcessCommandReceiptStore receipts;
        private final GovernedProcessStartStore store;

        private Fixture(boolean enabled) {
            this(enabled, NO_PILOTS);
        }

        private Fixture(boolean enabled, GovernedStartPilotProperties pilots) {
            var engine = mock(SpringProcessEngineConfiguration.class);
            var runtime = mock(RuntimeService.class);
            var query = mock(ProcessInstanceQuery.class, RETURNS_SELF);
            var instance = mock(ProcessInstance.class);
            when(engine.getTransactionManager()).thenReturn(transactionManager);
            when(transactionManager.getDataSource()).thenReturn(mock(DataSource.class));
            when(transactionManager.getTransaction(any())).thenReturn(status);
            when(engine.getDatabaseSchema()).thenReturn("tianji_process");
            when(engine.getRuntimeService()).thenReturn(runtime);
            when(engine.getHistoryService()).thenReturn(mock(HistoryService.class));
            when(engine.isDbHistoryUsed()).thenReturn(true);
            when(engine.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
            when(runtime.createProcessInstanceBuilder()).thenReturn(builder);
            when(runtime.createProcessInstanceQuery()).thenReturn(query);
            when(builder.start()).thenReturn(instance);
            when(query.singleResult()).thenReturn(instance);
            when(instance.getId()).thenReturn(INSTANCE);
            when(instance.getProcessDefinitionId()).thenReturn(definition.engineDefinitionId());
            when(instance.getTenantId()).thenReturn(Long.toString(CALLER.tenantId()));
            when(instance.getBusinessKey()).thenReturn(BUSINESS);

            if (!enabled) {
                service = new ProcessExecutionService(engine, null, null);
            } else if (pilots.pilots().isEmpty()) {
                service = new ProcessExecutionService(engine, null, null, authority);
            } else {
                service = new ProcessExecutionService(engine, null, null, pilots, authority);
            }
            var releases = releaseConstruction.constructed().getFirst();
            when(releases.get(CALLER, RELEASE)).thenReturn(new Release(RELEASE, MANIFEST, NOW, List.of(definition)));
            receipts = receiptConstruction.constructed().getFirst();
            when(receipts.lockIfPresent(CALLER, Kind.START, COMMAND)).thenReturn(Optional.empty());
            when(receipts.lock(CALLER, Kind.START, COMMAND)).thenReturn(applied);
            when(receipts.insert(CALLER, Kind.START, COMMAND, RESOLVED, RELEASE, CORRELATION)).thenReturn(true);
            store = enabled ? storeConstruction.constructed().getFirst() : null;
            if (enabled) {
                when(store.now()).thenReturn(NOW);
                when(store.transactionTime()).thenReturn(NOW);
                when(store.find(CALLER, COMMAND)).thenReturn(Optional.empty());
                when(store.current(CALLER.tenantId(), CHANNEL)).thenReturn(Optional.of(pointer));
                when(authority.authorize(any())).thenAnswer(invocation ->
                        new StartGrant(invocation.getArgument(0), AUTHORIZATION, NOW, EXPIRES));
                when(authority.currentWorker(workerQuery)).thenReturn(
                        new WorkerRouteProof(workerQuery, binding, REGISTRATION, NOW, EXPIRES));
            }
        }

        private Pointer pointer(long revision) {
            return pointer(revision, CHANNEL);
        }

        private Pointer pointer(long revision, String channel) {
            return new Pointer(CALLER.tenantId(), channel, revision, CANDIDATE, RELEASE, MANIFEST, DEFINITION,
                    MODEL, binding.topic(), binding.schemaVersion(),
                    PlatformReleaseActivationService.workerFingerprint(binding, REGISTRATION));
        }

        @Override
        public void close() {
            storeConstruction.close();
            receiptConstruction.close();
            modelConstruction.close();
            releaseConstruction.close();
        }
    }
}
