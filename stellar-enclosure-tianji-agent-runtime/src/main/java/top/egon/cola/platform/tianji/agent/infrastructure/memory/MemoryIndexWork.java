package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Applies one existing Outbox intention. Embedding is outside transactions; vector SQL and the current-fact fence commit together. */
@Slf4j
public final class MemoryIndexWork {
    private final DataSource source;
    private final PostgresMemoryService memory;
    private final PgVectorMemoryIndex index;
    private final Consumer<MemoryIndexPublication.Event> buildAuthority;
    private final Clock clock;
    private final MemoryJsonCodec codec = new MemoryJsonCodec();

    /** The authority is a current, local platform/realm/egress check, not an end-user impersonation or an I/O callback. */
    public MemoryIndexWork(DataSource source, PostgresMemoryService memory, PgVectorMemoryIndex index, Consumer<MemoryIndexPublication.Event> buildAuthority, Clock clock) {
        this.source = Objects.requireNonNull(source, "source"); this.memory = Objects.requireNonNull(memory, "memory"); this.index = Objects.requireNonNull(index, "index");
        this.buildAuthority = Objects.requireNonNull(buildAuthority, "buildAuthority"); this.clock = Objects.requireNonNull(clock, "clock");
        if (!memory.usesDataSource(source) || !index.usesDataSource(source) || !memory.indexBinding().equals(index.binding())) throw new IllegalArgumentException("Memory index work requires the matching fact/index source");
    }

    public void apply(MemoryIndexPublication.Event event, Instant deadline) {
        Objects.requireNonNull(event, "event"); Objects.requireNonNull(deadline, "deadline");
        index.bounded(deadline, () -> {
            if (event.action() == MemoryIndexPublication.Action.BUILD) build(event, deadline);
            else cleanup(event, deadline);
            return Boolean.TRUE;
        });
    }

