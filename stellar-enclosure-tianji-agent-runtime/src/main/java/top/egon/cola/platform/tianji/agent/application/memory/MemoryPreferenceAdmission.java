package top.egon.cola.platform.tianji.agent.application.memory;

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
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Current-request orchestration; every candidate owns its existing short receipt/fact/Outbox transaction. */
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
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(command, "command");
        if (TransactionSynchronizationManager.isActualTransactionActive() || context.runAuthorization() != null || !clock.instant().isBefore(context.deadlineAt())) throw unavailable();
        var binding = bindings.get(command.bindingId());
        if (binding == null || !binding.accepts(context.actor(), command)) throw denied();
        requireCurrent(context, command);
        var material = ledger.executionMaterial(context.actor(), new GetAgentRunQuery(command.runId()));
        boolean matches;
        try { matches = binding.matches(material.authorization()); }
        catch (IllegalArgumentException drift) { throw unavailable(); }
        if (!command.runId().equals(material.run().runId()) || !context.actor().equals(material.authorization().actor()) || !matches
                || material.snapshot().processLink() != null) throw denied();
        requireCurrent(context, command);
        if (material.run().status() != AgentRunStatusEnum.COMPLETED) return result(command, MemoryPreferenceExtractionOutcomeEnum.RUN_NOT_COMPLETED, List.of());
        if (material.run().lastEventSequence() < 1 || material.run().finishedAt() == null) throw unavailable();
        var events = ledger.events(context.actor(), new AgentEventsQuery(command.runId(), material.run().lastEventSequence() - 1, 1));
        if (events.size() != 1 || !command.sourceEventId().equals(events.getFirst().eventId()) || !command.runId().equals(events.getFirst().runId())
                || events.getFirst().sequence() != material.run().lastEventSequence() || !"RUN_STATUS_CHANGED".equals(events.getFirst().type())
                || !"COMPLETED".equals(events.getFirst().payload().get("status"))) throw denied();
        requireCurrent(context, command);
        var policy = policies.get(binding.policyRef());
        var prepared = extraction.evaluate(new MemoryPreferenceExtraction.Source(material.run().status(), MemoryPreferenceExtraction.SourceKind.USER_INPUT, true,
                material.snapshot().input().text() == null ? "" : material.snapshot().input().text()), policy);
        var receipts = new ArrayList<MemoryPreferenceExtractionItemDTO>();
        for (var proposal : prepared.proposals()) {
            if (!clock.instant().isBefore(context.deadlineAt())) throw unavailable();
            String name = proposal.content().facts().keySet().iterator().next();
            String memoryId = memoryId(context, command, name);
            String reference = "memory-preference:" + binding.id() + '#' + policy.reference().substring(policy.reference().lastIndexOf('#') + 1)
                    + ':' + proposal.sourceStart() + ':' + proposal.sourceEnd();
            var source = new MemorySourceDTO(reference, prepared.sourceSha256(), command.runId(), command.sourceEventId());
            var receipt = memory.upsert(context, new UpsertMemoryCommand(command.itemCommandId(name), command.owner(), binding.scopeId(), memoryId,
                    proposal.kind(), 0, proposal.content(), source, proposal.confidence(), false, proposal.sensitivity(), proposal.retentionPolicyRef(), null, null));
            receipts.add(new MemoryPreferenceExtractionItemDTO(name, receipt));
        }
        return result(command, MemoryPreferenceExtractionOutcomeEnum.valueOf(prepared.outcome().name()), receipts);
    }

    private void requireCurrent(MemoryOperationContext context, ExtractMemoryPreferencesCommand command) {
        if (!clock.instant().isBefore(context.deadlineAt())) throw unavailable();
        var access = authorization.authorize(new MemoryAuthorization.Request(context, command.owner(), command.scopeId(), MemoryAuthorization.Operation.UPSERT,
                command.itemCommandId("admission"), 0, command.runId(), command.sourceEventId()));
        if (access == MemoryAuthorization.Decision.DENY) throw denied(); if (access != MemoryAuthorization.Decision.ALLOW || !clock.instant().isBefore(context.deadlineAt())) throw unavailable();
    }

    /** Excluding request/policy/value identifiers prevents a retry or policy change from recreating this source's forgotten preference. */
    private static String memoryId(MemoryOperationContext context, ExtractMemoryPreferencesCommand command, String name) {
        var data = new StringBuilder("TIANJI_MEMORY_PREFERENCE_ID_V1:");
        for (var field : List.of(context.actor().identityFingerprint(), command.runId(), command.sourceEventId(), command.scopeId(), name)) data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try { return "pref-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    private static MemoryPreferenceExtractionResultDTO result(ExtractMemoryPreferencesCommand command, MemoryPreferenceExtractionOutcomeEnum outcome, List<MemoryPreferenceExtractionItemDTO> receipts) {
        return new MemoryPreferenceExtractionResultDTO(command.owner(), command.scopeId(), command.runId(), command.sourceEventId(), command.bindingId(), command.policyRef(), outcome, receipts);
    }
    private static IllegalArgumentException invalidConfiguration() { return new IllegalArgumentException("Memory preference admission bindings are invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory preference source or authority is unavailable"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory preference source is not available in this scope"); }
}
