package top.egon.cola.platform.tianji.agent.server.context;

import io.grpc.Status;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.rpc.consumer.interceptor.RpcClientInvocation;
import top.egon.cola.component.rpc.consumer.reference.RpcReferenceDefinitionResolver;
import top.egon.cola.component.rpc.consumer.reference.RpcServiceTargetVO;
import top.egon.cola.component.rpc.context.identity.RpcProcessIdentity;
import top.egon.cola.component.rpc.contract.identity.RpcServiceIdentity;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService;
import top.egon.cola.platform.tianji.agent.server.integration.mcp.TianjiFixedReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties.RpcConsumerModeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcClientCredentialInterceptorFactory;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcClientTenantResolver;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Binds published Prompt and Skill reads to the approved native SI Service. */
@Configuration(value = "tianjiTianshuContextConfiguration", proxyBeanMethods = false)
@ConditionalOnExpression("${stellar.tianji.agent.context.tianshu.enabled:false} or ("
        + TianshuContextConfiguration.RUN_ADMISSION + ") or (" + TianshuContextConfiguration.MCP_PUBLICATION_READ + ")")
@EnableConfigurationProperties(TianshuContextProperties.class)
public class TianshuContextConfiguration {
    static final String SERVICE = "egon.tianshu.v1.DdcSiResourceGrpcService";
    static final String METHOD = SERVICE + "/ResolveSiResource";
    private static final String GROUP = "tianshu";
    private static final String VERSION = "1.0.0";
    private static final String SI_RPC = "egon.cola.component.tianshu.si.rpc.";
    static final String RUN_ADMISSION = "${stellar.tianji.agent.api.enabled:false} and "
            + "${stellar.tianji.agent.linked.enabled:false} and ${stellar.tianji.agent.runtime-v2.enabled:false}";
    static final String MCP_PUBLICATION_READ = "${stellar.tianji.agent.tools.governed-process.enabled:false} and "
            + "${stellar.tianji.agent.runtime-v2.enabled:false}";
    private static final ScopedValue<CatalogAuthorityVO> READ_AUTHORITY = ScopedValue.newInstance();

