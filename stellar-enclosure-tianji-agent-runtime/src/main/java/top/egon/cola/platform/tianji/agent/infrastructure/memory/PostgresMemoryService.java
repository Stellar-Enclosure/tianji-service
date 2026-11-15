package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.api.capability.CallPermitVO;
import top.egon.cola.component.agentflow.api.memory.MemoryDeleteCommand;
import top.egon.cola.component.agentflow.api.memory.MemoryFactTypeEnum;
import top.egon.cola.component.agentflow.api.memory.MemoryQuery;
import top.egon.cola.component.agentflow.api.memory.MemoryRetrievalResult;
import top.egon.cola.component.agentflow.api.memory.MemoryScopeVO;
import top.egon.cola.component.agentflow.api.memory.MemoryStore;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteCommand;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteResult;
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
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidateQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidatePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryHitDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeHitDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeRelationEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
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
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static top.egon.cola.platform.tianji.agent.infrastructure.memory.JdbcMemoryRepository.StoredMemory;

/** A Domain Service for current authorization and short fact/revision/receipt/Outbox transactions; it runs no model in a transaction. */
@Slf4j
public final class PostgresMemoryService {
    private final DataSource source;
    private final MemoryAuthorization authorization;
    private final MemoryIndexPublication publication;
    private final MemoryIndexPublication.Binding binding;
    private final Map<String, MemoryRetentionPolicy> policies;
    private final Clock clock;
    private final PgVectorMemoryIndex index;
    private final LegacyMemoryStoreAdapter hostAdapter;
    private final MemoryJsonCodec codec = new MemoryJsonCodec();
    private final AtomicBoolean initialized = new AtomicBoolean();

    PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                          List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock) {
        this(ledger, source, authorization, policies, publication, clock, null);
    }

    PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                          List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock, PgVectorMemoryIndex index) {
        this(ledger, source, authorization, policies, publication, clock, index, null, false);
    }

    public PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                                 List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock,
                                 PgVectorMemoryIndex index, LegacyMemoryStoreAdapter adapter) {
        this(ledger, source, authorization, policies, publication, clock, index, Objects.requireNonNull(adapter, "scope authority"), true);
    }

    private PostgresMemoryService(AgentLedgerService ledger, DataSource source, MemoryAuthorization authorization,
                                  List<MemoryRetentionPolicy> policies, MemoryIndexPublication publication, Clock clock,
                                  PgVectorMemoryIndex index, LegacyMemoryStoreAdapter adapter, boolean fencedHost) {
        this.source = Objects.requireNonNull(source, "source"); this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.publication = Objects.requireNonNull(publication, "publication"); this.clock = Objects.requireNonNull(clock, "clock");
        if (!Objects.requireNonNull(ledger, "ledger").usesDataSource(source) || !publication.usesDataSource(source)
                || policies == null || policies.isEmpty() || policies.size() > 32) throw new IllegalArgumentException("Memory storage/publication binding is invalid");
        binding = Objects.requireNonNull(publication.binding(), "index binding");
        this.index = index;
        if (fencedHost && !adapter.usesDataSource(source)) throw new IllegalArgumentException("Memory scope authority requires the selected fact storage");
        this.hostAdapter = adapter;
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
        var verifier = new MemoryStorageVerifier(source, baseVectorDimensions, expectedVectorVersion, binding);
        if (hostAdapter == null) verifier.verify();
        else verifier.verifyGuardedHost();
        if (!binding.equals(publication.binding()) || !publication.usesDataSource(source)) throw unavailable();
        if (index != null) index.verifiedStorage();
        initialized.set(true);
    }
    public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    public boolean usesHostAuthority() { return hostAdapter != null; }
    public boolean usesHostAuthority(LegacyMemoryStoreAdapter selected) { return hostAdapter != null && hostAdapter == selected; }
    LegacyMemoryStoreAdapter hostAdapter() { return hostAdapter; }
    public List<String> policyReferences() { return policies.keySet().stream().sorted().toList(); }
    public MemoryIndexPublication.Binding indexBinding() { return binding; }

    /** The durable extraction cursor freezes the same canonical command bytes used by fact receipts, including their order. */
    public static String preferenceCommandListSha256(List<UpsertMemoryCommand> commands) {
        if (commands == null || commands.size() > 32) throw new IllegalArgumentException("Memory preference command list is invalid");
        var codec = new MemoryJsonCodec(); var identifiers = new java.util.HashSet<String>();
        var data = new StringBuilder("TIANJI_MEMORY_PREFERENCE_COMMAND_LIST_V1:").append(commands.size()).append(':');
        for (var command : commands) {
            if (command == null || !identifiers.add(command.commandId())) throw new IllegalArgumentException("Memory preference command list is invalid");
            data.append(codec.commandSha256(command));
        }
        return MemoryJsonCodec.sha256(data.toString());
    }

    public MemoryMutationResultDTO upsert(MemoryOperationContext context, UpsertMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        if (context != null && context.preferenceWrite() != null) {
            if (!command.equals(context.preferenceWrite().command())) throw denied();
            if (!context.preferenceWrite().usesDataSource(source)) throw unavailable();
        }
        var authorized = authorize(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.UPSERT, command.memoryId(), 0, command.source());
        String requestHash = codec.commandSha256(command); String contentHash = codec.contentSha256(command.content());
        return transaction(authorized, command.owner(), command.scopeId(), true, true, null, (repository, now) -> {
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
        return retrieve(context, query, null);
    }

    public MemoryCandidatePageDTO candidates(MemoryOperationContext context, MemoryCandidateQuery query) {
        Objects.requireNonNull(query, "query");
        var authorized = authorize(context, query.owner(), query.scopeId(), MemoryAuthorization.Operation.CANDIDATES, null, 0, null);
        return transaction(authorized, query.owner(), query.scopeId(), false, (repository, now) -> {
            var found = repository.candidates(now, query.kind(), query.afterMemoryId(), query.limit() + 1); boolean more = found.size() > query.limit();
            var items = found.stream().limit(query.limit()).map(StoredMemory::entry).toList();
            return new MemoryCandidatePageDTO(query.owner(), query.scopeId(), items, more ? items.getLast().memoryId() : null);
        });
    }

    /** Human comparison only. Candidate egress is separately approved; similarity never mutates or confirms a fact. */
    public MemoryMergePageDTO mergeSuggestions(MemoryOperationContext context, MemoryMergeQuery query) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(query, "query"); noTransaction();
        if (context.runAuthorization() != null || context.preferenceWrite() != null) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Candidate comparison requires a current request");
        var original = mergeCandidate(get(context, new GetMemoryQuery(query.owner(), query.scopeId(), query.memoryId())), query);
        String text = null;
        if (query.semantic()) {
            var policy = policy(original.retentionPolicyRef());
            if (!policy.semanticQueryAllowed() || !policy.embeddingAllowed().contains(original.sensitivity())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Candidate semantic egress is outside the fixed policy");
            text = codec.content(original.content());
            if (text.length() > 2000 || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8000) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Candidate exceeds the semantic comparison input limit");
        }
        var page = retrieve(context, new MemoryRetrievalQuery(query.owner(), query.scopeId(), original.kind(), text, query.afterMemoryId(), query.semantic() ? query.limit() : 50));
        var related = new ArrayList<MemoryMergeHitDTO>();
        for (var hit : page.items()) {
            if (query.semantic() && hit.score() == null) throw unavailable();
            if (query.semantic() && hit.score() < query.minimumScore()) continue;
            var relation = mergeRelation(original.content(), hit.entry().content(), query.semantic());
            if (relation == null) continue;
            related.add(new MemoryMergeHitDTO(hit, relation));
            if (related.size() > query.limit()) break;
        }
        boolean more = related.size() > query.limit();
        var selected = related.stream().limit(query.limit()).toList();
        String cursor = query.semantic() ? null : more ? selected.getLast().hit().entry().memoryId() : page.nextAfterMemoryId();
        var current = mergeCandidate(get(context, new GetMemoryQuery(query.owner(), query.scopeId(), query.memoryId())), query);
        if (!original.equals(current)) throw revisionConflict();
        Instant now = clock.instant();
        selected = selected.stream().filter(item -> !item.hit().entry().effectiveAt().isAfter(now)
                && (item.hit().entry().expiresAt() == null || item.hit().entry().expiresAt().isAfter(now))).toList();
        return new MemoryMergePageDTO(current, query.semantic(), selected, cursor);
    }

    MemoryRetrievalResult hostRetrieve(MemoryQuery query, MemoryScopeVO scope, CallPermitVO permit,
                                       LegacyMemoryStoreAdapter.HostAuthority host, LegacyMemoryFactConverter converter) {
        var identity = LegacyMemoryStoreAdapter.identity(scope);
        var fixed = query.snapshot();
        String purpose = MemoryStore.MODEL_CAPABILITY_REF.equals(permit.capabilityRef()) ? MemoryStore.MODEL_READ_PURPOSE : MemoryStore.READ_PURPOSE;
        MemoryOperationContext context = hostContext(scope, permit, host, identity, purpose);
        var authorized = authorize(context, identity.owner(), identity.scopeId(), MemoryAuthorization.Operation.RETRIEVE, null, 0, null);
        var policy = host.authorizeRead(scope, purpose);
        if (policy == null) throw denied();
        var types = new HashSet<MemoryKindEnum>();
        for (MemoryFactTypeEnum type : policy.types()) {
            if (fixed.getTypes().isEmpty() || fixed.getTypes().contains(type)) types.add(MemoryKindEnum.valueOf(type.code()));
        }
        var allowed = new ArrayList<PgVectorMemoryIndex.Allowed>();
        for (String ref : policies.keySet().stream().sorted().toList()) {
            if (!policy.retentionPolicyRefs().contains(ref)) continue;
            for (var sensitivity : MemorySensitivityEnum.values()) {
                if (policy.sensitivities().contains(sensitivity.name())) allowed.add(new PgVectorMemoryIndex.Allowed(ref, sensitivity));
            }
        }
        return transaction(authorized, identity.owner(), identity.scopeId(), false, false, permit.authorityEpoch(), (repository, now) -> {
            requireHostPermit(scope, permit, purpose);
            var matches = new ArrayList<MemoryRetrievalResult.HitVO>();
            String cursor = null;
            for (int page = 0; page < 200 && matches.size() < fixed.getLimit(); page++) {
                var rows = repository.listAuthorized(now, types, cursor, 50, allowed);
                for (var item : rows) {
                    var entry = item.entry();
                    var source = repository.sourceVersion(entry.source(), item.sourceActorSha256()).orElse(null);
                    if (source == null && (entry.source().sourceRunId() != null || repository.nativeUpsert(item))) continue;
                    var fact = converter.toTarget(LegacyMemoryFactBO.from(repository, item, source));
                    var metadata = new MemoryStore.WriteMetadataVO(entry.confirmed(), entry.effectiveAt(), entry.expiresAt(),
                            entry.sensitivity().name(), entry.retentionPolicyRef());
                    if (!policy.allows(scope, purpose, fact, metadata) || !hostTextMatches(entry.content(), fixed.getText())) continue;
                    matches.add(new MemoryRetrievalResult.HitVO(fact.factId(), fact.type(), fact.revision(), fact.content(), fact.provenance(), null, now));
                    if (matches.size() == fixed.getLimit()) break;
                }
                if (rows.size() < 50) break;
                cursor = rows.getLast().entry().memoryId();
            }
            if (!policy.equals(host.authorizeRead(scope, purpose))) throw denied();
            requireHostPermit(scope, permit, purpose);
            return new MemoryRetrievalResult(List.copyOf(matches));
        });
    }

    MemoryWriteResult hostUpsert(MemoryWriteCommand command, MemoryScopeVO scope, CallPermitVO permit,
                                 LegacyMemoryStoreAdapter.HostAuthority host) {
        var identity = LegacyMemoryStoreAdapter.identity(scope);
        var fixed = command.snapshot();
        MemoryOperationContext context = hostContext(scope, permit, host, identity, MemoryStore.UPSERT_PURPOSE);
        var approved = host.authorizeWrite(scope, fixed.snapshot());
        var proof = host.verifyWriteSource(scope, fixed.snapshot());
        if (approved == null || proof == null) throw denied();
        if (fixed.getProvenance().size() != 1
                || !fixed.getProvenance().getFirst().sourceRef().equals(LegacyMemoryFactConverter.sourceRef(proof.source()))
                || !fixed.getProvenance().getFirst().contentSha256().equals(proof.source().evidenceSha256())) throw denied();
        var content = hostContent(fixed.getContent());
        String hash = codec.contentSha256(content);
        var kind = MemoryKindEnum.valueOf(fixed.getType().code());
        var sensitivity = MemorySensitivityEnum.valueOf(approved.sensitivity());
        var operation = fixed.getExpectedRevision() == 0 ? MemoryAuthorization.Operation.UPSERT : MemoryAuthorization.Operation.CORRECT;
        var authorized = authorize(context, identity.owner(), identity.scopeId(), operation, fixed.getFactId(), fixed.getExpectedRevision(), proof.source());
        String requestHash = MemoryStore.NativeCodec.upsertFingerprint(scope.hash(), fixed, approved);
        return transaction(authorized, identity.owner(), identity.scopeId(), true, false, permit.authorityEpoch(), (repository, now) -> {
            requireHostPermit(scope, permit, MemoryStore.UPSERT_PURPOSE);
            if (!approved.equals(host.authorizeWrite(scope, fixed.snapshot()))) throw denied();
            var source = repository.sourceVersion(proof.source(), authorized.actor().identityFingerprint()).orElseThrow(PostgresMemoryService::denied);
            var supplied = fixed.getProvenance().getFirst();
            if (source.sequence() != supplied.sourceRevision() || !source.occurredAt().equals(supplied.observedAt())) throw denied();
            var receipt = repository.nativeReceipt("UPSERT", fixed.getCommandId(), requestHash);
            if (receipt.isPresent()) {
                if (!approved.equals(host.authorizeWrite(scope, fixed.snapshot()))) throw denied();
                requireHostPermit(scope, permit, MemoryStore.UPSERT_PURPOSE);
                return receipt.orElseThrow();
            }
            var policy = policy(approved.retentionPolicyRef());
            requireHostWindow(approved, policy, now);
            var previous = repository.current(fixed.getFactId(), true);
            if (previous.isEmpty() != (fixed.getExpectedRevision() == 0)) throw revisionConflict();
            var old = previous.map(StoredMemory::entry).orElse(null);
            if (old != null && (old.revision() != fixed.getExpectedRevision() || old.kind() != kind || !live(old, now))) throw revisionConflict();
            var duplicate = repository.duplicate(kind, hash);
            if (duplicate.isPresent() && !duplicate.orElseThrow().entry().memoryId().equals(fixed.getFactId())) {
                var other = duplicate.orElseThrow();
                if (other.entry().expiresAt().isAfter(now)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory content already belongs to another entry");
                retire(repository, authorized, other, MemoryStateEnum.EXPIRED, "RETENTION_EXPIRED", now);
            }
            if (old == null && repository.activeCount(now) >= policy.maximumActiveEntries()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory owner/scope quota is exhausted");
            }
            var entry = new MemoryEntryDTO(identity.owner(), identity.scopeId(), fixed.getFactId(),
                    kind, old == null ? 1 : next(old.revision()), old == null ? null : old.revision(),
                    approved.confirmed() ? MemoryStateEnum.ACTIVE : MemoryStateEnum.CANDIDATE, content, hash, proof.source(), proof.confidence(),
                    approved.confirmed(), sensitivity, policy.reference(), approved.effectiveAt(), approved.expiresAt(), old == null ? now : old.createdAt(), now);
            var stored = new StoredMemory(entry, binding.modelRef(), proof.source().evidenceSha256(),
                    authorized.actor().identityFingerprint(), "NATIVE_UPSERT", null);
            if (old == null) repository.insert(stored);
            else {
                repository.replace(stored, old.revision(), false);
                publication.publish(event(authorized, old, MemoryIndexPublication.Action.DELETE_REVISION, null, null, old.updatedAt()));
            }
            var result = repository.recordNativeReceipt("UPSERT", fixed.getCommandId(), requestHash, stored);
            if (!approved.equals(host.authorizeWrite(scope, fixed.snapshot()))) throw denied();
            requireHostPermit(scope, permit, MemoryStore.UPSERT_PURPOSE);
            return result;
        });
    }

    MemoryWriteResult hostDelete(MemoryDeleteCommand command, MemoryScopeVO scope, CallPermitVO permit, LegacyMemoryStoreAdapter.HostAuthority host) {
        var identity = LegacyMemoryStoreAdapter.identity(scope);
        var fixed = command.snapshot();
        var context = hostContext(scope, permit, host, identity, MemoryStore.DELETE_PURPOSE);
        host.authorizeDelete(scope, fixed.snapshot());
        var authorized = authorize(context, identity.owner(), identity.scopeId(), MemoryAuthorization.Operation.FORGET, fixed.getFactId(), fixed.getExpectedRevision(), null);
        String requestHash = MemoryStore.NativeCodec.deleteFingerprint(scope.hash(), fixed);
        return transaction(authorized, identity.owner(), identity.scopeId(), true, false, permit.authorityEpoch(), (repository, now) -> {
            requireHostPermit(scope, permit, MemoryStore.DELETE_PURPOSE);
            host.authorizeDelete(scope, fixed.snapshot());
            var receipt = repository.nativeReceipt("DELETE", fixed.getCommandId(), requestHash);
            if (receipt.isPresent()) {
                host.authorizeDelete(scope, fixed.snapshot());
                requireHostPermit(scope, permit, MemoryStore.DELETE_PURPOSE);
                return receipt.orElseThrow();
            }
            var previous = repository.current(fixed.getFactId(), true).orElseThrow(PostgresMemoryService::notFound);
            if (previous.entry().revision() != fixed.getExpectedRevision() || previous.entry().state() == MemoryStateEnum.FORGOTTEN) throw revisionConflict();
            var retired = retire(repository, authorized, previous, MemoryStateEnum.FORGOTTEN, "NATIVE_DELETE", now);
            var result = repository.recordNativeReceipt("DELETE", fixed.getCommandId(), requestHash, retired);
            host.authorizeDelete(scope, fixed.snapshot());
            requireHostPermit(scope, permit, MemoryStore.DELETE_PURPOSE);
            return result;
        });
    }

    private MemoryOperationContext hostContext(MemoryScopeVO scope, CallPermitVO permit, LegacyMemoryStoreAdapter.HostAuthority host,
                                               LegacyMemoryStoreAdapter.HostIdentityVO identity, String purpose) {
        if (hostAdapter == null) throw unavailable();
        requireHostPermit(scope, permit, purpose);
        var context = Objects.requireNonNull(host.context(scope), "Memory host context");
        if (context.preferenceWrite() != null || context.actor().tenantId() != identity.tenantId()
                || !identity.issuer().equals(context.actor().issuerFingerprint())) throw denied();
        Instant deadline = context.deadlineAt().isBefore(permit.notAfter()) ? context.deadlineAt() : permit.notAfter();
        return new MemoryOperationContext(context.actor(), deadline, context.runAuthorization());
    }

    private void requireHostPermit(MemoryScopeVO scope, CallPermitVO permit, String purpose) {
        String capability = MemoryStore.MODEL_READ_PURPOSE.equals(purpose) ? MemoryStore.MODEL_CAPABILITY_REF : MemoryStore.CAPABILITY_REF;
        MemoryStore.Access.requireCurrent(permit, scope.hash(), capability, purpose, clock);
    }

    private static void requireHostWindow(MemoryStore.WriteMetadataVO metadata, MemoryRetentionPolicy policy, Instant now) {
        Instant effective = metadata.effectiveAt();
        Instant expires = metadata.expiresAt();
        if (effective == null || expires == null || !expires.isAfter(effective) || !expires.isAfter(now)
                || expires.isAfter(now.plus(policy.maximumLifetime()))
                || Duration.between(effective, expires).compareTo(policy.maximumLifetime()) > 0) throw invalidLifetime();
    }

    private static boolean hostTextMatches(MemoryContentDTO content, String query) {
        return query == null || query.isBlank() || content.text() != null && content.text().contains(query)
                || content.facts().values().stream().anyMatch(value -> value.contains(query));
    }

    private static MemoryContentDTO hostContent(Map<String, Object> content) {
        if (!content.keySet().equals(Set.of("text", "facts")) || !(content.get("facts") instanceof Map<?, ?> fields)
                || content.get("text") != null && !(content.get("text") instanceof String)) throw invalidHostContent();
        var facts = new HashMap<String, String>();
        for (var part : fields.entrySet()) {
            if (!(part.getKey() instanceof String key) || !(part.getValue() instanceof String value)) throw invalidHostContent();
            facts.put(key, value);
        }
        var result = new MemoryContentDTO((String) content.get("text"), facts);
        if (!Objects.equals(result.text(), content.get("text")) || !result.facts().equals(facts)) throw invalidHostContent();
        return result;
    }

    private static AgentLedgerException invalidHostContent() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "Memory content cannot be represented by the current host");
    }

    private MemoryEntryDTO mergeCandidate(MemoryEntryDTO candidate, MemoryMergeQuery query) {
        if (candidate.state() != MemoryStateEnum.CANDIDATE || candidate.confirmed() || candidate.revision() != query.expectedRevision()
                || candidate.expiresAt() != null && !candidate.expiresAt().isAfter(clock.instant())) throw revisionConflict();
        return candidate;
    }

    private static MemoryMergeRelationEnum mergeRelation(MemoryContentDTO candidate, MemoryContentDTO active, boolean semantic) {
        boolean shared = candidate.text() != null && candidate.text().equals(active.text()); boolean different = false;
        for (var fact : candidate.facts().entrySet()) {
            if (!active.facts().containsKey(fact.getKey())) continue;
            if (fact.getValue().equals(active.facts().get(fact.getKey()))) shared = true; else different = true;
        }
        if (different) return MemoryMergeRelationEnum.DIFFERING_FIELDS;
        if (shared) return MemoryMergeRelationEnum.SHARED_VALUES;
        return semantic ? MemoryMergeRelationEnum.RELATED_CONTENT : null;
    }

    /** Model-egress policy is forced before SQL/native-vector topK, so excluded facts cannot crowd approved evidence. */
    public MemoryRetrievalPageDTO retrieveContext(MemoryOperationContext context, MemoryRetrievalQuery query, Set<String> retentionPolicyRefs) {
        var selected = Set.copyOf(retentionPolicyRefs); if (selected.isEmpty() || selected.size() > 32) throw new IllegalArgumentException("Memory context policy selection is invalid");
        selected.forEach(this::policy); return retrieve(context, query, selected);
    }

    private MemoryRetrievalPageDTO retrieve(MemoryOperationContext context, MemoryRetrievalQuery query, Set<String> contextPolicies) {
        Objects.requireNonNull(query, "query");
        var authorized = authorize(context, query.owner(), query.scopeId(), query.text() == null ? MemoryAuthorization.Operation.RETRIEVE : MemoryAuthorization.Operation.SEARCH, null, 0, null);
        if (query.text() != null) return semantic(authorized, query, contextPolicies);
        return transaction(authorized, query.owner(), query.scopeId(), false, (repository, now) -> {
            var found = contextPolicies == null ? repository.list(now, query.kind(), query.afterMemoryId(), query.limit() + 1)
                    : repository.list(now, query.kind(), query.afterMemoryId(), query.limit() + 1, contextAllowed(contextPolicies));
            boolean more = found.size() > query.limit();
            var items = found.stream().limit(query.limit()).map(row -> new MemoryHitDTO(row.entry(), null)).toList();
            return new MemoryRetrievalPageDTO(query.owner(), query.scopeId(), items, more ? items.getLast().entry().memoryId() : null);
        });
    }

    /** Revalidates a bounded context batch under one current scope-read decision, without embedding or replaying old bodies. */
    public List<MemoryEntryDTO> contextEntries(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, List<String> memoryIds) {
        var ids = List.copyOf(memoryIds); if (ids.size() > 32 || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Memory context batch is invalid");
        for (var id : ids) new GetMemoryQuery(owner, scope, id);
        var authorized = authorize(context, owner, scope, MemoryAuthorization.Operation.RETRIEVE, null, 0, null);
        if (ids.isEmpty() && hostAdapter == null) return List.of();
        return transaction(authorized, owner, scope, false, (repository, now) -> {
            var entries = new ArrayList<MemoryEntryDTO>();
            for (var id : ids) {
                var current = repository.current(id, false); if (current.isEmpty()) continue; var entry = current.orElseThrow().entry();
                if (entry.state() == MemoryStateEnum.ACTIVE && entry.confirmed() && live(entry, now) && !entry.effectiveAt().isAfter(now)) entries.add(entry);
            }
            return List.copyOf(entries);
        });
    }

    private MemoryRetrievalPageDTO semantic(MemoryOperationContext context, MemoryRetrievalQuery query, Set<String> contextPolicies) {
        if (index == null) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory semantic index is not configured");
        var prepared = transaction(context, query.owner(), query.scopeId(), false, (repository, now) -> new SearchPreparationVO(now, allowed(context, query.owner(), query.scopeId(), repository.indexReadiness(now, query.kind(), binding), contextPolicies), repository.authorityEpoch()));
        var candidates = hostAdapter != null && prepared.allowed().isEmpty() ? List.<org.springframework.ai.document.Document>of()
                : index.search(context.actor().tenantId(), context.actor().issuerFingerprint(), query.owner(), query.scopeId(), query.kind(),
                        query.text(), query.limit(), prepared.allowed(), prepared.now(), context.deadlineAt());
        var currentContext = authorize(context, query.owner(), query.scopeId(), MemoryAuthorization.Operation.SEARCH, null, 0, null);
        return transaction(currentContext, query.owner(), query.scopeId(), false, false,
                hostAdapter == null ? null : prepared.epoch(), (repository, now) -> {
            allowed(currentContext, query.owner(), query.scopeId(), repository.indexReadiness(now, query.kind(), binding), contextPolicies);
            var found = new ArrayList<MemoryHitDTO>(); var seen = new HashSet<String>();
            for (var candidate : candidates) {
                Object rawId = candidate.getMetadata().get("memoryId");
                if (!(rawId instanceof String id) || !seen.add(id) || candidate.getScore() == null || !Double.isFinite(candidate.getScore()) || candidate.getScore() < 0 || candidate.getScore() > 1) throw unavailable();
                new GetMemoryQuery(query.owner(), query.scopeId(), id);
                var current = repository.current(id, false); if (current.isEmpty()) continue;
                var row = current.orElseThrow(); var entry = row.entry();
                if (!live(entry, now) || entry.state() != MemoryStateEnum.ACTIVE || entry.effectiveAt().isAfter(now) || query.kind() != null && entry.kind() != query.kind()) continue;
                if (contextPolicies != null && !contextPolicies.contains(entry.retentionPolicyRef())) continue;
                var policy = policy(entry.retentionPolicyRef()); if (contextPolicies != null && !policy.modelContextAllowed().contains(entry.sensitivity())) continue;
                if (!policy.semanticQueryAllowed() || !policy.embeddingAllowed().contains(entry.sensitivity())) continue;
                var expectedId = MemoryIndexPublication.vectorDocumentId(currentContext.actor().tenantId(), currentContext.actor().issuerFingerprint(), entry.owner(), entry.scopeId(), id, entry.revision(), binding.modelRef());
                if (!expectedId.toString().equals(candidate.getId()) || !binding.modelRef().equals(row.indexModelRef())
                        || !PgVectorMemoryIndex.metadata(currentContext.actor().tenantId(), currentContext.actor().issuerFingerprint(), row, binding).equals(vectorFacts(candidate.getMetadata()))) throw unavailable();
                found.add(new MemoryHitDTO(entry, candidate.getScore()));
            }
            return new MemoryRetrievalPageDTO(query.owner(), query.scopeId(), found, null);
        });
    }

    private static Map<String, Object> vectorFacts(Map<String, Object> metadata) {
        String distanceKey = DocumentMetadata.DISTANCE.value();
        if (!metadata.containsKey(distanceKey)) return metadata;
        var stored = new HashMap<>(metadata);
        if (!(stored.remove(distanceKey) instanceof Number distance) || !Double.isFinite(distance.doubleValue())) throw unavailable();
        return stored;
    }

    private List<PgVectorMemoryIndex.Allowed> contextAllowed(Set<String> references) {
        var selected = new ArrayList<PgVectorMemoryIndex.Allowed>();
        for (var reference : references.stream().sorted().toList()) for (var sensitivity : policy(reference).modelContextAllowed().stream().sorted().toList()) selected.add(new PgVectorMemoryIndex.Allowed(reference, sensitivity));
        return List.copyOf(selected);
    }

    private List<PgVectorMemoryIndex.Allowed> allowed(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, List<JdbcMemoryRepository.IndexReadiness> rows, Set<String> contextPolicies) {
        if (rows.size() > 10_000) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Memory retrieval scope exceeds its fixed limit");
        var allowed = new LinkedHashSet<PgVectorMemoryIndex.Allowed>();
        for (var row : rows) {
            if (contextPolicies != null && !contextPolicies.contains(row.retentionPolicyRef())) continue;
            var policy = policy(row.retentionPolicyRef()); if (contextPolicies != null && !policy.modelContextAllowed().contains(row.sensitivity())) continue;
            if (!policy.embeddingAllowed().contains(row.sensitivity())) continue;
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
        return retire(repository, previous, state, reason, now, context.actor().identityFingerprint());
    }

    boolean readyForRetention(DataSource candidate, MemoryIndexPublication publisher) { return initialized.get() && source == candidate && publication == publisher && binding.equals(publisher.binding()); }

    StoredMemory expireForRetention(JdbcMemoryRepository repository, StoredMemory previous, String technicalActorSha256, Instant now) {
        if (!initialized.get() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                || technicalActorSha256 == null || !technicalActorSha256.matches("[0-9a-f]{64}")) throw unavailable();
        var entry = previous.entry(); policy(entry.retentionPolicyRef());
        if (entry.state() != MemoryStateEnum.ACTIVE && entry.state() != MemoryStateEnum.CANDIDATE || entry.expiresAt().isAfter(now)) throw revisionConflict();
        return retire(repository, previous, MemoryStateEnum.EXPIRED, "RETENTION_EXPIRED", now, technicalActorSha256);
    }

    private StoredMemory retire(JdbcMemoryRepository repository, StoredMemory previous, MemoryStateEnum state, String reason, Instant now, String actorSha256) {
        var old = previous.entry();
        var entry = new MemoryEntryDTO(old.owner(), old.scopeId(), old.memoryId(), old.kind(), next(old.revision()), old.revision(), state, null, old.contentSha256(), null,
                old.confidence(), old.confirmed(), old.sensitivity(), old.retentionPolicyRef(), old.effectiveAt(), old.expiresAt(), old.createdAt(), now);
        var retired = new StoredMemory(entry, previous.indexModelRef(), previous.sourceEvidenceSha256(), actorSha256, reason, now);
        if (repository.component()) repository.redactLegacyCopy(retired, binding);
        repository.replace(retired, old.revision(), true);
        publication.publish(new MemoryIndexPublication.Event(MemoryIndexPublication.Action.DELETE_MEMORY, repository.tenantId(), repository.issuer(), entry.owner(), entry.scopeId(),
                entry.memoryId(), entry.revision(), entry.contentSha256(), null, null, now)); return retired;
    }

    private void build(JdbcMemoryRepository repository, MemoryOperationContext context, StoredMemory stored, MemoryRetentionPolicy policy, Instant now) {
        var entry = stored.entry(); if (repository.component() || entry.state() != MemoryStateEnum.ACTIVE || !policy.embeddingAllowed().contains(entry.sensitivity())) return;
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
        if (context.preferenceWrite() != null && operation != MemoryAuthorization.Operation.UPSERT) throw denied();
        var actor = context.actor();
        if (owner.type() == MemoryOwnerTypeEnum.USER && (actor.subjectType() != TrustedAgentActor.SubjectType.USER || !owner.id().equals(actor.subjectId()))
                || owner.type() == MemoryOwnerTypeEnum.APPLICATION && (actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || !owner.id().equals(actor.subjectId()))) throw denied();
        Instant maximum = clock.instant().plusSeconds(30); Instant deadline = context.deadlineAt().isBefore(maximum) ? context.deadlineAt() : maximum;
        var bounded = new MemoryOperationContext(actor, deadline, context.runAuthorization(), context.preferenceWrite()); checkDeadline(bounded, clock.instant());
        MemoryAuthorization.Decision decision;
        try { decision = authorization.authorize(new MemoryAuthorization.Request(bounded, owner, scope, operation, id, expected, provenance == null ? null : provenance.sourceRunId(), provenance == null ? null : provenance.sourceEventId())); }
        catch (RuntimeException unavailable) { throw unavailable(); }
        if (decision == MemoryAuthorization.Decision.DENY) throw denied(); if (decision != MemoryAuthorization.Decision.ALLOW) throw unavailable();
        checkDeadline(bounded, clock.instant()); return bounded;
    }

    private <T> T transaction(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, boolean write, BiFunction<JdbcMemoryRepository, Instant, T> action) {
        return transaction(context, owner, scope, write, false, null, action);
    }

    private <T> T transaction(MemoryOperationContext context, MemoryOwnerDTO owner, String scope, boolean write,
                              boolean create, Long expectedEpoch, BiFunction<JdbcMemoryRepository, Instant, T> action) {
        noTransaction(); checkDeadline(context, clock.instant());
        if (!binding.equals(publication.binding()) || !publication.usesDataSource(source)) throw unavailable();
        int seconds = Math.max(1, Math.toIntExact((Duration.between(clock.instant(), context.deadlineAt()).toNanos() + 999_999_999) / 1_000_000_000));
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(seconds);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(seconds);
        try {
            T result = transaction.execute(status -> {
                var repository = new JdbcMemoryRepository(jdbc, context.actor().tenantId(), context.actor().issuerFingerprint(), owner, scope, codec);
                if (hostAdapter == null) return apply(context, repository, write, action);
                return hostAdapter.inScope(repository, context.deadlineAt(), write, create, expectedEpoch,
                        () -> initialized.get() && binding.equals(publication.binding()),
                        selected -> apply(context, selected, write, action));
            });
            if (result == null) throw unavailable(); return result;
        } catch (AgentLedgerException rejected) { throw rejected; }
        catch (DataAccessException | TransactionException failed) { log.debug("Memory transaction unavailable"); throw unavailable(); }
    }

    private <T> T apply(MemoryOperationContext context, JdbcMemoryRepository repository, boolean write,
                        BiFunction<JdbcMemoryRepository, Instant, T> action) {
        if (write) repository.lockScope();
        Instant now = repository.databaseNow();
        checkDeadline(context, now);
        if (context.preferenceWrite() != null) {
            if (!write || !context.preferenceWrite().usesDataSource(source)) throw unavailable();
            context.preferenceWrite().verifyTransaction();
            checkDeadline(context, repository.databaseNow());
        }
        T result = action.apply(repository, now);
        checkDeadline(context, repository.databaseNow());
        if (!binding.equals(publication.binding())) throw unavailable();
        return result;
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
    private record SearchPreparationVO(Instant now, List<PgVectorMemoryIndex.Allowed> allowed, long epoch) { }
}
