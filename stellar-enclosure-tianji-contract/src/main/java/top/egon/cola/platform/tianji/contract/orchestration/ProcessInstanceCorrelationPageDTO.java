package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Owned START receipt references from one Process store read. */
public record ProcessInstanceCorrelationPageDTO(String correlationId, Instant observedAt,
                                                List<ReceiptDTO> items, String nextCursor) {
    public static final int PAGE_SIZE = 20;

    public ProcessInstanceCorrelationPageDTO {
        correlationId = OrchestrationChecks.command(correlationId);
        observedAt = ListProcessInstancesByCorrelationQuery.receiptTime(observedAt);
        nextCursor = ListProcessInstancesByCorrelationQuery.optionalCursor(nextCursor);
        if (items == null || items.size() > PAGE_SIZE || nextCursor != null && items.size() != PAGE_SIZE) {
            throw OrchestrationChecks.invalid();
        }
        var instanceIds = new HashSet<String>();
        for (var item : items) {
            if (item == null || !instanceIds.add(item.instanceId()) || item.submittedAt().isAfter(observedAt)) {
                throw OrchestrationChecks.invalid();
            }
        }
        items = List.copyOf(items);
    }

    public record ReceiptDTO(String instanceId, String releaseVersion, Instant submittedAt) {
        public ReceiptDTO {
            instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
            releaseVersion = OrchestrationChecks.release(releaseVersion);
            submittedAt = ListProcessInstancesByCorrelationQuery.receiptTime(submittedAt);
        }
    }
}
