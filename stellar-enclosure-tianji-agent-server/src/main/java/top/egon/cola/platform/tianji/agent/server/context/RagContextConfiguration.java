package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import top.egon.cola.component.rag.api.RagRetrievalService;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextAuthority;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextProvider;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Requires an explicit business authority; enabling retrieval never invents a source grant or an embedding endpoint. */
@Configuration(value = "ragContextConfiguration", proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.context.rag", name = "enabled", havingValue = "true")
@Import(RagContextConfiguration.OptionalRagConfiguration.class)
public class RagContextConfiguration {
    private static final String RAG_SERVICE = "top.egon.cola.component.rag.api.RagRetrievalService";

    @Bean(name = "ragContextPrerequisites")
    static BeanFactoryPostProcessor ragContextPrerequisites(Environment environment) {
        return beanFactory -> {
            if (!ClassUtils.isPresent(RAG_SERVICE, beanFactory.getBeanClassLoader())) {
                throw new IllegalStateException("RAG context requires the optional RAG component");
            }
            if (!environment.getProperty("stellar.tianji.agent.runtime.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.context.snapshots.enabled", Boolean.class, false)) {
                throw new IllegalStateException("RAG context sources require the governed durable Agent runtime and context snapshots");
            }
        };
    }

    @ConditionalOnClass(name = RAG_SERVICE)
    @EnableConfigurationProperties(RagContextProperties.class)
    static class OptionalRagConfiguration {
        @Bean(name = "tianjiRagContextProvider", destroyMethod = "close")
        RagContextProvider tianjiRagContextProvider(RagContextProperties properties, RagRetrievalService retrieval,
                @Qualifier("tianjiRagSourceAuthority") RagContextAuthority authority, TrustedIssuerRegistry issuers) {
            Map<String, RagContextProperties.Binding> bindings = properties.bindings().stream()
                    .collect(Collectors.toUnmodifiableMap(RagContextProperties.Binding::id, Function.identity()));
            bindings.values().forEach(binding -> requireIssuer(binding, issuers));
            var guarded = new RagContextAuthority() {
                @Override public Scope authorize(ContextProvider.Request request, RagContextProvider.Binding binding) {
                    requireIssuer(bindings.get(binding.id()), issuers);
                    var scope = authority.authorize(request, binding);
                    requireIssuer(bindings.get(binding.id()), issuers);
                    return scope;
                }

                @Override public void revalidate(ContextProvider.Request request, RagContextProvider.Binding binding, Scope scope, List<Source> sources) {
                    requireIssuer(bindings.get(binding.id()), issuers);
                    authority.revalidate(request, binding, scope, sources);
                    requireIssuer(bindings.get(binding.id()), issuers);
                }

                @Override public String policyFingerprint() { return authority.policyFingerprint(); }
            };
            return new RagContextProvider(retrieval, guarded, properties.bindings().stream()
                    .map(RagContextProperties.Binding::runtime).toList(), properties.maxConcurrent(), Clock.systemUTC());
        }
    }

    private static void requireIssuer(RagContextProperties.Binding binding, TrustedIssuerRegistry issuers) {
        if (binding == null) throw unavailable();
        var trusted = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(RagContextConfiguration::unavailable);
        if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(binding.tenantId()).equals(trusted.tenantId())) throw unavailable();
    }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "RAG context issuer binding is unavailable"); }
}
