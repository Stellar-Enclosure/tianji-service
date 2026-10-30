package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Applies immutable expiry policy only inside deployment-approved namespaces; it is not a USER/SERVICE permission grant. */
public final class MemoryRetentionWork {
    private final DataSource source;
    private final PostgresMemoryService memory;
    private final MemoryIndexPublication publication;
    private final List<Origin> origins;
    private final Set<String> policies;
    private final String technicalActor;
    private final Clock clock;

    public MemoryRetentionWork(DataSource source, PostgresMemoryService memory, MemoryIndexPublication publication, List<Origin> origins, Set<String> policies, Clock clock) {
        this.source = Objects.requireNonNull(source, "source"); this.memory = Objects.requireNonNull(memory, "memory"); this.publication = Objects.requireNonNull(publication, "publication");
        this.origins = List.copyOf(origins); this.policies = Set.copyOf(policies); this.clock = Objects.requireNonNull(clock, "clock");
        if (!memory.usesDataSource(source) || !publication.usesDataSource(source) || !publication.binding().equals(memory.indexBinding())
                || origins.isEmpty() || origins.size() > 1000 || origins.stream().distinct().count() != origins.size() || policies.isEmpty() || policies.size() > 32
                || !memory.policyReferences().containsAll(policies)) throw new IllegalArgumentException("Memory retention namespace or policy approval is invalid");
        technicalActor = MemoryJsonCodec.sha256("TIANJI_MEMORY_RETENTION_TECHNICAL_ACTOR_V1:" + origins + ':' + policies.stream().sorted().toList());
    }

    public List<Origin> origins() { return origins; }
    public boolean usesMemory(PostgresMemoryService candidate) { return memory == candidate; }

    /** Selection is read-only. Each selected fact is reread under its same scope fence and retired in a short owned transaction. */
    public Result apply(Origin origin, int limit, Instant deadline) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Retention requires owned transaction boundaries");
        if (!origins.contains(origin) || limit < 1 || limit > 1000 || deadline == null || !deadline.isAfter(clock.instant()) || deadline.isAfter(clock.instant().plusSeconds(30))) throw new IllegalArgumentException("Memory retention batch is invalid");
        requireReady(deadline); var jdbc = jdbc(deadline); var transaction = transaction(deadline);
        List<Candidate> selected;
        try {
            selected = transaction.execute(status -> {
                String allowed = String.join(",", java.util.Collections.nCopies(policies.size(), "?"));
                var parameters = new ArrayList<Object>(List.of(origin.tenantId(), origin.issuerFingerprint())); parameters.addAll(policies.stream().sorted().toList()); parameters.add(limit);
                return jdbc.query("""
                        SELECT owner_type,owner_id,scope_id,memory_id,current_revision,content_sha256,retention_policy_ref,expires_at
                        FROM tianji_agent.agent_memory_entry WHERE tenant_id=? AND owner_issuer=?
                          AND state IN ('ACTIVE','CANDIDATE') AND expires_at<=clock_timestamp()
                          AND retention_policy_ref IN (%s)
                        ORDER BY expires_at,owner_type,owner_id,scope_id,memory_id LIMIT ?
                        """.formatted(allowed), (rs, row) -> new Candidate(new MemoryOwnerDTO(MemoryOwnerTypeEnum.valueOf(rs.getString("owner_type")), rs.getString("owner_id")),
                        rs.getString("scope_id"), rs.getString("memory_id"), rs.getLong("current_revision"), rs.getString("content_sha256"), rs.getString("retention_policy_ref"), rs.getTimestamp("expires_at").toInstant()), parameters.toArray());
            });
        } catch (DataAccessException failure) { throw unavailable(); }
        if (selected == null || selected.size() > limit) throw unavailable(); requireReady(deadline); int expired = 0;
        for (var candidate : selected) if (expire(origin, candidate, deadline)) expired++;
        return new Result(selected.size(), expired, selected.size() - expired);
    }

    private boolean expire(Origin origin, Candidate candidate, Instant deadline) {
        requireReady(deadline); if (!policies.contains(candidate.policy())) throw unavailable(); new GetMemoryQuery(candidate.owner(), candidate.scope(), candidate.id());
        try {
            return Boolean.TRUE.equals(transaction(deadline).execute(status -> {
                var repository = new JdbcMemoryRepository(jdbc(deadline), origin.tenantId(), origin.issuerFingerprint(), candidate.owner(), candidate.scope(), new MemoryJsonCodec());
                repository.lockScope(); var now = repository.databaseNow(); check(deadline, now);
                var current = repository.current(candidate.id(), true); if (current.isEmpty()) return false; var row = current.orElseThrow(); var entry = row.entry();
                if (entry.revision() != candidate.revision() || !entry.contentSha256().equals(candidate.content()) || !entry.retentionPolicyRef().equals(candidate.policy())
                        || !entry.expiresAt().equals(candidate.expires()) || entry.expiresAt().isAfter(now)
                        || entry.state() != MemoryStateEnum.ACTIVE && entry.state() != MemoryStateEnum.CANDIDATE) return false;
                memory.expireForRetention(repository, row, technicalActor, now); repository.touchScope(now);
                check(deadline, repository.databaseNow()); requireReady(deadline); return true;
            }));
        } catch (DataAccessException failure) { throw unavailable(); }
    }

    private void requireReady(Instant deadline) { check(deadline, clock.instant()); if (!memory.readyForRetention(source, publication)) throw unavailable(); }
    private static void check(Instant deadline, Instant now) { if (!now.isBefore(deadline) || Thread.currentThread().isInterrupted()) throw unavailable(); }
    private JdbcTemplate jdbc(Instant deadline) { var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(seconds(deadline)); return jdbc; }
    private TransactionTemplate transaction(Instant deadline) { var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(seconds(deadline)); return transaction; }
    private int seconds(Instant deadline) { long nanos = Duration.between(clock.instant(), deadline).toNanos(); if (nanos <= 0) throw unavailable(); return Math.max(1, (int) ((nanos + 999_999_999) / 1_000_000_000)); }
    public record Origin(long tenantId, String issuerFingerprint) {
        public Origin { if (tenantId < 1 || issuerFingerprint == null || !issuerFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Memory retention origin is invalid"); }
    }
    public record Result(int selected, int expired, int stale) { public Result { if (selected < 0 || expired < 0 || stale < 0 || selected != expired + stale) throw new IllegalArgumentException("Memory retention result is invalid"); } }
    private record Candidate(MemoryOwnerDTO owner, String scope, String id, long revision, String content, String policy, Instant expires) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory retention storage or policy is unavailable"); }
}
