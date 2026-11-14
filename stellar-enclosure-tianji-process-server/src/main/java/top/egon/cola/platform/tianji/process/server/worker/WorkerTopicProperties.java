package top.egon.cola.platform.tianji.process.server.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import jakarta.validation.Valid;

import java.util.List;

/** Administrative configuration; declarations are grants, not worker-supplied registration claims. */
@ConfigurationProperties(prefix = "stellar.tianji.process.worker")
public record WorkerTopicProperties(List<WorkerTopicRegistry.Registration> registrations,
                                    @Valid List<WorkerTopicRegistry.AutomationRegistrationVO> automationRegistrations) {
    public WorkerTopicProperties(List<WorkerTopicRegistry.Registration> registrations) {
        this(registrations, List.of());
    }

    @ConstructorBinding
    public WorkerTopicProperties {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
        automationRegistrations = automationRegistrations == null ? List.of() : List.copyOf(automationRegistrations);
    }
}
