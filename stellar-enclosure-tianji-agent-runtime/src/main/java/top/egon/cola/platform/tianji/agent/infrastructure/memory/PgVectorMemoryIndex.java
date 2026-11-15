package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.pgvector.PGvector;
import io.opentelemetry.context.Context;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reuses Spring AI stores: writes use the physical table, reads use its current-fact projection. No schema is initialized here. */
public final class PgVectorMemoryIndex implements AutoCloseable {
    private static final Set<String> OWNERSHIP = Set.of("tenantId", "issuerFingerprint", "ownerType", "ownerId", "scopeId", "memoryId", "contentRevision", "modelRef", "dimensions", "contentSha256");
    private final DataSource source;
    private final MemoryIndexPublication.Binding binding;
    private final VersionedMemoryEmbeddingModel model;
    private final VectorStore reader;
    private final Clock clock;
    private final ExecutorService calls;
    private final Semaphore admission;
    private final AtomicBoolean verified = new AtomicBoolean();
    private final MemoryJsonCodec codec = new MemoryJsonCodec();

    public PgVectorMemoryIndex(DataSource source, MemoryIndexPublication.Binding binding, VersionedMemoryEmbeddingModel model, int maximumCalls, Clock clock) {
        this.source = Objects.requireNonNull(source, "source"); this.binding = Objects.requireNonNull(binding, "binding");
        this.model = Objects.requireNonNull(model, "model"); this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumCalls < 1 || maximumCalls > 16 || !binding.modelRef().equals(model.descriptor().modelRef()) || binding.dimensions() != model.dimensions()) throw new IllegalArgumentException("Memory vector binding is invalid");
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(30);
        reader = store(jdbc, model, binding.activeView(), binding.dimensions());
        admission = new Semaphore(maximumCalls);
        calls = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-memory-index-", 0).factory());
    }

    private static PgVectorStore store(JdbcTemplate jdbc, EmbeddingModel model, String table, int dimensions) {
        // The Memory verifier owns the table/view contract. This private store must never create extensions, tables or indexes.
        return PgVectorStore.builder(jdbc, model).schemaName("tianji_agent").vectorTableName(table).dimensions(dimensions)
                .idType(PgVectorStore.PgIdType.UUID).distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.NONE).initializeSchema(false).removeExistingVectorStoreTable(false)
                .vectorTableValidationsEnabled(false).maxDocumentBatchSize(1).build();
    }

    public MemoryIndexPublication.Binding binding() { return binding; }
    public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    void verifiedStorage() { verified.set(true); }
    void unverifiedStorage() { verified.set(false); }
    void validateInput(String text) { model.inputTokenEstimate(text); }

    List<Document> search(long tenantId, String issuer, MemoryOwnerDTO owner, String scope, MemoryKindEnum kind, String text,
                          int limit, List<Allowed> allowed, Instant databaseNow, Instant deadline) {
        if (allowed.isEmpty()) return List.of();
        if (allowed.size() > 128 || limit < 1 || limit > 50) throw unavailable();
        var filters = new FilterExpressionBuilder();
        var forced = filters.and(filters.eq("tenantId", Long.toString(tenantId)), filters.eq("issuerFingerprint", issuer));
        forced = filters.and(forced, filters.and(filters.eq("ownerType", owner.type().name()), filters.eq("ownerId", owner.id())));
        forced = filters.and(forced, filters.and(filters.eq("scopeId", scope), filters.eq("modelRef", binding.modelRef())));
        forced = filters.and(forced, filters.and(filters.lte("effectiveAtMillis", databaseNow.toEpochMilli()), filters.gt("expiresAtMillis", databaseNow.toEpochMilli())));
        if (kind != null) forced = filters.and(forced, filters.eq("kind", kind.name()));
        FilterExpressionBuilder.Op policy = null;
        for (var item : allowed) {
            var condition = filters.and(filters.eq("retentionPolicyRef", item.retentionPolicyRef()), filters.eq("sensitivity", item.sensitivity().name()));
            policy = policy == null ? condition : filters.or(policy, condition);
        }
        var request = SearchRequest.builder().query(text).topK(limit).similarityThreshold(0).filterExpression(filters.and(forced, filters.group(policy)).build()).build();
        return bounded(deadline, () -> {
            requireReady(); model.inputTokenEstimate(text);
            var result = Objects.requireNonNull(reader.similaritySearch(request), "Memory vector result");
            if (result.size() > limit) throw unavailable(); return List.copyOf(result);
        });
    }

    EmbeddingResponse compute(String text) {
        requireReady(); return model.call(new EmbeddingRequest(List.of(text), EmbeddingOptions.builder().build()));
    }

    /** Only a precomputed vector enters this selected fact transaction. The actual provider has already returned. */
    void addComputed(Document document, EmbeddingResponse response) {
        if (!verified.get() || calls.isShutdown() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Computed Memory vectors require the selected fact transaction");
        }
        var vector = response.getResult().getOutput().clone(); String text = document.getText();
        if (vector.length != binding.dimensions() || !model.descriptor().modelName().equals(response.getMetadata().getModel())) throw unavailable();
        var cached = new EmbeddingModel() {
            @Override public int dimensions() { return binding.dimensions(); }
            @Override public String getEmbeddingContent(Document input) { return input.getText(); }
            @Override public float[] embed(Document input) { return call(new EmbeddingRequest(List.of(input.getText()), EmbeddingOptions.builder().build())).getResult().getOutput(); }
            @Override public EmbeddingResponse call(EmbeddingRequest request) {
                if (!request.getInstructions().equals(List.of(text))) throw unavailable();
                var metadata = new EmbeddingResponseMetadata(); metadata.setModel(model.descriptor().modelName());
                return new EmbeddingResponse(List.of(new Embedding(vector.clone(), 0)), metadata);
            }
        };
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
        store(jdbc, cached, binding.vectorTable(), binding.dimensions()).add(List.of(document));
    }

    Optional<PhysicalVector> physical(String table, UUID documentId) {
        requireJdbcReady(); table(table);
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
        var found = jdbc.query("SELECT content,metadata::text AS metadata,embedding::text AS embedding FROM tianji_agent.\"" + table + "\" WHERE id=?",
                (rs, row) -> new PhysicalVector(rs.getString("content"), codec.vectorMetadata(rs.getString("metadata")), new PGvector(rs.getString("embedding")).toArray()), documentId);
        if (found.size() > 1) throw unavailable(); return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    void deletePhysical(String table, UUID documentId, Map<String, Object> ownership) {
        requireJdbcReady(); table(table);
        if (!ownership.keySet().equals(OWNERSHIP) || ownership.values().stream().anyMatch(value -> !(value instanceof String))) throw unavailable();
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
        var current = physical(table, documentId); if (current.isEmpty()) return;
        for (var field : ownership.entrySet()) if (!field.getValue().equals(current.orElseThrow().metadata().get(field.getKey()))) throw unavailable();
        String sql = "DELETE FROM tianji_agent.\"" + table + "\" WHERE id=?";
        var arguments = new ArrayList<Object>(); arguments.add(documentId);
        for (var field : ownership.entrySet()) {
            sql += " AND metadata ->> ? = ?"; arguments.add(field.getKey()); arguments.add(field.getValue());
        }
        jdbc.update(sql, arguments.toArray()); if (physical(table, documentId).isPresent()) throw unavailable();
    }
    void requireEmptyFrozenScope(JdbcMemoryRepository repository, java.util.Collection<MemoryIndexPublication.Binding> bindings) {
        requireJdbcReady();
        if (!repository.frozen() || repository.component() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(3);
        var registered = new java.util.HashSet<>(List.of("agent_memory_vector_v1", binding.vectorTable()));
        for (var candidate : bindings) registered.add(candidate.vectorTable());
        var actual = jdbc.query("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tianji_agent' AND c.relkind IN ('r','p') AND c.relname ~ '^agent_memory_vector(_[a-z0-9]+)?$'
                """, (rs, row) -> rs.getString(1));
        if (!registered.equals(Set.copyOf(actual))) throw unavailable();
        String exact = LegacyMemoryStoreAdapter.json(Map.of("tenantId", Long.toString(repository.tenantId()), "issuerFingerprint", repository.issuer(),
                "ownerType", repository.owner().type().name(), "ownerId", repository.owner().id(), "scopeId", repository.scopeId()));
        for (String table : registered) {
            table(table);
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT has_table_privilege(current_user,?,'SELECT') AND has_table_privilege(current_user,?,'DELETE')
                        AND NOT has_any_column_privilege(current_user,?,'INSERT,UPDATE')
                        AND NOT has_table_privilege(current_user,?,'TRUNCATE,TRIGGER')
                    """, Boolean.class, "tianji_agent." + table, "tianji_agent." + table, "tianji_agent." + table, "tianji_agent." + table))) throw unavailable();
            if (!Boolean.FALSE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM tianji_agent."%s" physical WHERE metadata @> ?::jsonb
                        UNION ALL
                        SELECT 1 FROM tianji_agent.agent_memory_index_manifest manifest
                        JOIN tianji_agent."%s" physical ON physical.id=manifest.document_id
                        WHERE manifest.tenant_id=? AND manifest.owner_issuer=? AND manifest.owner_type=? AND manifest.owner_id=? AND manifest.scope_id=?)
                    """.formatted(table, table), Boolean.class, exact, repository.tenantId(), repository.issuer(), repository.owner().type().name(),
                    repository.owner().id(), repository.scopeId()))) throw unavailable();
        }
    }

    private static void table(String name) { if (name == null || name.length() > 56 || !name.matches("agent_memory_vector(?:_[a-z0-9]+)?")) throw unavailable(); }

    /** Index delivery wraps pre-read, model/write and post-fence cleanup in one task, so timeout cannot skip late-write cleanup. */
    <T> T bounded(Instant deadline, Callable<T> action) {
        requireReady();
        Instant now = clock.instant(); Instant maximum = now.plusSeconds(30);
        long nanos = Duration.between(now, deadline.isBefore(maximum) ? deadline : maximum).toNanos();
        if (nanos < 1_000_000) throw unavailable(); long end = System.nanoTime() + nanos;
        if (!admission.tryAcquire()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory vector capacity is unavailable");
        var released = new AtomicBoolean(); Runnable release = () -> { if (released.compareAndSet(false, true)) admission.release(); };
        var trace = TraceContext.capture(); var parent = Context.current();
        var future = new FutureTask<T>(() -> {
            try (var otel = parent.makeCurrent(); var scope = trace.open()) { return action.call(); }
            finally { release.run(); }
        });
        try { calls.execute(() -> { try { future.run(); } finally { release.run(); } }); }
        catch (RejectedExecutionException closed) { release.run(); throw unavailable(); }
        try { return future.get(Math.max(1, end - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); throw unavailable(); }
        catch (TimeoutException timeout) { future.cancel(true); throw unavailable(); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof AgentLedgerException rejected) throw rejected; throw unavailable();
        }
    }
    private void requireReady() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory vector I/O requires no ambient transaction");
        if (!verified.get() || calls.isShutdown()) throw unavailable();
    }
    private void requireJdbcReady() {
        if (!verified.get() || calls.isShutdown()) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.hasResource(source)) throw new IllegalStateException("Memory vector SQL requires the selected fact transaction");
    }
    @Override public void close() { verified.set(false); calls.shutdownNow(); }

    static Map<String, Object> metadata(long tenantId, String issuer, JdbcMemoryRepository.StoredMemory stored, MemoryIndexPublication.Binding binding) {
        var entry = stored.entry();
        return Map.ofEntries(Map.entry("tenantId", Long.toString(tenantId)), Map.entry("issuerFingerprint", issuer),
                Map.entry("ownerType", entry.owner().type().name()), Map.entry("ownerId", entry.owner().id()), Map.entry("scopeId", entry.scopeId()),
                Map.entry("memoryId", entry.memoryId()), Map.entry("contentRevision", Long.toString(entry.revision())),
                Map.entry("modelRef", binding.modelRef()), Map.entry("dimensions", Integer.toString(binding.dimensions())),
                Map.entry("contentSha256", entry.contentSha256()), Map.entry("kind", entry.kind().name()), Map.entry("sensitivity", entry.sensitivity().name()),
                Map.entry("retentionPolicyRef", entry.retentionPolicyRef()), Map.entry("effectiveAtMillis", entry.effectiveAt().toEpochMilli()), Map.entry("expiresAtMillis", entry.expiresAt().toEpochMilli()));
    }
    record Allowed(String retentionPolicyRef, MemorySensitivityEnum sensitivity) { }
    record PhysicalVector(String content, Map<String, Object> metadata, float[] vector) {
        @Override public String toString() { return "PhysicalMemoryVector[redacted]"; }
    }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory vector index is unavailable"); }
}
