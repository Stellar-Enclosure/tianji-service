package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomically reserves and reconciles per-run model and tool budgets. */
public final class AgentBudgetService {
    private final AgentLedgerService ledger;
    private final JdbcAgentLedgerRepository repository;
    private final TransactionTemplate transaction;
    private final AgentContextJsonCodec contexts = new AgentContextJsonCodec();

    AgentBudgetService(AgentLedgerService ledger, JdbcAgentLedgerRepository repository, TransactionTemplate transaction) {
        this.ledger = ledger;
        this.repository = repository;
        this.transaction = transaction;
    }

    /** Atomically reserves a trusted upper bound before a model or tool call starts. */
    public AgentBudgetReservation reserveBudget(TrustedAgentActor actor, AgentRunLease lease,
                                                 AgentBudgetRequest request) {
        return reserveBudget(actor, lease, request, null);
    }

    /** A model context and its budget commit together before transport is opened; old callers do not query the optional table. */
    public AgentBudgetReservation reserveBudget(TrustedAgentActor actor, AgentRunLease lease,
                                                 AgentBudgetRequest request, AgentContextMetadataDTO context) {
        return reserveBudget(actor, lease, request, context, null);
    }

    /** Atomically records a verified model slot with its existing reservation. */
    public AgentBudgetReservation reserveBudget(TrustedAgentActor actor, AgentRunLease lease,
                                                 AgentBudgetRequest request, AgentContextMetadataDTO context, ModelUsageSeedVO usage) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(request, "request");
        if (context != null && (request.kind() != AgentBudgetRequest.Kind.MODEL || "LEGACY".equals(actor.issuerFingerprint())
                || !context.ownerSha256().equals(actor.identityFingerprint()) || !context.runId().equals(lease.runId())
                || !context.reservationId().equals(request.reservationId()) || context.leaseEpoch() != lease.epoch()
                || context.estimatedInputTokens() != request.inputTokens() || context.effectiveOutputLimit() != request.outputTokens())) {
            throw invalid("model context does not match its scoped reservation");
        }
        if (usage != null && (request.kind() != AgentBudgetRequest.Kind.MODEL || "LEGACY".equals(actor.issuerFingerprint())
                || !ledger.acceptsModelUsage("CONFIG", usage.routeRef()))) throw invalid("model usage seed is not admitted");
        if (context != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Model context preparation must own its commit transaction");
        }
        if (usage != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Model usage preparation must own its commit transaction");
        }
        String payload = context == null ? null : contexts.write(context);
        String fingerprint = context == null ? null : contexts.fingerprint(context);
        return transaction.execute(status -> {
            if (!repository.lockRunForBudget(actor, lease.runId())) throw notFound();
            var material = context == null ? null : ledger.executionMaterial(actor, new GetAgentRunQuery(lease.runId()));
            var snapshot = material == null ? ledger.snapshot(actor, new GetAgentRunQuery(lease.runId())) : material.snapshot();
            ExecutionPolicyDTO policy = snapshot.effectivePolicy();
            if (usage != null) requireSeed(snapshot, usage);
            if (context != null && (!context.definitionContentSha256().equals(material.authorization().definitionSha256())
                    || !context.requestSha256().equals(material.authorization().requestSha256())
                    || !context.executionSnapshotSha256().equals(material.authorization().snapshotSha256())
                    || material.run().sessionId() != null && !material.run().sessionId().equals(context.nativeSessionId())
                    || !context.window().modelRouteRef().equals(material.snapshot().definition().dependencies().modelRouteRef())
                    || !context.window().policyVersionRef().equals(material.authorization().policyVersionRef()))) {
                throw invalid("model context does not match the immutable execution snapshot");
            }
            if (request.kind() == AgentBudgetRequest.Kind.TOOL
                    && !policy.allowedToolVersions().contains(request.toolVersion())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                        "tool version is not allowed by the effective policy");
            }
            if (policy.maxEstimatedCost() != null && request.estimatedCost() == null) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN,
                        "a cost upper bound is required by the effective policy");
            }
            Instant reservedAt = now();
            int inserted = repository.insertBudgetReservation(actor, lease, request, reservedAt);
            if (inserted == 0) {
                AgentBudgetReservation existing = repository.budgetReservation(actor, lease.runId(),
                        request.reservationId(), usage != null).orElseThrow();
                if (!sameBudgetRequest(existing, lease, request)) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                            "budget reservation id was already used for different values");
                }
                if (!repository.validLease(actor, lease)) {
                    throw leaseLost();
                }
                if (context != null && !repository.contextSnapshot(actor, lease.runId(), request.reservationId())
                        .map(value -> value.fingerprint().equals(fingerprint)).orElse(false)) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "model context reservation is missing or changed");
                }
                if (usage != null) repository.modelUsageForUpdate(actor, lease, request.reservationId())
                        .ifPresent(row -> requireSameSeed(row, usage));
                return existing;
            }
            if (repository.reserveBudget(actor, lease, request, policy) != 1) {
                if (!repository.validLease(actor, lease)) throw leaseLost();
                JdbcAgentLedgerRepository.BudgetCounters counters = repository.budgetCounters(actor, lease.runId());
                if (budgetExhausted(counters, policy, request)) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED,
                            "Agent run budget is exhausted");
                }
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED,
                        "Agent run parallel capacity is temporarily unavailable");
            }
            if (context != null) {
                if (!repository.contextPolicyMatches(actor, lease.runId(), context, payload)) throw new AgentLedgerException(
                        AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "model context policy changed during the run");
                if (repository.insertContextSnapshot(actor, lease, request.reservationId(), payload, fingerprint) != 1) throw leaseLost();
            }
            AgentBudgetReservation stored = repository.budgetReservation(actor, lease.runId(),
                    request.reservationId(), usage != null).orElseThrow();
            if (usage != null && repository.insertModelUsageSeed(actor, lease, request.reservationId(), usage, reservedAt) != 1) {
                var existing = repository.modelUsageForUpdate(actor, lease, request.reservationId()).orElseThrow(AgentBudgetService::leaseLost);
                requireSameSeed(existing, usage);
            }
            return new AgentBudgetReservation(stored.reservationId(), stored.leaseEpoch(), stored.kind(),
                    stored.toolVersion(), stored.inputTokens(), stored.outputTokens(), stored.estimatedCost(),
                    stored.state(), stored.actualInputTokens(), stored.actualOutputTokens(), stored.actualCost(), true);
        });
    }

    /** Refunds only verified unused amounts; unknown usage keeps its reservation. */
    public AgentBudgetReservation settleBudget(TrustedAgentActor actor, AgentRunLease lease, String reservationId,
                                                AgentBudgetSettlement actual) {
        return settleBudget(actor, lease, reservationId, actual, null);
    }

    /** A verified measurement and its existing budget settlement commit or roll back together. */
    public AgentBudgetReservation settleBudget(TrustedAgentActor actor, AgentRunLease lease, String reservationId,
                                                AgentBudgetSettlement actual, ModelUsageFinalVO usage) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(actual, "actual");
        if (reservationId == null || reservationId.isBlank() || reservationId.length() > 128) {
            throw invalid("budget reservation id is invalid");
        }
        if (usage != null) {
            if ("LEGACY".equals(actor.issuerFingerprint()) || !ledger.acceptsModelUsage("CONFIG", usage.routeRef())) {
                throw invalid("model usage final is not admitted");
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Model usage settlement must own its commit transaction");
            }
        }
        BudgetSettlementOutcome outcome = transaction.execute(status -> {
            if (!repository.lockRunForBudget(actor, lease.runId())) throw notFound();
            AgentBudgetReservation reservation = repository.budgetReservation(actor, lease.runId(), reservationId, false)
                    .orElseThrow(AgentBudgetService::notFound);
            if (reservation.leaseEpoch() != lease.epoch()) throw leaseLost();
            if (reservation.state() == AgentBudgetReservation.State.RESERVED) {
                if (!repository.lockLeaseForSettlement(actor, lease)) throw leaseLost();
                reservation = repository.budgetReservation(actor, lease.runId(), reservationId, true)
                        .orElseThrow(AgentBudgetService::notFound);
            }
            if (reservation.state() != AgentBudgetReservation.State.RESERVED) {
                if (!sameSettlement(reservation, actual)) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                            "budget reservation was settled with different values");
                }
                if (usage != null && reservation.kind() == AgentBudgetRequest.Kind.MODEL) {
                    requireUsageActual(usage, actual);
                    repository.budgetReservation(actor, lease.runId(), reservationId, true).orElseThrow(AgentBudgetService::notFound);
                    settleModelUsage(actor, lease, reservationId, usage, null, false);
                }
                return new BudgetSettlementOutcome(reservation, exceeds(reservation, actual));
            }
            boolean exceeded = exceeds(reservation, actual);
            long inputDelta = delta(reservation.inputTokens(), actual.inputTokens(), exceeded);
            long outputDelta = delta(reservation.outputTokens(), actual.outputTokens(), exceeded);
            BigDecimal costDelta = costDelta(reservation.estimatedCost(), actual.actualCost(), exceeded);
            if (repository.adjustBudget(actor, lease, reservation.kind(), inputDelta, outputDelta, costDelta) != 1) throw leaseLost();
            AgentBudgetReservation.State state = !exceeded && actual.inputTokens() != null && actual.outputTokens() != null
                    && actual.actualCost() != null ? AgentBudgetReservation.State.SETTLED : AgentBudgetReservation.State.UNKNOWN;
            Instant settledAt = now();
            repository.settleBudget(actor.tenantId(), lease.runId(), reservationId, state, actual, settledAt);
            if (usage != null && reservation.kind() == AgentBudgetRequest.Kind.MODEL) {
                requireUsageActual(usage, actual);
                settleModelUsage(actor, lease, reservationId, usage, settledAt, true);
            }
            return new BudgetSettlementOutcome(repository.budgetReservation(actor, lease.runId(), reservationId, false)
                    .orElseThrow(), exceeded);
        });
        if (outcome.exceeded()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED,
                    "reported usage exceeded the reserved upper bound");
        }
        return outcome.reservation();
    }

    private void requireSeed(AgentExecutionSnapshot snapshot, ModelUsageSeedVO usage) {
        var definition = snapshot.definition();
        if (!ledger.acceptsModelUsage(definition.executionMode(), usage.routeRef())
                || !usage.routeRef().equals(definition.dependencies().modelRouteRef())
                || !usage.modelName().equals(definition.configuration().get("modelName"))
                || usage.price() != null && !usage.price().currency().equals(snapshot.effectivePolicy().currency())) {
            throw invalid("model usage seed does not match the exact execution binding");
        }
    }

    private static void requireSameSeed(JdbcAgentLedgerRepository.ModelUsageWriteRow row, ModelUsageSeedVO seed) {
        if (!row.routeRef().equals(seed.routeRef()) || !row.modelName().equals(seed.modelName())
                || !Objects.equals(frozenPrice(row), seed.price())) {
            throw usageConflict();
        }
    }

    private static void requireUsageActual(ModelUsageFinalVO usage, AgentBudgetSettlement actual) {
        if (usage.reason() == null && (!usage.inputTokens().equals(actual.inputTokens())
                || !usage.outputTokens().equals(actual.outputTokens()))) throw invalid("model usage does not match budget evidence");
    }

    private static AgentModelUsagePriceVO frozenPrice(JdbcAgentLedgerRepository.ModelUsageWriteRow row) {
        if (row.priceVersionRef() == null) return null;
        var price = new AgentModelUsagePriceVO(row.routeRef(), row.modelName(), row.currency(),
                row.inputRatePerMillion(), row.outputRatePerMillion());
        if (!row.priceVersionRef().equals(price.priceVersionRef())) throw usageUnavailable();
        return price;
    }

    private void settleModelUsage(TrustedAgentActor actor, AgentRunLease lease, String reservationId,
                                  ModelUsageFinalVO usage, Instant observedAt, boolean active) {
        var selected = repository.modelUsageForUpdate(actor, lease, reservationId);
        if (selected.isEmpty()) return;
        var row = selected.orElseThrow();
        if (!row.routeRef().equals(usage.routeRef()) || !row.modelName().equals(usage.modelName())) throw usageConflict();
        if ("PENDING".equals(row.state()) && !active) return;
        var measured = measure(actor.tenantId(), lease, reservationId, row, usage);
        if (!"PENDING".equals(row.state())) {
            requireSameFinal(row, measured);
            return;
        }
        if (observedAt.isBefore(row.createdAt())) throw usageUnavailable();
        Instant measuredAt = measured.finalSha256() == null ? null : observedAt;
        if (repository.settleModelUsage(actor, lease, reservationId, measured, measuredAt) == 1) return;
        var latest = repository.modelUsageForUpdate(actor, lease, reservationId).orElseThrow(AgentBudgetService::usageUnavailable);
        requireSameFinal(latest, measured);
    }

    private static void requireSameFinal(JdbcAgentLedgerRepository.ModelUsageWriteRow row, ModelUsageMeasurementVO value) {
        if (!row.state().equals(value.state()) || !Objects.equals(row.safeReason(), value.safeReason())
                || !Objects.equals(row.finalSha256(), value.finalSha256())) throw usageConflict();
    }

    private static ModelUsageMeasurementVO measure(long tenant, AgentRunLease lease, String reservationId,
                                                   JdbcAgentLedgerRepository.ModelUsageWriteRow row, ModelUsageFinalVO usage) {
        if (usage.reason() != null) return new ModelUsageMeasurementVO("UNKNOWN", null, null, null, usage.reason().name(), null);
        String state = "UNPRICED";
        String reason = "PRICE_MISSING";
        BigDecimal cost = null;
        var price = frozenPrice(row);
        if (price != null) {
            BigDecimal calculated = price.inputRatePerMillion().multiply(BigDecimal.valueOf(usage.inputTokens()))
                    .add(price.outputRatePerMillion().multiply(BigDecimal.valueOf(usage.outputTokens())))
                    .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);
            if (calculated.precision() <= 24) {
                state = "PRICED";
                reason = null;
                cost = calculated;
            } else {
                reason = "PRICE_OVERFLOW";
            }
        }
        var fields = new ArrayList<>(List.of("TIANJI_MODEL_USAGE_FINAL_V1", Long.toString(tenant), lease.runId(),
                reservationId, Long.toString(lease.epoch()), row.routeRef(), row.modelName(), state,
                Long.toString(usage.inputTokens()), Long.toString(usage.outputTokens())));
        nullable(fields, row.priceVersionRef());
        nullable(fields, cost == null ? null : cost.stripTrailingZeros().toPlainString());
        nullable(fields, reason);
        return new ModelUsageMeasurementVO(state, usage.inputTokens(), usage.outputTokens(), cost, reason,
                OrchestrationRequestDigests.hash(fields, Map.of()));
    }

    private static void nullable(List<String> fields, String value) {
        fields.add(value == null ? "0" : "1");
        if (value != null) fields.add(value);
    }

    private static AgentLedgerException usageConflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Model usage does not match its first stored fact");
    }

    private static AgentLedgerException usageUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Model usage facts are unavailable");
    }

    private static boolean validUsageText(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }

    public record ModelUsageSeedVO(String routeRef, String modelName, AgentModelUsagePriceVO price) {
        public ModelUsageSeedVO {
            if (!validUsageText(routeRef, 256) || !validUsageText(modelName, 512)
                    || price != null && (!routeRef.equals(price.routeRef()) || !modelName.equals(price.modelName()))) {
                throw invalid("model usage seed is invalid");
            }
        }
    }

    public enum ModelUsageUnknownReason { MISSING_USAGE, INVALID_USAGE, MODEL_ERROR }

    public record ModelUsageFinalVO(String routeRef, String modelName, Long inputTokens, Long outputTokens,
                                   ModelUsageUnknownReason reason) {
        public ModelUsageFinalVO {
            if (!validUsageText(routeRef, 256) || !validUsageText(modelName, 512)
                    || (reason == null
                    ? inputTokens == null || outputTokens == null || inputTokens < 0 || outputTokens < 0
                            || inputTokens > Long.MAX_VALUE - outputTokens
                    : inputTokens != null || outputTokens != null)) throw invalid("model usage final is invalid");
        }
    }

    record ModelUsageMeasurementVO(String state, Long inputTokens, Long outputTokens, BigDecimal cost,
                                   String safeReason, String finalSha256) { }

    private static boolean sameBudgetRequest(AgentBudgetReservation reservation, AgentRunLease lease,
                                             AgentBudgetRequest request) {
        return reservation.leaseEpoch() == lease.epoch() && reservation.kind() == request.kind()
                && Objects.equals(reservation.toolVersion(), request.toolVersion())
                && reservation.inputTokens() == request.inputTokens()
                && reservation.outputTokens() == request.outputTokens()
                && equalCost(reservation.estimatedCost(), request.estimatedCost());
    }

    private static boolean budgetExhausted(JdbcAgentLedgerRepository.BudgetCounters counters,
                                           ExecutionPolicyDTO policy, AgentBudgetRequest request) {
        return request.kind() == AgentBudgetRequest.Kind.MODEL && counters.modelCalls() >= policy.maxModelCalls()
                || request.kind() == AgentBudgetRequest.Kind.TOOL && counters.toolCalls() >= policy.maxToolCalls()
                || request.inputTokens() > policy.maxInputTokens() - counters.inputTokens()
                || request.outputTokens() > policy.maxOutputTokens() - counters.outputTokens()
                || policy.maxEstimatedCost() != null && request.estimatedCost() != null
                && counters.cost().add(request.estimatedCost()).compareTo(policy.maxEstimatedCost()) > 0;
    }

    private static boolean sameSettlement(AgentBudgetReservation reservation, AgentBudgetSettlement actual) {
        return Objects.equals(reservation.actualInputTokens(), actual.inputTokens())
                && Objects.equals(reservation.actualOutputTokens(), actual.outputTokens())
                && equalCost(reservation.actualCost(), actual.actualCost());
    }

    private static boolean equalCost(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static boolean exceeds(AgentBudgetReservation reservation, AgentBudgetSettlement actual) {
        return actual.inputTokens() != null && actual.inputTokens() > reservation.inputTokens()
                || actual.outputTokens() != null && actual.outputTokens() > reservation.outputTokens()
                || actual.actualCost() != null && reservation.estimatedCost() != null
                && actual.actualCost().compareTo(reservation.estimatedCost()) > 0;
    }

    private static long delta(long reserved, Long actual, boolean exceeded) {
        if (actual == null) return 0;
        long difference = actual - reserved;
        return exceeded ? Math.max(0, difference) : difference;
    }

    private static BigDecimal costDelta(BigDecimal reserved, BigDecimal actual, boolean exceeded) {
        if (actual == null) return BigDecimal.ZERO;
        BigDecimal difference = actual.subtract(reserved == null ? BigDecimal.ZERO : reserved);
        return exceeded && difference.signum() < 0 ? BigDecimal.ZERO : difference;
    }

    private static AgentLedgerException leaseLost() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                "Agent run lease is no longer valid");
    }

    private record BudgetSettlementOutcome(AgentBudgetReservation reservation, boolean exceeded) { }

    private static AgentLedgerException notFound() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                "Agent resource is not available in the current scope");
    }

    private static AgentLedgerException invalid(String message) {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, message);
    }

    private Instant now() {
        return repository.databaseNow().truncatedTo(ChronoUnit.MICROS);
    }
}
