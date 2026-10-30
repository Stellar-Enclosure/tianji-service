package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adapts current governed facts into USER evidence and revalidates exact revisions before model reuse. */
public final class MemoryContextProvider implements ContextProvider, AutoCloseable {
    private static final String PREFIX = "memory-source-v1:";
    private final PostgresMemoryService memory;
    private final List<MemoryContextBinding> bindings;
    private final Map<String, MemoryContextBinding> references;
    private final Map<String, MemoryRetentionPolicy> policies;
    private final MemoryJsonCodec codec = new MemoryJsonCodec();
    private final Clock clock;
    private final String policy;
    private final ExecutorService calls;
    private final Semaphore admission;

    public MemoryContextProvider(PostgresMemoryService memory, List<MemoryContextBinding> bindings, List<MemoryRetentionPolicy> policies, int maxConcurrent, Clock clock) {
        this.memory = Objects.requireNonNull(memory, "memory"); this.bindings = MemoryContextBinding.validate(bindings); this.clock = Objects.requireNonNull(clock, "clock");
        if (maxConcurrent < 1 || maxConcurrent > 16) throw invalid();
        var configured = new HashMap<String, MemoryRetentionPolicy>(); for (var item : policies) if (configured.putIfAbsent(item.reference(), item) != null) throw invalid();
        this.policies = Map.copyOf(configured); if (!memory.policyReferences().equals(configured.keySet().stream().sorted().toList())) throw invalid();
        var sourceRefs = new HashMap<String, MemoryContextBinding>(); var descriptors = new ArrayList<String>();
        for (var binding : this.bindings) {
            String fingerprint = binding.fingerprint(); if (sourceRefs.putIfAbsent(fingerprint, binding) != null) throw invalid(); descriptors.add(fingerprint);
            for (var ref : binding.retentionPolicyRefs()) {
                var selected = configured.get(ref);
                if (selected == null || selected.modelContextAllowed().isEmpty() || binding.semantic() && (!selected.semanticQueryAllowed()
                        || java.util.Collections.disjoint(selected.modelContextAllowed(), selected.embeddingAllowed()))) throw invalid();
            }
        }
        references = Map.copyOf(sourceRefs); descriptors.addAll(configured.keySet().stream().sorted().toList());
        policy = MemoryJsonCodec.sha256("TIANJI_MEMORY_CONTEXT_PROVIDER_V1:" + descriptors);
        admission = new Semaphore(maxConcurrent);
        calls = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-memory-source-", 0).factory());
    }

    @Override public List<Fragment> provide(Request request) {
        var selected = selected(request); if (selected.isEmpty()) return List.of();
        if (request.authorization().sessionRequired() && !request.historyValidated()) throw unsupported(); long deadline = deadline(request);
        return bounded(deadline, () -> {
            var fragments = new ArrayList<Fragment>(); long bytes = 0;
            for (var binding : selected) {
                String text = binding.semantic() ? request.snapshot().input().text() : null;
                if (binding.semantic() && (text == null || text.isBlank() || text.length() > 2000)) throw unsupported();
                var owner = binding.owner(request.authorization().actor()); var context = context(request, deadline);
                var page = memory.retrieveContext(context, new MemoryRetrievalQuery(owner, binding.scopeId(), binding.kind(), text, null, binding.limit()), binding.retentionPolicyRefs());
                if (!owner.equals(page.owner()) || !binding.scopeId().equals(page.scopeId()) || page.items().size() > binding.limit()) throw unavailable();
                for (var hit : page.items()) {
                    var entry = hit.entry(); if (!owner.equals(entry.owner()) || !binding.scopeId().equals(entry.scopeId())) throw unavailable(); if (!eligible(binding, entry)) continue;
                    String rendered = codec.modelContext(entry); bytes += rendered.getBytes(StandardCharsets.UTF_8).length;
                    if (bytes > 1_048_576) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Memory context evidence exceeds its byte limit");
                    var source = new Source(binding.fingerprint(), ownerHash(context, binding), entry.memoryId(), entry.revision(), entry.contentSha256(),
                            MemoryJsonCodec.sha256(entry.retentionPolicyRef()), MemoryJsonCodec.sha256(rendered));
                    String reference = encode(source);
                    fragments.add(new Fragment(Kind.MEMORY, sourceId(reference), Long.toString(entry.revision()), entry.contentSha256(), reference, rendered, clock.instant()));
                }
                if (page.items().isEmpty()) memory.contextEntries(context(request, deadline), owner, binding.scopeId(), List.of());
            }
            context(request, deadline); return List.copyOf(fragments);
        });
    }

