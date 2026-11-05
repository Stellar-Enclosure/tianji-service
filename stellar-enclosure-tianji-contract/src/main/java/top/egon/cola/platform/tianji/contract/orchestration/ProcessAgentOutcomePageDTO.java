package top.egon.cola.platform.tianji.contract.orchestration;

import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.time.Instant;
import java.util.List;

/** Process-local evidence for one authorized BPMN instance. */
public record ProcessAgentOutcomePageDTO(String instanceId, Instant observedAt, List<ItemDTO> items, String nextCursor) {
    public static final int PAGE_SIZE = 20;

    public ProcessAgentOutcomePageDTO {
        new GetProcessAgentOutcomesQuery(instanceId, nextCursor);
        if (observedAt == null || items == null || items.size() > PAGE_SIZE
                || nextCursor != null && items.size() != PAGE_SIZE) {
            throw OrchestrationChecks.invalid();
        }
        for (var item : items) {
            if (item == null || item.dispatchRecordedAt().isAfter(observedAt)
                    || item.receivedAt() != null && item.receivedAt().isAfter(observedAt)
                    || item.decidedAt() != null && item.decidedAt().isAfter(observedAt)) {
                throw OrchestrationChecks.invalid();
            }
        }
        items = List.copyOf(items);
    }

    public enum DispatchState { PENDING, SUBMITTED, ACKNOWLEDGED, CLOSING, CLOSED }

    public enum InboxState { RUN_NOT_CONFIRMED, NOT_OBSERVED, RECEIVED, APPLIED, IGNORED_LATE, DEAD }

    public record ItemDTO(String activityId, DispatchState dispatchState, InboxState inboxState,
                          AgentRunStatusEnum terminalStatus, Instant dispatchRecordedAt, Instant receivedAt,
                          Instant decidedAt) {
        public ItemDTO {
            activityId = OrchestrationChecks.definition(activityId);
            if (dispatchState == null || inboxState == null || dispatchRecordedAt == null) {
                throw OrchestrationChecks.invalid();
            }
            boolean hasReceipt = inboxState != InboxState.RUN_NOT_CONFIRMED && inboxState != InboxState.NOT_OBSERVED;
            boolean decided = hasReceipt && inboxState != InboxState.RECEIVED;
            if (hasReceipt != (receivedAt != null) || decided != (decidedAt != null)
                    || terminalStatus != null && (!hasReceipt || !terminal(terminalStatus))
                    || hasReceipt && inboxState != InboxState.DEAD && terminalStatus == null
                    || receivedAt != null && receivedAt.isBefore(dispatchRecordedAt)
                    || decidedAt != null && decidedAt.isBefore(receivedAt)) {
                throw OrchestrationChecks.invalid();
            }
        }
    }

    private static boolean terminal(AgentRunStatusEnum status) {
        return switch (status) {
            case COMPLETED, FAILED, CANCELLED, TIMED_OUT -> true;
            default -> false;
        };
    }
}
