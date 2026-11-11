package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.Action;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ChangeCommand;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.AdmissionWindow;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;

final class PlatformReleaseActivationStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(1);
    private static final AdmissionWindow WINDOW = new AdmissionWindow(NOW, EXPIRES);
    private static final String CANDIDATE_A = "a".repeat(64);
    private static final String CANDIDATE_B = "b".repeat(64);
    private static final String CANDIDATE_C = "c".repeat(64);
    private static final String BINDING = "d".repeat(64);
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "activator", "https://process.example.test");

    @Test
    void first_activation_uses_read_committed_tenant_fence_and_an_exact_accepted_insert() {
        try (var f = new Fixture()) {
            var proposed = proposal(CANDIDATE_A, 0, Action.ACTIVATE);
            var stored = receipt(proposed.command(), null, "expenseReview", NOW);
            f.insertReturns(stored);

            assertEquals(stored, f.store.append(proposed, WINDOW));
            var order = inOrder(f.manager, f.jdbc);
            order.verify(f.manager).getTransaction(argThat(transaction ->
                    transaction.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED && !transaction.isReadOnly()));
            order.verify(f.jdbc).query(eq("SELECT pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(?, 0))"),
                    any(RowCallbackHandler.class), eq("tianji-platform-release-activation-v1:20"));
            order.verify(f.jdbc).query(argThat(sql -> sql.contains("actor_type=? AND actor_issuer=? AND actor_subject=? AND command_id=?")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), eq(20L), eq("expense"), eq("USER"),
                    eq(ACTOR.issuerFingerprint()), eq(ACTOR.subjectId()), eq(proposed.command().commandId()));
            order.verify(f.jdbc).query(argThat(sql -> sql.contains("ORDER BY revision DESC LIMIT 1")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), eq(20L), eq("expense"));
            order.verify(f.jdbc).queryForObject(argThat(sql -> sql.contains("worker_binding_sha256 < CAST(? AS char(64))")
                            && sql.contains("worker_binding_sha256 > CAST(? AS char(64))") && !sql.contains("channel")),
                    eq(Boolean.class), eq(20L), eq("agentStart"), eq("1"), eq(BINDING),
                    eq(20L), eq("agentStart"), eq("1"), eq(BINDING));
            order.verify(f.jdbc).query(argThat(sql -> sql.contains("r.tenant_id=? AND r.channel=? AND r.candidate_sha256=CAST(? AS char(64)) AND r.decision='ACCEPT'")
                            && sql.contains("c.release_version=? AND c.manifest_sha256=CAST(? AS char(64)) AND c.trust_generation=?")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class));
            order.verify(f.jdbc).queryForObject(argThat(sql -> sql.contains("transaction_isolation')='read committed'")),
                    ArgumentMatchers.<RowMapper<Instant>>any());
            order.verify(f.manager).commit(f.status);
            verify(f.startup.constructed().getFirst()).verifyPlatformActivation();
        }
    }

    @Test
    void historical_exact_command_replays_under_the_fence_but_changed_command_conflicts() {
        try (var f = new Fixture()) {
            var command = proposal(CANDIDATE_A, 0, Action.ACTIVATE).command();
            var historic = receipt(command, null, "expenseReview", NOW);
            when(f.jdbc.query(argThat(sql -> sql != null && sql.contains("actor_type=?")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class))).thenReturn(List.of(historic));
            assertEquals(historic, f.store.append(receipt(command, null, "expenseReview", null), WINDOW));
            assertThrows(ProcessConflictException.class, () -> f.store.append(proposal(CANDIDATE_B, 0, Action.ACTIVATE), WINDOW));
            f.verifyNoInsert();
        }
    }

    @Test
    void exact_command_replay_fails_when_authority_expires_during_locked_lookup() {
        try (var f = new Fixture()) {
            var proposed = proposal(CANDIDATE_A, 0, Action.ACTIVATE);
            var historic = receipt(proposed.command(), null, "expenseReview", NOW);
            when(f.jdbc.query(argThat(sql -> sql != null && sql.contains("actor_type=?")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class))).thenReturn(List.of(historic));
            when(f.jdbc.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Instant>>any()))
                    .thenReturn(NOW, EXPIRES);

            assertThrows(AccessDeniedException.class, () -> f.store.append(proposed, WINDOW));
            f.verifyNoInsert();
            verify(f.manager).rollback(f.status);
            verify(f.manager, never()).commit(any());
        }
    }

    @Test
    void rollback_moves_to_exact_previous_candidate_and_revisions_are_monotonic() {
        try (var f = new Fixture()) {
            f.currentIs(receipt(proposal(CANDIDATE_B, 1, Action.ACTIVATE).command(), CANDIDATE_A, "expenseReview", NOW));
            assertThrows(ProcessVersionConflictException.class, () -> f.store.append(proposal(CANDIDATE_A, 1, Action.ROLLBACK), WINDOW));
            assertThrows(ProcessVersionConflictException.class, () -> f.store.append(proposal(CANDIDATE_C, 2, Action.ROLLBACK), WINDOW));
            var valid = proposal(CANDIDATE_A, 2, Action.ROLLBACK);
            var persisted = receipt(valid.command(), CANDIDATE_B, "expenseReview", NOW);
            f.insertReturns(persisted);
            assertEquals(persisted, f.store.append(valid, WINDOW));
            assertEquals(3, persisted.revision());
            var bindings = ArgumentCaptor.forClass(Object[].class);
            verify(f.jdbc).query(argThat(sql -> sql.startsWith("INSERT INTO ")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), bindings.capture());
            Object[] arguments = bindings.getValue();
            assertEquals(26, arguments.length);
            assertEquals(3L, arguments[2]);
            assertEquals(CANDIDATE_A, arguments[3]);
            assertEquals(CANDIDATE_B, arguments[4]);
            assertEquals(CANDIDATE_A, arguments[22]);
        }
    }

    @Test
    void current_candidate_channel_definition_and_historical_route_conflicts_never_insert() {
        try (var f = new Fixture()) {
            f.currentIs(receipt(proposal(CANDIDATE_A, 0, Action.ACTIVATE).command(), null, "expenseReview", NOW));
            assertThrows(ProcessConflictException.class, () -> f.store.append(proposal(CANDIDATE_A, 1, Action.ACTIVATE), WINDOW));
            var proposed = proposal(CANDIDATE_B, 1, Action.ACTIVATE);
            assertThrows(ProcessConflictException.class, () -> f.store.append(receipt(proposed.command(), null, "different", null), WINDOW));
            when(f.jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("worker_binding_sha256 < CAST(? AS char(64))")),
                    eq(Boolean.class), any(Object[].class))).thenReturn(true);
            assertThrows(ProcessConflictException.class, () -> f.store.append(proposed, WINDOW));
            f.verifyNoInsert();
        }
    }

    @Test
    void missing_accepted_review_and_post_insert_expiry_both_fail_closed() {
        try (var f = new Fixture()) {
            var proposed = proposal(CANDIDATE_A, 0, Action.ACTIVATE);
            assertThrows(AccessDeniedException.class, () -> f.store.append(proposed, WINDOW));
            verify(f.manager).rollback(f.status);
            verify(f.manager, never()).commit(any());
        }
        try (var f = new Fixture()) {
            var proposed = proposal(CANDIDATE_A, 0, Action.ACTIVATE);
            f.insertReturns(receipt(proposed.command(), null, "expenseReview", NOW));
            when(f.jdbc.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Instant>>any()))
                    .thenReturn(NOW, NOW, EXPIRES);
            assertThrows(AccessDeniedException.class, () -> f.store.append(proposed, WINDOW));
            verify(f.manager).rollback(f.status);
            verify(f.manager, never()).commit(any());
        }
    }

    private static ActivationReceipt proposal(String candidate, long expected, Action action) {
        return receipt(new ChangeCommand(new Scope(20, "expense", candidate), "activate-1", expected, action),
                null, "expenseReview", null);
    }

    private static ActivationReceipt receipt(ChangeCommand command, String previous, String definitionKey, Instant time) {
        return new ActivationReceipt(command, ACTOR, previous, "expense-v1", "1".repeat(64), definitionKey,
                "2".repeat(64), "agentStart", "1", BINDING, "3".repeat(64), "4".repeat(64), 1, time);
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSourceTransactionManager manager = mock(DataSourceTransactionManager.class);
        private final TransactionStatus status = mock(TransactionStatus.class);
        private final MockedConstruction<ProcessApiSchemaVerifier> startup;
        private final MockedConstruction<JdbcTemplate> construction;
        private final JdbcTemplate jdbc;
        private final PlatformReleaseActivationStore store;

        private Fixture() {
            var source = mock(DataSource.class);
            var engine = mock(SpringProcessEngineConfiguration.class);
            var releases = mock(ProcessReleaseService.class);
            when(manager.getDataSource()).thenReturn(source);
            when(manager.getTransaction(any())).thenReturn(status);
            when(engine.getDataSource()).thenReturn(source);
            when(engine.getDatabaseSchema()).thenReturn("tianji_process");
            when(engine.getTransactionManager()).thenReturn(manager);
            when(releases.usesStorage(engine, source, manager)).thenReturn(true);
            startup = mockConstruction(ProcessApiSchemaVerifier.class);
            construction = mockConstruction(JdbcTemplate.class);
            store = new PlatformReleaseActivationStore(engine, releases);
            jdbc = construction.constructed().getFirst();
            when(jdbc.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Instant>>any())).thenReturn(NOW);
            when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("worker_binding_sha256 < CAST(? AS char(64))")),
                    eq(Boolean.class), any(Object[].class))).thenReturn(false);
        }

        void currentIs(ActivationReceipt current) {
            when(jdbc.query(argThat(sql -> sql != null && sql.contains("ORDER BY revision DESC LIMIT 1")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class))).thenReturn(List.of(current));
        }

        void insertReturns(ActivationReceipt inserted) {
            when(jdbc.query(argThat(sql -> sql != null && sql.startsWith("INSERT INTO ")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class))).thenReturn(List.of(inserted));
        }

        void verifyNoInsert() {
            verify(jdbc, never()).query(argThat(sql -> sql != null && sql.startsWith("INSERT INTO ")),
                    ArgumentMatchers.<RowMapper<ActivationReceipt>>any(), any(Object[].class));
        }

        @Override public void close() {
            construction.close();
            startup.close();
        }
    }
}