    @Override public void revalidate(Request request, List<Fragment> fragments) {
        if (!selected(request).isEmpty() && request.authorization().sessionRequired() && !request.historyValidated()) throw unsupported(); long deadline = deadline(request);
        if (fragments.size() > 64) throw unavailable(); var sources = new ArrayList<Expected>();
        for (var fragment : List.copyOf(fragments)) {
            if (fragment.kind() != Kind.MEMORY) throw unavailable();
            sources.add(new Expected(fragment.sourceId(), fragment.sourceVersion(), fragment.sourceSha256(), fragment.provenanceId(), MemoryJsonCodec.sha256(fragment.text()), false, -1));
        }
        bounded(deadline, () -> { verify(context(request, deadline), sources, false); return Boolean.TRUE; });
    }

    @Override public Set<Kind> historicalKinds() { return Set.of(Kind.MEMORY); }
    @Override public void revalidateHistory(Request request, List<AgentContextSourceDTO> sources) {
        if (!request.historyValidated()) throw unavailable(); selected(request); revalidateReferences(context(request, deadline(request)), sources);
    }

    /** Saved metadata contains no body; a current HTTP caller or current-run grant must independently authorize its exact facts. */
    public void revalidateReferences(MemoryOperationContext context, List<AgentContextSourceDTO> metadata) {
        noTransaction(); Objects.requireNonNull(context, "context"); if (metadata.size() > 256) throw unavailable(); var sources = new ArrayList<Expected>();
        var remaining = Duration.between(clock.instant(), context.deadlineAt()); if (remaining.isNegative() || remaining.isZero() || remaining.compareTo(Duration.ofSeconds(30)) > 0) throw unavailable();
        for (var source : List.copyOf(metadata)) {
            if (source.kind() != AgentContextSourceKindEnum.MEMORY || source.role() != AgentContextRoleEnum.USER) throw unavailable();
            sources.add(new Expected(source.sourceId(), source.sourceVersion(), source.sourceSha256(), source.provenanceId(), source.modelTextSha256(), true, source.utf8Bytes()));
        }
        // HTTP authority stays on its authenticated request thread; run authority carries its own sealed evidence.
        if (context.runAuthorization() == null) verify(context, sources, true);
        else {
            long nanos = Duration.between(clock.instant(), context.deadlineAt()).toNanos(); if (nanos < 1_000_000 || nanos > TimeUnit.SECONDS.toNanos(30)) throw unavailable();
            bounded(System.nanoTime() + nanos, () -> { verify(context, sources, true); return Boolean.TRUE; });
        }
    }

