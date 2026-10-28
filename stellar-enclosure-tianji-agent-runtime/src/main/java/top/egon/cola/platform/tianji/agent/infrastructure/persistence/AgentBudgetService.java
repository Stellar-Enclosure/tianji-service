package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Atomically reserves and reconciles per-run model and tool budgets. */
public final class AgentBudgetService {
    private final AgentLedgerService ledger;
    private final JdbcAgentLedgerRepository repository;
    private final TransactionTemplate transaction;

    AgentBudgetService(AgentLedgerService ledger, JdbcAgentLedgerRepository repository, TransactionTemplate transaction) {
        this.ledger = ledger;
        this.repository = repository;
        this.transaction = transaction;
    }

    /** Atomically reserves a trusted upper bound before a model or tool call starts. */
    public AgentBudgetReservation reserveBudget(TrustedAgentActor actor, AgentRunLease lease,
                                                 AgentBudgetRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(request, "request");
        return transaction.execute(status -> {
            ExecutionPolicyDTO policy = ledger.snapshot(actor, new GetAgentRunQuery(lease.runId())).effectivePolicy();
            if (request.kind() == AgentBudgetRequest.Kind.TOOL
                    && !policy.allowedToolVersions().contains(request.toolVersion())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                        "tool version is not allowed by the effective policy");
            }
            if (policy.maxEstimatedCost() != null && request.estimatedCost() == null) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN,
                        "a cost upper bound is required by the effective policy");
            }
            int inserted = repository.insertBudgetReservation(actor, lease, request, now());
            if (inserted == 0) {
                AgentBudgetReservation existing = repository.budgetReservation(actor, lease.runId(),
                        request.reservationId(), false).orElseThrow();
                if (!sameBudgetRequest(existing, lease, request)) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                            "budget reservation id was already used for different values");
                }
                if (!repository.validLease(actor, lease)) {
                    throw leaseLost();
                }
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
            AgentBudgetReservation stored = repository.budgetReservation(actor, lease.runId(),
                    request.reservationId(), false).orElseThrow();
            return new AgentBudgetReservation(stored.reservationId(), stored.leaseEpoch(), stored.kind(),
                    stored.toolVersion(), stored.inputTokens(), stored.outputTokens(), stored.estimatedCost(),
                    stored.state(), stored.actualInputTokens(), stored.actualOutputTokens(), stored.actualCost(), true);
        });
    }

    /** Refunds only verified unused amounts; unknown usage keeps its reservation. */
    public AgentBudgetReservation settleBudget(TrustedAgentActor actor, AgentRunLease lease, String reservationId,
                                                AgentBudgetSettlement actual) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(actual, "actual");
        if (reservationId == null || reservationId.isBlank() || reservationId.length() > 128) {
            throw invalid("budget reservation id is invalid");
        }
        BudgetSettlementOutcome outcome = transaction.execute(status -> {
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
                return new BudgetSettlementOutcome(reservation, exceeds(reservation, actual));
            }
            boolean exceeded = exceeds(reservation, actual);
            long inputDelta = delta(reservation.inputTokens(), actual.inputTokens(), exceeded);
            long outputDelta = delta(reservation.outputTokens(), actual.outputTokens(), exceeded);
            BigDecimal costDelta = costDelta(reservation.estimatedCost(), actual.actualCost(), exceeded);
            if (repository.adjustBudget(actor, lease, reservation.kind(), inputDelta, outputDelta, costDelta) != 1) throw leaseLost();
            AgentBudgetReservation.State state = !exceeded && actual.inputTokens() != null && actual.outputTokens() != null
                    && actual.actualCost() != null ? AgentBudgetReservation.State.SETTLED : AgentBudgetReservation.State.UNKNOWN;
            repository.settleBudget(actor.tenantId(), lease.runId(), reservationId, state, actual, now());
            return new BudgetSettlementOutcome(repository.budgetReservation(actor, lease.runId(), reservationId, false)
                    .orElseThrow(), exceeded);
        });
        if (outcome.exceeded()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED,
                    "reported usage exceeded the reserved upper bound");
        }
        return outcome.reservation();
    }

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
