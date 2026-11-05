package top.egon.cola.platform.tianji.acceptance;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import java.net.URI;

/** Explicitly selected only by the isolated, artifact-locked S12 metrics fixture launch. */
public final class S12MetricsInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    @Override
    public void initialize(GenericApplicationContext context) {
        var environment = context.getEnvironment();
        String prefix = "tianji.expense.fixture.";
        if (!"true".equals(environment.getProperty(prefix + "run-authorized"))
                || !"true".equals(environment.getProperty(prefix + "s12-metrics"))
                || "true".equals(environment.getProperty(prefix + "s12-receive-link"))) {
            throw new IllegalStateException("Explicit S12 metrics fixture is required");
        }
        var role = S12MetricsTelemetry.Role.valueOf(environment.getRequiredProperty(prefix + "role"));
        var endpoint = S12MetricsTelemetry.requireEndpoint(URI.create(environment.getRequiredProperty(prefix + "s12-metrics-endpoint")));
        var bean = BeanDefinitionBuilder.genericBeanDefinition(S12MetricsTelemetry.class,
                () -> new S12MetricsTelemetry(role, endpoint)).setDestroyMethodName("close").getBeanDefinition();
        bean.setPrimary(true);
        context.registerBeanDefinition("tianjiS12MetricsRegistry", bean);
    }
}