    private void build(MemoryIndexPublication.Event event, Instant deadline) {
        var before = transaction(event, false, deadline, null, (repository, now) -> {
            if (repository.component() || repository.frozen()) throw unavailable();
            var snapshot = snapshot(repository, event, now);
            if (!current(snapshot, event)) return new BuildTicketVO(snapshot, repository.authorityEpoch(), false);
            eligible(event, snapshot.current());
            var manifest = target(snapshot.manifest(), event);
            if (manifest.state().equals("DELETED")) throw unavailable();
            var physical = index.physical(manifest.vectorTable(), manifest.documentId());
            if (physical.isPresent()) validPhysical(event, snapshot.current(), physical.orElseThrow());
            return new BuildTicketVO(snapshot, repository.authorityEpoch(), physical.isEmpty());
        });
        if (!current(before.snapshot(), event)) {
            cleanupBuild(event, deadline, before.epoch());
            return;
        }
        EmbeddingResponse computed = null;
        String text = codec.content(before.snapshot().current().entry().content());
        if (before.needsEmbedding()) {
            if (!clock.instant().isBefore(deadline) || Thread.currentThread().isInterrupted()) throw unavailable();
            computed = index.compute(text);
        }
        EmbeddingResponse result = computed;
        // A timed-out caller may have interrupted the completed model call. Only bounded local SQL follows from here.
        boolean interrupted = Thread.interrupted();
        try {
            transaction(event, true, deadline, before.epoch(), (repository, now) -> {
                if (repository.component()) throw unavailable();
                var current = snapshot(repository, event, now);
                if (!current(current, event)) { deleteBuild(repository, current.manifest()); return Boolean.TRUE; }
                eligible(event, current.current());
                var target = target(current.manifest(), event); if (target.state().equals("DELETED")) throw unavailable();
                var existing = index.physical(target.vectorTable(), target.documentId());
                if (existing.isPresent()) validPhysical(event, current.current(), existing.orElseThrow());
                else {
                    if (result == null) throw unavailable();
                    String content = codec.content(current.current().entry().content());
                    var document = new Document(target.documentId().toString(), content, PgVectorMemoryIndex.metadata(event.tenantId(), event.ownerIssuer(), current.current(), index.binding()));
                    index.addComputed(document, result);
                }
                if (!repository.activate(target)) throw unavailable(); return Boolean.TRUE;
            });
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private void eligible(MemoryIndexPublication.Event event, JdbcMemoryRepository.StoredMemory stored) {
        if (!event.modelRef().equals(index.binding().modelRef()) || !stored.indexModelRef().equals(index.binding().modelRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "The fixed Memory embedding model is unavailable");
        }
        var policy = memory.policy(stored.entry().retentionPolicyRef());
        if (!policy.embeddingAllowed().contains(stored.entry().sensitivity())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Memory embedding egress is outside its fixed policy");
        buildAuthority.accept(event);
    }

    private JdbcMemoryRepository.Manifest target(JdbcMemoryRepository.Manifest manifest, MemoryIndexPublication.Event event) {
        if (manifest == null || !manifest.contentSha256().equals(event.contentSha256()) || !manifest.modelRef().equals(event.modelRef())
                || !manifest.documentId().equals(event.documentId()) || !manifest.vectorTable().equals(index.binding().vectorTable()) || manifest.dimensions() != index.binding().dimensions()) throw unavailable();
        return manifest;
    }

    private void cleanupBuild(MemoryIndexPublication.Event event, Instant deadline, long epoch) {
        transaction(event, true, deadline, epoch, (repository, now) -> {
            if (repository.component()) throw unavailable();
            var snapshot = snapshot(repository, event, now);
            if (current(snapshot, event)) throw unavailable(); deleteBuild(repository, snapshot.manifest()); return Boolean.TRUE;
        });
    }

    private void deleteBuild(JdbcMemoryRepository repository, JdbcMemoryRepository.Manifest manifest) {
        if (manifest == null) throw unavailable();
        index.deletePhysical(manifest.vectorTable(), manifest.documentId(), ownership(repository, manifest)); repository.deleted(manifest);
    }

    private void cleanup(MemoryIndexPublication.Event event, Instant deadline) {
        Long epoch = null;
        for (int chunk = 0; chunk < 128; chunk++) {
            if (!clock.instant().isBefore(deadline) || Thread.currentThread().isInterrupted()) throw continuation();
            var work = transaction(event, true, deadline, epoch, (repository, now) -> {
                reference(repository, event);
                var current = repository.current(event.memoryId(), true).orElseThrow(MemoryIndexWork::unavailable).entry();
                if (event.action() == MemoryIndexPublication.Action.DELETE_MEMORY) {
                    if (current.revision() < event.revision() || current.state() != MemoryStateEnum.FORGOTTEN && current.state() != MemoryStateEnum.EXPIRED) throw unavailable();
                } else if (current.revision() <= event.revision() && current.state() != MemoryStateEnum.FORGOTTEN && current.state() != MemoryStateEnum.EXPIRED) throw unavailable();
                if (repository.component()) {
                    repository.assertLegacyIndexRetired(event.memoryId(), index.binding(), false);
                    return new CleanupResultVO(false, repository.authorityEpoch());
                }
                Long revision = event.action() == MemoryIndexPublication.Action.DELETE_REVISION ? event.revision() : null;
                var batch = repository.deletionBatch(event.memoryId(), revision, 9);
                for (var manifest : batch.stream().limit(8).toList()) {
                    index.deletePhysical(manifest.vectorTable(), manifest.documentId(), ownership(repository, manifest)); repository.deleted(manifest);
                }
                return new CleanupResultVO(batch.size() > 8, repository.authorityEpoch());
            });
            if (!work.more()) return;
            epoch = work.epoch();
        }
        throw continuation();
    }

    private Snapshot snapshot(JdbcMemoryRepository repository, MemoryIndexPublication.Event event, Instant now) {
        reference(repository, event);
        return new Snapshot(repository.current(event.memoryId(), false).orElseThrow(MemoryIndexWork::unavailable),
                repository.manifest(event.memoryId(), event.revision(), event.modelRef()).orElse(null), now);
    }
    private static void reference(JdbcMemoryRepository repository, MemoryIndexPublication.Event event) {
        var reference = repository.reference(event.memoryId(), event.revision()).orElseThrow(MemoryIndexWork::unavailable);
        if (!event.contentSha256().equals(reference.contentSha256()) || !event.occurredAt().equals(reference.writtenAt())
                || event.action() == MemoryIndexPublication.Action.BUILD && !event.modelRef().equals(reference.modelRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory index event reference changed");
        }
    }
    private static boolean current(Snapshot snapshot, MemoryIndexPublication.Event event) {
        var entry = snapshot.current().entry();
        return entry.revision() == event.revision() && entry.state() == MemoryStateEnum.ACTIVE && entry.confirmed()
                && entry.expiresAt().isAfter(snapshot.now()) && entry.contentSha256().equals(event.contentSha256());
    }

    private Map<String, Object> ownership(JdbcMemoryRepository repository, JdbcMemoryRepository.Manifest manifest) {
        return Map.ofEntries(Map.entry("tenantId", Long.toString(repository.tenantId())), Map.entry("issuerFingerprint", repository.issuer()),
                Map.entry("ownerType", repository.owner().type().name()), Map.entry("ownerId", repository.owner().id()), Map.entry("scopeId", repository.scopeId()),
                Map.entry("memoryId", manifest.memoryId()), Map.entry("contentRevision", Long.toString(manifest.revision())), Map.entry("modelRef", manifest.modelRef()),
                Map.entry("dimensions", Integer.toString(manifest.dimensions())), Map.entry("contentSha256", manifest.contentSha256()));
    }
    private void validPhysical(MemoryIndexPublication.Event event, JdbcMemoryRepository.StoredMemory current, PgVectorMemoryIndex.PhysicalVector physical) {
        if (!codec.content(current.entry().content()).equals(physical.content()) || !PgVectorMemoryIndex.metadata(event.tenantId(), event.ownerIssuer(), current, index.binding()).equals(physical.metadata())
                || physical.vector().length != index.binding().dimensions()) throw unavailable();
        double norm = 0; for (float value : physical.vector()) { if (!Float.isFinite(value)) throw unavailable(); norm += (double) value * value; }
        if (norm == 0) throw unavailable();
    }
    private <T> T transaction(MemoryIndexPublication.Event event, boolean lock, Instant deadline, Long expectedEpoch,
                              BiFunction<JdbcMemoryRepository, Instant, T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory index work requires its own short transaction");
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(3);
        try {
            T value = transaction.execute(status -> {
                var repository = new JdbcMemoryRepository(jdbc, event.tenantId(), event.ownerIssuer(), event.owner(), event.scopeId(), codec);
                var adapter = memory.hostAdapter();
                Function<JdbcMemoryRepository, T> apply = selected -> {
                    if (lock) selected.lockScope();
                    Instant now = selected.databaseNow();
                    if (adapter != null && !now.isBefore(deadline)) throw unavailable();
                    T result = action.apply(selected, now);
                    if (adapter != null && !selected.databaseNow().isBefore(deadline)) throw unavailable();
                    return result;
                };
                if (adapter == null) return apply.apply(repository);
                return adapter.inScope(repository, deadline, lock, false, expectedEpoch,
                        () -> memory.usesHostAuthority(adapter) && index.binding().equals(memory.indexBinding()), apply);
            });
            if (value == null) throw unavailable(); return value;
        } catch (AgentLedgerException rejected) { throw rejected; }
        catch (DataAccessException | TransactionException failed) { log.debug("Memory index transaction unavailable"); throw unavailable(); }
    }
    private record BuildTicketVO(Snapshot snapshot, long epoch, boolean needsEmbedding) { }
    private record CleanupResultVO(boolean more, long epoch) { }
    private record Snapshot(JdbcMemoryRepository.StoredMemory current, JdbcMemoryRepository.Manifest manifest, Instant now) { }
    private static AgentLedgerException continuation() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory vector deletion has retained continuation work"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory index work is unavailable"); }
}
