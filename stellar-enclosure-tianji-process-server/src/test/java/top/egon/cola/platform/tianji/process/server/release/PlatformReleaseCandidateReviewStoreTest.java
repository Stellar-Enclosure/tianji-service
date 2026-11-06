package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.AdmissionWindow;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Decision;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewCommand;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;

final class PlatformReleaseCandidateReviewStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(1);
    private static final AdmissionWindow WINDOW = new AdmissionWindow(NOW, EXPIRES);
    private static final Scope SCOPE = new Scope(20, "expense", "0".repeat(64));
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER,
            "reviewer", "https://process.example.test");
    private static final ReviewReceipt RECEIPT = new ReviewReceipt(SCOPE, REVIEWER, new ReviewCommand("review-1", 0, Decision.ACCEPT),
            "a".repeat(64), "b".repeat(64), "c".repeat(64), NOW, NOW);

    @Test
    void late_authority_expiry_rolls_back_even_if_insert_already_returned_success() {
        var fixture = new Fixture();
        try (var startup = mockConstruction(ProcessApiSchemaVerifier.class); var jdbc = mockConstruction(JdbcTemplate.class)) {
            var store = new PlatformReleaseCandidateReviewStore(fixture.engine, fixture.releases);
            var queries = jdbc.constructed().getFirst();
            when(queries.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Instant>>any())).thenReturn(NOW, EXPIRES);
            when(queries.update(anyString(), any(Object[].class))).thenReturn(1);
            when(queries.query(anyString(), ArgumentMatchers.<RowMapper<ReviewReceipt>>any(), any(Object[].class)))
                    .thenReturn(List.of(RECEIPT));

            assertThrows(AccessDeniedException.class, () -> store.appendReview(RECEIPT, WINDOW));
            verify(fixture.manager).rollback(fixture.status);
            verify(fixture.manager, never()).commit(any());
            verify(startup.constructed().getFirst()).verifyPlatformCandidateReviews();
        }
    }

    @Test
    void conflicting_insert_uses_a_fresh_scoped_winner_read_in_read_committed() {
        var fixture = new Fixture();
        try (var startup = mockConstruction(ProcessApiSchemaVerifier.class); var jdbc = mockConstruction(JdbcTemplate.class)) {
            var store = new PlatformReleaseCandidateReviewStore(fixture.engine, fixture.releases);
            var queries = jdbc.constructed().getFirst();
            when(queries.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Instant>>any())).thenReturn(NOW);
            when(queries.query(anyString(), ArgumentMatchers.<RowMapper<ReviewReceipt>>any(), any(Object[].class)))
                    .thenReturn(List.of(RECEIPT));

            var replay = store.appendReview(RECEIPT, WINDOW);
            assertFalse(replay.created());
            assertEquals(RECEIPT, replay.value());
            var order = inOrder(fixture.manager, queries);
            order.verify(fixture.manager).getTransaction(argThat(transaction ->
                    transaction.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED));
            order.verify(queries).update(argThat(sql -> sql.endsWith("ON CONFLICT DO NOTHING")), any(Object[].class));
            order.verify(queries).query(argThat(sql -> sql.contains("tenant_id=? AND channel=? AND reviewer_issuer=?")
                            && sql.contains("reviewer_subject=? AND command_id=?")),
                    ArgumentMatchers.<RowMapper<ReviewReceipt>>any(), eq(SCOPE.tenantId()), eq(SCOPE.channel()),
                    eq(REVIEWER.issuerFingerprint()), eq(REVIEWER.subjectId()), eq(RECEIPT.command().commandId()));
            order.verify(fixture.manager).commit(fixture.status);
        }
    }

    private static final class Fixture {
        private final DataSource source = mock(DataSource.class);
        private final DataSourceTransactionManager manager = mock(DataSourceTransactionManager.class);
        private final TransactionStatus status = mock(TransactionStatus.class);
        private final SpringProcessEngineConfiguration engine = mock(SpringProcessEngineConfiguration.class);
        private final ProcessReleaseService releases = mock(ProcessReleaseService.class);

        private Fixture() {
            when(manager.getDataSource()).thenReturn(source);
            when(manager.getTransaction(any())).thenReturn(status);
            when(engine.getDataSource()).thenReturn(source);
            when(engine.getDatabaseSchema()).thenReturn("tianji_process");
            when(engine.getTransactionManager()).thenReturn(manager);
            when(releases.usesStorage(engine, source, manager)).thenReturn(true);
        }
    }
}
