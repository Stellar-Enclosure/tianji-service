package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.AgentRuntimeToolExecutionService;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextResolver;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.ApprovalReceiptResolver;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.agent.server.integration.mcp.TianjiFixedReadOnlyMcpToolBindings;
import top.egon.cola.platform.tianji.agent.server.integration.mcp.TianjiFixedReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolAuthorityProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantBindings;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit host assembly for governed fixed Decision, MCP and approved or idempotent BPMN START tools. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.tools.governed-process", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiGovernedProcessToolProperties.class)
public class TianjiGovernedProcessToolConfiguration {
    @Bean("tianjiRuntimeToolExecutionService")
    @Lazy
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime-v2", name = "enabled", havingValue = "true")
    AgentRuntimeToolExecutionService tianjiRuntimeToolExecutionService(DataSource source, AgentLedgerService ledger,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("toolActionIntentStore") ToolActionIntentStore actions,
            @Qualifier("governedProcessToolResultAccessStore") ToolResultAccessStore results,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants,
            @Qualifier("tianjiGovernedProcessDecisionTransport") TianjiFixedDecisionToolTransport decision,
            @Qualifier("tianjiGovernedProcessStartTransport") TianjiFixedBpmnStartToolTransport start,
            @Qualifier("tianjiGovernedProcessMcpTransport") ObjectProvider<TianjiFixedReadOnlyMcpToolTransport> mcp,
            @Qualifier("tianjiGovernedProcessApprovalVerifier") ObjectProvider<TianjiFixedToolApprovalVerifier> verifier,
            @Qualifier("tianjiRuntimeToolActionIdentity") ObjectProvider<AgentRuntimeToolExecutionService.ActionIdentity> identity,
            @Qualifier("tianjiRuntimeToolApprovalReceipt") ObjectProvider<AgentRuntimeToolExecutionService.ApprovalReceipt> approvalReceipt,
            TianjiGovernedProcessToolProperties properties) {
        if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)
                || !actions.usesDataSource(source) || !actions.usesLedger(ledger) || !actions.usesAuthorization(authorization)
                || !results.usesDataSource(source) || !results.usesLedger(ledger) || !results.usesCapabilities(capabilities)
                || !results.usesAuthorization(authorization) || !authorization.usesLedger(ledger) || !authorization.usesBindings(grants)) {
            throw unavailable();
        }
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(5);
        return new AgentRuntimeToolExecutionService(ledger, capabilities, actions, results, authorization, decision,
                properties.mcpBindings().isEmpty() ? null : mcp.getObject(), start,
                properties.approvalBindings().isEmpty() ? null : verifier.getObject(),
                (runtime, reference) -> grants.resolveRuntime(runtime).stream()
                        .filter(tool -> reference.equals(tool.descriptor().reference()))
                        .map(tool -> new AgentRuntimeToolExecutionService.SelectedToolVO(tool.descriptor(), tool.binding()))
                        .findFirst().orElse(null),
                identity.getIfAvailable(), approvalReceipt.getIfAvailable(),
                new AgentRuntimeCallReceiptStore(source, new AgentRuntimeBindingStore(source)), transaction);
    }

    @Bean("governedProcessToolInvocationStore")
    ToolInvocationStore governedProcessToolInvocationStore(DataSource source, AgentLedgerService ledger,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization, Environment environment) {
        requirePrerequisites(environment);
        var store = new ToolInvocationStore(source, ledger, capabilities, authorization); store.verifyStorage(); return store;
    }

    @Bean("governedProcessToolInvocationRecorder")
    ToolInvocationRecorder governedProcessToolInvocationRecorder(DataSource source, AgentLedgerService ledger,
            @Qualifier("governedProcessToolInvocationStore") ToolInvocationStore preparations,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization, Environment environment) {
        requirePrerequisites(environment);
        var recorder = new ToolInvocationRecorder(source, ledger, preparations, capabilities, authorization); recorder.verifyStorage(); return recorder;
    }

    @Bean("governedProcessToolResultAccessStore")
    ToolResultAccessStore governedProcessToolResultAccessStore(DataSource source, AgentLedgerService ledger,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization, Environment environment) {
        requirePrerequisites(environment);
        var store = new ToolResultAccessStore(source, ledger, capabilities, authorization); store.verifyStorage(); return store;
    }

    @Bean("governedProcessToolResultAccessRecorder")
    ToolResultAccessRecorder governedProcessToolResultAccessRecorder(
            @Qualifier("governedProcessToolResultAccessStore") ToolResultAccessStore store,
            AgentLedgerService ledger, Environment environment) {
        requirePrerequisites(environment);
        return new ToolResultAccessRecorder(store, ledger);
    }

    @Bean("tianjiGovernedProcessDecisionBindings")
    TianjiFixedDecisionToolBindings tianjiGovernedProcessDecisionBindings(TianjiToolAuthorityProperties authority,
            TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!authority.enabled() || !properties.enabled()) throw unavailable();
            requireMcpSlots(authority, properties, grants);
            var fixed = new TianjiFixedDecisionToolBindings(authority.descriptors(), properties.decisionBindings());
            var expected = expectedSlots(authority, grants, ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY);
            var configured = new HashSet<List<Object>>();
            for (var binding : fixed.bindings()) if (!configured.add(slot(binding))) throw unavailable();
            if (!expected.equals(configured)) throw unavailable();
            return fixed;
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean("tianjiGovernedProcessStartBindings")
    TianjiFixedBpmnStartToolBindings tianjiGovernedProcessStartBindings(TianjiToolAuthorityProperties authority,
            TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!authority.enabled() || !properties.enabled()) throw unavailable();
            var fixed = new TianjiFixedBpmnStartToolBindings(authority.descriptors(), properties.startBindings());
            var registered = registered(authority);
            var expected = expectedSlots(authority, grants, ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY);
            expected.addAll(expectedSlots(authority, grants, ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY));
            var configured = new HashSet<List<Object>>();
            for (var binding : fixed.bindings()) {
                var descriptor = registered.get(binding.tool());
                if (descriptor == null || descriptor.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE && descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED
                        || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey()) || !configured.add(slot(binding))) throw unavailable();
            }
            if (!expected.equals(configured)) throw unavailable();
            requireApprovalSlots(authority, properties, grants);
            return fixed;
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean("tianjiGovernedProcessApprovalBindings")
    @Lazy
    TianjiFixedToolApprovalBindings tianjiGovernedProcessApprovalBindings(TianjiToolAuthorityProperties authority,
            TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!authority.enabled() || !properties.enabled()) throw unavailable();
            if (properties.approvalBindings().isEmpty()) return null;
            requireApprovalSlots(authority, properties, grants);
            return new TianjiFixedToolApprovalBindings(authority.descriptors(), properties.approvalBindings());
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean(name = "tianjiGovernedProcessApprovalVerifier", destroyMethod = "close")
    @Lazy
    TianjiFixedToolApprovalVerifier tianjiGovernedProcessApprovalVerifier(TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiGovernedProcessApprovalBindings") ObjectProvider<TianjiFixedToolApprovalBindings> bindings,
            @Qualifier("idpServiceOAuth2Client") ObjectProvider<IdpServiceOAuth2Client> oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") ObjectProvider<TenantIssuerAuthenticationManagerComponent> authentication,
            @Qualifier("verifiedPrincipalConverter") ObjectProvider<VerifiedPrincipalConverter> converter,
            @Qualifier("trustedIssuerRegistry") ObjectProvider<TrustedIssuerRegistry> issuers,
            @Qualifier("tianjiGovernedProcessHttpClient") ObjectProvider<HttpClient> http, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!properties.enabled()) throw unavailable();
            if (properties.approvalBindings().isEmpty()) return null;
            return new TianjiFixedToolApprovalVerifier(bindings.getObject(), oauth.getObject(), authentication.getObject(),
                    converter.getObject(), issuers.getObject(), http.getObject(), properties.concurrency());
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean("tianjiGovernedProcessMcpBindings")
    @Lazy
    TianjiFixedReadOnlyMcpToolBindings tianjiGovernedProcessMcpBindings(TianjiToolAuthorityProperties authority,
            TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!authority.enabled() || !properties.enabled()) throw unavailable();
            requireMcpSlots(authority, properties, grants);
            if (properties.mcpBindings().isEmpty()) return null;
            return new TianjiFixedReadOnlyMcpToolBindings(authority.descriptors(), properties.mcpBindings());
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean(name = "tianjiGovernedProcessMcpTransport", destroyMethod = "close")
    @Lazy
    TianjiFixedReadOnlyMcpToolTransport tianjiGovernedProcessMcpTransport(TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiGovernedProcessMcpBindings") ObjectProvider<TianjiFixedReadOnlyMcpToolBindings> bindings,
            @Qualifier("idpServiceOAuth2Client") ObjectProvider<IdpServiceOAuth2Client> oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") ObjectProvider<TenantIssuerAuthenticationManagerComponent> authentication,
            @Qualifier("verifiedPrincipalConverter") ObjectProvider<VerifiedPrincipalConverter> converter,
            @Qualifier("trustedIssuerRegistry") ObjectProvider<TrustedIssuerRegistry> issuers,
            @Qualifier("tianjiGovernedProcessHttpClient") ObjectProvider<HttpClient> http, Environment environment) {
        requirePrerequisites(environment);
        try {
            if (!properties.enabled()) throw unavailable();
            if (properties.mcpBindings().isEmpty()) return null;
            return new TianjiFixedReadOnlyMcpToolTransport(bindings.getObject(), oauth.getObject(), authentication.getObject(),
                    converter.getObject(), issuers.getObject(), http.getObject(), properties.concurrency());
        } catch (RuntimeException privateConfiguration) { throw unavailable(); }
    }

    @Bean(name = "tianjiGovernedProcessHttpClient", destroyMethod = "shutdownNow")
    HttpClient tianjiGovernedProcessHttpClient(Environment environment) {
        requirePrerequisites(environment);
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Bean(name = "tianjiGovernedProcessDecisionTransport", destroyMethod = "close")
    TianjiFixedDecisionToolTransport tianjiGovernedProcessDecisionTransport(
            @Qualifier("tianjiGovernedProcessDecisionBindings") TianjiFixedDecisionToolBindings bindings,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("tianjiGovernedProcessHttpClient") HttpClient http,
            TianjiGovernedProcessToolProperties properties, Environment environment) {
        requirePrerequisites(environment);
        if (!properties.enabled()) throw unavailable();
        return new TianjiFixedDecisionToolTransport(bindings, oauth, authentication, converter, issuers, http, properties.concurrency());
    }

    @Bean(name = "tianjiGovernedProcessStartTransport", destroyMethod = "close")
    TianjiFixedBpmnStartToolTransport tianjiGovernedProcessStartTransport(
            @Qualifier("tianjiGovernedProcessStartBindings") TianjiFixedBpmnStartToolBindings bindings,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("tianjiGovernedProcessHttpClient") HttpClient http,
            TianjiGovernedProcessToolProperties properties, Environment environment) {
        requirePrerequisites(environment);
        if (!properties.enabled()) throw unavailable();
        return new TianjiFixedBpmnStartToolTransport(bindings, oauth, authentication, converter, issuers, http, properties.concurrency());
    }

    @Bean("tianjiAdkGovernedProcessTools")
    @Lazy
    AdkGovernedProcessToolBridge tianjiAdkGovernedProcessTools(AgentLedgerService ledger,
            @Qualifier("governedProcessToolInvocationRecorder") ToolInvocationRecorder recorder,
            @Qualifier("tianjiGovernedProcessDecisionTransport") TianjiFixedDecisionToolTransport decision,
            @Qualifier("tianjiGovernedProcessStartTransport") TianjiFixedBpmnStartToolTransport start,
            @Qualifier("governedProcessToolResultAccessRecorder") ToolResultAccessRecorder access,
            TianjiToolAuthorityProperties authority,
            @Qualifier("tianjiGovernedProcessDecisionBindings") TianjiFixedDecisionToolBindings decisions,
            @Qualifier("tianjiGovernedProcessStartBindings") TianjiFixedBpmnStartToolBindings starts,
            @Qualifier("tianjiGovernedProcessMcpBindings") ObjectProvider<TianjiFixedReadOnlyMcpToolBindings> mcpBindings,
            @Qualifier("tianjiGovernedProcessMcpTransport") ObjectProvider<TianjiFixedReadOnlyMcpToolTransport> mcpTransports,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants,
            @Qualifier("tianjiToolStartActionContextResolver") ObjectProvider<StartActionContextResolver> resolvers,
            TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiGovernedProcessApprovalVerifier") ObjectProvider<TianjiFixedToolApprovalVerifier> approvalVerifiers,
            @Qualifier("tianjiToolApprovalReceiptResolver") ObjectProvider<ApprovalReceiptResolver> approvalReceipts,
            ListableBeanFactory beans,
            ObjectProvider<OpenTelemetry> telemetry, Environment environment) {
        requirePrerequisites(environment);
        requireMcpSlots(authority, properties, grants);
        var references = new HashSet<DefinitionResourceRef>();
        decisions.bindings().forEach(binding -> references.add(binding.tool())); starts.bindings().forEach(binding -> references.add(binding.tool()));
        TianjiFixedReadOnlyMcpToolTransport mcp = null;
        if (!properties.mcpBindings().isEmpty()) {
            var fixed = mcpBindings.getObject(); mcp = mcpTransports.getObject();
            if (fixed == null || mcp == null) throw unavailable();
            fixed.bindings().forEach(binding -> references.add(binding.tool()));
        }
        var selected = authority.descriptors().stream().filter(descriptor -> references.contains(descriptor.reference())).toList();
        if (!authority.enabled() || selected.size() != references.size()) throw unavailable();
        var resolver = resolvers.getObject(); if (resolver == null) throw unavailable();
        telemetry.getIfAvailable();
        if (!properties.approvalBindings().isEmpty()) {
            requireApprovalReceiptSingleton(beans);
            var verifier = approvalVerifiers.getObject(); var receipt = approvalReceipts.getObject();
            if (verifier == null || receipt == null) throw unavailable();
            if (mcp != null) return new AdkGovernedProcessToolBridge(ledger, recorder, decision, mcp, start, access, selected,
                    material -> grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver, verifier, receipt);
            return new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, selected,
                    material -> grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver, verifier, receipt);
        }
        if (mcp != null) return new AdkGovernedProcessToolBridge(ledger, recorder, decision, mcp, start, access, selected,
                material -> grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver);
        return new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, selected,
                material -> grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver);
    }

    @Bean("tianjiGovernedProcessToolRuntimeChainVerifier")
    @Lazy(false)
    SmartInitializingSingleton tianjiGovernedProcessToolRuntimeChainVerifier(Environment environment, DataSource source,
            AgentLedgerService ledger, @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorization,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants,
            @Qualifier("governedProcessToolInvocationStore") ToolInvocationStore preparations,
            @Qualifier("governedProcessToolInvocationRecorder") ToolInvocationRecorder recorder,
            @Qualifier("governedProcessToolResultAccessStore") ToolResultAccessStore accesses,
            @Qualifier("governedProcessToolResultAccessRecorder") ToolResultAccessRecorder accessRecorder,
            @Qualifier("tianjiGovernedProcessDecisionBindings") TianjiFixedDecisionToolBindings decisions,
            @Qualifier("tianjiGovernedProcessStartBindings") TianjiFixedBpmnStartToolBindings starts,
            @Qualifier("tianjiGovernedProcessMcpBindings") ObjectProvider<TianjiFixedReadOnlyMcpToolBindings> mcpBindings,
            @Qualifier("tianjiGovernedProcessMcpTransport") ObjectProvider<TianjiFixedReadOnlyMcpToolTransport> mcpTransports,
            @Qualifier("tianjiGovernedProcessHttpClient") HttpClient http,
            @Qualifier("tianjiGovernedProcessDecisionTransport") TianjiFixedDecisionToolTransport decision,
            @Qualifier("tianjiGovernedProcessStartTransport") TianjiFixedBpmnStartToolTransport start,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("tianjiAdkGovernedProcessTools") ObjectProvider<AdkGovernedProcessToolBridge> bridges,
            @Qualifier("tianjiAdkAgentRunExecutor") ObjectProvider<AdkAgentRunExecutor> executors,
            @Qualifier("tianjiToolStartActionContextResolver") ObjectProvider<StartActionContextResolver> resolvers,
            TianjiToolAuthorityProperties authority, TianjiGovernedProcessToolProperties properties,
            @Qualifier("tianjiGovernedProcessApprovalBindings") ObjectProvider<TianjiFixedToolApprovalBindings> approvalBindings,
            @Qualifier("tianjiGovernedProcessApprovalVerifier") ObjectProvider<TianjiFixedToolApprovalVerifier> approvalVerifiers,
            @Qualifier("tianjiToolApprovalReceiptResolver") ObjectProvider<ApprovalReceiptResolver> approvalReceipts,
            ListableBeanFactory beans,
            @Qualifier("tianjiExecutionGrantAuthorizer") TianjiExecutionGrantAuthorizer runAuthorization) {
        return () -> {
            requirePrerequisites(environment);
            requireMcpSlots(authority, properties, grants);
            if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)
                    || !preparations.usesDataSource(source) || !preparations.usesLedger(ledger)
                    || !preparations.usesCapabilities(capabilities) || !preparations.usesAuthorization(authorization)
                    || !recorder.usesDataSource(source) || !recorder.usesLedger(ledger) || !recorder.usesPreparations(preparations)
                    || !recorder.usesCapabilities(capabilities) || !recorder.usesAuthorization(authorization)
                    || !accesses.usesDataSource(source) || !accesses.usesLedger(ledger)
                    || !accesses.usesCapabilities(capabilities) || !accesses.usesAuthorization(authorization)
                    || !accessRecorder.usesStore(accesses) || !accessRecorder.usesLedger(ledger)
                    || !authorization.usesLedger(ledger) || !authorization.usesBindings(grants) || !authorization.usesIssuerRegistry(issuers)
                    || !runAuthorization.usesToolAuthorization(authorization)
                    || !decision.usesBindings(decisions) || !decision.usesOAuth(oauth) || !decision.usesAuthentication(authentication)
                    || !decision.usesConverter(converter) || !decision.usesIssuerRegistry(issuers) || !decision.usesHttpClient(http)
                    || !start.usesBindings(starts) || !start.usesOAuth(oauth) || !start.usesAuthentication(authentication)
                    || !start.usesConverter(converter) || !start.usesIssuerRegistry(issuers) || !start.usesHttpClient(http)
                    || http.followRedirects() != HttpClient.Redirect.NEVER) throw unavailable();
            var resolver = resolvers.getObject(); var bridge = bridges.getObject(); var worker = executors.getObject();
            if (resolver == null || bridge == null || worker == null || !bridge.usesLedger(ledger) || !bridge.usesRecorder(recorder)
                    || !bridge.usesDecisionTransport(decision) || !bridge.usesStartTransport(start)
                    || !bridge.usesResultAccessRecorder(accessRecorder) || !bridge.usesActionContextResolver(resolver)
                    || !worker.usesLedger(ledger) || !worker.usesAuthorization(runAuthorization) || !worker.usesGovernedProcessTools(bridge)) throw unavailable();
            if (!properties.approvalBindings().isEmpty()) {
                requireApprovalReceiptSingleton(beans);
                var fixed = approvalBindings.getObject(); var verifier = approvalVerifiers.getObject(); var receipt = approvalReceipts.getObject();
                if (fixed == null || verifier == null || receipt == null || !verifier.usesBindings(fixed) || !verifier.usesOAuth(oauth)
                        || !verifier.usesAuthentication(authentication) || !verifier.usesConverter(converter)
                        || !verifier.usesIssuerRegistry(issuers) || !verifier.usesHttpClient(http)
                        || !bridge.usesApprovalVerifier(verifier) || !bridge.usesApprovalReceiptResolver(receipt)) throw unavailable();
            }
            if (!properties.mcpBindings().isEmpty()) {
                var fixed = mcpBindings.getObject(); var mcp = mcpTransports.getObject();
                if (fixed == null || mcp == null || !mcp.usesBindings(fixed) || !mcp.usesOAuth(oauth)
                        || !mcp.usesAuthentication(authentication) || !mcp.usesConverter(converter)
                        || !mcp.usesIssuerRegistry(issuers) || !mcp.usesHttpClient(http) || !bridge.usesMcpTransport(mcp)) throw unavailable();
            }
        };
    }

    private static void requireMcpSlots(TianjiToolAuthorityProperties authority, TianjiGovernedProcessToolProperties properties,
                                      TianjiToolGrantBindings grants) {
        var registered = registered(authority);
        var expected = expectedSlots(authority, grants, ToolPolicy.Risk.READ_ONLY, ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY);
        var configured = new HashSet<List<Object>>();
        for (var binding : properties.mcpBindings()) {
            var descriptor = registered.get(binding.tool());
            if (descriptor == null || descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY
                    || !ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                    || !configured.add(slot(binding))) throw unavailable();
        }
        if (!expected.equals(configured)) throw unavailable();
    }

    private static void requireApprovalSlots(TianjiToolAuthorityProperties authority, TianjiGovernedProcessToolProperties properties,
                                             TianjiToolGrantBindings grants) {
        var registered = registered(authority);
        var expected = expectedSlots(authority, grants, ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY);
        var configured = new HashSet<List<Object>>();
        for (var binding : properties.approvalBindings()) {
            var descriptor = registered.get(binding.tool());
            if (descriptor == null || descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED
                    || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                    || !descriptor.policy().approvalPolicyRef().equals(binding.approval().policyRef()) || !configured.add(slot(binding))) throw unavailable();
        }
        if (!expected.equals(configured)) throw unavailable();
    }

    private static void requireApprovalReceiptSingleton(ListableBeanFactory beans) {
        if (beans == null) throw unavailable();
        int matches = 0;
        for (String name : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beans, ApprovalReceiptResolver.class, true, false)) {
            if (BeanFactoryAnnotationUtils.isQualifierMatch("tianjiToolApprovalReceiptResolver"::equals, name, beans)
                    && (!beans.isSingleton(name) || ++matches > 1)) throw unavailable();
        }
        if (matches != 1) throw unavailable();
    }

    private static Map<DefinitionResourceRef, ToolDescriptor> registered(TianjiToolAuthorityProperties authority) {
        var registered = new HashMap<DefinitionResourceRef, ToolDescriptor>();
        for (var descriptor : authority.descriptors()) if (registered.putIfAbsent(descriptor.reference(), descriptor) != null) throw unavailable();
        return registered;
    }
    private static Set<List<Object>> expectedSlots(TianjiToolAuthorityProperties authority, TianjiToolGrantBindings grants,
                                                   ToolPolicy.Risk risk, String resource) {
        var registered = registered(authority); var expected = new HashSet<List<Object>>();
        for (var binding : grants.bindings()) {
            var descriptor = registered.get(binding.tool()); if (descriptor == null) throw unavailable();
            if (descriptor.policy().risk() == risk && resource.equals(descriptor.policy().executionResourceKey()) && !expected.add(slot(binding))) throw unavailable();
        }
        return expected;
    }
    private static List<Object> slot(TianjiToolGrantBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.issuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionSha256(), binding.tool());
    }
    private static List<Object> slot(TianjiFixedDecisionToolBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
    }
    private static List<Object> slot(TianjiFixedBpmnStartToolBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
    }
    private static List<Object> slot(TianjiFixedReadOnlyMcpToolBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
    }
    private static List<Object> slot(TianjiFixedToolApprovalBindings.BindingVO binding) {
        return List.of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
    }
    private static void requirePrerequisites(Environment environment) {
        if (environment.getProperty("stellar.tianji.agent.tools.read-only-decision.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_TOOL_RUNTIME_MODE_CONFLICT");
        }
        for (String feature : new String[]{"api", "storage", "execution-grants", "execution-authorization", "runtime", "tools.authority"}) {
            if (!environment.getProperty("stellar.tianji.agent." + feature + ".enabled", Boolean.class, false)) {
                throw new IllegalStateException("TIANJI_GOVERNED_PROCESS_TOOL_PREREQUISITES_REQUIRED");
            }
        }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_GOVERNED_PROCESS_TOOL_RUNTIME_UNAVAILABLE"); }
}
