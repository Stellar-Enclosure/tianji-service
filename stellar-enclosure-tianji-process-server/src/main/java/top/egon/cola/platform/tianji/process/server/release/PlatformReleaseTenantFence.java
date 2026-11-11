package top.egon.cola.platform.tianji.process.server.release;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Coordinates changes to an activation pointer with starts that use it. */
public final class PlatformReleaseTenantFence {
    private static final String PREFIX = "tianji-platform-release-activation-v1:";

    private PlatformReleaseTenantFence() { }

    static void acquireExclusive(JdbcTemplate jdbc, long tenantId) {
        jdbc.query("SELECT pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(?, 0))",
                rs -> { }, PREFIX + tenantId);
    }

    public static void acquireShared(JdbcTemplate jdbc, long tenantId) {
        requireOwningTransaction(jdbc, tenantId);
        jdbc.query("SELECT pg_catalog.pg_advisory_xact_lock_shared(pg_catalog.hashtextextended(?, 0))",
                rs -> { }, PREFIX + tenantId);
    }

    private static void requireOwningTransaction(JdbcTemplate jdbc, long tenantId) {
        if (tenantId <= 0 || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("Platform release fence requires the owning transaction");
        }
    }
}
