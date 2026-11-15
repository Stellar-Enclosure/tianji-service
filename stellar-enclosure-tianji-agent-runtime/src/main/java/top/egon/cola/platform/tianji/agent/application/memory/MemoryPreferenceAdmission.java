package top.egon.cola.platform.tianji.agent.application.memory;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionItemDTO;
import top.egon.cola.platform.tianji.contract.memory.PrepareMemoryPreferencesQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferencePreparationDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Current-request orchestration; every candidate owns its existing short receipt/fact/Outbox transaction. */
@Slf4j
public final class MemoryPreferenceAdmission {
    private final AgentLedgerService ledger;
    private final PostgresMemoryService memory;
    private final MemoryAuthorization authorization;
    private final Map<String, MemoryPreferenceSourceBinding> bindings;
    private final Map<String, MemoryExtractionPolicy> policies;
    private final Clock clock;
    private final MemoryPreferenceExtraction extraction = new MemoryPreferenceExtraction();

    public MemoryPreferenceAdmission(AgentLedgerService ledger, PostgresMemoryService memory, MemoryAuthorization authorization,
                                     List<MemoryPreferenceSourceBinding> bindings, List<MemoryExtractionPolicy> policies, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.memory = Objects.requireNonNull(memory, "memory");
        this.authorization = Objects.requireNonNull(authorization, "authorization"); this.clock = Objects.requireNonNull(clock, "clock");
        if (bindings == null || bindings.isEmpty() || bindings.size() > 1000 || policies == null || policies.isEmpty() || policies.size() > 32) throw invalidConfiguration();
        var configured = new HashMap<String, MemoryExtractionPolicy>(); var versions = new HashSet<String>();
        for (var policy : policies) {
            if (!memory.policyReferences().contains(policy.retentionPolicyRef()) || !versions.add(policy.key() + '@' + policy.version())
                    || configured.putIfAbsent(policy.reference(), policy) != null) throw invalidConfiguration();
        }
        var approved = new HashMap<String, MemoryPreferenceSourceBinding>(); var scopes = new HashSet<List<String>>(); var definitions = new HashMap<List<String>, String>();
        for (var binding : bindings) {
            var identity = List.of(Long.toString(binding.tenantId()), binding.issuerFingerprint(), binding.definitionKey(), Long.toString(binding.definitionVersion()));
            var scope = new ArrayList<>(identity); scope.add(binding.scopeId());
            String previous = definitions.putIfAbsent(identity, binding.definitionSha256());
            if (!configured.containsKey(binding.policyRef()) || approved.putIfAbsent(binding.id(), binding) != null || !scopes.add(List.copyOf(scope))
                    || previous != null && !previous.equals(binding.definitionSha256())) throw invalidConfiguration();
        }
        this.bindings = Map.copyOf(approved); this.policies = Map.copyOf(configured);
    }

    public MemoryPreferenceExtractionResultDTO extract(MemoryOperationContext context, ExtractMemoryPreferencesCommand command) {
        return extract(context, command, false);
    }

    public MemoryPreferenceExtractionResultDTO replayCommittedExtraction(MemoryOperationContext context,
                                                                          @Valid ExtractMemoryPreferencesCommand command) {
        return extract(context, command, true);
    }

