package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ExecutionPolicyReducer;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Local Agent ledger. Callers must authenticate and authorize before constructing the trusted actor;
 * this service is not an HTTP entry point and is not automatically enabled.
 */
public final class AgentLedgerService {
    private final JdbcAgentLedgerRepository repository;
    private final AgentJsonCodec json = new AgentJsonCodec();
    private final TransactionTemplate transaction;
    private final Clock clock;

    public AgentLedgerService(DataSource dataSource, Clock clock) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.repository = new JdbcAgentLedgerRepository(new JdbcTemplate(dataSource), json);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Inserts a new immutable version, or returns the existing version if its content is identical. */
    public AgentDefinitionDTO publish(TrustedAgentActor actor, AgentDefinitionSpecDTO specification) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(specification, "specification");
        String hash = json.definitionHash(specification);
        String content = json.write(specification, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        return transaction.execute(status -> {
            Instant now = now();
            repository.insertDefinitionFamily(actor, specification.definitionKey(), now);
            repository.insertDefinitionVersion(actor, specification.definitionKey(), specification.version(),
                    hash, content, now);
            JdbcAgentLedgerRepository.DefinitionRow stored = repository.definition(actor.tenantId(),
                    specification.definitionKey(), specification.version()).orElseThrow();
            if (!stored.hash().equals(hash)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT,
                        "definition version already exists with different content");
            }
            return definition(stored.specification(), stored.hash());
        });
    }

    /** Atomically records the queued run and its first event; no model is called in this transaction. */
    public AgentRunDTO start(TrustedAgentActor actor, StartAgentRunCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String requestHash = json.requestHash(command);
        return transaction.execute(status -> {
            Optional<JdbcAgentLedgerRepository.StoredRun> existing =
                    repository.byIdempotencyKey(actor, command.idempotencyKey());
            if (existing.isPresent()) {
                return sameRequest(existing.get(), requestHash);
            }
            JdbcAgentLedgerRepository.DefinitionRow definition = repository.definition(actor.tenantId(),
                    command.definitionKey(), command.definitionVersion()).orElseThrow(AgentLedgerService::notFound);
            ExecutionPolicyDTO policy = ExecutionPolicyReducer.reduce(definition.specification().policy(),
                    command.policyOverride());
            String snapshot = json.snapshot(definition.specification(), definition.hash(), policy, command);
            Instant now = now();
            String runId = UUID.randomUUID().toString();
            int inserted = repository.insertRun(actor, runId, command.definitionKey(), command.definitionVersion(),
                    command.idempotencyKey(), requestHash, command.sessionId(), command.correlationId(),
                    now.plus(policy.totalTimeout()), snapshot, now);
            if (inserted == 0) {
                return sameRequest(repository.byIdempotencyKey(actor, command.idempotencyKey()).orElseThrow(),
                        requestHash);
            }
            repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(), runId, 1,
                    "RUN_QUEUED", null, null, null, now, command.correlationId(), "1",
                    Map.of("status", AgentRunStatusEnum.QUEUED.name())));
            return repository.run(actor, runId).orElseThrow().run();
        });
    }

    public AgentRunDTO get(TrustedAgentActor actor, GetAgentRunQuery query) {
        return requireRun(actor, query).run();
    }

    public Optional<AgentExecutionResult> getResult(TrustedAgentActor actor, GetAgentRunQuery query) {
        String result = requireRun(actor, query).resultJson();
        return result == null ? Optional.empty() : Optional.of(json.readResult(result));
    }

    public List<AgentEventDTO> events(TrustedAgentActor actor, AgentEventsQuery query) {
        Objects.requireNonNull(query, "query");
        requireRun(actor, new GetAgentRunQuery(query.runId()));
        return List.copyOf(repository.events(actor.tenantId(), query.runId(), query.afterSequence(), query.limit()));
    }

    /** A status CAS and its event/result commit in one local transaction. */
    public AgentRunDTO transition(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                  AgentRunStatusEnum from, AgentRunStatusEnum to, String waitReason,
                                  AgentExecutionResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (expectedRevision < 0 || !AgentRunTransitions.allows(from, to)) {
            throw invalid("invalid Agent run transition");
        }
        if (to == AgentRunStatusEnum.WAITING && (waitReason == null || waitReason.isBlank())) {
            throw invalid("waiting runs require a reason");
        }
        if (waitReason != null && (waitReason.isBlank() || waitReason.length() > 128
                || (to != AgentRunStatusEnum.WAITING && to != AgentRunStatusEnum.SUSPENDED))) {
            throw invalid("invalid wait reason");
        }
        if (AgentRunTransitions.terminal(to) != (result != null)
                || (result != null && (!query.runId().equals(result.runId()) || to != result.status()))) {
            throw invalid("terminal result must match the run and status");
        }
        String resultJson = result == null ? null : json.write(result, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            Instant now = now();
            JdbcAgentLedgerRepository.StoredRun updated = repository.transition(actor, query.runId(),
                    expectedRevision, from, to, waitReason, resultJson, result == null ? null : result.errorCode(),
                    AgentRunTransitions.terminal(to) ? now : null).orElseThrow(() -> new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run revision or state changed"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", from.name());
            payload.put("status", to.name());
            if (waitReason != null) {
                payload.put("reason", waitReason);
            }
            repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(), query.runId(),
                    updated.run().lastEventSequence(), "RUN_STATUS_CHANGED", null, null, null, now,
                    previous.correlationId(), "1", payload));
            return updated.run();
        });
    }

    private JdbcAgentLedgerRepository.StoredRun requireRun(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return repository.run(actor, query.runId()).orElseThrow(AgentLedgerService::notFound);
    }

    private static AgentRunDTO sameRequest(JdbcAgentLedgerRepository.StoredRun run, String hash) {
        if (!run.requestHash().equals(hash)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                    "idempotency key was already used for a different request");
        }
        return run.run();
    }

    private static AgentDefinitionDTO definition(AgentDefinitionSpecDTO spec, String hash) {
        return new AgentDefinitionDTO(spec.definitionKey(), spec.version(), hash, spec.executionMode(),
                spec.codeRef(), spec.inputSchemaVersion(), spec.outputSchemaVersion(), spec.dependencies());
    }

    private static AgentLedgerException notFound() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                "Agent resource is not available in the current scope");
    }

    private static AgentLedgerException invalid(String message) {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, message);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
