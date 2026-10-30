package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import top.egon.cola.platform.tianji.client.HttpLinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerNativeAvailability;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Enables bounded local orchestration only after identity, native recovery and durable reception are configured. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.process.agent-bridge", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ProcessAgentBridgeProperties.class)
public class ProcessAgentBridgeConfiguration {
    @Bean
    static BeanFactoryPostProcessor processAgentBridgePrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.process.agent-results.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Process Agent bridge requires secured Worker API and durable Agent result reception");
            }
        };
    }

    @Bean
    AgentResultCorrelationService agentResultCorrelationService(ProcessEngine engine, FlowableWorkerService workers,
            AgentResultInbox inbox, AgentDispatchStore dispatches, AgentResultProducerRegistry producers, ProcessAgentBridgeProperties properties) {
        inbox.requireBindings(dispatches, producers);
        return new AgentResultCorrelationService(configuration(engine), workers, inbox, properties.inboxRetryDelay(), properties.inboxMaxFailures());
    }

    @Bean(destroyMethod = "close")
    BridgeResources tianjiProcessAgentBridgeResources(ProcessEngine engine, FlowableWorkerService workers, AgentDispatchStore dispatches,
            WorkerTopicRegistry topics, AgentResultProducerRegistry producers, AgentResultInboxProperties inbox,
            ProcessAgentBridgeProperties properties, IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        var process = configuration(engine);
        WorkerNativeAvailability.require(process);
        FlowableAgentWaitInspector.requireHistory(process);
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw invalid();
        workers.requireStorage(process, manager, topics);
        dispatches.requireStorage(manager.getDataSource(), manager, process.getDatabaseSchema());
        validateBindings(properties, topics, producers, inbox, starter, issuers, oauth);
        var resources = new BridgeResources(properties);
        try {
            for (var item : properties.bindings()) {
                var contexts = contexts(item, starter);
                var client = new HttpLinkedAgentClient(item.baseUri(), resources.http,
                        operation -> oauth.authorizeBounded(contexts.get(operation), item.issuer().toASCIIString(), item.httpTimeout()).getTokenValue(),
                        item.httpTimeout(), item.maxTokenCalls());
                resources.clients.add(client);
                resources.workers.add(new AgentStartWorker(item.dispatch(), workers, dispatches, client));
            }
            return resources;
        } catch (RuntimeException failure) { resources.close(); throw failure; }
    }

    @Bean(destroyMethod = "close")
    ProcessAgentBridgePoller tianjiProcessAgentBridgePoller(BridgeResources resources, AgentResultProducerRegistry producers,
            AgentResultInboxProperties inbox, AgentResultCorrelationService results, ProcessAgentBridgeProperties properties) {
        var registrations = inbox.registrations();
        registrations.forEach(producers::require);
        return new ProcessAgentBridgePoller(resources.workers, registrations, results,
                new ConcurrentTaskScheduler(resources.dispatchScheduler), new ConcurrentTaskScheduler(resources.maintenanceScheduler),
                resources.dispatchExecutor, resources.maintenanceExecutor, properties.dispatchConcurrency(), properties.maintenanceConcurrency(),
                properties.inboxBatchSize(), properties.pollInterval(), properties.shutdownTimeout(), properties.dispatchEnabled());
    }

    static void validateBindings(ProcessAgentBridgeProperties properties, WorkerTopicRegistry topics, AgentResultProducerRegistry producers,
            AgentResultInboxProperties inbox, IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        if (properties.bindings().isEmpty() || starter.getServiceClient() == null) throw invalid();
        var receptions = inbox.registrations();
        receptions.forEach(producers::require);
        Set<WorkerTopicRegistry.Registration> covered = new HashSet<>();
        for (var item : properties.bindings()) {
            var binding = item.dispatch();
            var registration = topics.require(binding.actor(), binding.topic(), binding.schemaVersion());
            var requiredLease = item.httpTimeout().multipliedBy(2).plus(properties.acknowledgementReserve()).plus(properties.maxClockSkew());
            if (!registration.internalAgentDispatch() || registration.leaseDuration().compareTo(requiredLease) < 0
                    || registration.leaseDuration().compareTo(binding.submissionTimeout()) >= 0
                    || receptions.stream().noneMatch(value -> value.owner().equals(binding.actor()) && value.sourceId().equals(binding.sourceId()))) throw invalid();
            covered.add(registration);
            var trusted = issuers.findByIssuer(item.issuer().toASCIIString()).orElseThrow(ProcessAgentBridgeConfiguration::invalid);
            if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || trusted.status() != TrustedIssuerRegistry.Status.ACTIVE
                    || !Long.toString(item.tenantId()).equals(trusted.tenantId())) throw invalid();
            for (var request : contexts(item, starter).values()) {
                var key = oauth.authorizationKey(request);
                if (key == null || !key.issuer().equals(item.issuer().toASCIIString()) || !key.clientId().equals(item.subjectId())
                        || !key.registrationId().equals(request.registrationId()) || !key.appId().equals(request.appId())
                        || !key.audience().equals(request.audience()) || key.context() != ServiceTokenContext.TENANT
                        || !key.tenantId().equals(request.tenantId()) || !key.scopes().equals(request.scopes())) throw invalid();
            }
        }
        if (topics.registrations().stream().anyMatch(value -> value.internalAgentDispatch() && !covered.contains(value))) throw invalid();
    }

    private static Map<HttpLinkedAgentClient.Operation, IdpServiceTokenRequest> contexts(ProcessAgentBridgeProperties.Binding binding,
                                                                                      IdpStarterProperties starter) {
        return Map.of(HttpLinkedAgentClient.Operation.SUBMIT, request(binding, starter, LinkedAgentClient.SUBMIT_SCOPE),
                HttpLinkedAgentClient.Operation.RESOLVE, request(binding, starter, LinkedAgentClient.RESOLVE_SCOPE),
                HttpLinkedAgentClient.Operation.CLOSE, request(binding, starter, LinkedAgentClient.CLOSE_SCOPE));
    }
    private static IdpServiceTokenRequest request(ProcessAgentBridgeProperties.Binding binding, IdpStarterProperties starter, String scope) {
        return new IdpServiceTokenRequest(binding.registrationId(), starter.getServiceClient().getAppId(), binding.resourceUri(),
                ServiceTokenContext.TENANT, Long.toString(binding.tenantId()), Set.of(scope));
    }
    private static SpringProcessEngineConfiguration configuration(ProcessEngine engine) {
        if (!(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration configuration)) throw invalid();
        return configuration;
    }

    static final class BridgeResources implements AutoCloseable {
        private final ScheduledThreadPoolExecutor dispatchScheduler = scheduler("process-agent-dispatch-clock-");
        private final ScheduledThreadPoolExecutor maintenanceScheduler = scheduler("process-agent-maintenance-clock-");
        private final ThreadPoolExecutor dispatchExecutor;
        private final ThreadPoolExecutor maintenanceExecutor;
        private final HttpClient http;
        private final List<HttpLinkedAgentClient> clients = new ArrayList<>();
        private final List<AgentStartWorker> workers = new ArrayList<>();

        BridgeResources(ProcessAgentBridgeProperties properties) {
            dispatchExecutor = executor(properties.dispatchConcurrency(), "process-agent-dispatch-");
            maintenanceExecutor = executor(properties.maintenanceConcurrency(), "process-agent-maintenance-");
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        }
        private static ScheduledThreadPoolExecutor scheduler(String name) {
            var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false).name(name, 0).factory());
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }
        private static ThreadPoolExecutor executor(int size, String name) {
            var executor = new ThreadPoolExecutor(size, size, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                    Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false).name(name, 0).factory(), new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
        @Override public void close() {
            dispatchScheduler.shutdownNow(); maintenanceScheduler.shutdownNow();
            dispatchExecutor.shutdownNow(); maintenanceExecutor.shutdownNow();
            clients.forEach(HttpLinkedAgentClient::close); http.shutdownNow();
        }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Process Agent bridge bindings are invalid"); }
}