    private MemoryPreferenceExtractionResultDTO extract(MemoryOperationContext context, ExtractMemoryPreferencesCommand command, boolean replayOnly) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(command, "command");
        var binding = bindings.get(command.bindingId());
        if (binding == null || !binding.accepts(context.actor(), command)) throw denied();
        var loaded = source(context, command.sourceQuery());
        if (!binding.equals(loaded.binding())) throw denied();
        var material = loaded.material();
        if (material.run().status() != AgentRunStatusEnum.COMPLETED) {
            if (replayOnly) throw denied();
            return result(command, MemoryPreferenceExtractionOutcomeEnum.RUN_NOT_COMPLETED, List.of());
        }
        var policy = policies.get(binding.policyRef());
        var prepared = extraction.evaluate(new MemoryPreferenceExtraction.Source(material.run().status(), MemoryPreferenceExtraction.SourceKind.USER_INPUT, true,
                material.snapshot().input().text() == null ? "" : material.snapshot().input().text()), policy);
        var candidates = MemoryPreferenceCommands.create(context.actor(), command, policy, prepared);
        if (replayOnly && candidates.isEmpty()) throw denied();
        var committed = replayOnly ? memory.replayCommittedUpserts(context, candidates) : writeCandidates(context, candidates);
        var receipts = new ArrayList<MemoryPreferenceExtractionItemDTO>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            var key = candidates.get(index).content().facts().keySet().iterator().next();
            receipts.add(new MemoryPreferenceExtractionItemDTO(key, committed.get(index)));
        }
        return result(command, MemoryPreferenceExtractionOutcomeEnum.valueOf(prepared.outcome().name()), receipts);
    }

    private List<MemoryMutationResultDTO> writeCandidates(MemoryOperationContext context, List<UpsertMemoryCommand> candidates) {
        var receipts = new ArrayList<MemoryMutationResultDTO>(candidates.size());
        for (var candidate : candidates) {
            if (!clock.instant().isBefore(context.deadlineAt())) throw unavailable();
            receipts.add(memory.upsert(context, candidate));
        }
        return receipts;
    }

    public MemoryPreferencePreparationDTO prepare(MemoryOperationContext context, PrepareMemoryPreferencesQuery query) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(query, "query");
        var loaded = source(context, query); var material = loaded.material(); var selected = loaded.binding();
        var command = new ExtractMemoryPreferencesCommand(query.owner(), query.scopeId(), query.runId(), query.sourceEventId(), selected.id(), selected.policyRef());
        var policy = policies.get(selected.policyRef());
        var prepared = extraction.evaluate(new MemoryPreferenceExtraction.Source(material.run().status(), MemoryPreferenceExtraction.SourceKind.USER_INPUT, true,
                material.snapshot().input().text() == null ? "" : material.snapshot().input().text()), policy);
        var keys = prepared.proposals().stream().map(proposal -> proposal.content().facts().keySet().iterator().next()).toList();
        requireCurrent(context, query);
        return new MemoryPreferencePreparationDTO(command, MemoryPreferenceExtractionOutcomeEnum.valueOf(prepared.outcome().name()), keys.size(), keys, policy.sensitivity());
    }

    private PreferenceSource source(MemoryOperationContext context, PrepareMemoryPreferencesQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || context.runAuthorization() != null || context.preferenceWrite() != null || !clock.instant().isBefore(context.deadlineAt())) throw unavailable();
        if (bindings.values().stream().noneMatch(binding -> binding.acceptsScope(context.actor(), query.owner(), query.scopeId()))) throw denied();
        requireCurrent(context, query);
        var material = ledger.executionMaterial(context.actor(), new GetAgentRunQuery(query.runId()));
        if (!query.runId().equals(material.run().runId()) || !context.actor().equals(material.authorization().actor()) || material.snapshot().processLink() != null) throw denied();
        MemoryPreferenceSourceBinding selected = null;
        for (var binding : bindings.values()) {
            if (!binding.acceptsScope(context.actor(), query.owner(), query.scopeId())) continue;
            boolean matches;
            try { matches = binding.matches(material.authorization()); }
            catch (IllegalArgumentException drift) { throw unavailable(); }
            if (matches) { if (selected != null) throw unavailable(); selected = binding; }
        }
        if (selected == null) throw denied();
        requireCurrent(context, query);
        if (material.run().status() == AgentRunStatusEnum.COMPLETED) {
            if (material.run().lastEventSequence() < 1 || material.run().finishedAt() == null) throw unavailable();
            var events = ledger.events(context.actor(), new AgentEventsQuery(query.runId(), material.run().lastEventSequence() - 1, 1));
            if (events.size() != 1 || !query.sourceEventId().equals(events.getFirst().eventId()) || !query.runId().equals(events.getFirst().runId())
                    || events.getFirst().sequence() != material.run().lastEventSequence() || !"RUN_STATUS_CHANGED".equals(events.getFirst().type())
                    || !"COMPLETED".equals(events.getFirst().payload().get("status"))) throw denied();
            requireCurrent(context, query);
        }
        return new PreferenceSource(material, selected);
    }

    private record PreferenceSource(AgentLedgerService.ExecutionMaterial material, MemoryPreferenceSourceBinding binding) { }

    private void requireCurrent(MemoryOperationContext context, PrepareMemoryPreferencesQuery query) {
        if (!clock.instant().isBefore(context.deadlineAt())) throw unavailable();
        var access = authorization.authorize(new MemoryAuthorization.Request(context, query.owner(), query.scopeId(), MemoryAuthorization.Operation.UPSERT,
                query.admissionId(), 0, query.runId(), query.sourceEventId()));
        if (access == MemoryAuthorization.Decision.DENY) throw denied(); if (access != MemoryAuthorization.Decision.ALLOW || !clock.instant().isBefore(context.deadlineAt())) throw unavailable();
    }

    private static MemoryPreferenceExtractionResultDTO result(ExtractMemoryPreferencesCommand command, MemoryPreferenceExtractionOutcomeEnum outcome, List<MemoryPreferenceExtractionItemDTO> receipts) {
        return new MemoryPreferenceExtractionResultDTO(command.owner(), command.scopeId(), command.runId(), command.sourceEventId(), command.bindingId(), command.policyRef(), outcome, receipts);
    }
    private static IllegalArgumentException invalidConfiguration() { return new IllegalArgumentException("Memory preference admission bindings are invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory preference source or authority is unavailable"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory preference source is not available in this scope"); }
}
