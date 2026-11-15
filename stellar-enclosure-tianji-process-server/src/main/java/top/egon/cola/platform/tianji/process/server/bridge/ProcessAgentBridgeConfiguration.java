package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import top.egon.cola.platform.tianji.client.HttpLinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerNativeAvailability;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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
@EnableConfigurationProperties({ProcessAgentBridgeProperties.class, ProcessAgentBridgeConfiguration.RuntimeProperties.class})
public class ProcessAgentBridgeConfiguration {
    @FunctionalInterface
    public interface TypedRouteProvider {
        AgentDispatchStore.TypedRoute route(long tenantId);
    }

    @Bean("automationAgentDispatchRoutes")
    @ConditionalOnMissingBean(name = "automationAgentDispatchRoutes")
    TypedRouteProvider automationAgentDispatchRoutes(RuntimeProperties settings) {
        var routes = new HashMap<Long, AgentDispatchStore.TypedRoute>();
        settings.bindings().forEach(binding -> {
            if (routes.putIfAbsent(binding.tenantId(), binding.route()) != null) throw invalid();
        });
        var verified = Map.copyOf(routes);
        return verified::get;
    }

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

    @Bean("agentResultCorrelationService")
    AgentResultCorrelationService agentResultCorrelationService(ProcessEngine engine, FlowableWorkerService workers,
            AgentResultInbox inbox, AgentDispatchStore dispatches, AgentResultProducerRegistry producers, ProcessAgentBridgeProperties properties,
            @Qualifier("automationInstanceGuardCommandInterceptor") ObjectProvider<AutomationInstanceGuardCommandInterceptor> guards,
            @Qualifier("automationReleaseStore") ObjectProvider<AutomationReleaseStore> releases,
            @Qualifier("processNodeExecutionStore") ObjectProvider<ProcessNodeExecutionStore> nodes,
            ObjectProvider<ProcessReleaseService> releaseServices, ObjectProvider<ProcessModelValidator> validators,
            ObjectProvider<DmnEngine> decisions) {
        inbox.requireBindings(dispatches, producers);
        var process = configuration(engine);
        var guard = guards.getIfAvailable();
        var releaseStore = releases.getIfAvailable();
        var nodeStore = nodes.getIfAvailable();
        if (guard == null && releaseStore == null && nodeStore == null) {
            return new AgentResultCorrelationService(process, workers, inbox, properties.inboxRetryDelay(), properties.inboxMaxFailures());
        }
        var releaseService = releaseServices.getIfAvailable();
        var validator = validators.getIfAvailable();
        var decisionEngine = decisions.getIfAvailable();
        if (guard == null || releaseStore == null || nodeStore == null || releaseService == null || validator == null
                || decisionEngine == null || !(decisionEngine.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration dmn)
                || !(process.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || !releaseService.usesStorage(process, manager.getDataSource(), manager)) throw invalid();
        var inspector = new FlowableAgentWaitInspector(process, releaseService, new VerifiedProcessModels(process, dmn, validator), validator);
        var typed = new AgentResultCorrelationService.TypedParticipantsVO(guard, releaseStore, releaseService, nodeStore, inspector);
        return new AgentResultCorrelationService(process, workers, inbox, properties.inboxRetryDelay(), properties.inboxMaxFailures(), typed);
    }

    @Bean(destroyMethod = "close")
    BridgeResources tianjiProcessAgentBridgeResources(ProcessEngine engine, FlowableWorkerService workers, AgentDispatchStore dispatches,
            WorkerTopicRegistry topics, AgentResultProducerRegistry producers, AgentResultInboxProperties inbox,
            ProcessAgentBridgeProperties properties, RuntimeProperties runtime,
            @Qualifier("automationAgentDispatchRoutes") TypedRouteProvider routes,
            @Qualifier("automationInstanceGuardCommandInterceptor") ObjectProvider<AutomationInstanceGuardCommandInterceptor> guards,
            IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        var process = configuration(engine);
        WorkerNativeAvailability.require(process);
        FlowableAgentWaitInspector.requireHistory(process);
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw invalid();
        workers.requireStorage(process, manager, topics);
        dispatches.requireStorage(manager.getDataSource(), manager, process.getDatabaseSchema());
        if (starter.getServiceClient() == null || properties.bindings().isEmpty() && runtime.bindings().isEmpty()
                || properties.bindings().size() + runtime.bindings().size() > 1000) throw invalid();
        validateLegacyBindings(properties, topics, producers, inbox, starter, issuers, oauth);
        if (!runtime.bindings().isEmpty() && guards.getIfAvailable() == null) throw invalid();
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
            for (var item : runtime.bindings()) {
                var route = item.route();
                var contexts = contexts(item.tenantId(), item.registrationId(), item.resourceUri(), starter);
                var requiredLease = item.httpTimeout().multipliedBy(2).plus(properties.acknowledgementReserve()).plus(properties.maxClockSkew());
                if (route.leaseDuration().compareTo(requiredLease) < 0 || !route.equals(routes.route(item.tenantId()))
                        || inbox.registrations().stream().noneMatch(value ->
                                value.owner().equals(route.actor()) && value.sourceId().equals(route.sourceId()))) throw invalid();
                requireTrustedAuthorization(item.issuer(), item.tenantId(), item.subjectId(), contexts, issuers, oauth);
                var client = new HttpLinkedAgentClient(item.baseUri(), resources.http, operation ->
                        oauth.authorizeBounded(contexts.get(operation), item.issuer().toASCIIString(), item.httpTimeout()).getTokenValue(),
                        item.httpTimeout(), item.maxTokenCalls());
                resources.clients.add(client);
                resources.workers.add(new AgentStartWorker(route, workers, dispatches, client));
            }
            return resources;
        } catch (RuntimeException failure) { resources.close(); throw failure; }
    }

