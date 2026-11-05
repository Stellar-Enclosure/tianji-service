package top.egon.cola.platform.tianji.acceptance;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import java.net.URI;

/** Explicitly named only by the private, artifact-locked S12 fixture launch. */
public final class S12ReceiveLinkInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    @Override
    public void initialize(GenericApplicationContext context) {
        var environment = context.getEnvironment();
        if (!"true".equals(environment.getProperty("tianji.expense.fixture.run-authorized"))
                || !"true".equals(environment.getProperty("tianji.expense.fixture.s12-receive-link"))) {
            throw new IllegalStateException("Explicit S12 receive-link fixture is required");
        }
        var role = S12ReceiveLinkTelemetry.Role.valueOf(environment.getRequiredProperty("tianji.expense.fixture.role"));
        URI endpoint = URI.create(environment.getRequiredProperty("management.otlp.tracing.endpoint"));
        var bean = BeanDefinitionBuilder.genericBeanDefinition(S12ReceiveLinkTelemetry.class,
                () -> S12ReceiveLinkTelemetry.open(role, endpoint)).setDestroyMethodName("close").getBeanDefinition();
        context.registerBeanDefinition("tianjiS12ReceiveLinkTelemetry", bean);
    }
}