    private void verify(MemoryOperationContext context, List<Expected> supplied, boolean historical) {
        noTransaction(); if (calls.isShutdown() || supplied.size() > (historical ? 256 : 64) || !clock.instant().isBefore(context.deadlineAt())) throw unavailable();
        var grouped = new LinkedHashMap<MemoryContextBinding, List<Source>>(); var unique = new HashSet<String>();
        var expectations = new HashMap<String, Expected>();
        for (var item : supplied) {
            var source = decode(item.reference()); var binding = references.get(source.binding());
            if (binding == null || !unique.add(item.reference()) || !sourceId(item.reference()).equals(item.id()) || !Long.toString(source.revision()).equals(item.version())
                    || !source.content().equals(item.content()) || !item.wrapped() && !source.modelText().equals(item.modelText()) || !ownerHash(context, binding).equals(source.owner())
                    || context.runAuthorization() != null && !binding.matches(context.runAuthorization())) throw unavailable();
            new GetMemoryQuery(binding.owner(context.actor()), binding.scopeId(), source.memoryId());
            grouped.computeIfAbsent(binding, ignored -> new ArrayList<>()).add(source);
            expectations.put(item.reference(), item);
        }
        for (var group : grouped.entrySet()) {
            var binding = group.getKey(); if (!historical && group.getValue().size() > binding.limit()) throw unavailable();
            var ids = group.getValue().stream().map(Source::memoryId).distinct().toList(); var current = new HashMap<String, MemoryEntryDTO>();
            for (int offset = 0; offset < ids.size(); offset += 32) {
                var entries = memory.contextEntries(context, binding.owner(context.actor()), binding.scopeId(), ids.subList(offset, Math.min(ids.size(), offset + 32)));
                for (var entry : entries) if (current.putIfAbsent(entry.memoryId(), entry) != null) throw unavailable();
            }
            if (current.size() != ids.size()) throw unavailable();
            for (var source : group.getValue()) {
                var entry = current.get(source.memoryId());
                if (entry == null || !binding.owner(context.actor()).equals(entry.owner()) || !eligible(binding, entry) || entry.revision() != source.revision() || !entry.contentSha256().equals(source.content())
                        || !MemoryJsonCodec.sha256(entry.retentionPolicyRef()).equals(source.retention()) || !MemoryJsonCodec.sha256(codec.modelContext(entry)).equals(source.modelText())) throw unavailable();
                var expected = expectations.get(encode(source));
                if (expected.wrapped()) {
                    String actual = new Fragment(Kind.MEMORY, expected.id(), expected.version(), source.content(), expected.reference(), codec.modelContext(entry), clock.instant()).modelText();
                    if (!MemoryJsonCodec.sha256(actual).equals(expected.modelText()) || actual.getBytes(StandardCharsets.UTF_8).length != expected.bytes()) throw unavailable();
                }
            }
        }
        if (!clock.instant().isBefore(context.deadlineAt()) || Thread.currentThread().isInterrupted()) throw unavailable();
    }

    private boolean eligible(MemoryContextBinding binding, MemoryEntryDTO entry) {
        if (entry.state() != MemoryStateEnum.ACTIVE || !entry.confirmed() || entry.content() == null || entry.expiresAt() == null
                || !entry.expiresAt().isAfter(clock.instant()) || entry.effectiveAt().isAfter(clock.instant()) || binding.kind() != null && binding.kind() != entry.kind()) throw unavailable();
        if (!binding.scopeId().equals(entry.scopeId()) || !binding.retentionPolicyRefs().contains(entry.retentionPolicyRef())) return false;
        var selected = policies.get(entry.retentionPolicyRef()); if (selected == null) throw unavailable();
        if (!codec.contentSha256(entry.content()).equals(entry.contentSha256())) throw unavailable();
        return selected.modelContextAllowed().contains(entry.sensitivity());
    }
    private List<MemoryContextBinding> selected(Request request) {
        noTransaction(); Objects.requireNonNull(request, "request"); if (calls.isShutdown()) throw unavailable();
        var selected = bindings.stream().filter(binding -> binding.matches(request.authorization())).toList();
        return selected;
    }
    @Override public String policyFingerprint(Request request) { selected(request); return policy; }
    @Override public void close() { calls.shutdownNow(); }

