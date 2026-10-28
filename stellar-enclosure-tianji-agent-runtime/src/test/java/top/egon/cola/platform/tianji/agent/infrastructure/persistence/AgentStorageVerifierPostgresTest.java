package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(60)
class AgentStorageVerifierPostgresTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static AgentStorageVerifier verifier;

    @BeforeAll static void setup() {
        var source = PostgresTestDatabase.dataSource();
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        verifier = new AgentStorageVerifier(source);
    }

    @Test void accepts_the_migrated_primary_without_writing_to_it() {
        assertDoesNotThrow(verifier::verify);
    }

    @Test void rejects_missing_changed_or_failed_migration_history() {
        rejectedAfter("DELETE FROM tianji_agent.flyway_schema_history WHERE version='6'");
        rejectedAfter("UPDATE tianji_agent.flyway_schema_history SET checksum=checksum+1 WHERE version='5'");
        rejectedAfter("UPDATE tianji_agent.flyway_schema_history SET success=false WHERE version='3'");
    }

    @Test void rejects_missing_issuer_uniqueness_or_event_deduplication() {
        rejectedAfter("ALTER TABLE tianji_agent.agent_run DROP CONSTRAINT agent_run_issuer_idempotency_key");
        rejectedAfter("DROP INDEX tianji_agent.agent_event_native_id_idx");
        rejectedAfter("ALTER TABLE tianji_agent.agent_session_event DROP CONSTRAINT agent_session_event_issuer_session_fkey");
    }

    @Test void rejects_missing_columns_or_changed_identity_collation() {
        rejectedAfter("ALTER TABLE tianji_agent.agent_run_execution_grant DROP COLUMN ciphertext");
        rejectedAfter("ALTER TABLE tianji_agent.agent_run ALTER COLUMN subject_id TYPE varchar(256) COLLATE \"POSIX\"");
    }

    @Test void rejects_a_read_only_connection() {
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET TRANSACTION READ ONLY");
            assertThrows(AgentLedgerException.class, verifier::verify);
            status.setRollbackOnly();
        });
        assertDoesNotThrow(verifier::verify);
    }

    private void rejectedAfter(String change) {
        transaction.executeWithoutResult(status -> {
            jdbc.execute(change);
            assertThrows(AgentLedgerException.class, verifier::verify);
            status.setRollbackOnly();
        });
        assertDoesNotThrow(verifier::verify);
    }
}
