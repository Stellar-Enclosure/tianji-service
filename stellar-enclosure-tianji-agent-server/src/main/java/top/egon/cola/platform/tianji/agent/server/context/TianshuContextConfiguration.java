package top.egon.cola.platform.tianji.agent.server.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.rpc.context.identity.RpcProcessIdentity;
import top.egon.cola.component.rpc.tianshu.client.DdcRpcClientFactory;
import top.egon.cola.component.rpc.tianshu.client.DdcRpcClientHandle;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns explicit read-only Tianshu clients without enabling configuration refresh, registration or resource authoring. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.context.tianshu", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianshuContextProperties.class)
public class TianshuContextConfiguration {
    @Bean
    static BeanFactoryPostProcessor tianshuContextPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.runtime.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Tianshu context sources require the governed durable Agent runtime");
            }
        };
    }

    @Bean(destroyMethod = "close")
    OwnedClients tianjiTianshuContextClients(TianshuContextProperties properties, Environment environment, TrustedIssuerRegistry issuers) {
        if (properties.connections().isEmpty() || properties.bindings().isEmpty()) throw invalid();
        properties.bindings().forEach(binding -> TianshuContextProvider.requireIssuer(binding, issuers));
        var owned = new OwnedClients();
        String application = environment.getProperty("spring.application.name", "tianji-agent-server");
        String host = environment.getProperty("egon.cola.component.rpc.identity.host", "127.0.0.1");
        String instance = environment.getProperty("egon.cola.component.rpc.identity.instance-id", "tianji-context-" + UUID.randomUUID());
        try {
            for (var connection : properties.connections()) {
                var selected = properties.bindings().stream().filter(binding -> binding.connectionId().equals(connection.id())).toList();
                if (selected.isEmpty()) throw invalid();
                var scope = connection.scope();
                var settings = new DdcProperties(); settings.setBizCode(scope.bizCode()); settings.setEnv(scope.env()); settings.setAppCode(scope.appCode());
                var tenant = new DdcTenantProperties(); tenant.setTenantId(scope.tenantId());
                var factory = new DdcRpcClientFactory(connection.rpc(), settings, tenant,
                        new RpcProcessIdentity(application, scope.env(), host, ProcessHandle.current().pid(), instance));
                DdcPromptClient prompt = null;
                DdcSkillClient skill = null;
                if (selected.stream().anyMatch(binding -> binding.kind() == ContextProvider.Kind.PROMPT)) {
                    var handle = factory.promptClient(); owned.handles.add(handle); prompt = handle.client();
                }
                if (selected.stream().anyMatch(binding -> binding.kind() == ContextProvider.Kind.SKILL)) {
                    var handle = factory.skillClient(); owned.handles.add(handle); skill = handle.client();
                }
                owned.ports.put(connection.id(), new TianshuContextProvider.Ports(scope, prompt, skill, connection.rpc().getDefaultTimeout(), connection.maxConcurrent()));
            }
            return owned;
        } catch (RuntimeException failure) { owned.close(); throw failure; }
    }

    @Bean
    TianshuContextProvider tianjiTianshuContextProvider(TianshuContextProperties properties, OwnedClients clients, TrustedIssuerRegistry issuers) {
        return new TianshuContextProvider(properties, clients.ports, issuers, Clock.systemUTC());
    }

    @Bean
    DefaultContextAssembler tianjiContextAssembler(TianshuContextProvider provider) {
        return new DefaultContextAssembler(List.of(provider));
    }

    @Bean
    SmartInitializingSingleton tianshuRpcRequestIdGuard(OwnedClients clients) {
        return () -> {
            // RPC uses the existing process-wide engine; this guard neither selects nor initializes a worker ID.
            try { SnowflakeIdGenerator.nextId(); }
            catch (RuntimeException failure) { throw new IllegalStateException("Tianshu context RPC requires an explicitly initialized request ID engine"); }
        };
    }

    static final class OwnedClients implements AutoCloseable {
        private static final Logger log = LoggerFactory.getLogger(OwnedClients.class);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final List<DdcRpcClientHandle<?>> handles = new ArrayList<>();
        private final Map<String, TianshuContextProvider.Ports> ports = new HashMap<>();
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            List<Thread> closers = new ArrayList<>();
            for (var handle : handles) {
                var closer = Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-tianshu-close").unstarted(() -> {
                    try { handle.close(); }
                    catch (RuntimeException failure) { log.warn("Tianshu context client close failed: type={}", failure.getClass().getName()); }
                });
                closers.add(closer); closer.start();
            }
            for (var closer : closers) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { closer.join(Duration.ofNanos(remaining)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            for (var closer : closers) if (closer.isAlive()) closer.interrupt();
        }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Tianshu context source connections are invalid"); }
}
