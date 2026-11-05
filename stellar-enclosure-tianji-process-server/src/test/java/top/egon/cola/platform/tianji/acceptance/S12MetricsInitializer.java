package top.egon.cola.platform.tianji.acceptance;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.net.URI;

/** Explicitly selected only by the isolated, artifact-locked S12 metrics fixture launch. */
public final class S12MetricsInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    public static final String LOG_PRIVACY_ENV = "TIANJI_S12_CAPTURE_CHILD_LOG";

    public static String logMarker(String role, boolean closing) {
        return "TIANJI_S12_PRIVATE_LOG_" + S12MetricsTelemetry.Role.valueOf(role).name()
                + (closing ? "_CLOSING" : "_READY");
    }

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
        if ("true".equals(System.getenv(LOG_PRIVACY_ENV))) {
            context.addApplicationListener(event -> {
                if (event.getSource() != context) return;
                if (event instanceof ContextRefreshedEvent) System.out.println(logMarker(role.name(), false));
                else if (event instanceof ContextClosedEvent) System.err.println(logMarker(role.name(), true));
            });
        }
    }
}
