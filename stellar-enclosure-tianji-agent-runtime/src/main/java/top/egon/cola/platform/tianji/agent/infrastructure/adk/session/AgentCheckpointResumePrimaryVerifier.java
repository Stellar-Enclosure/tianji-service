package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;

/** Verifies the two host-supplied Agent primary pools without writing durable state. */
public final class AgentCheckpointResumePrimaryVerifier {
    private static final int TIMEOUT_SECONDS = 2;
    private static final SecureRandom PROBE_KEYS = new SecureRandom();
    private static final String ROLE_SQL = """
            SELECT current_database(), current_user
            WHERE current_user=session_user AND NOT pg_catalog.pg_is_in_recovery()
              AND pg_catalog.current_setting('transaction_read_only')='off'
              AND pg_catalog.current_setting('server_encoding')='UTF8'
              AND pg_catalog.current_setting('session_replication_role')='origin'
              AND pg_catalog.current_setting('standard_conforming_strings')='on'
            """;
    private static final String LOCK_SQL = "SELECT pg_catalog.pg_try_advisory_xact_lock(?)";

    private AgentCheckpointResumePrimaryVerifier() { }

    public static void verify(DataSource ordinary, DataSource recovery, String expectedRole) {
        if (ordinary == null || recovery == null || ordinary == recovery || expectedRole == null || expectedRole.isBlank()
                || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(ordinary)
                || TransactionSynchronizationManager.hasResource(recovery)) {
            throw invalid();
        }

        long probeKey = PROBE_KEYS.nextLong();
        try (var ordinaryConnection = ordinary.getConnection();
             var ordinaryProbe = new Probe(ordinaryConnection);
             var recoveryConnection = recovery.getConnection();
             var recoveryProbe = new Probe(recoveryConnection)) {
            var ordinaryRole = ordinaryProbe.readRole();
            var recoveryRole = recoveryProbe.readRole();
            if (!ordinaryRole.database().equals(recoveryRole.database()) || ordinaryRole.role().equals(recoveryRole.role())
                    || !expectedRole.equals(recoveryRole.role())
                    || !ordinaryProbe.tryLock(probeKey) || recoveryProbe.tryLock(probeKey)) {
                throw invalid();
            }
        } catch (SQLException | RuntimeException failed) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("TIANJI_CHECKPOINT_RESUME_PRIMARY_BINDING_INVALID");
    }

    private record Login(String database, String role) { }

    private static final class Probe implements AutoCloseable {
        private final Connection connection;

        private Probe(Connection connection) throws SQLException {
            if (!connection.getAutoCommit()) throw invalid();
            connection.setAutoCommit(false);
            this.connection = connection;
        }

        private Login readRole() throws SQLException {
            try (var timeout = connection.createStatement()) {
                timeout.setQueryTimeout(TIMEOUT_SECONDS);
                timeout.execute("SET LOCAL statement_timeout = '2s'");
            }
            try (var query = connection.prepareStatement(ROLE_SQL)) {
                query.setQueryTimeout(TIMEOUT_SECONDS);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw invalid();
                    var login = new Login(rows.getString(1), rows.getString(2));
                    if (login.database() == null || login.role() == null || rows.next()) throw invalid();
                    return login;
                }
            }
        }

        private boolean tryLock(long key) throws SQLException {
            try (var query = connection.prepareStatement(LOCK_SQL)) {
                query.setQueryTimeout(TIMEOUT_SECONDS);
                query.setLong(1, key);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw invalid();
                    boolean acquired = rows.getBoolean(1);
                    if (rows.wasNull() || rows.next()) throw invalid();
                    return acquired;
                }
            }
        }

        @Override
        public void close() throws SQLException {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }
}