    @Bean("tianshuContextPrerequisites")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.context.tianshu", name = "enabled", havingValue = "true")
    static BeanFactoryPostProcessor tianshuContextPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.runtime.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.context.snapshots.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Tianshu context sources require the governed durable Agent runtime and context snapshots");
            }
        };
    }

    @Bean("tianjiTianshuContextProvider")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.context.tianshu", name = "enabled", havingValue = "true")
    TianshuContextProvider tianjiTianshuContextProvider(TianshuContextProperties properties,
            Environment environment, @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("ddcSiCatalogRpcClient") DdcSiCatalogClient catalog,
            @Qualifier("rpcReferenceDefinitionResolver") RpcReferenceDefinitionResolver definitions,
            @Qualifier("rpcProcessIdentity") RpcProcessIdentity process,
            @Qualifier("idpStarterProperties") IdpStarterProperties idp,
            @Qualifier("idpRpcClientCredentialInterceptorFactory") IdpRpcClientCredentialInterceptorFactory credentials) {
        if (properties.connections().isEmpty() || properties.bindings().isEmpty()
                || !environment.getProperty(SI_RPC + "enabled", Boolean.class, false)) throw invalid();
        credentials.validateConfiguration();
        properties.bindings().forEach(binding -> TianshuContextProvider.requireIssuer(binding, issuers));
        var readers = new HashMap<String, TianshuContextProvider.Ports>();
        for (var connection : properties.connections()) {
            if (properties.bindings().stream().noneMatch(binding -> binding.connectionId().equals(connection.id()))) throw invalid();
            CatalogAuthorityVO authority = executionAuthority(connection, environment, definitions, process, idp);
            readers.put(connection.id(), new TianshuContextProvider.Ports(connection.scope(), catalog, connection.timeout(),
                    connection.maxConcurrent(), authority));
        }
        return new TianshuContextProvider(properties, readers, issuers, Clock.systemUTC());
    }

    @Bean("tianjiSiExecutionAuthority")
    @ConditionalOnExpression(RUN_ADMISSION)
    TianjiSiExecutionBundleService.ExecutionAuthority tianjiSiExecutionAuthority(TianshuContextProperties properties,
            @Qualifier("environment") Environment environment,
            @Qualifier("rpcReferenceDefinitionResolver") RpcReferenceDefinitionResolver definitions,
            @Qualifier("rpcProcessIdentity") RpcProcessIdentity process,
            @Qualifier("idpStarterProperties") IdpStarterProperties idp,
            @Qualifier("idpRpcClientCredentialInterceptorFactory") IdpRpcClientCredentialInterceptorFactory credentials) {
        if (properties.connections().isEmpty() || !environment.getProperty(SI_RPC + "enabled", Boolean.class, false)) throw invalid();
        credentials.validateConfiguration();
        return (actor, scope, read) -> {
            var connection = selectConnection(properties, actor.tenantId(), scope);
            var approved = executionAuthority(connection, environment, definitions, process, idp);
            return withAuthority(approved, () -> read.apply(connection.tianshuTenantId()));
        };
    }

    @Bean("tianjiMcpSiPublicationAuthority")
    @Lazy
    @ConditionalOnExpression(MCP_PUBLICATION_READ)
    TianjiFixedReadOnlyMcpToolTransport.PublicationAuthority tianjiMcpSiPublicationAuthority(
            TianshuContextProperties properties, @Qualifier("ddcSiCatalogRpcClient") DdcSiCatalogClient catalog,
            @Qualifier("environment") Environment environment,
            @Qualifier("rpcReferenceDefinitionResolver") RpcReferenceDefinitionResolver definitions,
            @Qualifier("rpcProcessIdentity") RpcProcessIdentity process,
            @Qualifier("idpStarterProperties") IdpStarterProperties idp,
            @Qualifier("idpRpcClientCredentialInterceptorFactory") IdpRpcClientCredentialInterceptorFactory credentials) {
        if (properties.connections().isEmpty() || !environment.getProperty(SI_RPC + "enabled", Boolean.class, false)) throw invalid();
        credentials.validateConfiguration();
        return (actor, material) -> {
            var scope = material.scope();
            var publication = material.publication();
            var identity = publication.identity();
            var connection = selectConnection(properties, actor.tenantId(), scope);
            long tenant = connection.tianshuTenantId();
            long versionNo = Long.parseLong(identity.versionNo());
            if (actor.tenantId() != material.identityTenantId() || !Long.toString(tenant).equals(identity.tenantId())) {
                throw TianjiSiExecutionBundleService.unavailable();
            }
            var approved = executionAuthority(connection, environment, definitions, process, idp);
            var current = withAuthority(approved, () -> catalog.resolveExecutionResource(tenant, scope.bizCode(),
                    scope.namespaceCode(), scope.envCode(), publication.ownerAppCode(), scope.callerAppCode(),
                    DdcSiKindEnum.MCP, publication.key(), versionNo)).orElseThrow(TianjiSiExecutionBundleService::unavailable);
            if (!current.isExecutionSnapshotShapeValid() || current.getStatus() == null
                    || !current.getStatus().isQueryableByRuntime() || !Objects.equals(current.getTenantId(), tenant)
                    || current.getKind() != DdcSiKindEnum.MCP || !scope.bizCode().equals(current.getBizCode())
                    || !scope.namespaceCode().equals(current.getNamespaceCode()) || !scope.envCode().equals(current.getEnvCode())
                    || !Objects.equals(publication.ownerAppCode(), current.getOwnerAppCode())
                    || !publication.key().equals(current.getResourceKey())
                    || !Objects.equals(current.getResourceId(), Long.valueOf(identity.resourceId()))
                    || !Objects.equals(current.getResolvedVersionId(), Long.valueOf(identity.versionId()))
                    || !Objects.equals(current.getResolvedVersionNo(), versionNo)
                    || !identity.digestSha256().equals(current.getDigestSha256())
                    || !publication.specJson().equals(current.getSpecJson())) {
                throw TianjiSiExecutionBundleService.unavailable();
            }
        };
    }

    private static TianshuContextProperties.Connection selectConnection(TianshuContextProperties properties, long identityTenantId,
            TianjiSiExecutionBundleService.ScopeVO scope) {
        var selected = properties.connections().stream().filter(connection ->
                connection.identityTenantId() == identityTenantId && connection.bizCode().equals(scope.bizCode())
                        && connection.env().equals(scope.envCode()) && connection.appCode().equals(scope.callerAppCode())).toList();
        if (selected.size() != 1) throw TianjiSiExecutionBundleService.unavailable();
        return selected.getFirst();
    }

    private static CatalogAuthorityVO executionAuthority(TianshuContextProperties.Connection connection,
            Environment environment, RpcReferenceDefinitionResolver definitions, RpcProcessIdentity process, IdpStarterProperties idp) {
        Duration transportDeadline = Binder.get(environment).bind(SI_RPC + "default-timeout", Duration.class).orElse(null);
        RpcServiceTargetVO target = definitions.resolve(connection.bindingId(),
                new RpcServiceIdentity(SERVICE, GROUP, VERSION), Set.of(METHOD)).target();
        if (!connection.bindingId().equals(environment.getProperty(SI_RPC + "binding-id"))
                || connection.bindingVersion() != environment.getProperty(SI_RPC + "binding-version", Long.class, 0L)
                || connection.bindingVersion() != target.version() || !connection.timeout().equals(transportDeadline)
                || !target.methods().equals(Set.of(METHOD)) || !process.env().equals(connection.env())) throw invalid();
        return verifiedAuthority(connection, process, target, idp);
    }

    private static CatalogAuthorityVO verifiedAuthority(TianshuContextProperties.Connection connection, RpcProcessIdentity process,
            RpcServiceTargetVO target, IdpStarterProperties idp) {
        if (!idp.getRpc().isEnabled() || !idp.getRpc().getConsumer().isEnabled()) throw invalid();
        var bindings = idp.getRpc().getConsumer().getBindings().values().stream().filter(binding ->
                SERVICE.equals(binding.getServiceName()) && GROUP.equals(binding.getGroup()) && VERSION.equals(binding.getVersion())
                        && METHOD.equals(binding.getFullMethodName()) && target.bindingId().equals(binding.getTransportBindingId())
                        && Long.valueOf(target.version()).equals(binding.getTransportBindingVersion())
                        && binding.getProcessIdentity() != null
                        && process.applicationName().equals(binding.getProcessIdentity().get("applicationName"))
                        && process.env().equals(binding.getProcessIdentity().get("env"))).toList();
        if (bindings.size() != 1) throw invalid();
        var selected = bindings.getFirst();
        String identityTenant = Long.toString(connection.identityTenantId());
        if (selected.getMode() != RpcConsumerModeEnum.SERVICE || selected.getScopeContext() != ServiceTokenContext.TENANT
                || selected.getTenantRegistrationIds() == null
                || !selected.getTenantRegistrationIds().containsKey(identityTenant)) throw invalid();
        return new CatalogAuthorityVO(target, process, identityTenant);
    }

    @Bean("idpRpcClientTenantResolver")
    IdpRpcClientTenantResolver tianshuContextTenantResolver() {
        return (invocation, authentication) -> resolveIdentityTenant(invocation);
    }

    private static Optional<String> resolveIdentityTenant(RpcClientInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        if (!SERVICE.equals(invocation.serviceName())) return Optional.empty();
        CatalogAuthorityVO authority = READ_AUTHORITY.isBound() ? READ_AUTHORITY.get() : null;
        if (authority == null || !METHOD.equals(invocation.fullMethodName()) || !GROUP.equals(invocation.group())
                || !VERSION.equals(invocation.version()) || !authority.target().equals(invocation.target())
                || !authority.process().equals(invocation.processIdentity())) {
            throw Status.PERMISSION_DENIED.withDescription("No approved Tianshu context credential").asRuntimeException();
        }
        return Optional.of(authority.identityTenant());
    }

    static <T> T withAuthority(CatalogAuthorityVO authority, Supplier<T> read) {
        return ScopedValue.where(READ_AUTHORITY, Objects.requireNonNull(authority)).call(read::get);
    }

    @Bean("tianshuRpcRequestIdGuard")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.context.tianshu", name = "enabled", havingValue = "true")
    SmartInitializingSingleton tianshuRpcRequestIdGuard(@Qualifier("tianjiTianshuContextProvider") TianshuContextProvider provider) {
        return () -> {
            // The shared native runtime uses the existing process-wide ID engine.
            try {
                SnowflakeIdGenerator.nextId();
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Tianshu context RPC requires an explicitly initialized request ID engine");
            }
        };
    }

    record CatalogAuthorityVO(RpcServiceTargetVO target, RpcProcessIdentity process, String identityTenant) {
        CatalogAuthorityVO {
            Objects.requireNonNull(target);
            Objects.requireNonNull(process);
            if (identityTenant == null || !identityTenant.matches("[1-9][0-9]{0,18}")) throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Tianshu context source connections are invalid");
    }
}
