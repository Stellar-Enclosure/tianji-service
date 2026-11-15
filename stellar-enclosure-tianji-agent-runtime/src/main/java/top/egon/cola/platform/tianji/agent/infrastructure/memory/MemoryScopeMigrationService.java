package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.agentflow.api.memory.MemoryScopeVO;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore.HostFenceVO;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Function;

/** Explicit maintenance entry point. Every copied page and the final switch share the live HOST authority row. */
@Slf4j
@Validated
public class MemoryScopeMigrationService {
    private static final int BATCH_SIZE = 100;
    private static final Duration TRANSACTION_LIMIT = Duration.ofSeconds(25);
    private static final String CHANNEL = "tianji-memory-index";
    private static final String DESTINATION = "agent-memory";
    private static final String HEADER = "tianji-memory-index-fingerprint";
    private static final String OUTBOX = "tianji_agent.egon_cola_outbox_message";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final DataSource source;
    private final String schema;
    private final MemoryIndexWork indexes;
    private final String migrationRole;
    private final MigrationAuthority authority;
    private final OutboxProtocol outbox;
    private final Clock clock;

    public MemoryScopeMigrationService(DataSource source, String schema, JdbcMemoryStore store, MemoryIndexWork indexes,
                                       String migrationRole, MigrationAuthority authority, OutboxProtocol outbox, Clock clock) {
        if (source == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || store == null
                || !store.usesStorage(source, schema) || indexes == null || !indexes.usesDataSource(source)
                || migrationRole == null || !migrationRole.matches("[a-z_][a-z0-9_]{0,62}") || migrationRole.startsWith("pg_")) {
            throw new IllegalArgumentException("Memory migration requires the exact same storage and a dedicated maintenance role");
        }
        this.source = source;
        this.schema = schema;
        this.indexes = indexes;
        this.migrationRole = migrationRole;
        this.authority = Objects.requireNonNull(authority, "migration authority");
        this.outbox = Objects.requireNonNull(outbox, "outbox protocol");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CutVO freezeAndMigrate(@Valid @NotNull MemoryScopeVO scope, @Positive long expectedEpoch, @NotNull Instant deadline) {
        if (expectedEpoch < 1 || expectedEpoch > Long.MAX_VALUE - 2) throw new IllegalArgumentException("Invalid Memory epoch");
        var identity = authorized(scope, deadline);
        try (var ownership = acquire(scope, deadline)) {
            return migrate(ownership, scope, identity, expectedEpoch + 1, false, deadline);
        }
    }

    /** Resume is an explicit operator action on a previously observed frozen generation. */
    public CutVO resumeFrozen(@Valid @NotNull MemoryScopeVO scope, @Positive long frozenEpoch, @NotNull Instant deadline) {
        if (frozenEpoch < 2 || frozenEpoch == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid Memory freeze epoch");
        var identity = authorized(scope, deadline);
        try (var ownership = acquire(scope, deadline)) {
            return migrate(ownership, scope, identity, frozenEpoch, true, deadline);
        }
    }

    private CutVO migrate(SessionOwnership ownership, MemoryScopeVO scope, LegacyMemoryStoreAdapter.HostIdentityVO identity,
                          long frozenEpoch, boolean resume, Instant deadline) {
        var bindings = registeredBindings(scope);
        var attempt = new Attempt();
        boolean drainOpen = false;
        try {
            var sourceBound = transaction(ownership, deadline, jdbc -> {
                if (resume) {
                    frozen(jdbc, scope, identity, frozenEpoch);
                    return new SourceBoundVO(maxOutboxId(jdbc));
                }
                return freeze(jdbc, scope, identity, frozenEpoch - 1, frozenEpoch);
            });
            indexes.openFrozenDrain(scope, frozenEpoch);
            drainOpen = true;
            var witness = authority.quiesce(scope, frozenEpoch, deadline);
            indexes.stopAndAwaitFrozenDrain(scope, frozenEpoch, deadline);
            var bounds = transaction(ownership, deadline, jdbc -> {
                var repository = frozen(jdbc, scope, identity, frozenEpoch);
                requireWitness(scope, frozenEpoch, witness, deadline, repository.databaseNow());
                verifyOutbox(repository, sourceBound.maxOutboxId(), false, deadline);
                repository.requireSafePreferenceState();
                var original = repository.migrationBounds();
                if (repository.scopeWatermark() != original.revisions()) throw unavailable();
                return original;
            });
            PageTransaction batches = work -> transaction(ownership, deadline, jdbc -> {
                var repository = frozen(jdbc, scope, identity, frozenEpoch);
                requireWitness(scope, frozenEpoch, witness, deadline, repository.databaseNow());
                return work.apply(repository);
            });
            var imported = traverse(bounds, batches, true, deadline);
            return transaction(ownership, deadline, jdbc -> {
                var repository = frozen(jdbc, scope, identity, frozenEpoch);
                indexes.requireStopped(scope, frozenEpoch);
                requireWitness(scope, frozenEpoch, witness, deadline, repository.databaseNow());
                if (!bounds.equals(repository.migrationBounds()) || repository.scopeWatermark() != bounds.revisions()) throw unavailable();
                var verified = traverse(bounds, page -> page.apply(repository), false, deadline);
                if (!imported.equals(verified)) throw unavailable();
                repository.requireShadowCounts(bounds);
                verifyOutbox(repository, sourceBound.maxOutboxId(), true, deadline);
                repository.requireSafePreferenceState();
                indexes.retireFrozen(repository, bindings,
                        event -> verifyBuildOutbox(repository, event, sourceBound.maxOutboxId()), deadline);
                requireWitness(scope, frozenEpoch, witness, deadline, repository.databaseNow());
                var stamps = jdbc.query("""
                        UPDATE %s.scope SET storage_ref='COMPONENT',state='ACTIVE',authority_epoch=?,high_water_revision=?,
                            updated_at=GREATEST(updated_at,clock_timestamp())
                        WHERE scope_hash=? AND canonical_scope=?::jsonb AND storage_ref='LEGACY' AND state='FROZEN' AND authority_epoch=?
                        RETURNING xmin::text
                        """.formatted(schema), (rs, row) -> rs.getString(1), frozenEpoch + 1, bounds.revisions(), scope.hash(),
                        LegacyMemoryStoreAdapter.json(scope.canonicalScope()), frozenEpoch);
                if (stamps.size() != 1) throw unavailable();
                var cut = new CutVO(frozenEpoch + 1, "COMPONENT", bounds.revisions());
                attempt.switchEvidence = new CasEvidenceVO(stamps.getFirst(), cut);
                return cut;
            });
        } catch (RuntimeException failure) {
            if (!drainOpen) throw failure;
            try {
                var reconciled = reconcile(ownership, scope, identity, frozenEpoch, deadline, attempt.switchEvidence);
                if (reconciled != null) return reconciled;
            } catch (RuntimeException recoveryError) {
                // Unknown scope or expired deadline leaves the frozen generation for an explicit retry.
                log.debug("Memory migration outcome requires reconciliation");
            }
            throw failure;
        } finally {
            if (drainOpen) indexes.closeFrozenDrain(scope, frozenEpoch);
        }
    }

    /** Reference-only access for an explicitly bound component index provider, at one unchanged scope epoch and high water. */
    public RebuildPageVO rebuildHeads(@Valid @NotNull MemoryScopeVO scope, @Positive long epoch, Long expectedWatermark,
                                      long afterScopeRevision, int limit, @NotNull Instant deadline) {
        if (epoch < 1 || afterScopeRevision < 0 || limit < 1 || limit > BATCH_SIZE || expectedWatermark == null && afterScopeRevision != 0) {
            throw new IllegalArgumentException("Invalid Memory rebuild cursor");
        }
        authorized(scope, deadline);
        try (var ownership = acquire(scope, deadline)) {
            return transaction(ownership, deadline, jdbc -> {
                var route = select(jdbc, scope, false);
                if (route == null || route.epoch() != epoch || !"COMPONENT".equals(route.storage()) || !"ACTIVE".equals(route.state())
                        || expectedWatermark != null && expectedWatermark != route.watermark() || afterScopeRevision > route.watermark()) throw unavailable();
                Instant observed = databaseNow(jdbc);
                var heads = jdbc.query("""
                        SELECT f.fact_id,f.fact_type,r.revision,r.scope_revision,r.content_profile,r.content_sha256,r.state,r.confirmed,
                            r.effective_at,r.expires_at
                        FROM %1$s.fact f JOIN %1$s.revision r ON r.scope_hash=f.scope_hash AND r.fact_id=f.fact_id AND r.revision=f.current_revision
                        WHERE f.scope_hash=? AND r.scope_revision>? ORDER BY r.scope_revision LIMIT ?
                        """.formatted(schema), (rs, row) -> {
                            long sequence = rs.getLong("scope_revision");
                            if (sequence > route.watermark() || !"HOST_V1".equals(rs.getString("content_profile"))) throw unavailable();
                            String state = rs.getString("state");
                            boolean visible = "ACTIVE".equals(state) && rs.getBoolean("confirmed")
                                    && !rs.getTimestamp("effective_at").toInstant().isAfter(observed)
                                    && rs.getTimestamp("expires_at").toInstant().isAfter(observed);
                            return new RebuildHeadVO(rs.getString("fact_id"), rs.getString("fact_type"), rs.getLong("revision"),
                                    sequence, rs.getString("content_sha256"), state, visible);
                        }, scope.hash(), afterScopeRevision, limit);
                boolean complete = heads.size() < limit;
                long cursor = complete ? route.watermark() : heads.getLast().scopeRevision();
                return new RebuildPageVO(epoch, route.watermark(), cursor, complete, List.copyOf(heads));
            });
        }
    }

    public record RebuildHeadVO(String factId, String factType, long revision, long scopeRevision, String contentSha256,
                                String state, boolean indexableNow) { }
    public record RebuildPageVO(long authorityEpoch, long highWaterRevision, long nextScopeRevision, boolean complete,
                                List<RebuildHeadVO> heads) { }

    /** Cleanup is authorized again and can only remove shadow data under the exact still-frozen epoch. */
    public void rollbackFrozen(@Valid @NotNull MemoryScopeVO scope, @Positive long frozenEpoch, @NotNull Instant deadline) {
        if (frozenEpoch < 2 || frozenEpoch == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid Memory freeze epoch");
        var identity = authorized(scope, deadline);
        try (var ownership = acquire(scope, deadline)) {
            transaction(ownership, deadline, jdbc -> {
                clearAndRestore(jdbc, scope, frozen(jdbc, scope, identity, frozenEpoch), frozenEpoch);
                return Boolean.TRUE;
            });
        }
    }

    private LegacyMemoryStoreAdapter.HostIdentityVO authorized(MemoryScopeVO scope, Instant deadline) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory migration requires its own short transactions");
        Instant now = clock.instant();
        if (deadline == null || !now.isBefore(deadline)) {
            throw new IllegalArgumentException("Memory migration deadline is invalid");
        }
        var identity = LegacyMemoryStoreAdapter.identity(scope);
        authority.authorize(scope, deadline);
        check(deadline, clock.instant());
        return identity;
    }

    private SourceBoundVO freeze(JdbcTemplate jdbc, MemoryScopeVO scope, LegacyMemoryStoreAdapter.HostIdentityVO identity,
                                 long expected, long frozenEpoch) {
        var repository = repository(jdbc, identity);
        var route = select(jdbc, scope, true);
        if (route == null) {
            if (expected != 1 || !repository.scopeExists()) throw unavailable();
            jdbc.update("""
                    INSERT INTO %s.scope(scope_hash,canonical_scope,authority_epoch,storage_ref,state,high_water_revision,created_at,updated_at)
                    VALUES (?,?::jsonb,1,'LEGACY','ACTIVE',?,clock_timestamp(),clock_timestamp()) ON CONFLICT DO NOTHING
                    """.formatted(schema), scope.hash(), LegacyMemoryStoreAdapter.json(scope.canonicalScope()), repository.legacyRevisionCount());
            route = select(jdbc, scope, true);
        }
        if (route == null || !"LEGACY".equals(route.storage())) throw unavailable();
        if ("ACTIVE".equals(route.state()) && route.epoch() == expected) {
            requireOne(jdbc.update("""
                    UPDATE %s.scope SET state='FROZEN',authority_epoch=?,updated_at=GREATEST(updated_at,clock_timestamp())
                    WHERE scope_hash=? AND storage_ref='LEGACY' AND state='ACTIVE' AND authority_epoch=?
                    """.formatted(schema), frozenEpoch, scope.hash(), expected));
        } else throw unavailable();
        return new SourceBoundVO(maxOutboxId(jdbc));
    }

    private JdbcMemoryRepository frozen(JdbcTemplate jdbc, MemoryScopeVO scope, LegacyMemoryStoreAdapter.HostIdentityVO identity, long epoch) {
        var route = select(jdbc, scope, true);
        if (route == null || route.epoch() != epoch || !"LEGACY".equals(route.storage()) || !"FROZEN".equals(route.state())) throw unavailable();
        return repository(jdbc, identity).fenced(schema, scope.hash(), new HostFenceVO("LEGACY", epoch, route.watermark(), true));
    }

    private ScopeRowVO select(JdbcTemplate jdbc, MemoryScopeVO scope, boolean write) {
        var routes = jdbc.query("""
                SELECT canonical_scope=?::jsonb AS exact_scope,storage_ref,state,authority_epoch,high_water_revision AS watermark,xmin::text AS stamp
                FROM %s.scope WHERE scope_hash=?
                """.formatted(schema) + (write ? " FOR UPDATE" : " FOR SHARE"), (rs, row) -> {
                    if (!rs.getBoolean("exact_scope")) throw unavailable();
                    return new ScopeRowVO(rs.getString("storage_ref"), rs.getString("state"), rs.getLong("authority_epoch"),
                            rs.getLong("watermark"), rs.getString("stamp"));
                }, LegacyMemoryStoreAdapter.json(scope.canonicalScope()), scope.hash());
        if (routes.size() > 1) throw unavailable();
        return routes.isEmpty() ? null : routes.getFirst();
    }

    private JdbcMemoryRepository repository(JdbcTemplate jdbc, LegacyMemoryStoreAdapter.HostIdentityVO identity) {
        return new JdbcMemoryRepository(jdbc, identity.tenantId(), identity.issuer(), identity.owner(), identity.scopeId(), new MemoryJsonCodec());
    }

    private CutVO reconcile(SessionOwnership ownership, MemoryScopeVO scope, LegacyMemoryStoreAdapter.HostIdentityVO identity, long epoch,
                            Instant deadline, CasEvidenceVO evidence) {
        return transaction(ownership, deadline, jdbc -> {
            var observed = select(jdbc, scope, true);
            if (observed == null) throw unavailable();
            if ("COMPONENT".equals(observed.storage())) {
                if (evidence != null && observed.epoch() == evidence.cut().authorityEpoch()
                        && "ACTIVE".equals(observed.state()) && observed.watermark() >= evidence.cut().highWaterRevision()) return evidence.cut();
                return null;
            }
            if ("FROZEN".equals(observed.state()) && observed.epoch() == epoch) {
                clearAndRestore(jdbc, scope, frozen(jdbc, scope, identity, epoch), epoch);
            }
            return null;
        });
    }

    private void clearAndRestore(JdbcTemplate jdbc, MemoryScopeVO scope, JdbcMemoryRepository repository, long epoch) {
        repository.clearFrozenShadow();
        requireOne(jdbc.update("""
                UPDATE %s.scope SET state='ACTIVE',authority_epoch=?,updated_at=GREATEST(updated_at,clock_timestamp())
                WHERE scope_hash=? AND canonical_scope=?::jsonb AND storage_ref='LEGACY' AND state='FROZEN' AND authority_epoch=?
                """.formatted(schema), epoch + 1, scope.hash(), LegacyMemoryStoreAdapter.json(scope.canonicalScope()), epoch));
    }

    private Map<String, MemoryIndexPublication.Binding> registeredBindings(MemoryScopeVO scope) {
        var fixed = indexes.indexBinding();
        var registered = new LinkedHashMap<String, MemoryIndexPublication.Binding>();
        registered.put(fixed.modelRef(), fixed);
        var history = List.copyOf(authority.legacyIndexBindings(scope));
        if (history.size() > 32) throw unavailable();
        for (var binding : history) {
            var previous = registered.putIfAbsent(binding.modelRef(), binding);
            if (previous != null && !previous.equals(binding)) throw unavailable();
        }
        return Map.copyOf(registered);
    }

    private void requireWitness(MemoryScopeVO scope, long epoch, DrainWitnessVO witness, Instant deadline, Instant now) {
        check(deadline, now);
        if (witness == null || !scope.hash().equals(witness.scopeHash()) || epoch != witness.frozenEpoch()
                || witness.deploymentGeneration() == null || !witness.deploymentGeneration().matches("[A-Za-z0-9_.:-]{1,128}")
                || witness.observedAt() == null || witness.observedAt().isAfter(now) || witness.expiresAt() == null
                || !now.isBefore(witness.expiresAt())
                || !hexProof(witness.stoppedIndexWorkSha256()) || !hexProof(witness.reconciledPreferenceProgressSha256())
                || !authority.current(witness)) throw unavailable();
    }

    private String traverse(JdbcMemoryRepository.MigrationBoundsVO bounds, PageTransaction pages, boolean copy, Instant deadline) {
        var digest = digest();
        var version = JdbcMemoryRepository.VersionKeyVO.FIRST;
        long revisions = 0;
        while (revisions < bounds.revisions()) {
            var after = version;
            long starting = revisions;
            var page = pages.run(repository -> {
                var rows = repository.migrationRevisions(after);
                if (rows.isEmpty()) throw unavailable();
                long sequence = starting;
                for (var row : rows) {
                    check(deadline, clock.instant());
                    digest.update(HexFormat.of().parseHex(repository.copyRevision(row, ++sequence, copy)));
                }
                var last = rows.getLast().entry();
                return new PageVO<>(rows.size(), new JdbcMemoryRepository.VersionKeyVO(last.memoryId(), last.revision()));
            });
            revisions += page.count();
            version = (JdbcMemoryRepository.VersionKeyVO) page.last();
        }
        if (revisions != bounds.revisions() || !version.equals(bounds.lastVersion())) throw unavailable();

        String head = "";
        long heads = 0;
        while (heads < bounds.heads()) {
            String after = head;
            var page = pages.run(repository -> {
                var rows = repository.migrationHeads(after);
                if (rows.isEmpty()) throw unavailable();
                for (var row : rows) {
                    check(deadline, clock.instant());
                    digest.update(HexFormat.of().parseHex(repository.copyHead(row, copy)));
                }
                return new PageVO<>(rows.size(), rows.getLast().entry().memoryId());
            });
            heads += page.count();
            head = (String) page.last();
        }
        if (heads != bounds.heads() || !head.equals(bounds.lastHead())) throw unavailable();

        var receipt = JdbcMemoryRepository.ReceiptKeyVO.FIRST;
        long receipts = 0;
        while (receipts < bounds.receipts()) {
            var after = receipt;
            var page = pages.run(repository -> {
                var rows = repository.migrationReceipts(after);
                if (rows.isEmpty()) throw unavailable();
                for (var row : rows) {
                    check(deadline, clock.instant());
                    digest.update(HexFormat.of().parseHex(repository.copyReceipt(row, copy)));
                }
                return new PageVO<>(rows.size(), rows.getLast().key());
            });
            receipts += page.count();
            receipt = (JdbcMemoryRepository.ReceiptKeyVO) page.last();
        }
        if (receipts != bounds.receipts() || !receipt.equals(bounds.lastReceipt())) throw unavailable();
        return HexFormat.of().formatHex(digest.digest());
    }

    private void verifyOutbox(JdbcMemoryRepository repository, long upper, boolean finalCheck, Instant deadline) {
        scanOutbox(repository, 0, upper, false, deadline);
        if (finalCheck) scanOutbox(repository, upper, maxOutboxId(repository.jdbc()), true, deadline);
    }

    private void scanOutbox(JdbcMemoryRepository repository, long start, long upper, boolean tail, Instant deadline) {
        long after = start;
        while (after < upper) {
            check(deadline, repository.databaseNow());
            var rows = repository.jdbc().query("""
                    SELECT id,message_id,idempotency_key,message_fingerprint,destination,payload,content_type,schema_version,headers_json,
                        status,locked_by,locked_until,completed_at
                    FROM tianji_agent.egon_cola_outbox_message
                    WHERE channel=? AND id>? AND id<=? ORDER BY id LIMIT ?
                    """, (rs, row) -> outboxRow(rs), CHANNEL, after, upper, BATCH_SIZE);
            if (rows.isEmpty()) return;
            for (var row : rows) {
                var event = decode(row);
                if (event.tenantId() != repository.tenantId() || !event.ownerIssuer().equals(repository.issuer())
                        || !event.owner().equals(repository.owner()) || !event.scopeId().equals(repository.scopeId())) continue;
                if (tail || !settled(repository, row, event)) throw unavailable();
            }
            after = rows.getLast().id();
        }
    }

    private boolean verifyBuildOutbox(JdbcMemoryRepository repository, MemoryIndexPublication.Event expected, long upper) {
        var rows = repository.jdbc().query("""
                SELECT id,message_id,idempotency_key,message_fingerprint,destination,payload,content_type,schema_version,headers_json,
                    status,locked_by,locked_until,completed_at
                FROM tianji_agent.egon_cola_outbox_message WHERE channel=? AND idempotency_key=?
                """, (rs, row) -> outboxRow(rs), CHANNEL, expected.eventId());
        if (rows.isEmpty()) return false;
        if (rows.size() != 1) throw unavailable();
        var row = rows.getFirst();
        var event = decode(row);
        if (row.id() > upper || !expected.equals(event) || !settled(repository, row, event)) throw unavailable();
        return true;
    }

    private boolean settled(JdbcMemoryRepository repository, OutboxRowVO row, MemoryIndexPublication.Event event) {
        indexes.requireOutboxReference(repository, event);
        return row.lockedBy() == null && row.lockedUntil() == null && row.completed() != null
                && ("SUCCEEDED".equals(row.status()) || "DEAD".equals(row.status()) && indexes.outboxDeadIsHarmless(repository, event));
    }

    private static OutboxRowVO outboxRow(java.sql.ResultSet rs) throws SQLException {
        return new OutboxRowVO(rs.getLong("id"), rs.getString("message_id"), rs.getString("idempotency_key"),
                rs.getString("message_fingerprint"), rs.getString("destination"), rs.getString("payload"), rs.getString("content_type"),
                rs.getString("schema_version"), rs.getString("headers_json"), rs.getString("status"), rs.getString("locked_by"),
                rs.getTimestamp("locked_until"), rs.getTimestamp("completed_at"));
    }

    private MemoryIndexPublication.Event decode(OutboxRowVO row) {
        if (!DESTINATION.equals(row.destination()) || !"1".equals(row.version()) || !"application/json".equals(row.contentType())
                || row.headers() == null || row.headers().length() > 512 || row.payload() == null || row.payload().length() > 16_384) throw unavailable();
        try {
            var event = outbox.decode(row.payload());
            var headers = JSON.readTree(row.headers());
            if (!outbox.messageId(event).equals(row.messageId()) || !event.eventId().equals(row.idempotencyKey())
                    || headers == null || !headers.isObject() || headers.size() != 1 || !headers.path(HEADER).isTextual()
                    || !event.fingerprint().equals(headers.path(HEADER).textValue())
                    || !outbox.fingerprint(row.payload(), Map.of(HEADER, event.fingerprint())).equals(row.fingerprint())) throw unavailable();
            return event;
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    private static long maxOutboxId(JdbcTemplate jdbc) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(max(id),0) FROM " + OUTBOX, Long.class));
    }

    private <T> T transaction(SessionOwnership ownership, Instant deadline, Function<JdbcTemplate, T> action) {
        Instant now = clock.instant();
        check(deadline, now);
        var jdbc = new JdbcTemplate(source);
        Instant bounded = deadline.isBefore(now.plus(TRANSACTION_LIMIT)) ? deadline : now.plus(TRANSACTION_LIMIT);
        int timeout = Math.toIntExact(Math.max(1, (Duration.between(now, bounded).toNanos() + 999_999_999L) / 1_000_000_000L));
        jdbc.setQueryTimeout(timeout);
        var template = new TransactionTemplate(new DataSourceTransactionManager(source));
        template.setTimeout(timeout);
        try {
            return template.execute(status -> {
                jdbc.execute("SET LOCAL ROLE \"" + migrationRole + "\"");
                requireMaintenanceRole(jdbc);
                ownership.requireHeld(jdbc);
                check(deadline, databaseNow(jdbc));
                T result = action.apply(jdbc);
                check(deadline, databaseNow(jdbc));
                return result;
            });
        } catch (DataAccessException | TransactionException failure) {
            log.debug("Memory migration transaction unavailable");
            throw unavailable();
        }
    }

    private void requireMaintenanceRole(JdbcTemplate jdbc) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_user=? AND (session_user=current_user OR NOT pg_has_role(session_user,current_user,'USAGE'))
                   AND NOT pg_is_in_recovery() AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
                   AND NOT rolreplication AND NOT rolbypassrls AND NOT has_database_privilege(current_user,current_database(),'CREATE')
                   AND NOT has_schema_privilege(current_user,'tianji_agent','CREATE') AND NOT has_schema_privilege(current_user,?,'CREATE')
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.agent_memory_scope','INSERT,UPDATE')
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.egon_cola_outbox_message','INSERT,UPDATE')
                   AND NOT has_table_privilege(current_user,'tianji_agent.egon_cola_outbox_message','DELETE,TRUNCATE,TRIGGER')
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.agent_memory_index_manifest','INSERT')
                   AND NOT has_table_privilege(current_user,'tianji_agent.agent_memory_index_manifest','UPDATE,DELETE,TRUNCATE,TRIGGER')
                   AND ARRAY(SELECT a.attname::text FROM pg_attribute a
                       WHERE a.attrelid='tianji_agent.agent_memory_index_manifest'::regclass AND a.attnum>0 AND NOT a.attisdropped
                           AND has_column_privilege(current_user,a.attrelid,a.attnum,'UPDATE') ORDER BY a.attname::text COLLATE "C")
                       = ARRAY['deleted_at','index_state','updated_at']
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.agent_memory_entry','INSERT,UPDATE')
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.agent_memory_revision','INSERT,UPDATE')
                   AND NOT has_any_column_privilege(current_user,'tianji_agent.agent_memory_command_receipt','INSERT,UPDATE')
                   AND NOT has_table_privilege(current_user,'tianji_agent.agent_memory_entry','DELETE,TRUNCATE,TRIGGER')
                   AND NOT has_table_privilege(current_user,'tianji_agent.agent_memory_revision','DELETE,TRUNCATE,TRIGGER')
                   AND NOT has_table_privilege(current_user,'tianji_agent.agent_memory_command_receipt','DELETE,TRUNCATE,TRIGGER')
                FROM pg_roles WHERE rolname=current_user
                """, Boolean.class, migrationRole, schema))) throw unavailable();
    }

    private SessionOwnership acquire(MemoryScopeVO scope, Instant deadline) {
        if (TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        byte[] key = HexFormat.of().parseHex(MemoryJsonCodec.sha256("TIANJI_MEMORY_SCOPE_MIGRATION_LOCK_V1:" + scope.hash()));
        long lockId = ByteBuffer.wrap(key).getLong();
        Connection connection = null;
        try {
            connection = source.getConnection();
            if (!connection.getAutoCommit()) throw unavailable();
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?),pg_backend_pid()")) {
                long seconds = Duration.between(clock.instant(), deadline).toSeconds();
                statement.setQueryTimeout((int) Math.max(1, Math.min(5, seconds)));
                statement.setLong(1, lockId);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) throw unavailable();
                    int pid = result.getInt(2);
                    TransactionSynchronizationManager.bindResource(source, new ConnectionHolder(connection));
                    return new SessionOwnership(connection, lockId, pid);
                }
            }
        } catch (SQLException | RuntimeException failure) {
            if (connection != null) abortConnection(connection);
            throw unavailable();
        }
    }

    private static void abortConnection(Connection connection) {
        try { connection.abort(Runnable::run); }
        catch (SQLException | RuntimeException failure) { log.debug("Memory migration connection could not be aborted"); }
        try { connection.close(); }
        catch (SQLException failure) { log.debug("Memory migration connection could not be closed"); }
    }

    private final class SessionOwnership implements AutoCloseable {
        private final Connection connection;
        private final long lock;
        private final int pid;

        private SessionOwnership(Connection connection, long lock, int pid) {
            this.connection = connection;
            this.lock = lock;
            this.pid = pid;
        }

        private void requireHeld(JdbcTemplate jdbc) {
            long high = lock >>> 32;
            long low = lock & 0xffffffffL;
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype='advisory' AND mode='ExclusiveLock'
                        AND granted AND pid=pg_backend_pid() AND pid=? AND classid::bigint=? AND objid::bigint=? AND objsubid=1)
                    """, Boolean.class, pid, high, low))) throw unavailable();
        }

        @Override public void close() {
            TransactionSynchronizationManager.unbindResourceIfPossible(source);
            try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setQueryTimeout(2);
                statement.setLong(1, lock);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) throw unavailable();
                }
            } catch (SQLException | RuntimeException failure) {
                abortConnection(connection);
                return;
            }
            try { connection.close(); }
            catch (SQLException failure) { abortConnection(connection); }
        }
    }

    private static Instant databaseNow(JdbcTemplate jdbc) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant();
    }

    private static void check(Instant deadline, Instant now) {
        if (!now.isBefore(deadline) || Thread.currentThread().isInterrupted()) throw unavailable();
    }

    private static boolean hexProof(String proof) { return proof != null && proof.matches("[a-f0-9]{64}"); }

    private static void requireOne(int count) { if (count != 1) throw unavailable(); }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 unavailable", missing); }
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory migration evidence or storage is unavailable");
    }

    /** Explicit server-registered authority for a maintenance workflow; current() uses local evidence only. */
    public interface MigrationAuthority {
        void authorize(MemoryScopeVO scope, Instant deadline);
        /**
         * Confirms stopped index admissions, returned model calls across the deployment, and preference progress
         * reconciled against the original receipts. Unknown work and expired leases do not establish this witness.
         */
        DrainWitnessVO quiesce(MemoryScopeVO scope, long frozenEpoch, Instant deadline);
        boolean current(DrainWitnessVO witness);
        default List<MemoryIndexPublication.Binding> legacyIndexBindings(MemoryScopeVO scope) { return List.of(); }
    }

    public interface OutboxProtocol {
        MemoryIndexPublication.Event decode(String payload);
        String messageId(MemoryIndexPublication.Event event);
        String fingerprint(String payload, Map<String, String> headers);
    }

    private interface PageTransaction {
        PageVO<?> run(Function<JdbcMemoryRepository, PageVO<?>> page);
    }

    public record DrainWitnessVO(String scopeHash, long frozenEpoch, String deploymentGeneration, Instant observedAt, Instant expiresAt,
                                 String stoppedIndexWorkSha256, String reconciledPreferenceProgressSha256) { }
    public record CutVO(long authorityEpoch, String storageRef, long highWaterRevision) { }
    private record SourceBoundVO(long maxOutboxId) { }
    private record ScopeRowVO(String storage, String state, long epoch, long watermark, String stamp) { }
    private record PageVO<T>(int count, T last) { }
    private record CasEvidenceVO(String stamp, CutVO cut) { }
    private record OutboxRowVO(long id, String messageId, String idempotencyKey, String fingerprint, String destination, String payload,
                               String contentType, String version, String headers, String status, String lockedBy, Timestamp lockedUntil,
                               Timestamp completed) { }
    private static final class Attempt {
        private CasEvidenceVO switchEvidence;
    }
}
