package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkReadOnlyDecisionToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolAuthorityProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Explicit host assembly for the fixed read-only DMN tool chain. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.tools.read-only-decision", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiReadOnlyDecisionToolProperties.class)
public class TianjiReadOnlyDecisionToolConfiguration {
    @Bean("toolInvocationStore")
    ToolInvocationStore toolInvocationStore(DataSource source, AgentLedgerService ledger,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization, Environment environment) {
        requirePrerequisites(environment);
        var store = new ToolInvocationStore(source, ledger, capabilities, authorization); store.verifyStorage(); return store;
    }

    @Bean("toolInvocationRecorder")
    ToolInvocationRecorder toolInvocationRecorder(DataSource source, AgentLedgerService ledger,
            @Qualifier("toolInvocationStore") ToolInvocationStore preparations,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization, Environment environment) {
        requirePrerequisites(environment);
        var recorder = new ToolInvocationRecorder(source, ledger, preparations, capabilities, authorization); recorder.verifyStorage(); return recorder;
    }

    @Bean("tianjiFixedDecisionToolBindings")
    TianjiFixedDecisionToolBindings tianjiFixedDecisionToolBindings(TianjiToolAuthorityProperties authority,
            TianjiReadOnlyDecisionToolProperties properties,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grantBindings, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!authority.enabled() || !properties.enabled()) throw unavailable();
            var fixed = new TianjiFixedDecisionToolBindings(authority.descriptors(), properties.bindings());
            var registered = new HashMap<DefinitionResourceRef, ToolDescriptor>();
            for (var descriptor : authority.descriptors()) registered.put(descriptor.reference(), descriptor);
            var actual = new HashSet<List<Object>>();
            for (var binding : grantBindings.bindings()) {
                var descriptor = registered.get(binding.tool()); if (descriptor == null) throw unavailable();
                if (descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY && DecisionClient.EXECUTE_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                        && !actual.add(slot(binding))) throw unavailable();
            }
            var configured = new HashSet<List<Object>>();
            for (var binding : fixed.bindings()) if (!configured.add(slot(binding))) throw unavailable();
            if (!actual.equals(configured)) throw unavailable();
            return fixed;
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    @Bean(name = "tianjiFixedDecisionToolHttpClient", destroyMethod = "shutdownNow")
    HttpClient tianjiFixedDecisionToolHttpClient(Environment environment) {
        requirePrerequisites(environment);
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Bean(name = "tianjiFixedDecisionToolTransport", destroyMethod = "close")
    TianjiFixedDecisionToolTransport tianjiFixedDecisionToolTransport(
            @Qualifier("tianjiFixedDecisionToolBindings") TianjiFixedDecisionToolBindings bindings,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("tianjiFixedDecisionToolHttpClient") HttpClient http,
            TianjiReadOnlyDecisionToolProperties properties, Environment environment) {
        requirePrerequisites(environment);
        if (!properties.enabled()) throw unavailable();
        return new TianjiFixedDecisionToolTransport(bindings, oauth, authentication, converter, issuers, http, properties.concurrency());
    }

    @Bean("tianjiAdkReadOnlyDecisionTools")
    @Lazy
    AdkReadOnlyDecisionToolBridge tianjiAdkReadOnlyDecisionTools(
            @Qualifier("toolInvocationRecorder") ToolInvocationRecorder recorder,
            @Qualifier("tianjiFixedDecisionToolTransport") TianjiFixedDecisionToolTransport transport,
            TianjiToolAuthorityProperties authority,
            @Qualifier("tianjiFixedDecisionToolBindings") TianjiFixedDecisionToolBindings bindings,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grantBindings,
            ObjectProvider<OpenTelemetry> telemetry, Environment environment) {
        requirePrerequisites(environment);
        var references = new HashSet<DefinitionResourceRef>(); bindings.bindings().forEach(binding -> references.add(binding.tool()));
        var selected = authority.descriptors().stream().filter(descriptor -> references.contains(descriptor.reference())).toList();
        if (!authority.enabled() || selected.size() != references.size()) throw unavailable();
        telemetry.getIfAvailable();
        return new AdkReadOnlyDecisionToolBridge(recorder, transport, selected,
                material -> grantBindings.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList());
    }

    @Bean("tianjiReadOnlyToolRuntimeChainVerifier")
    @Lazy(false)
    SmartInitializingSingleton tianjiReadOnlyToolRuntimeChainVerifier(Environment environment, DataSource source,
            AgentLedgerService ledger, @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grantBindings,
            @Qualifier("toolInvocationStore") ToolInvocationStore preparations,
            @Qualifier("toolInvocationRecorder") ToolInvocationRecorder recorder,
            @Qualifier("tianjiFixedDecisionToolBindings") TianjiFixedDecisionToolBindings bindings,
            @Qualifier("tianjiFixedDecisionToolHttpClient") HttpClient http,
            @Qualifier("tianjiFixedDecisionToolTransport") TianjiFixedDecisionToolTransport transport,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge> bridges,
            @Qualifier("tianjiAdkAgentRunExecutor") ObjectProvider<AdkAgentRunExecutor> executors,
            @Qualifier("tianjiExecutionGrantAuthorizer") TianjiExecutionGrantAuthorizer runAuthorization) {
        return () -> {
            requirePrerequisites(environment);
            if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)
                    || !preparations.usesDataSource(source) || !preparations.usesLedger(ledger)
                    || !preparations.usesCapabilities(capabilities) || !preparations.usesAuthorization(authorization)
                    || !recorder.usesDataSource(source) || !recorder.usesLedger(ledger) || !recorder.usesPreparations(preparations)
                    || !recorder.usesCapabilities(capabilities) || !recorder.usesAuthorization(authorization)
                    || !authorization.usesLedger(ledger) || !authorization.usesBindings(grantBindings) || !authorization.usesIssuerRegistry(issuers)
                    || !runAuthorization.usesToolAuthorization(authorization)
                    || !transport.usesBindings(bindings) || !transport.usesOAuth(oauth) || !transport.usesAuthentication(authentication)
                    || !transport.usesConverter(converter) || !transport.usesIssuerRegistry(issuers) || !transport.usesHttpClient(http)
                    || http.followRedirects() != HttpClient.Redirect.NEVER) throw unavailable();
            var bridge = bridges.getObject(); var executor = executors.getObject();
            if (bridge == null || executor == null || !bridge.usesRecorder(recorder) || !bridge.usesTransport(transport)
                    || !executor.usesLedger(ledger) || !executor.usesReadOnlyDecisionTools(bridge)
                    || !executor.usesAuthorization(runAuthorization)) throw unavailable();
        };
    }

    private static List<Object> slot(TianjiToolGrantBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.issuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionSha256(), binding.tool());
    }
    private static List<Object> slot(TianjiFixedDecisionToolBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
    }
    private static void requirePrerequisites(Environment environment) {
        if (environment.getProperty("stellar.tianji.agent.tools.governed-process.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_TOOL_RUNTIME_MODE_CONFLICT");
        }
        for (String feature : new String[]{"api", "storage", "execution-grants", "execution-authorization", "runtime", "tools.authority"}) {
            if (!environment.getProperty("stellar.tianji.agent." + feature + ".enabled", Boolean.class, false)) {
                throw new IllegalStateException("TIANJI_READ_ONLY_TOOL_PREREQUISITES_REQUIRED");
            }
        }
    }

    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_READ_ONLY_TOOL_RUNTIME_UNAVAILABLE"); }
}
