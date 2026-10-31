package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.CancelMemoryPreferenceIntentCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryPreferenceIntentQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentCancellationDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStatusDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;

/** Current USER management of an owned source, including previously approved bindings that have since been withdrawn. */
public final class MemoryPreferenceIntentAccess {
    private final AgentLedgerService ledger;
    private final MemoryPreferenceIntentStore store;
    private final TianjiAgentAccess access;
    private final TianjiMemoryProperties storage;
    private final TrustedIssuerRegistry issuers;
    private final Clock clock;

    public MemoryPreferenceIntentAccess(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, TianjiAgentAccess access,
            TianjiMemoryProperties storage, TrustedIssuerRegistry issuers, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store"); this.access = Objects.requireNonNull(access, "access");
        this.storage = Objects.requireNonNull(storage, "storage"); this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clock = Objects.requireNonNull(clock, "clock");
        if (source == null || !ledger.usesDataSource(source) || !store.usesDataSource(source) || !store.usesLedger(ledger)) throw new IllegalArgumentException("Preference intent management requires the exact ledger DataSource");
    }

    public MemoryPreferenceIntentStatusDTO status(MemoryOperationContext context, GetMemoryPreferenceIntentQuery query) {
        boundary(context, query.owner()); current(context, TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API);
        ledger.get(context.actor(), new GetAgentRunQuery(query.runId()));
        var view = store.view(context.actor(), query.runId(), query.scopeId());
        current(context, TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API);
        return result(query, view.orElse(null));
    }

    public MemoryPreferenceIntentCancellationDTO cancel(MemoryOperationContext context, CancelMemoryPreferenceIntentCommand command) {
        boundary(context, command.owner()); current(context, TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API);
        ledger.get(context.actor(), new GetAgentRunQuery(command.runId()));
        var existing = store.view(context.actor(), command.runId(), command.scopeId()).orElseThrow(MemoryPreferenceIntentAccess::notFound);
        if (!command.runId().equals(existing.runId()) || !command.scopeId().equals(existing.scopeId())) throw unavailable();
        current(context, TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API);
        var cancelled = store.cancel(context.actor(), existing.intentId(), command.expectedRevision(), command.commandId(), command.requestSha256(), context.deadlineAt());
        checkDeadline(context); if (!cancelled.state().terminal()) throw unavailable();
        return new MemoryPreferenceIntentCancellationDTO(command.commandId(), result(command.sourceQuery(), cancelled));
    }

    private void boundary(MemoryOperationContext context, MemoryOwnerDTO owner) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(owner, "owner");
        if (TransactionSynchronizationManager.isActualTransactionActive() || context.runAuthorization() != null || context.preferenceWrite() != null) throw unavailable();
        if (context.actor().subjectType() != TrustedAgentActor.SubjectType.USER || owner.type() != MemoryOwnerTypeEnum.USER || !context.actor().subjectId().equals(owner.id())) throw notFound();
        checkDeadline(context);
    }
    private void current(MemoryOperationContext context, String resource) {
        checkDeadline(context); TianjiMemoryConfiguration.requireOrigin(context.actor().tenantId(), context.actor().issuerFingerprint(), storage, issuers);
        try {
            for (var key : new String[]{resource, TianjiAgentPermissions.GET_API}) {
                var current = access.require(key);
                if (!context.actor().equals(current.actor()) || context.deadlineAt().isAfter(current.expiresAt()) || current.issuer() == null
                        || !context.actor().equals(TrustedAgentActor.verified(context.actor().tenantId(), context.actor().subjectType(), context.actor().subjectId(), current.issuer().toASCIIString()))) throw notFound();
            }
        } catch (AccessDeniedException | AuthenticationException denied) { throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current preference intent or source access was denied"); }
        catch (AgentLedgerException known) { throw known; }
        catch (RuntimeException unknown) { throw unavailable(); }
        TianjiMemoryConfiguration.requireOrigin(context.actor().tenantId(), context.actor().issuerFingerprint(), storage, issuers); checkDeadline(context);
    }
    private static MemoryPreferenceIntentStatusDTO result(GetMemoryPreferenceIntentQuery query, MemoryPreferenceIntent.View view) {
        if (view == null) return new MemoryPreferenceIntentStatusDTO(query.owner(), query.scopeId(), query.runId(), null);
        if (!query.runId().equals(view.runId()) || !query.scopeId().equals(view.scopeId())) throw unavailable();
        try {
            var intent = new MemoryPreferenceIntentDTO(view.intentId().toString(), view.bindingId(), view.policyRef(), MemoryPreferenceIntentStateEnum.valueOf(view.state().name()), view.revision(), view.nextIndex(), view.totalCount(),
                    view.attemptCount(), view.reasonCode(), view.createdAt(), view.updatedAt(), view.closedAt());
            return new MemoryPreferenceIntentStatusDTO(query.owner(), query.scopeId(), query.runId(), intent);
        } catch (RuntimeException malformed) { throw unavailable(); }
    }
    private void checkDeadline(MemoryOperationContext context) { if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(context.deadlineAt())) throw unavailable(); }
    private static AgentLedgerException notFound() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Preference extraction is not available in this owned scope"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Preference extraction status or current authority is unavailable"); }
}
