package top.egon.cola.platform.tianji.agent.server.integration.process;

import jakarta.validation.Validator;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;

import java.time.Clock;

/** Optional bridge to the exact metadata and disclosure authority installed by the host. */
@Configuration(proxyBeanMethods = false)
public class TianjiAgentResultArtifactConfiguration {
    public static final String METADATA_AUTHORITY = "tianjiAgentArtifactMetadataAuthority";
    public static final String VERIFIER = "tianjiAgentResultArtifactVerifier";

    @Bean("tianjiAgentResultArtifactRegistration")
    static BeanFactoryPostProcessor tianjiAgentResultArtifactRegistration() {
        return factory -> {
            // Host @Bean methods have all been registered before an ordinary factory post-processor runs.
            if (!factory.containsBean(METADATA_AUTHORITY)) return;
            if (!(factory instanceof BeanDefinitionRegistry registry)
                    || !factory.isTypeMatch(METADATA_AUTHORITY, AgentResultArtifactVerifier.class)
                    || !factory.isSingleton(METADATA_AUTHORITY) || factory.containsBean(VERIFIER)) {
                throw new IllegalStateException("Invalid Agent result artifact authority registration");
            }
            var definition = new RootBeanDefinition(AgentResultArtifactVerifier.class);
            definition.setInstanceSupplier(() -> verifier(factory));
            definition.setDependsOn(METADATA_AUTHORITY, "defaultValidator");
            definition.setDestroyMethodName("close");
            registry.registerBeanDefinition(VERIFIER, definition);
        };
    }

    private static AgentResultArtifactVerifier verifier(ConfigurableListableBeanFactory factory) {
        return AgentResultArtifactVerifier.guarded(
                factory.getBean(METADATA_AUTHORITY, AgentResultArtifactVerifier.class),
                Clock.systemUTC(), factory.getBean("defaultValidator", Validator.class));
    }
}