    BridgeResources tianjiProcessAgentBridgeResources(ProcessEngine engine, FlowableWorkerService workers, AgentDispatchStore dispatches,
            WorkerTopicRegistry topics, AgentResultProducerRegistry producers, AgentResultInboxProperties inbox,
            ProcessAgentBridgeProperties properties, IdpStarterProperties starter, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        return tianjiProcessAgentBridgeResources(engine, workers, dispatches, topics, producers, inbox, properties,
                new RuntimeProperties(List.of()), ignored -> null, null, starter, issuers, oauth);
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
        validateLegacyBindings(properties, topics, producers, inbox, starter, issuers, oauth);
    }

    private static void validateLegacyBindings(ProcessAgentBridgeProperties properties, WorkerTopicRegistry topics,
            AgentResultProducerRegistry producers, AgentResultInboxProperties inbox, IdpStarterProperties starter,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
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
            requireTrustedAuthorization(item.issuer(), item.tenantId(), item.subjectId(), contexts(item, starter), issuers, oauth);
        }
        if (topics.registrations().stream().anyMatch(value -> value.internalAgentDispatch() && !covered.contains(value))) throw invalid();
    }

    private static Map<HttpLinkedAgentClient.Operation, IdpServiceTokenRequest> contexts(ProcessAgentBridgeProperties.Binding binding,
                                                                                      IdpStarterProperties starter) {
        return contexts(binding.tenantId(), binding.registrationId(), binding.resourceUri(), starter);
    }
    private static Map<HttpLinkedAgentClient.Operation, IdpServiceTokenRequest> contexts(long tenantId, String registration, URI resource,
                                                                                      IdpStarterProperties starter) {
        var app = starter.getServiceClient().getAppId();
        return Map.of(HttpLinkedAgentClient.Operation.SUBMIT, request(tenantId, registration, resource, app, LinkedAgentClient.SUBMIT_SCOPE),
                HttpLinkedAgentClient.Operation.RESOLVE, request(tenantId, registration, resource, app, LinkedAgentClient.RESOLVE_SCOPE),
                HttpLinkedAgentClient.Operation.CLOSE, request(tenantId, registration, resource, app, LinkedAgentClient.CLOSE_SCOPE));
    }
    private static IdpServiceTokenRequest request(long tenantId, String registration, URI resource, String app, String scope) {
        return new IdpServiceTokenRequest(registration, app, resource, ServiceTokenContext.TENANT, Long.toString(tenantId), Set.of(scope));
    }

    private static void requireTrustedAuthorization(URI issuer, long tenantId, String subject,
            Map<HttpLinkedAgentClient.Operation, IdpServiceTokenRequest> contexts, TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        String exactIssuer = issuer.toASCIIString();
        var trusted = issuers.findByIssuer(exactIssuer).orElseThrow(ProcessAgentBridgeConfiguration::invalid);
        if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || trusted.status() != TrustedIssuerRegistry.Status.ACTIVE
                || !Long.toString(tenantId).equals(trusted.tenantId())) throw invalid();
        for (var request : contexts.values()) {
            var key = oauth.authorizationKey(request);
            if (key == null || !key.issuer().equals(exactIssuer) || !key.clientId().equals(subject)
                    || !key.registrationId().equals(request.registrationId()) || !key.appId().equals(request.appId())
                    || !key.audience().equals(request.audience()) || key.context() != ServiceTokenContext.TENANT
                    || !key.tenantId().equals(request.tenantId()) || !key.scopes().equals(request.scopes())) throw invalid();
        }
    }
    private static SpringProcessEngineConfiguration configuration(ProcessEngine engine) {
        if (!(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration configuration)) throw invalid();
        return configuration;
    }

    @ConfigurationProperties(prefix = "stellar.tianji.process.agent-bridge.runtime-v2")
    public record RuntimeProperties(List<RuntimeBinding> bindings) {
        public RuntimeProperties {
            bindings = bindings == null ? List.of() : List.copyOf(bindings);
            if (bindings.size() > 1000) throw invalid();
        }
    }

    public record RuntimeBinding(long tenantId, URI issuer, String subjectId, String sourceId, Duration leaseDuration,
                                 Duration submissionTimeout, URI baseUri, URI resourceUri, String registrationId,
                                 Duration httpTimeout, int maxTokenCalls) {
        public RuntimeBinding {
            requireHttps(issuer, 2048);
            requireHttps(baseUri, 2048);
            requireHttps(resourceUri, 512);
            leaseDuration = leaseDuration == null ? Duration.ofSeconds(20) : leaseDuration;
            submissionTimeout = submissionTimeout == null ? Duration.ofMinutes(2) : submissionTimeout;
            httpTimeout = httpTimeout == null ? Duration.ofSeconds(3) : httpTimeout;
            maxTokenCalls = maxTokenCalls == 0 ? 4 : maxTokenCalls;
            if (registrationId == null || !registrationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || httpTimeout == null || httpTimeout.compareTo(Duration.ofMillis(100)) < 0
                    || httpTimeout.compareTo(Duration.ofSeconds(30)) > 0 || httpTimeout.toNanos() % 1_000_000 != 0
                    || maxTokenCalls < 1 || maxTokenCalls > 8) throw invalid();
            new AgentDispatchStore.TypedRoute(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId,
                    issuer.toASCIIString()), sourceId, leaseDuration, submissionTimeout);
        }

        public AgentDispatchStore.TypedRoute route() {
            return new AgentDispatchStore.TypedRoute(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId,
                    issuer.toASCIIString()), sourceId, leaseDuration, submissionTimeout);
        }

        @Override public String toString() { return "RuntimeAgentBridgeBinding[redacted]"; }

        private static void requireHttps(URI value, int maximum) {
            if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null
                    || value.getQuery() != null || value.getFragment() != null || !value.normalize().equals(value)
                    || !value.toASCIIString().equals(value.toString()) || value.toASCIIString().length() > maximum) throw invalid();
        }
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
