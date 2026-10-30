package top.egon.cola.platform.tianji.agent.infrastructure.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.rag.api.RagRetrievalService;
import top.egon.cola.component.rag.model.RagRetrievalQuery;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adapts the existing RAG mechanics to current owner authorization and immutable source references. */
public final class RagContextProvider implements ContextProvider, AutoCloseable {
    public static final String TENANT = "tianjiTenantId";
    public static final String ISSUER = "tianjiIssuerFingerprint";
    public static final String OWNER_TYPE = "tianjiOwnerType";
    public static final String OWNER_ID = "tianjiOwnerId";
    public static final String ACL_REVISION = "tianjiAclRevision";
    public static final String DOCUMENT_REVISION = "tianjiDocumentRevision";
    public static final String CONTENT_SHA256 = "tianjiChunkSha256";
    private static final String PREFIX = "rag-source-v1:";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private final RagRetrievalService retrieval;
    private final RagContextAuthority authority;
    private final Map<Key, Binding> bindings;
    private final Clock clock;
    private final ExecutorService calls;
    private final Semaphore admission;
    private final String authorityPolicy;
    private final String policy;

    public RagContextProvider(RagRetrievalService retrieval, RagContextAuthority authority, List<Binding> bindings, int maxConcurrent, Clock clock) {
        this.retrieval = Objects.requireNonNull(retrieval, "retrieval"); this.authority = Objects.requireNonNull(authority, "authority");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (bindings == null || bindings.isEmpty() || bindings.size() > 1000 || maxConcurrent < 1 || maxConcurrent > 16) throw invalid();
        Map<Key, Binding> indexed = new HashMap<>(); var ids = new HashSet<String>();
        for (var binding : bindings) {
            if (indexed.putIfAbsent(new Key(binding.tenantId(), binding.issuerFingerprint(), binding.definitionKey(), binding.definitionVersion()), binding) != null
                    || !ids.add(binding.id())) throw invalid();
        }
        this.bindings = Map.copyOf(indexed); authorityPolicy = authority.policyFingerprint();
        if (authorityPolicy == null || !authorityPolicy.matches("[0-9a-f]{64}")) throw invalid();
        try {
            policy = sha256(JSON.writeValueAsString(List.of("TIANJI_RAG_OWNER_CONTEXT_V1", authorityPolicy,
                    bindings.stream().sorted(Comparator.comparing(Binding::id)).toList())));
        } catch (JsonProcessingException failure) { throw invalid(); }
        admission = new Semaphore(maxConcurrent);
        calls = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-rag-source-", 0).factory());
    }

