package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryHitDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static top.egon.cola.platform.tianji.agent.infrastructure.memory.JdbcMemoryRepository.StoredMemory;

/** A Domain Service for current authorization and short fact/revision/receipt/Outbox transactions; it runs no model in a transaction. */
public final class PostgresMemoryService {
    private final DataSource source;
    private final MemoryAuthorization authorization;
    private final MemoryIndexPublication publication;
    private final MemoryIndexPublication.Binding binding;
    private final Map<String, MemoryRetentionPolicy> policies;
    private final Clock clock;
    private final PgVectorMemoryIndex index;
    private final MemoryJsonCodec codec = new MemoryJsonCodec();
    private final AtomicBoolean initialized = new AtomicBoolean();

    public PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                                 List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock) {
        this(ledger, source, authorization, policies, publication, clock, null);
    }

    public PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                                 List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock, PgVectorMemoryIndex index) {
        this.source = Objects.requireNonNull(source, "source"); this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.publication = Objects.requireNonNull(publication, "publication"); this.clock = Objects.requireNonNull(clock, "clock");
        if (!Objects.requireNonNull(ledger, "ledger").usesDataSource(source) || !publication.usesDataSource(source)
                || policies == null || policies.isEmpty() || policies.size() > 32) throw new IllegalArgumentException("Memory storage/publication binding is invalid");
        binding = Objects.requireNonNull(publication.binding(), "index binding");
        this.index = index;
        if (index != null && (!index.usesDataSource(source) || !binding.equals(index.binding()))) throw new IllegalArgumentException("Memory vector source does not match its fact binding");
        var versions = new HashMap<String, String>(); var selected = new HashMap<String, MemoryRetentionPolicy>();
        for (var policy : policies) {
            String previous = versions.putIfAbsent(policy.key() + '@' + policy.version(), policy.reference());
            if (previous != null && !previous.equals(policy.reference()) || selected.putIfAbsent(policy.reference(), policy) != null) throw new IllegalArgumentException("Memory policy versions are ambiguous");
        }
        this.policies = Map.copyOf(selected);
    }

    /** Read-only startup verification; schema/extension provisioning is an operator action. */
    public synchronized void verifyStorage(int baseVectorDimensions, String expectedVectorVersion) {
        noTransaction(); initialized.set(false);
        if (index != null) index.unverifiedStorage();
        new MemoryStorageVerifier(source, baseVectorDimensions, expectedVectorVersion, binding).verify();
        if (!binding.equals(publication.binding()) || !publication.usesDataSource(source)) throw unavailable();
        if (index != null) index.verifiedStorage();
        initialized.set(true);
    }
    public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    public List<String> policyReferences() { return policies.keySet().stream().sorted().toList(); }
    public MemoryIndexPublication.Binding indexBinding() { return binding; }

    public MemoryMutationResultDTO upsert(MemoryOperationContext context, UpsertMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var authorized = authorize(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.UPSERT, command.memoryId(), 0, command.source());
        String requestHash = codec.commandSha256(command); String contentHash = codec.contentSha256(command.content());
        return transaction(authorized, command.owner(), command.scopeId(), true, (repository, now) -> {
            var receipt = repository.receipt(authorized.actor(), "UPSERT", command.commandId(), requestHash); if (receipt.isPresent()) return receipt.orElseThrow();
            var policy = policy(command.retentionPolicyRef()); repository.verifySource(authorized.actor(), command.source());
            var existing = repository.current(command.memoryId(), true);
            StoredMemory duplicate = null;
            if (existing.isPresent()) {
                var row = existing.orElseThrow(); var entry = row.entry();
                if (!live(entry, now) || !entry.kind().equals(command.kind()) || !entry.contentSha256().equals(contentHash)) throw revisionConflict();
                duplicate = row;
            } else {
                var sameContent = repository.duplicate(command.kind(), contentHash);
                if (sameContent.isPresent()) {
                    var row = sameContent.orElseThrow();
                    if (row.entry().expiresAt().isAfter(now)) duplicate = row;
                    else retire(repository, authorized, row, MemoryStateEnum.EXPIRED, "RETENTION_EXPIRED", now);
                }
            }
            MemoryMutationResultDTO result;
            if (duplicate != null) result = result(command.commandId(), duplicate.entry(), MemoryMutationOutcomeEnum.DEDUPLICATED, now);
            else {
                if (repository.activeCount(now) >= policy.maximumActiveEntries()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory owner/scope quota is exhausted");
                Instant effective = command.effectiveAt() == null ? now : command.effectiveAt().truncatedTo(ChronoUnit.MICROS);
                if (effective.isAfter(now.plus(policy.maximumLifetime()))) throw invalidLifetime();
                Instant expires = command.expiresAt() == null ? effective.plus(policy.defaultLifetime()) : command.expiresAt().truncatedTo(ChronoUnit.MICROS);
                if (!expires.isAfter(effective) || !expires.isAfter(now) || expires.isAfter(now.plus(policy.maximumLifetime()))
                        || Duration.between(effective, expires).compareTo(policy.maximumLifetime()) > 0) throw invalidLifetime();
                var entry = new MemoryEntryDTO(command.owner(), command.scopeId(), command.memoryId(), command.kind(), 1, null,
                        command.confirmed() ? MemoryStateEnum.ACTIVE : MemoryStateEnum.CANDIDATE, command.content(), contentHash, command.source(), command.confidence(),
                        command.confirmed(), command.sensitivity(), policy.reference(), effective, expires, now, now);
                var row = new StoredMemory(entry, binding.modelRef(), command.source().evidenceSha256(), authorized.actor().identityFingerprint(), "EXPLICIT_CREATE", null);
                repository.insert(row); build(repository, authorized, row, policy, now);
                result = result(command.commandId(), entry, MemoryMutationOutcomeEnum.CREATED, now);
            }
            repository.receipt(authorized.actor(), "UPSERT", requestHash, result); repository.touchScope(now); return result;
        });
    }

    public MemoryMutationResultDTO correct(MemoryOperationContext context, CorrectMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var authorized = authorize(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.CORRECT, command.memoryId(), command.expectedRevision(), command.source());
        String requestHash = codec.commandSha256(command); String hash = codec.contentSha256(command.content());
        return transaction(authorized, command.owner(), command.scopeId(), true, (repository, now) -> {
            var receipt = repository.receipt(authorized.actor(), "CORRECT", command.commandId(), requestHash); if (receipt.isPresent()) return receipt.orElseThrow();
            var previous = repository.current(command.memoryId(), true).orElseThrow(PostgresMemoryService::notFound); var old = previous.entry();
            if (old.revision() != command.expectedRevision() || !live(old, now)) throw revisionConflict();
            var policy = policy(old.retentionPolicyRef()); repository.verifySource(authorized.actor(), command.source());
            var duplicate = repository.duplicate(old.kind(), hash);
            if (duplicate.isPresent() && !duplicate.orElseThrow().entry().memoryId().equals(old.memoryId())) {
                var other = duplicate.orElseThrow();
                if (other.entry().expiresAt().isAfter(now)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory content already belongs to another entry");
                retire(repository, authorized, other, MemoryStateEnum.EXPIRED, "RETENTION_EXPIRED", now);
            }
            var entry = new MemoryEntryDTO(old.owner(), old.scopeId(), old.memoryId(), old.kind(), next(old.revision()), old.revision(), MemoryStateEnum.ACTIVE,
                    command.content(), hash, command.source(), command.confidence(), true, old.sensitivity(), old.retentionPolicyRef(), old.effectiveAt(), old.expiresAt(), old.createdAt(), now);
            var row = new StoredMemory(entry, binding.modelRef(), command.source().evidenceSha256(), authorized.actor().identityFingerprint(), command.reasonCode(), null);
            repository.replace(row, old.revision(), false);
            publication.publish(event(authorized, old, MemoryIndexPublication.Action.DELETE_REVISION, null, null, previous.entry().updatedAt()));
            build(repository, authorized, row, policy, now);
            var result = result(command.commandId(), entry, MemoryMutationOutcomeEnum.CORRECTED, now);
            repository.receipt(authorized.actor(), "CORRECT", requestHash, result); repository.touchScope(now); return result;
        });
    }

    public MemoryMutationResultDTO forget(MemoryOperationContext context, ForgetMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var authorized = authorize(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.FORGET, command.memoryId(), command.expectedRevision(), null);
        String requestHash = codec.commandSha256(command);
        return transaction(authorized, command.owner(), command.scopeId(), true, (repository, now) -> {
            var receipt = repository.receipt(authorized.actor(), "FORGET", command.commandId(), requestHash); if (receipt.isPresent()) return receipt.orElseThrow();
            var previous = repository.current(command.memoryId(), true).orElseThrow(PostgresMemoryService::notFound);
            if (previous.entry().revision() != command.expectedRevision() || previous.entry().state() == MemoryStateEnum.FORGOTTEN) throw revisionConflict();
            var forgotten = retire(repository, authorized, previous, MemoryStateEnum.FORGOTTEN, command.reasonCode(), now);
            var result = result(command.commandId(), forgotten.entry(), MemoryMutationOutcomeEnum.FORGOTTEN, now);
            repository.receipt(authorized.actor(), "FORGET", requestHash, result); repository.touchScope(now); return result;
        });
    }

    public MemoryEntryDTO get(MemoryOperationContext context, GetMemoryQuery query) {
        Objects.requireNonNull(query, "query");
        var authorized = authorize(context, query.owner(), query.scopeId(), MemoryAuthorization.Operation.GET, query.memoryId(), 0, null);
        return transaction(authorized, query.owner(), query.scopeId(), false, (repository, now) -> projection(repository.current(query.memoryId(), false).orElseThrow(PostgresMemoryService::notFound).entry(), now));
    }

    public MemoryRetrievalPageDTO retrieve(MemoryOperationContext context, MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query");
        var authorized = authorize(context, query.owner(), query.scopeId(), query.text() == null ? MemoryAuthorization.Operation.RETRIEVE : MemoryAuthorization.Operation.SEARCH, null, 0, null);
        if (query.text() != null) return semantic(authorized, query);
        return transaction(authorized, query.owner(), query.scopeId(), false, (repository, now) -> {
            var found = repository.list(now, query.kind(), query.afterMemoryId(), query.limit() + 1); boolean more = found.size() > query.limit();
            var items = found.stream().limit(query.limit()).map(row -> new MemoryHitDTO(row.entry(), null)).toList();
            return new MemoryRetrievalPageDTO(query.owner(), query.scopeId(), items, more ? items.getLast().entry().memoryId() : null);
        });
    }

    private MemoryRetrievalPageDTO semantic(MemoryOperationContext context, MemoryRetrievalQuery query) {
        if (index == null) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory semantic index is not configured");
        var prepared = transaction(context, query.owner(), query.scopeId(), false, (repository, now) -> new SearchPreparation(now, allowed(context, query.owner(), query.scopeId(), repository.indexReadiness(now, query.kind(), binding))));
        var candidates = index.search(context.actor().tenantId(), context.actor().issuerFingerprint(), query.owner(), query.scopeId(), query.kind(), query.text(), query.limit(),
                prepared.allowed(), prepared.now(), context.deadlineAt());
        return transaction(context, query.owner(), query.scopeId(), false, (repository, now) -> {
            allowed(context, query.owner(), query.scopeId(), repository.indexReadiness(now, query.kind(), binding));
            var found = new ArrayList<MemoryHitDTO>(); var seen = new HashSet<String>();
            for (var candidate : candidates) {
                Object rawId = candidate.getMetadata().get("memoryId");
                if (!(rawId instanceof String id) || !seen.add(id) || candidate.getScore() == null || !Double.isFinite(candidate.getScore()) || candidate.getScore() < 0 || candidate.getScore() > 1) throw unavailable();
                new GetMemoryQuery(query.owner(), query.scopeId(), id);
                var current = repository.current(id, false); if (current.isEmpty()) continue;
                var row = current.orElseThrow(); var entry = row.entry();
                if (!live(entry, now) || entry.state() != MemoryStateEnum.ACTIVE || entry.effectiveAt().isAfter(now) || query.kind() != null && entry.kind() != query.kind()) continue;
                var policy = policy(entry.retentionPolicyRef());
                if (!policy.semanticQueryAllowed() || !policy.embeddingAllowed().contains(entry.sensitivity())) continue;
                var expectedId = MemoryIndexPublication.vectorDocumentId(context.actor().tenantId(), context.actor().issuerFingerprint(), entry.owner(), entry.scopeId(), id, entry.revision(), binding.modelRef());
                if (!expectedId.toString().equals(candidate.getId()) || !binding.modelRef().equals(row.indexModelRef())
                        || !PgVectorMemoryIndex.metadata(context.actor().tenantId(), context.actor().issuerFingerprint(), row, binding).equals(candidate.getMetadata())) throw unavailable();
                found.add(new MemoryHitDTO(entry, candidate.getScore()));
            }
            return new MemoryRetrievalPageDTO(query.owner(), query.scopeId(), found, null);
        });
    }

    private List<PgVectorMemoryIndex.Allowed> allowed(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, List<JdbcMemoryRepository.IndexReadiness> rows) {
        if (rows.size() > 10_000) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Memory retrieval scope exceeds its fixed limit");
        var allowed = new LinkedHashSet<PgVectorMemoryIndex.Allowed>();
        for (var row : rows) {
            var policy = policy(row.retentionPolicyRef()); if (!policy.embeddingAllowed().contains(row.sensitivity())) continue;
            if (!policy.semanticQueryAllowed()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory semantic egress is outside the fixed policy");
            var expectedId = MemoryIndexPublication.vectorDocumentId(context.actor().tenantId(), context.actor().issuerFingerprint(), owner, scope, row.memoryId(), row.revision(), binding.modelRef());
            if (!binding.modelRef().equals(row.modelRef()) || !"ACTIVE".equals(row.state()) || !row.physicalPresent() || !expectedId.equals(row.documentId())
                    || !row.contentSha256().equals(row.indexedHash()) || !Integer.valueOf(binding.dimensions()).equals(row.dimensions()) || !binding.vectorTable().equals(row.vectorTable())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory semantic index is pending or unavailable");
            }
            allowed.add(new PgVectorMemoryIndex.Allowed(policy.reference(), row.sensitivity()));
        }
        return allowed.stream().sorted(java.util.Comparator.comparing(PgVectorMemoryIndex.Allowed::retentionPolicyRef).thenComparing(value -> value.sensitivity().name())).toList();
    }

    private StoredMemory retire(JdbcMemoryRepository repository, MemoryOperationContext context, StoredMemory previous, MemoryStateEnum state, String reason, Instant now) {
        var old = previous.entry();
        var entry = new MemoryEntryDTO(old.owner(), old.scopeId(), old.memoryId(), old.kind(), next(old.revision()), old.revision(), state, null, old.contentSha256(), null,
                old.confidence(), old.confirmed(), old.sensitivity(), old.retentionPolicyRef(), old.effectiveAt(), old.expiresAt(), old.createdAt(), now);
        var retired = new StoredMemory(entry, previous.indexModelRef(), previous.sourceEvidenceSha256(), context.actor().identityFingerprint(), reason, now);
        repository.replace(retired, old.revision(), true);
        publication.publish(event(context, entry, MemoryIndexPublication.Action.DELETE_MEMORY, null, null, now)); return retired;
    }

    private void build(JdbcMemoryRepository repository, MemoryOperationContext context, StoredMemory stored, MemoryRetentionPolicy policy, Instant now) {
        var entry = stored.entry(); if (entry.state() != MemoryStateEnum.ACTIVE || !policy.embeddingAllowed().contains(entry.sensitivity())) return;
        if (index == null) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory indexing is not configured");
        index.validateInput(codec.content(entry.content()));
        var id = MemoryIndexPublication.vectorDocumentId(context.actor().tenantId(), context.actor().issuerFingerprint(), entry.owner(), entry.scopeId(), entry.memoryId(), entry.revision(), binding.modelRef());
        repository.insertManifest(stored, binding, id, now);
        publication.publish(event(context, entry, MemoryIndexPublication.Action.BUILD, binding.modelRef(), id, now));
    }
    private MemoryIndexPublication.Event event(MemoryOperationContext context, MemoryEntryDTO entry, MemoryIndexPublication.Action action, String modelRef, java.util.UUID id, Instant now) {
        return new MemoryIndexPublication.Event(action, context.actor().tenantId(), context.actor().issuerFingerprint(), entry.owner(), entry.scopeId(), entry.memoryId(), entry.revision(), entry.contentSha256(), modelRef, id, now);
    }

    private MemoryOperationContext authorize(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, MemoryAuthorization.Operation operation, String id, long expected, MemorySourceDTO provenance) {
        Objects.requireNonNull(context, "context"); noTransaction(); if (!initialized.get()) throw unavailable();
        var actor = context.actor();
        if (owner.type() == MemoryOwnerTypeEnum.USER && (actor.subjectType() != TrustedAgentActor.SubjectType.USER || !owner.id().equals(actor.subjectId()))
                || owner.type() == MemoryOwnerTypeEnum.APPLICATION && (actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || !owner.id().equals(actor.subjectId()))) throw denied();
        Instant maximum = clock.instant().plusSeconds(30); Instant deadline = context.deadlineAt().isBefore(maximum) ? context.deadlineAt() : maximum;
        var bounded = new MemoryOperationContext(actor, deadline, context.runAuthorization()); checkDeadline(bounded, clock.instant());
        MemoryAuthorization.Decision decision;
        try { decision = authorization.authorize(new MemoryAuthorization.Request(bounded, owner, scope, operation, id, expected, provenance == null ? null : provenance.sourceRunId(), provenance == null ? null : provenance.sourceEventId())); }
        catch (RuntimeException unavailable) { throw unavailable(); }
        if (decision == MemoryAuthorization.Decision.DENY) throw denied(); if (decision != MemoryAuthorization.Decision.ALLOW) throw unavailable();
        checkDeadline(bounded, clock.instant()); return bounded;
    }

    private <T> T transaction(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, boolean write, BiFunction<JdbcMemoryRepository, Instant, T> action) {
        noTransaction(); checkDeadline(context, clock.instant());
        if (!binding.equals(publication.binding()) || !publication.usesDataSource(source)) throw unavailable();
        int seconds = Math.max(1, Math.toIntExact((Duration.between(clock.instant(), context.deadlineAt()).toNanos() + 999_999_999) / 1_000_000_000));
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(seconds);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(seconds);
        try {
            T result = transaction.execute(status -> {
                var repository = new JdbcMemoryRepository(jdbc, context.actor().tenantId(), context.actor().issuerFingerprint(), owner, scope, codec);
                if (write) repository.lockScope();
                Instant now = repository.databaseNow(); checkDeadline(context, now);
                T value = action.apply(repository, now); checkDeadline(context, repository.databaseNow());
                if (!binding.equals(publication.binding())) throw unavailable(); return value;
            });
            if (result == null) throw unavailable(); return result;
        } catch (AgentLedgerException rejected) { throw rejected; }
        catch (DataAccessException failed) { throw unavailable(); }
    }

    MemoryRetentionPolicy policy(String reference) { var policy = policies.get(reference); if (policy == null) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "The fixed Memory retention policy is unavailable"); return policy; }
    private static MemoryMutationResultDTO result(String commandId, MemoryEntryDTO entry, MemoryMutationOutcomeEnum outcome, Instant now) { return new MemoryMutationResultDTO(commandId, entry.owner(), entry.scopeId(), entry.memoryId(), entry.revision(), outcome, entry.state(), now); }
    private static boolean live(MemoryEntryDTO entry, Instant now) { return (entry.state() == MemoryStateEnum.ACTIVE || entry.state() == MemoryStateEnum.CANDIDATE) && entry.expiresAt().isAfter(now); }
    private static long next(long revision) { try { return Math.addExact(revision, 1); } catch (ArithmeticException exhausted) { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory revision capacity is exhausted"); } }
    private static MemoryEntryDTO projection(MemoryEntryDTO entry, Instant now) {
        if (entry.state() == MemoryStateEnum.FORGOTTEN || entry.state() == MemoryStateEnum.EXPIRED || entry.expiresAt().isAfter(now)) return entry;
        return new MemoryEntryDTO(entry.owner(), entry.scopeId(), entry.memoryId(), entry.kind(), entry.revision(), entry.supersedesRevision(), MemoryStateEnum.EXPIRED, null, entry.contentSha256(), null,
                entry.confidence(), entry.confirmed(), entry.sensitivity(), entry.retentionPolicyRef(), entry.effectiveAt(), entry.expiresAt(), entry.createdAt(), entry.updatedAt());
    }
    private static void noTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory operations require their own transaction boundary"); }
    private static void checkDeadline(MemoryOperationContext context, Instant now) { if (!now.isBefore(context.deadlineAt()) || Thread.currentThread().isInterrupted()) throw unavailable(); }
    private static AgentLedgerException invalidLifetime() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Memory lifetime is outside its fixed policy"); }
    private static AgentLedgerException revisionConflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Memory revision or lifecycle changed"); }
    private static AgentLedgerException notFound() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory entry was not found in the current scope"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current Memory access is denied"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory authority or storage is unavailable"); }
    private record SearchPreparation(Instant now, List<PgVectorMemoryIndex.Allowed> allowed) { }
}
