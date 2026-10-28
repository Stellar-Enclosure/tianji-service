package top.egon.cola.platform.tianji.contract.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The release version is immutable within the authenticated tenant. */
public record PublishProcessReleaseCommand(String releaseVersion, List<ProcessModelResource> models) {
    public PublishProcessReleaseCommand {
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        if (models == null || models.isEmpty() || models.size() > 16) throw OrchestrationChecks.invalid();
        models = List.copyOf(models);
        int bytes = 0;
        Set<String> keys = new HashSet<>();
        for (ProcessModelResource model : models) {
            bytes += model.xml().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 4 * 1024 * 1024 || !keys.add(model.type().name() + ":" + model.definitionKey())) {
                throw OrchestrationChecks.invalid();
            }
        }
    }
}
