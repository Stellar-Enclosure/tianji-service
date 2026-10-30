package top.egon.cola.platform.tianji.agent.server.context;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcProperties;
import top.egon.cola.component.rpc.tianshu.client.DdcRpcClientFactory;
import top.egon.cola.component.rpc.tianshu.client.DdcRpcClientHandle;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.server.context.TianshuContextProviderTest.*;

class TianshuContextConfigurationTest {
    private final TianshuContextConfiguration configuration = new TianshuContextConfiguration();

    @Test
    void feature_is_absent_by_default_and_cannot_enable_outside_the_governed_runtime_or_with_plaintext_rpc() {
        new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class)
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(TianshuContextProvider.class).isEmpty()); });
        new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.context.tianshu.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        var rpc = new DdcRpcProperties(); rpc.setTarget("localhost:19080");
        assertThrows(IllegalArgumentException.class, () -> new TianshuContextProperties.Connection("library", 101, "xingyuan", "test", "tianji", rpc, 1));
    }

    @Test
    void owned_clients_use_the_explicit_internal_scope_and_are_closed_once_without_a_source_read() {
        var prompt = mock(DdcPromptClient.class); var skill = mock(DdcSkillClient.class);
        when(prompt.scope()).thenReturn(SCOPE); when(skill.scope()).thenReturn(SCOPE);
        var closed = new AtomicInteger();
        try (var factories = mockConstruction(DdcRpcClientFactory.class, (factory, context) -> {
            var settings = (DdcProperties) context.arguments().get(1);
            var tenant = (DdcTenantProperties) context.arguments().get(2);
            assertEquals(101L, tenant.requireTenantId()); assertEquals("xingyuan", settings.getBizCode());
            assertEquals("test", settings.getEnv()); assertEquals("tianji", settings.getAppCode());
            when(factory.promptClient()).thenReturn(new DdcRpcClientHandle<>(prompt, closed::incrementAndGet));
            when(factory.skillClient()).thenReturn(new DdcRpcClientHandle<>(skill, closed::incrementAndGet));
        })) {
            var clients = configuration.tianjiTianshuContextClients(properties(), new MockEnvironment(), issuers());
            assertNotNull(configuration.tianjiTianshuContextProvider(properties(), clients, issuers()));
            verify(prompt, never()).get(any()); verify(skill, never()).get(any());
            clients.close(); clients.close();
            assertEquals(2, closed.get()); assertEquals(1, factories.constructed().size());
        }
    }

    @Test
    void request_id_readiness_fails_without_initializing_a_machine_id_and_sanitizes_the_reason() {
        try (var ids = mockStatic(SnowflakeIdGenerator.class)) {
            ids.when(SnowflakeIdGenerator::nextId).thenThrow(new IllegalStateException("private-id-detail")).thenReturn("1");
            var guard = configuration.tianshuRpcRequestIdGuard(new TianshuContextConfiguration.OwnedClients());
            var failure = assertThrows(IllegalStateException.class, guard::afterSingletonsInstantiated);
            assertFalse(failure.toString().contains("private-id-detail")); assertNull(failure.getCause());
            assertDoesNotThrow(guard::afterSingletonsInstantiated);
        }
    }

    @Test
    void spring_binds_the_explicit_mapping_and_wires_one_assembler_without_opening_a_model_or_read_request() {
        var prompt = mock(DdcPromptClient.class); when(prompt.scope()).thenReturn(SCOPE);
        var closed = new AtomicInteger();
        try (var ids = mockStatic(SnowflakeIdGenerator.class);
             var factories = mockConstruction(DdcRpcClientFactory.class, (factory, context) ->
                     when(factory.promptClient()).thenReturn(new DdcRpcClientHandle<>(prompt, closed::incrementAndGet)))) {
            ids.when(SnowflakeIdGenerator::nextId).thenReturn("1");
            new ApplicationContextRunner().withUserConfiguration(TianshuContextConfiguration.class)
                    .withBean(TrustedIssuerRegistry.class, TianshuContextProviderTest::issuers)
                    .withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                            "stellar.tianji.agent.context.tianshu.enabled=true",
                            "stellar.tianji.agent.context.tianshu.connections[0].id=library",
                            "stellar.tianji.agent.context.tianshu.connections[0].tianshu-tenant-id=101",
                            "stellar.tianji.agent.context.tianshu.connections[0].biz-code=xingyuan",
                            "stellar.tianji.agent.context.tianshu.connections[0].env=test",
                            "stellar.tianji.agent.context.tianshu.connections[0].app-code=tianji",
                            "stellar.tianji.agent.context.tianshu.connections[0].rpc.target=localhost:19080",
                            "stellar.tianji.agent.context.tianshu.connections[0].rpc.tls.enabled=true",
                            "stellar.tianji.agent.context.tianshu.connections[0].rpc.tls.development-plaintext=false",
                            "stellar.tianji.agent.context.tianshu.bindings[0].tenant-id=20",
                            "stellar.tianji.agent.context.tianshu.bindings[0].issuer=" + ISSUER,
                            "stellar.tianji.agent.context.tianshu.bindings[0].connection-id=library",
                            "stellar.tianji.agent.context.tianshu.bindings[0].kind=PROMPT",
                            "stellar.tianji.agent.context.tianshu.bindings[0].key=fixed",
                            "stellar.tianji.agent.context.tianshu.bindings[0].namespace=prompts",
                            "stellar.tianji.agent.context.tianshu.bindings[0].resource-key=review",
                            "stellar.tianji.agent.context.tianshu.bindings[0].resource-id=10")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertTrue(context.getBean(DefaultContextAssembler.class).hasProviders());
                        assertNotNull(context.getBean(TianshuContextProvider.class));
                        verify(prompt, never()).get(any());
                    });
            assertEquals(1, closed.get());
        }
    }
}
