package top.egon.cola.platform.tianji.contract.orchestration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record ProcessWorkerFailuresDTO(String instanceId, Instant observedAt, List<ProcessWorkerFailureDTO> failures) {
    public ProcessWorkerFailuresDTO {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (failures == null || failures.size() > 128) throw OrchestrationChecks.invalid();
        failures = List.copyOf(failures);
        byte[] previous = null;
        for (var row : failures) {
            byte[] next = row.jobId().getBytes(StandardCharsets.UTF_8);
            if (previous != null && Arrays.compareUnsigned(previous, next) >= 0) throw OrchestrationChecks.invalid();
            previous = next;
        }
    }
}
