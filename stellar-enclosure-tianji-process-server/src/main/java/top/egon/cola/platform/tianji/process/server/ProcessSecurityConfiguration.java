package top.egon.cola.platform.tianji.process.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;
import top.egon.cola.platform.tianji.process.server.task.ApprovalConsumerRegistry;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationProperties;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationService;
import top.egon.cola.platform.tianji.process.server.security.TianjiApprovalVerificationAccess;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicProperties;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.worker.WorkerNativeAvailability;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxProperties;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistry;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessAgentBridgePoller;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import java.time.Clock;
import java.util.List;

/** The process API is opt-in and requires both identity and authorization filters. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProcessSecurityConfiguration {
    @Bean
    SecurityFilterChain processSecurityFilterChain(HttpSecurity http, Environment environment,
            ObjectProvider<IdpBearerAuthenticationFilter> identityFilters,
            ObjectProvider<Rbac3BearerAuthenticationFilter> authorizationFilters,
            ObjectProvider<PermissionExecutionGuard> policies) throws Exception {
        boolean enabled = environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false);
        boolean workerEnabled = environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false);
        boolean inboxEnabled = environment.getProperty("stellar.tianji.process.agent-results.enabled", Boolean.class, false);
        boolean tasksEnabled = environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false);
        boolean taskPublicationEnabled = environment.getProperty("stellar.tianji.process.human-tasks.publish-enabled", Boolean.class, false);
        boolean approvalVerificationEnabled = environment.getProperty("stellar.tianji.process.approval-verification.enabled", Boolean.class, false);
        if (workerEnabled && !enabled) throw new IllegalStateException("Worker API requires the Process API security boundary");
        if (inboxEnabled && !enabled) throw new IllegalStateException("Agent result Inbox requires the Process API security boundary");
        if (tasksEnabled && !enabled) throw new IllegalStateException("Human task API requires the Process API security boundary");
        if (taskPublicationEnabled && !tasksEnabled) throw new IllegalStateException("Governed task publication requires the human task API");
        if (approvalVerificationEnabled && !enabled) throw new IllegalStateException("Approval verification requires the Process API security boundary");
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (enabled) authorize.requestMatchers("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                            "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                            "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**").authenticated();
                    if (workerEnabled) authorize.requestMatchers("/api/tianji/v1/worker-tasks", "/api/tianji/v1/worker-tasks/**").authenticated();
                    if (inboxEnabled) authorize.requestMatchers("/api/tianji/v1/agent-result-inbox").authenticated();
                    if (tasksEnabled) authorize.requestMatchers("/api/tianji/v1/human-tasks", "/api/tianji/v1/human-tasks/**").authenticated();
                    if (approvalVerificationEnabled) authorize.requestMatchers(ApprovalClient.PATH).authenticated();
                    authorize.anyRequest().denyAll();
                });
        if (enabled) {
            IdpBearerAuthenticationFilter identity = identityFilters.getIfAvailable();
            Rbac3BearerAuthenticationFilter authorization = authorizationFilters.getIfAvailable();
            if (identity == null || authorization == null || policies.getIfAvailable() == null
                    || environment.getProperty("egon.cola.platform.tianquan.shoubing.register-filter", Boolean.class, true)
                    || environment.getProperty("egon.cola.platform.tianquan.jianshen.register-filter", Boolean.class, true)) {
                throw new IllegalStateException("Tianji Process API requires both identity and permission filters in its security chain");
            }
            http.exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, failure) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write("{\"code\":\"TIANJI_UNAUTHENTICATED\"}");
                    })
                    .accessDeniedHandler((request, response, failure) -> {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write("{\"code\":\"TIANJI_PERMISSION_DENIED\"}");
                    }));
            http.addFilterBefore(identity, AnonymousAuthenticationFilter.class);
            http.addFilterBefore(new RequestContextFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(new VerifiedProcessIssuerFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(authorization, VerifiedProcessIssuerFilter.class);
        }
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(IdpEndpointAuthenticationPolicy.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    IdpEndpointAuthenticationPolicy processEndpointAuthenticationPolicy() {
        return new IdpEndpointAuthenticationPolicy(List.of("/actuator/health", "/actuator/health/**"),
                List.of("/api/tianji/v1/worker-tasks", "/api/tianji/v1/worker-tasks/**", "/api/tianji/v1/agent-result-inbox", ApprovalClient.PATH),
                List.of("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                        "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                        "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**"), true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    TianjiProcessAccess tianjiProcessAccess(PermissionExecutionGuard permissions) {
        return new TianjiProcessAccess(permissions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(ProcessModelValidator.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessModelValidator processModelValidator() { return new ProcessModelValidator(); }

    @Bean
    @ConditionalOnMissingBean(ProcessApiSchemaVerifier.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessApiSchemaVerifier processApiSchemaVerifier(ProcessEngine engine) {
        var process = processConfiguration(engine);
        var source = process.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        var verifier = new ProcessApiSchemaVerifier(source, process.getDatabaseSchema());
        verifier.verify();
        return verifier;
    }

    @Bean
    @ConditionalOnMissingBean(ProcessReleaseService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessReleaseService processReleaseService(ProcessEngine process, DmnEngine dmn,
                                                 ProcessModelValidator validator, Environment environment,
                                                 ObjectProvider<WorkerTopicRegistry> registries,
                                                 ObjectProvider<ProcessAgentBridgePoller> bridges) {
        boolean decisionTasksEnabled = environment.getProperty("stellar.tianji.process.decision-tasks.enabled", Boolean.class, false);
        boolean workerEnabled = environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false);
        WorkerTopicRegistry registry = workerEnabled ? registries.getIfAvailable() : null;
        if (workerEnabled && registry == null) throw new IllegalStateException("Worker API requires an operator-owned topic registry");
        boolean bridgeEnabled = environment.getProperty("stellar.tianji.process.agent-bridge.enabled", Boolean.class, false);
        boolean humanPublicationEnabled = environment.getProperty("stellar.tianji.process.human-tasks.publish-enabled", Boolean.class, false);
        if (humanPublicationEnabled && !environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false)) throw new IllegalStateException("Governed task publication requires the human task API");
        return new ProcessReleaseService(processConfiguration(process), decisionConfiguration(dmn), validator, decisionTasksEnabled, registry,
                bridgeEnabled, () -> {
                    var bridge = bridges.getIfAvailable();
                    return bridge != null && bridge.isRunning();
                }, humanPublicationEnabled);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessExecutionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessExecutionService processExecutionService(ProcessEngine process, DmnEngine dmn,
                                                     ProcessModelValidator validator) {
        return new ProcessExecutionService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDecisionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessDecisionService processDecisionService(ProcessEngine process, DmnEngine dmn,
                                                   ProcessModelValidator validator) {
        return new ProcessDecisionService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "approval-verification.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(ApprovalVerificationProperties.class)
    static class ApprovalVerificationConfiguration {
        @Bean
        @ConditionalOnMissingBean(ApprovalConsumerRegistry.class)
        ApprovalConsumerRegistry approvalConsumerRegistry(ApprovalVerificationProperties properties) {
            if (properties.consumers().isEmpty()) throw new IllegalStateException("Approval verification requires registered consumers");
            return new ApprovalConsumerRegistry(properties.registrations());
        }
        @Bean
        @ConditionalOnMissingBean(HumanTaskReceiptStore.class)
        HumanTaskReceiptStore approvalReceiptStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw new IllegalStateException("Approval verification requires the owning Process JDBC manager");
            var store = new HumanTaskReceiptStore(manager.getDataSource(), configuration.getDatabaseSchema()); store.verifyStorage(); return store;
        }
        @Bean
        @ConditionalOnMissingBean(ApprovalVerificationService.class)
        ApprovalVerificationService approvalVerificationService(ProcessEngine process, HumanTaskReceiptStore store, ApprovalConsumerRegistry consumers) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager)) throw new IllegalStateException("Approval verification requires the owning Process JDBC manager");
            return new ApprovalVerificationService(manager.getDataSource(), manager, store, consumers);
        }
        @Bean
        @ConditionalOnMissingBean(TianjiApprovalVerificationAccess.class)
        TianjiApprovalVerificationAccess tianjiApprovalVerificationAccess(TianjiProcessAccess access) { return new TianjiApprovalVerificationAccess(access, Clock.systemUTC()); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "human-tasks.enabled"}, havingValue = "true")
    static class HumanTaskConfiguration {
        @Bean
        @ConditionalOnMissingBean(HumanTaskReceiptStore.class)
        HumanTaskReceiptStore humanTaskReceiptStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw new IllegalStateException("Human task API requires the owning Process JDBC manager");
            var store = new HumanTaskReceiptStore(manager.getDataSource(), configuration.getDatabaseSchema()); store.verifyStorage(); return store;
        }

        @Bean
        @ConditionalOnMissingBean(FlowableHumanTaskService.class)
        FlowableHumanTaskService flowableHumanTaskService(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts) {
            return new FlowableHumanTaskService(processConfiguration(process), decisionConfiguration(dmn), validator, receipts);
        }

        @Bean
        @ConditionalOnMissingBean(TianjiHumanTaskAccess.class)
        TianjiHumanTaskAccess tianjiHumanTaskAccess(TianjiProcessAccess access) { return new TianjiHumanTaskAccess(access, Clock.systemUTC()); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "worker.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(WorkerTopicProperties.class)
    static class WorkerConfiguration {
        @Bean
        @ConditionalOnMissingBean(WorkerTopicRegistry.class)
        WorkerTopicRegistry workerTopicRegistry(WorkerTopicProperties properties) {
            return new WorkerTopicRegistry(properties.registrations());
        }

        @Bean
        @ConditionalOnMissingBean(FlowableWorkerService.class)
        FlowableWorkerService flowableWorkerService(ProcessEngine process, DmnEngine dmn,
                                                     ProcessModelValidator validator, WorkerTopicRegistry registry) {
            var configuration = processConfiguration(process);
            WorkerNativeAvailability.requireConfigured(configuration);
            return new FlowableWorkerService(configuration, decisionConfiguration(dmn), validator, registry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "agent-results.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(AgentResultInboxProperties.class)
    static class AgentResultConfiguration {
        @Bean
        @ConditionalOnMissingBean(AgentResultProducerRegistry.class)
        AgentResultProducerRegistry agentResultProducerRegistry(AgentResultInboxProperties properties) {
            return new AgentResultProducerRegistry(properties.registrations());
        }

        @Bean
        @ConditionalOnMissingBean(AgentDispatchStore.class)
        AgentDispatchStore agentDispatchStore(ProcessEngine engine) {
            var configuration = processConfiguration(engine);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) {
                throw new IllegalStateException("Agent result Inbox requires shared JDBC storage");
            }
            return new AgentDispatchStore(manager.getDataSource(), manager, configuration.getDatabaseSchema());
        }

        @Bean
        @ConditionalOnMissingBean(AgentResultInbox.class)
        AgentResultInbox agentResultInbox(ProcessEngine engine, AgentDispatchStore dispatches, AgentResultProducerRegistry producers,
                                           AgentResultInboxProperties properties) {
            var configuration = processConfiguration(engine);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) {
                throw new IllegalStateException("Agent result Inbox requires shared JDBC storage");
            }
            return new AgentResultInbox(manager.getDataSource(), manager, configuration.getDatabaseSchema(), dispatches, producers, properties.pendingRetention());
        }
    }

    private static SpringProcessEngineConfiguration processConfiguration(ProcessEngine engine) {
        if (!(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration configuration)) {
            throw new IllegalStateException("Process API requires the Spring Flowable process engine");
        }
        return configuration;
    }

    private static SpringDmnEngineConfiguration decisionConfiguration(DmnEngine engine) {
        if (!(engine.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration configuration)) {
            throw new IllegalStateException("Process API requires the Spring Flowable decision engine");
        }
        return configuration;
    }
}
