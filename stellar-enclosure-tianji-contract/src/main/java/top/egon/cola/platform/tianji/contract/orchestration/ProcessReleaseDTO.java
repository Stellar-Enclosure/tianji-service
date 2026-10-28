package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/** Public release metadata deliberately omits the engine's deployment and definition IDs. */
public record ProcessReleaseDTO(String releaseVersion, String manifestSha256, Instant createdAt, List<ProcessModelDTO> models) {
    public ProcessReleaseDTO {
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        manifestSha256 = OrchestrationChecks.hash(manifestSha256);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (models == null || models.isEmpty() || models.size() > 16) throw OrchestrationChecks.invalid();
        models = List.copyOf(models);
        var keys = new HashSet<String>();
        for (ProcessModelDTO model : models) {
            if (!keys.add(model.type().name() + ":" + model.definitionKey())) throw OrchestrationChecks.invalid();
        }
    }
}
