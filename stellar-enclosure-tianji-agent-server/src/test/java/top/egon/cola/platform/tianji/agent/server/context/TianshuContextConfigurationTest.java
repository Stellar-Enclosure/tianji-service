package top.egon.cola.platform.tianji.agent.server.context;

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.rpc.config.EgonRpcProperties;
import top.egon.cola.component.rpc.consumer.generic.RpcGenericInvocation;
import top.egon.cola.component.rpc.consumer.generic.RpcGenericInvoker;
import top.egon.cola.component.rpc.consumer.interceptor.RpcClientInvocation;
import top.egon.cola.component.rpc.consumer.reference.RpcReferenceDefinitionResolver;
import top.egon.cola.component.rpc.consumer.reference.RpcServiceTargetVO;
import top.egon.cola.component.rpc.context.identity.RpcProcessIdentity;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcAutoConfiguration;
import top.egon.cola.component.rpc.tianshu.client.si.DdcSiCatalogRpcClient;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceResponse;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapper;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcClientCredentialInterceptorFactory;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcClientTenantResolver;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.server.context.TianshuContextConfiguration.METHOD;
import static top.egon.cola.platform.tianji.agent.server.context.TianshuContextConfiguration.SERVICE;
import static top.egon.cola.platform.tianji.agent.server.context.TianshuContextProviderTest.*;

class TianshuContextConfigurationTest {
    private static final String CONTEXT = "stellar.tianji.agent.context.tianshu.";
    private static final String RPC = "egon.cola.component.tianshu.si.rpc.";
    private final TianshuContextConfiguration configuration = new TianshuContextConfiguration();
    private final DdcSiCatalogClient catalog = mock(DdcSiCatalogClient.class);
    private final IdpRpcClientCredentialInterceptorFactory credentials = mock(IdpRpcClientCredentialInterceptorFactory.class);

