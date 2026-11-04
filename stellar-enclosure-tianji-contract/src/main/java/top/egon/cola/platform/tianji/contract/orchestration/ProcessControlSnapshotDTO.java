package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ProcessControlSnapshotDTO(ProcessInstanceDTO process, List<ProcessActiveWaitDTO> activeWaits,
                                        Set<ProcessControlActionEnum> allowedActions,
                                        ProcessControlBlockReasonEnum cancellationBlockReason,
                                        String version, Instant observedAt) {
    private static final Comparator<ProcessActiveWaitDTO> WAIT_ORDER = Comparator
            .comparing((ProcessActiveWaitDTO wait) -> wait.kind().name())
            .thenComparing(ProcessActiveWaitDTO::activityKey);

    public ProcessControlSnapshotDTO {
        process = Objects.requireNonNull(process, "process");
        activeWaits = List.copyOf(activeWaits);
        allowedActions = Set.copyOf(allowedActions);
        cancellationBlockReason = Objects.requireNonNull(cancellationBlockReason, "cancellationBlockReason");
        version = OrchestrationChecks.hash(version);
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (activeWaits.size() > 256) throw OrchestrationChecks.invalid();
        ProcessActiveWaitDTO previous = null;
        int occurrences = 0;
        for (var wait : activeWaits) {
            if (previous != null && WAIT_ORDER.compare(previous, wait) >= 0) throw OrchestrationChecks.invalid();
            occurrences += wait.occurrences();
            previous = wait;
        }
        if (occurrences > 256) throw OrchestrationChecks.invalid();
        boolean terminal = process.status() == ProcessInstanceStatusEnum.COMPLETED
                || process.status() == ProcessInstanceStatusEnum.CANCELLED;
        if (terminal) {
            if (!activeWaits.isEmpty() || !allowedActions.isEmpty()
                    || cancellationBlockReason != ProcessControlBlockReasonEnum.PROCESS_TERMINAL) {
                throw OrchestrationChecks.invalid();
            }
        } else if (cancellationBlockReason == ProcessControlBlockReasonEnum.PROCESS_TERMINAL
                || cancellationBlockReason == ProcessControlBlockReasonEnum.NONE
                && !allowedActions.equals(Set.of(ProcessControlActionEnum.CANCEL))
                || cancellationBlockReason == ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE
                && !allowedActions.isEmpty()) {
            throw OrchestrationChecks.invalid();
        }
    }
}
