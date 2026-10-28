package top.egon.cola.platform.tianji.process.server.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Administrative configuration; declarations are grants, not worker-supplied registration claims. */
@ConfigurationProperties(prefix = "stellar.tianji.process.worker")
public record WorkerTopicProperties(List<WorkerTopicRegistry.Registration> registrations) {
    public WorkerTopicProperties {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
    }
}