    @Test
    void featureIsAbsentByDefaultAndRequiresTheGovernedRuntime() {
        new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(TianshuContextProvider.class).isEmpty());
                });
        new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class)
                .withPropertyValues(CONTEXT + "enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void contextCannotSelectAnUnknownTransportRevisionOrUnregisteredIdentityTenant() {
        assertThrows(IllegalStateException.class, () -> configuration.tianjiTianshuContextProvider(properties(),
                transport().withProperty(RPC + "binding-version", "2"), issuers(), catalog, bindings(), PROCESS,
                idp(Set.of("20")), credentials));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiTianshuContextProvider(properties(),
                transport(), issuers(), catalog, bindings(), PROCESS, idp(Set.of("21")), credentials));
        verifyNoInteractions(catalog);
    }

    @Test
    void aBindingCannotUseAnotherRealmCredentialEvenWithAValidTransport() {
        var original = properties();
        var binding = original.bindings().getFirst();
        var other = new TianshuContextProperties.Binding(21, ISSUER, binding.connectionId(), binding.kind(), binding.key(),
                binding.namespace(), binding.ownerAppCode(), binding.resourceKey(), binding.resourceId());
        assertThrows(IllegalArgumentException.class, () -> new TianshuContextProperties(true,
                original.connections(), java.util.List.of(other)));
    }

    @Test
    void tenantCredentialExistsOnlyInsideTheExactMethodTargetAndProcessFrame() {
        var resolver = configuration.tianshuContextTenantResolver();
        var invocation = invocation(TARGET, PROCESS);
        var otherTarget = new RpcServiceTargetVO("other-si", 1, "platform", "other-si", 19080, Set.of(METHOD));
        var foreign = new RpcProcessIdentity("foreign", "test", "localhost", 10L, "test");
        assertThrows(StatusRuntimeException.class, () -> resolver.resolve(invocation, null));
        TianshuContextConfiguration.withAuthority(AUTHORITY, () -> {
            assertEquals(Optional.of("20"), resolver.resolve(invocation, null));
            assertThrows(StatusRuntimeException.class, () -> resolver.resolve(invocation(otherTarget, PROCESS), null));
            assertThrows(StatusRuntimeException.class, () -> resolver.resolve(invocation(TARGET, foreign), null));
            return null;
        });
        assertThrows(StatusRuntimeException.class, () -> resolver.resolve(invocation, null));
    }

    @Test
    void linkedRunAdmissionBindsItsIdentityTenantAndFixedSiDataTenant() {
        var authority = configuration.tianjiSiExecutionAuthority(properties(), transport(), bindings(), PROCESS,
                idp(Set.of("20")), credentials);
        var tenantResolver = configuration.tianshuContextTenantResolver();
        var invocation = invocation(TARGET, PROCESS);
        var actor = verifiedServiceActor(20);
        var connection = properties().connections().getFirst();
        var scope = new top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.ScopeVO(
                connection.bizCode(), "main", connection.env(), "owner", connection.appCode());
        var material = mock(top.egon.cola.platform.tianji.agent.si.TianshuTemplateResolver.ResolvedAgentExecutionBundle.class);
        assertSame(material, authority.read(actor, scope, dataTenant -> {
            assertEquals(101L, dataTenant);
            assertEquals(Optional.of("20"), tenantResolver.resolve(invocation, null));
            return material;
        }));
        assertThrows(StatusRuntimeException.class, () -> tenantResolver.resolve(invocation, null));
        assertThrows(top.egon.cola.platform.tianji.agent.domain.AgentLedgerException.class,
                () -> authority.read(verifiedServiceActor(21), scope, unrelated -> {
                    throw new AssertionError("Foreign realm must not enter the native transport frame");
                }));
    }

    private static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor verifiedServiceActor(long tenant) {
        return top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.verified(tenant,
                top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.SERVICE, "process-worker",
                ISSUER.toASCIIString());
    }

    @Test
    void requestIdReadinessSanitizesFailuresWithoutInitializingAnEngine() {
        var provider = configuration.tianjiTianshuContextProvider(properties(),
                transport(), issuers(), catalog, bindings(), PROCESS, idp(Set.of("20")), credentials);
        try (var ids = mockStatic(SnowflakeIdGenerator.class)) {
            ids.when(SnowflakeIdGenerator::nextId).thenThrow(new IllegalStateException("private-id-detail")).thenReturn("1");
            var guard = configuration.tianshuRpcRequestIdGuard(provider);
            var failure = assertThrows(IllegalStateException.class, guard::afterSingletonsInstantiated);
            assertFalse(failure.toString().contains("private-id-detail"));
            assertNull(failure.getCause());
            assertDoesNotThrow(guard::afterSingletonsInstantiated);
        }
    }

    @Test
    void springBindsTheApprovedNativeRouteWithoutAnEagerCredentialOrCatalogRequest() {
        try (var ids = mockStatic(SnowflakeIdGenerator.class)) {
            ids.when(SnowflakeIdGenerator::nextId).thenReturn("1");
            runner().run(context -> {
                if (context.getStartupFailure() != null) {
                    throw new AssertionError("Native context assembly failed", context.getStartupFailure());
                }
                assertTrue(context.getBean(DefaultContextAssembler.class).hasProviders());
                assertNotNull(context.getBean(TianshuContextProvider.class));
                assertTrue(context.containsBean("idpRpcClientTenantResolver"));
                verify(credentials, atLeastOnce()).validateConfiguration();
                verifyNoInteractions(catalog);
            });
        }
    }

    @Test
    void nativeAdapterArtifactAndContextProviderShareTheSameExactAuthorizedRead() {
        var invoker = mock(RpcGenericInvoker.class);
        try (var ids = mockStatic(SnowflakeIdGenerator.class)) {
            ids.when(SnowflakeIdGenerator::nextId).thenReturn("1");
            baseRunner().withConfiguration(AutoConfigurations.of(DdcRpcAutoConfiguration.class))
                    .withBean("rpcGenericInvoker", RpcGenericInvoker.class, () -> invoker)
                    .withBean("egon.cola.component.rpc-top.egon.cola.component.rpc.config.EgonRpcProperties",
                            EgonRpcProperties.class, TianshuContextConfigurationTest::nativeProperties).run(context -> {
                        if (context.getStartupFailure() != null) {
                            throw new AssertionError("Native SI artifact assembly failed", context.getStartupFailure());
                        }
                        assertInstanceOf(DdcSiCatalogRpcClient.class, context.getBean("ddcSiCatalogRpcClient"));
                        var provider = context.getBean(TianshuContextProvider.class);
                        var adopter = context.getBean("idpRpcClientTenantResolver", IdpRpcClientTenantResolver.class);
                        var mapper = context.getBean("ddcSiProtoMapperImpl", DdcSiProtoMapper.class);
                        when(invoker.invokeBlocking(any())).thenAnswer(call -> {
                            RpcGenericInvocation invocation = call.getArgument(0);
                            assertEquals(METHOD, invocation.fullMethodName());
                            assertEquals(Optional.of("20"), adopter.resolve(RpcClientInvocation.generic(
                                    invocation, PROCESS, "fixture", TARGET), null));
                            var request = ResolveSiResourceRequest.parseFrom(invocation.requestPayload());
                            assertEquals(101, request.getTenantId());
                            assertEquals(2L, request.getExactVersionNo());
                            assertTrue(request.getExecutionRequired());
                            assertEquals("prompt-owner", request.getOwnerAppCode());
                            assertEquals("tianji", request.getCallerAppCode());
                            return ResolveSiResourceResponse.newBuilder().setResource(mapper.toProto(prompt(11L)))
                                    .build().toByteArray();
                        });
                        assertEquals("Review", provider.provide(request(20, ISSUER, true, false)).getFirst().text());
                        assertThrows(StatusRuntimeException.class, () -> adopter.resolve(invocation(TARGET, PROCESS), null));
                        verify(invoker).invokeBlocking(any());
                    });
        }
    }

    @Test
    void springRejectsRetiredArbitraryTargetsAndTlsKeysDuringBinding() {
        runner().withPropertyValues(CONTEXT + "connections[0].rpc.target=dns:///other")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues(CONTEXT + "connections[0].rpc.tls.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private ApplicationContextRunner runner() {
        return baseRunner().withBean("ddcSiCatalogRpcClient", DdcSiCatalogClient.class, () -> catalog);
    }

    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class, TianjiContextAssemblyConfiguration.class)
                .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, TianshuContextProviderTest::issuers)
                .withBean("rpcReferenceDefinitionResolver", RpcReferenceDefinitionResolver.class, TianshuContextConfigurationTest::bindings)
                .withBean("rpcProcessIdentity", RpcProcessIdentity.class, () -> PROCESS)
                .withBean("idpStarterProperties", IdpStarterProperties.class, () -> idp(Set.of("20")))
                .withBean("idpRpcClientCredentialInterceptorFactory", IdpRpcClientCredentialInterceptorFactory.class, () -> credentials)
                .withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                        "stellar.tianji.agent.context.snapshots.enabled=true", CONTEXT + "enabled=true",
                        RPC + "enabled=true", RPC + "binding-id=" + TARGET.bindingId(), RPC + "binding-version=1", RPC + "default-timeout=PT1S",
                        CONTEXT + "connections[0].id=library", CONTEXT + "connections[0].tianshu-tenant-id=101",
                        CONTEXT + "connections[0].identity-tenant-id=20", CONTEXT + "connections[0].biz-code=xingyuan",
                        CONTEXT + "connections[0].env=test", CONTEXT + "connections[0].app-code=tianji",
                        CONTEXT + "connections[0].binding-id=" + TARGET.bindingId(), CONTEXT + "connections[0].binding-version=1",
                        CONTEXT + "connections[0].timeout=PT1S",
                        CONTEXT + "bindings[0].tenant-id=20", CONTEXT + "bindings[0].issuer=" + ISSUER,
                        CONTEXT + "bindings[0].connection-id=library", CONTEXT + "bindings[0].kind=PROMPT",
                        CONTEXT + "bindings[0].key=fixed", CONTEXT + "bindings[0].namespace=prompts",
                        CONTEXT + "bindings[0].owner-app-code=prompt-owner", CONTEXT + "bindings[0].resource-key=review",
                        CONTEXT + "bindings[0].resource-id=10");
    }

    private static MockEnvironment transport() {
        return new MockEnvironment().withProperty(RPC + "enabled", "true").withProperty(RPC + "binding-id", TARGET.bindingId())
                .withProperty(RPC + "binding-version", "1").withProperty(RPC + "default-timeout", "PT1S");
    }

    private static RpcReferenceDefinitionResolver bindings() {
        return new RpcReferenceDefinitionResolver(nativeProperties());
    }

    private static EgonRpcProperties nativeProperties() {
        var properties = new EgonRpcProperties();
        properties.getConsumer().setBindings(Map.of(TARGET.bindingId(), new EgonRpcProperties.RpcServiceBindingConfig()
                .setVersion(TARGET.version()).setNamespace(TARGET.namespace()).setService(TARGET.service())
                .setPort(TARGET.port()).setMethods(TARGET.methods())));
        return properties;
    }

    private static IdpStarterProperties idp(Set<String> identityTenants) {
        var properties = new IdpStarterProperties();
        var binding = new IdpStarterProperties.RpcConsumerBindingProperties();
        binding.setFullMethodName(METHOD);
        binding.setServiceName(SERVICE);
        binding.setGroup("tianshu");
        binding.setVersion("1.0.0");
        binding.setTransportBindingId(TARGET.bindingId());
        binding.setTransportBindingVersion(TARGET.version());
        binding.setProcessIdentity(Map.of("applicationName", PROCESS.applicationName(), "env", PROCESS.env()));
        binding.setMode(IdpStarterProperties.RpcConsumerModeEnum.SERVICE);
        binding.setScopeContext(ServiceTokenContext.TENANT);
        binding.setAppId("tianji-context-app");
        binding.setResourceUri(java.net.URI.create("https://tianshu.example"));
        binding.setScopes(Set.of("tianshu:si:read"));
        for (String tenant : identityTenants) binding.getTenantRegistrationIds().put(tenant, "tianshu-context");
        properties.getRpc().setEnabled(true);
        properties.getRpc().getConsumer().setEnabled(true);
        properties.getRpc().getConsumer().getBindings().put("context", binding);
        return properties;
    }

    private static RpcClientInvocation invocation(RpcServiceTargetVO target, RpcProcessIdentity process) {
        return RpcClientInvocation.generic(new RpcGenericInvocation(target.bindingId(), SERVICE, "tianshu", "1.0.0",
                METHOD, new byte[]{1}, 1000), process, "fixture", target);
    }
}