    private long deadline(Request request) {
        try {
            long nanos = Math.min(request.timeout().toNanos(), Duration.between(clock.instant(), request.authorization().deadlineAt()).toNanos()); if (nanos < 1_000_000) throw unavailable();
            return System.nanoTime() + nanos;
        } catch (ArithmeticException invalidDeadline) { throw unavailable(); }
    }
    private MemoryOperationContext context(Request request, long deadline) {
        long nanos = deadline - System.nanoTime(); if (nanos < 1_000_000 || Thread.currentThread().isInterrupted()) throw unavailable();
        var end = clock.instant().plusNanos(nanos); if (end.isAfter(request.authorization().deadlineAt())) end = request.authorization().deadlineAt();
        return new MemoryOperationContext(request.authorization().actor(), end, request.authorization());
    }
    private <T> T bounded(long deadline, Callable<T> action) {
        if (deadline - System.nanoTime() < 1_000_000) throw unavailable(); if (!admission.tryAcquire()) throw tooLarge();
        var released = new AtomicBoolean(); Runnable release = () -> { if (released.compareAndSet(false, true)) admission.release(); }; var trace = TraceContext.capture();
        var future = new FutureTask<T>(() -> { try (var ignored = trace.open()) { return action.call(); } finally { release.run(); } });
        try { calls.execute(() -> { try { future.run(); } finally { release.run(); } }); }
        catch (RejectedExecutionException closed) { release.run(); throw unavailable(); }
        try { return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); throw unavailable(); }
        catch (TimeoutException timedOut) { future.cancel(true); throw unavailable(); }
        catch (ExecutionException failed) {
            if (failed.getCause() instanceof AgentLedgerException rejected && rejected.code() == AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current Memory context access is denied");
            throw unavailable();
        }
    }

    private static String ownerHash(MemoryOperationContext context, MemoryContextBinding binding) {
        var owner = binding.owner(context.actor()); var framed = new StringBuilder("TIANJI_MEMORY_CONTEXT_OWNER_V1:");
        for (var field : List.of(context.actor().identityFingerprint(), owner.type().name(), owner.id(), binding.scopeId())) framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return MemoryJsonCodec.sha256(framed.toString());
    }
    private static String encode(Source source) {
        byte[] id = source.memoryId().getBytes(StandardCharsets.UTF_8); if (id.length < 1 || id.length > 512 || source.revision() < 1) throw unavailable();
        var bytes = ByteBuffer.allocate(173 + id.length); bytes.put((byte) 1).put(HexFormat.of().parseHex(source.binding())).put(HexFormat.of().parseHex(source.owner())).putInt(id.length).put(id).putLong(source.revision())
                .put(HexFormat.of().parseHex(source.content())).put(HexFormat.of().parseHex(source.retention())).put(HexFormat.of().parseHex(source.modelText()));
        String result = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array()); if (result.length() > 1024) throw unavailable(); return result;
    }
    private static Source decode(String reference) {
        try {
            if (reference == null || !reference.startsWith(PREFIX) || reference.length() > 1024) throw unavailable();
            var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(reference.substring(PREFIX.length()))); if (bytes.get() != 1) throw unavailable();
            String binding = hash(bytes); String owner = hash(bytes); int length = bytes.getInt(); if (length < 1 || length > 512 || bytes.remaining() != length + 104) throw unavailable();
            byte[] id = new byte[length]; bytes.get(id); String memoryId = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(id)).toString();
            var source = new Source(binding, owner, memoryId, bytes.getLong(), hash(bytes), hash(bytes), hash(bytes)); if (!encode(source).equals(reference)) throw unavailable(); return source;
        } catch (IllegalArgumentException | java.nio.BufferUnderflowException | CharacterCodingException invalid) { throw unavailable(); }
    }
    private static String hash(ByteBuffer bytes) { var value = new byte[32]; bytes.get(value); return HexFormat.of().formatHex(value); }
    private static String sourceId(String reference) { return "memory:" + MemoryJsonCodec.sha256(reference); }
    private static void noTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory context requires no ambient transaction"); }
    private record Source(String binding, String owner, String memoryId, long revision, String content, String retention, String modelText) { }
    private record Expected(String id, String version, String content, String reference, String modelText, boolean wrapped, long bytes) { }
    private static AgentLedgerException tooLarge() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory context capacity is unavailable"); }
    private static AgentLedgerException unsupported() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory context requires reviewed text input and session lineage"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory context source is unavailable or changed"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory context configuration is invalid"); }
}