    @Override public List<Fragment> provide(Request request) {
        var binding = binding(request); if (binding == null) return List.of();
        String query = request.snapshot().input().text();
        if (query == null || query.isBlank() || query.length() > 2000 || !StandardCharsets.UTF_8.newEncoder().canEncode(query)) throw unsupported();
        long deadline = deadline(request);
        return bounded(deadline, () -> {
            var scope = scope(remaining(request, deadline), binding);
            var attributes = attributes(request, scope);
            var chunks = Objects.requireNonNull(retrieval.retrieve(new RagRetrievalQuery(binding.collectionId(), binding.logicalModelName(), query,
                    binding.topK(), binding.similarityThreshold(), attributes)), "RAG result");
            remaining(request, deadline);
            if (chunks.size() > binding.topK()) throw unavailable();
            List<Fragment> fragments = new ArrayList<>(); List<RagContextAuthority.Source> sources = new ArrayList<>();
            var seen = new HashSet<String>(); long bytes = 0;
            for (var chunk : chunks) {
                if (!binding.collectionId().equals(chunk.collectionId()) || !binding.logicalModelName().equals(chunk.logicalModelName())
                        || !seen.add(chunk.chunkId()) || chunk.content() == null || chunk.content().isBlank() || chunk.content().length() > 1_048_576
                        || !StandardCharsets.UTF_8.newEncoder().canEncode(chunk.content())
                        || chunk.score() != null && (!Double.isFinite(chunk.score()) || chunk.score() < binding.similarityThreshold() || chunk.score() > 1)) throw unavailable();
                for (var attribute : attributes.entrySet()) if (!attribute.getValue().equals(chunk.attributes().get(attribute.getKey()))) throw unavailable();
                String textHash = sha256(chunk.content()); bytes += chunk.content().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > 1_048_576 || !textHash.equals(chunk.attributes().get(CONTENT_SHA256))) throw unavailable();
                var source = new RagContextAuthority.Source(scope.ownerSha256(), binding.id(), binding.collectionId(), binding.logicalModelName(), chunk.documentId(),
                        chunk.chunkId(), chunk.chunkIndex(), chunk.attributes().get(DOCUMENT_REVISION), textHash, scope.aclRevision());
                sources.add(source);
                String reference = encode(source);
                fragments.add(new Fragment(Kind.RAG, sourceId(reference), source.documentRevision(), source.contentSha256(), reference, chunk.content(), clock.instant()));
            }
            authority.revalidate(remaining(request, deadline), binding, scope, List.copyOf(sources));
            remaining(request, deadline);
            return List.copyOf(fragments);
        });
    }

    @Override public void revalidate(Request request, List<Fragment> fragments) {
        var binding = binding(request);
        if (binding == null) { if (!fragments.isEmpty()) throw unavailable(); return; }
        long deadline = deadline(request);
        bounded(deadline, () -> {
            var scope = scope(remaining(request, deadline), binding);
            if (fragments.size() > binding.topK()) throw unavailable();
            List<RagContextAuthority.Source> sources = new ArrayList<>(); var seen = new HashSet<String>();
            for (var fragment : fragments) {
                var source = decode(fragment.provenanceId());
                if (fragment.kind() != Kind.RAG || !seen.add(fragment.sourceId()) || !fragment.sourceId().equals(sourceId(fragment.provenanceId()))
                        || !scope.ownerSha256().equals(source.ownerSha256()) || !binding.id().equals(source.bindingId()) || !binding.collectionId().equals(source.collectionId())
                        || !binding.logicalModelName().equals(source.logicalModelName()) || !scope.aclRevision().equals(source.aclRevision())
                        || !fragment.sourceVersion().equals(source.documentRevision()) || !fragment.sourceSha256().equals(source.contentSha256())
                        || !source.contentSha256().equals(sha256(fragment.text()))) throw unavailable();
                sources.add(source);
            }
            authority.revalidate(remaining(request, deadline), binding, scope, List.copyOf(sources));
            remaining(request, deadline); return Boolean.TRUE;
        });
    }

    @Override public String policyFingerprint(Request request) { binding(request); return policy; }
    @Override public void close() { calls.shutdownNow(); }

    private Binding binding(Request request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("RAG context access requires no ambient transaction");
        Objects.requireNonNull(request, "request");
        var actor = request.authorization().actor(); var definition = request.snapshot().definition();
        var binding = bindings.get(new Key(actor.tenantId(), actor.issuerFingerprint(), definition.definitionKey(), definition.version()));
        if (binding != null && request.authorization().sessionRequired()) throw unsupported();
        try { if (!authorityPolicy.equals(authority.policyFingerprint()) || calls.isShutdown()) throw unavailable(); }
        catch (RuntimeException failure) { throw unavailable(); }
        return binding;
    }
    private RagContextAuthority.Scope scope(Request request, Binding binding) {
        var scope = Objects.requireNonNull(authority.authorize(request, binding), "RAG search scope");
        if (!request.authorization().actor().identityFingerprint().equals(scope.ownerSha256()) || !binding.collectionId().equals(scope.collectionId())
                || !binding.logicalModelName().equals(scope.logicalModelName())) throw unavailable();
        return scope;
    }
    private static Map<String, String> attributes(Request request, RagContextAuthority.Scope scope) {
        var actor = request.authorization().actor();
        return Map.of(TENANT, Long.toString(actor.tenantId()), ISSUER, actor.issuerFingerprint(), OWNER_TYPE, actor.subjectType().name(),
                OWNER_ID, actor.subjectId(), ACL_REVISION, scope.aclRevision());
    }
    private long deadline(Request request) {
        long nanos;
        try { nanos = Math.min(request.timeout().toNanos(), Duration.between(clock.instant(), request.authorization().deadlineAt()).toNanos()); }
        catch (ArithmeticException invalidDeadline) { throw unavailable(); }
        if (nanos < 1_000_000) throw unavailable();
        return System.nanoTime() + nanos;
    }
    private <T> T bounded(long deadline, Callable<T> action) {
        long nanos = deadline - System.nanoTime(); if (nanos < 1_000_000) throw unavailable();
        if (!admission.tryAcquire()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "RAG context source capacity is unavailable");
        var released = new AtomicBoolean();
        Runnable release = () -> { if (released.compareAndSet(false, true)) admission.release(); };
        // Publish completion only after the actual call releases capacity; cancellation before start also releases it.
        var future = new FutureTask<T>(() -> { try { return action.call(); } finally { release.run(); } });
        try { calls.execute(() -> { try { future.run(); } finally { release.run(); } }); }
        catch (RejectedExecutionException closed) { release.run(); throw unavailable(); }
        try { return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); throw unavailable(); }
        catch (TimeoutException timedOut) { future.cancel(true); throw unavailable(); }
        catch (ExecutionException failed) {
            if (failed.getCause() instanceof AgentLedgerException rejected && rejected.code() == AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current RAG context access is denied");
            }
            throw unavailable();
        }
    }
    private static Request remaining(Request request, long deadline) {
        long nanos = deadline - System.nanoTime(); if (nanos < 1_000_000 || Thread.currentThread().isInterrupted()) throw unavailable();
        return new Request(request.authorization(), request.snapshot(), request.leaseEpoch(), Duration.ofNanos(nanos));
    }
    private static String encode(RagContextAuthority.Source source) {
        try {
            String value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(source));
            if (value.length() > 1024) throw unavailable(); return value;
        } catch (JsonProcessingException failure) { throw unavailable(); }
    }
    private static RagContextAuthority.Source decode(String value) {
        try {
            if (value.length() > 1024 || !value.startsWith(PREFIX)) throw unavailable();
            var source = JSON.readValue(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), RagContextAuthority.Source.class);
            if (!value.equals(encode(source))) throw unavailable(); return source;
        } catch (java.io.IOException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private static String sourceId(String reference) { return "rag:" + sha256(reference); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }

    public record Binding(String id, long tenantId, String issuerFingerprint, String definitionKey, long definitionVersion,
                          String collectionId, String logicalModelName, int topK, double similarityThreshold) {
        public Binding {
            for (var value : List.of(id, definitionKey, collectionId, logicalModelName)) {
                if (value.isBlank() || !value.equals(value.trim()) || value.length() > 128 || value.chars().anyMatch(Character::isISOControl)
                        || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
            }
            if (tenantId < 1 || issuerFingerprint == null || !issuerFingerprint.matches("[0-9a-f]{64}") || definitionVersion < 1
                    || topK < 1 || topK > 32 || !Double.isFinite(similarityThreshold) || similarityThreshold < 0 || similarityThreshold > 1) throw invalid();
        }
    }
    private record Key(long tenantId, String issuerFingerprint, String definitionKey, long definitionVersion) { }
    private static AgentLedgerException unsupported() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "RAG context requires bounded text input and reviewed source lineage"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current RAG context authorization or source is unavailable"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("RAG context bindings are invalid"); }
}
