package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.models.BaseLlm;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.autoconfigure.ModelBudgetProperties;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.JTokkitChatModelBudgetEstimator;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AgentPublishedDefinitionEvidenceTest {
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(42,
            TrustedAgentActor.SubjectType.SERVICE, "release-reader", "https://identity.example.test/tenant");
    private static final String KEY = "review";
    private static final long VERSION = 7;
    private static final Instant OBSERVED = Instant.parse("2026-11-11T02:04:06Z");
    private static final String BODY = "private publication content";
    private static final String RESOURCE_KEY = "private dependency key";
    private static final String MODEL_NAME = "gpt-4o-mini";
    private static final String MODEL_BEAN = "modelBean";

    @Test
    void projects_only_exact_published_metadata_from_the_owning_primary() throws SQLException {
        try (var fixture = new Fixture()) {
            var result = fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION);
            assertEquals(new AgentLedgerService.PublishedDefinitionEvidenceDTO(42, KEY, VERSION,
                    fixture.hash, "CONFIG", "in-v2", "out-v4", "model-route@2", "budget-policy@3", false, OBSERVED), result);
            assertEquals(List.of("read-only", "timeout", "primary-clock", "definition"), fixture.calls);
            assertFalse(result.toString().contains(BODY));
            verify(fixture.connection).commit();

            fixture.store(definition(KEY, VERSION, true));
            var withDependency = fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION);
            assertTrue(withDependency.optionalDependenciesPresent());
            assertFalse(withDependency.toString().contains(RESOURCE_KEY));
        }
    }

    @Test
    void does_not_fall_back_to_other_tenants_keys_or_versions() throws SQLException {
        try (var fixture = new Fixture()) {
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION + 1));
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, "other-key", VERSION));
            var foreign = new TrustedAgentActor(43, OWNER.subjectType(), OWNER.subjectId(), OWNER.issuerFingerprint());
            assertCode(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    () -> fixture.ledger.publishedDefinitionEvidence(foreign, KEY, VERSION));
        }
    }

    @Test
    void refuses_mismatched_digest_and_embedded_identity_independently() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.hash = "0".repeat(64);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            fixture.store(definition("different-key", VERSION, false));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            fixture.store(definition(KEY, VERSION + 1, false));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
        }
    }

    @Test
    void refuses_unreadable_content_and_unavailable_primary_without_exposing_source_data() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.specification = BODY;
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertFalse(failure.getMessage().contains(BODY));
            assertNull(failure.getCause());

            fixture.calls.clear();
            fixture.observed = null;
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertEquals(List.of("read-only", "timeout", "primary-clock"), fixture.calls);

            fixture.observed = OBSERVED;
            fixture.sourceFailed = true;
            var unavailable = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            assertFalse(unavailable.getMessage().contains(BODY));
            assertNull(unavailable.getCause());
        }
    }

    @Test
    void rejects_unverified_identity_inexact_key_and_nonpositive_version_before_io() throws SQLException {
        try (var fixture = new Fixture()) {
            var legacy = new TrustedAgentActor(42, OWNER.subjectType(), OWNER.subjectId());
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.publishedDefinitionEvidence(legacy, KEY, VERSION));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                    () -> fixture.ledger.publishedDefinitionEvidence(null, KEY, VERSION));
            for (var invalidKey : List.of(" " + KEY, KEY + " ", KEY + "\nline", "\uD800", "x".repeat(129))) {
                assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                        () -> fixture.ledger.publishedDefinitionEvidence(OWNER, invalidKey, VERSION));
            }
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, 0));
            verifyNoInteractions(fixture.source);
        }
    }

    @Test
    void refuses_to_observe_an_existing_transaction() throws SQLException {
        try (var fixture = new Fixture()) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        () -> fixture.ledger.publishedDefinitionEvidence(OWNER, KEY, VERSION));
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            verifyNoInteractions(fixture.source);
        }
    }

    @Test
    void verifies_committed_single_leaf_against_the_installed_executor_without_provider_io() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var model = chatModel(32);
            var estimator = estimator(model);
            fixture.store(config(estimator, "USD", 0, Set.of(), singleLeaf()));
            var query = runtime.query(fixture.ledger, factory(new SpringAiModelAdapterFactory()), model, estimator);
            var ready = query.requireReady(OWNER, KEY, VERSION, fixture.hash);
            assertEquals(fixture.hash, ready.publication().definitionContentSha256());
            assertEquals(estimator.descriptors().getFirst().modelRouteRef(), ready.publication().modelRouteRef());
            assertEquals(OBSERVED, ready.publication().observedAt());
            assertFalse(ready.toString().contains(BODY));
            String projected = fixture.json.write(ready, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
            assertFalse(projected.contains(BODY));
            assertFalse(projected.contains("configuration"));
            assertFalse(projected.contains("instruction"));
            assertEquals(List.of("read-only", "timeout", "primary-clock", "definition"), fixture.calls);
            verify(fixture.connection).commit();
            assertNoProviderIo(model);
        }
    }

    @Test
    void refuses_wrong_candidate_digest_before_touching_the_runtime() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var model = chatModel(32);
            var estimator = estimator(model);
            fixture.store(config(estimator, "USD", 0, Set.of(), singleLeaf()));
            clearInvocations(model);
            var query = runtime.query(fixture.ledger, factory(new SpringAiModelAdapterFactory()), model, estimator);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    () -> query.requireReady(OWNER, KEY, VERSION, "a".repeat(64)));
            verifyNoInteractions(model);
            assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                    () -> query.requireReady(OWNER, KEY, VERSION, "malformed"));
            assertEquals(List.of("read-only", "timeout", "primary-clock", "definition"), fixture.calls);
        }
    }

    @Test
    void refuses_a_compiled_root_bound_to_a_different_model_instance() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var selected = chatModel(32);
            var replacement = chatModel(32);
            var estimator = estimator(selected);
            fixture.store(config(estimator, "USD", 0, Set.of(), singleLeaf()));
            var compiler = factory(new SpringAiModelAdapterFactory());
            var config = new AgentFlowConfigDTO(MODEL_BEAN, MODEL_NAME, "reviewer",
                    List.of(new AgentConfigDTO("reviewer", null, BODY, null)), List.of());
            var wrongRoot = compiler.compileRoot("tianji", config, Map.of(MODEL_BEAN, replacement), List.of());
            // Keep the exact built-in classes and inject only the compiled result for this negative control.
            var substitutedResult = spy(compiler);
            assertTrue(AgentFlowFactory.usesBuiltInModelFactory(substitutedResult));
            doReturn(wrongRoot).when(substitutedResult)
                    .compileRoot(eq("tianji"), any(AgentFlowConfigDTO.class), anyMap(), anyList());
            var query = runtime.query(fixture.ledger, substitutedResult, selected, estimator);
            assertUnavailable(query, fixture);
            assertNoProviderIo(selected, replacement);
        }
    }

    @Test
    void refuses_delegating_adapter_factory_and_validator_before_either_is_invoked() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var model = chatModel(32);
            var estimator = estimator(model);
            fixture.store(config(estimator, "USD", 0, Set.of(), singleLeaf()));
            clearInvocations(model);
            var factoryInvoked = new AtomicBoolean();
            var customAdapterFactory = new SpringAiModelAdapterFactory() {
                @Override public BaseLlm create(ChatModel selected, String modelName) {
                    factoryInvoked.set(true);
                    return super.create(selected, modelName);
                }
            };
            var customFactoryQuery = runtime.query(fixture.ledger, factory(customAdapterFactory), model, estimator);
            assertUnavailable(customFactoryQuery, fixture);
            assertFalse(factoryInvoked.get());
            verifyNoInteractions(model);

            var validatorInvoked = new AtomicBoolean();
            var customValidator = new AgentFlowConfigValidator(runtime.validationUtils()) {
                @Override public AgentFlowProperties validate(AgentFlowProperties properties) {
                    validatorInvoked.set(true);
                    return super.validate(properties);
                }
            };
            var builtInFactory = factory(new SpringAiModelAdapterFactory());
            var customExecutor = runtime.executor(fixture.ledger, builtInFactory, customValidator, model, estimator);
            assertUnavailable(new AgentPublishedConfigReadinessQueryExe(fixture.ledger, customExecutor), fixture);
            assertFalse(validatorInvoked.get());
            verifyNoInteractions(model);
        }
    }

    @Test
    void refuses_same_name_options_when_the_estimator_owns_a_different_model_instance() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var selected = chatModel(32);
            var estimated = chatModel(32);
            var estimator = estimator(estimated);
            fixture.store(config(estimator, "USD", 0, Set.of(), singleLeaf()));
            var query = runtime.query(fixture.ledger, factory(new SpringAiModelAdapterFactory()), selected, estimator);
            assertUnavailable(query, fixture);
            assertNoProviderIo(selected, estimated);
        }
    }

    @Test
    void refuses_drifted_binding_and_custom_runtime_components() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var selected = chatModel(32);
            var estimator = estimator(selected);
            var query = runtime.query(fixture.ledger, factory(new SpringAiModelAdapterFactory()), selected, estimator);
            var good = config(estimator, "USD", 0, Set.of(), singleLeaf());
            var refs = good.dependencies();
            for (var invalid : List.of(
                    config(estimator, "EUR", 0, Set.of(), singleLeaf()),
                    withDependencies(good, new DefinitionDependenciesDTO(List.of(), List.of(), List.of(),
                            "wrong-route", refs.policyVersionRef())),
                    withDependencies(good, new DefinitionDependenciesDTO(List.of(), List.of(), List.of(),
                            refs.modelRouteRef(), "wrong-policy")))) {
                fixture.store(invalid);
                assertUnavailable(query, fixture);
            }
            fixture.store(good);
            when(selected.getOptions()).thenReturn(modelOptions(64));
            assertUnavailable(query, fixture);

            var custom = mock(AgentModelBudgetEstimator.class);
            clearInvocations(selected);
            var customQuery = runtime.query(fixture.ledger, factory(new SpringAiModelAdapterFactory()), selected, custom);
            assertUnavailable(customQuery, fixture);
            verifyNoInteractions(selected, custom);

            var customCompiler = new AgentFlowFactory(new SpringAiModelAdapterFactory(),
                    new AgentWorkflowStrategyFactory(List.of())) { };
            var customCompilerQuery = runtime.query(fixture.ledger, customCompiler, selected, estimator);
            assertUnavailable(customCompilerQuery, fixture);
            verifyNoInteractions(selected);
        }
    }

    @Test
    void refuses_non_a1_shapes_and_an_executor_using_another_ledger() throws SQLException {
        try (var fixture = new Fixture(); var runtime = new ReadinessRuntime()) {
            var model = chatModel(32);
            var estimator = estimator(model);
            var factory = factory(new SpringAiModelAdapterFactory());
            var query = runtime.query(fixture.ledger, factory, model, estimator);
            var good = config(estimator, "USD", 0, Set.of(), singleLeaf());
            var refs = good.dependencies();
            var extraField = new java.util.HashMap<>(singleLeaf());
            extraField.put("workflows", List.of());
            var inexactModel = new java.util.HashMap<>(singleLeaf());
            inexactModel.put("modelName", MODEL_NAME + " ");
            var inexactRoot = new java.util.HashMap<>(singleLeaf());
            inexactRoot.put("rootAgentName", "reviewer ");
            inexactRoot.put("agents", List.of(Map.of("name", "reviewer ", "instruction", BODY)));
            for (var invalid : List.of(
                    config(estimator, "USD", 1, Set.of(), singleLeaf()),
                    config(estimator, "USD", 0, Set.of("tool@1"), singleLeaf()),
                    config(estimator, "USD", 0, Set.of(), extraField),
                    config(estimator, "USD", 0, Set.of(), inexactModel),
                    config(estimator, "USD", 0, Set.of(), inexactRoot),
                    withDependencies(good, new DefinitionDependenciesDTO(
                            List.of(new DefinitionResourceRef(RESOURCE_KEY, 1, "a".repeat(64))), List.of(), List.of(),
                            refs.modelRouteRef(), refs.policyVersionRef())))) {
                fixture.store(invalid);
                assertUnavailable(query, fixture);
            }
            var other = new AgentLedgerService(fixture.source);
            var wrongExecutor = runtime.executor(other, factory, model, estimator);
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentPublishedConfigReadinessQueryExe(fixture.ledger, wrongExecutor));
            assertNoProviderIo(model);
        }
    }

    private static void assertUnavailable(AgentPublishedConfigReadinessQueryExe query, Fixture fixture) {
        var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                () -> query.requireReady(OWNER, KEY, VERSION, fixture.hash));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(BODY));
    }

    private static void assertNoProviderIo(ChatModel... models) {
        for (var model : models) {
            verify(model, never()).call(any(Prompt.class));
            verify(model, never()).stream(any(Prompt.class));
        }
    }

    private static ChatOptions modelOptions(int maxTokens) {
        return ChatOptions.builder().model(MODEL_NAME).maxTokens(maxTokens).temperature(0.2).build();
    }

    private static ChatModel chatModel(int maxTokens) {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(modelOptions(maxTokens));
        return model;
    }

    private static JTokkitChatModelBudgetEstimator estimator(ChatModel model) {
        var profile = new ModelBudgetProperties.Profile("route", 1, "policy", 1, MODEL_BEAN, MODEL_NAME,
                4096, 64, 128, 8, 16, 32, BigDecimal.ONE, BigDecimal.valueOf(2), "USD", 8, 1, true, true);
        return new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile)), Map.of(MODEL_BEAN, model));
    }

    private static Map<String, Object> singleLeaf() {
        return Map.of("chatModelBeanName", MODEL_BEAN, "modelName", MODEL_NAME, "rootAgentName", "reviewer",
                "agents", List.of(Map.of("name", "reviewer", "instruction", BODY)));
    }

    private static AgentDefinitionSpecDTO config(JTokkitChatModelBudgetEstimator estimator, String currency,
                                                 int toolCalls, Set<String> toolVersions, Map<String, Object> configuration) {
        var descriptor = estimator.descriptors().getFirst();
        var dependencies = new DefinitionDependenciesDTO(List.of(), List.of(), List.of(),
                descriptor.modelRouteRef(), descriptor.policyVersionRef());
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(5),
                1, toolCalls, 128, 64, BigDecimal.ONE, currency, 1, toolVersions, null, null);
        return new AgentDefinitionSpecDTO(KEY, VERSION, "CONFIG", null, "in-v2", "out-v4", dependencies, policy, configuration);
    }

    private static AgentDefinitionSpecDTO withDependencies(AgentDefinitionSpecDTO source, DefinitionDependenciesDTO dependencies) {
        return new AgentDefinitionSpecDTO(source.definitionKey(), source.version(), source.executionMode(), source.codeRef(),
                source.inputSchemaVersion(), source.outputSchemaVersion(), dependencies, source.policy(), source.configuration());
    }

    private static AgentFlowFactory factory(SpringAiModelAdapterFactory adapterFactory) {
        return new AgentFlowFactory(adapterFactory, new AgentWorkflowStrategyFactory(List.of()));
    }

    private static final class ReadinessRuntime implements AutoCloseable {
        private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
        private final ScheduledThreadPoolExecutor control = new ScheduledThreadPoolExecutor(1);

        private AdkAgentRunExecutor executor(AgentLedgerService ledger, AgentFlowFactory factory, ChatModel model,
                                            AgentModelBudgetEstimator estimator) {
            return executor(ledger, factory, new AgentFlowConfigValidator(validationUtils()), model, estimator);
        }

        private ValidationUtils validationUtils() {
            return new ValidationUtils(validators.getValidator());
        }

        private AdkAgentRunExecutor executor(AgentLedgerService ledger, AgentFlowFactory factory,
                                            AgentFlowConfigValidator validator, ChatModel model,
                                            AgentModelBudgetEstimator estimator) {
            return new AdkAgentRunExecutor(ledger, factory, validator, Map.of(MODEL_BEAN, model), Map.of(), control, estimator);
        }

        private AgentPublishedConfigReadinessQueryExe query(AgentLedgerService ledger, AgentFlowFactory factory, ChatModel model,
                                                            AgentModelBudgetEstimator estimator) {
            return new AgentPublishedConfigReadinessQueryExe(ledger, executor(ledger, factory, model, estimator));
        }

        @Override
        public void close() {
            control.shutdownNow();
            validators.close();
        }
    }

    private static AgentLedgerException assertCode(AgentErrorCodeEnum expected, Executable operation) {
        var failure = assertThrows(AgentLedgerException.class, operation);
        assertEquals(expected, failure.code());
        return failure;
    }

    private static AgentDefinitionSpecDTO definition(String key, long version, boolean withOptionalDependency) {
        var prompts = withOptionalDependency ? List.of(new DefinitionResourceRef(RESOURCE_KEY, 1, "a".repeat(64)))
                : List.<DefinitionResourceRef>of();
        var dependencies = new DefinitionDependenciesDTO(prompts, List.of(), List.of(), "model-route@2", "budget-policy@3");
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(5),
                1, 0, 128, 64, null, null, 1, Set.of(), null, null);
        return new AgentDefinitionSpecDTO(key, version, "CONFIG", null, "in-v2", "out-v4", dependencies, policy,
                Map.of("instruction", BODY));
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final AgentJsonCodec json = new AgentJsonCodec();
        private final List<String> calls = new ArrayList<>();
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private String hash;
        private String specification;
        private Instant observed = OBSERVED;
        private boolean sourceFailed;

        private Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = new AgentLedgerService(source);
            store(definition(KEY, VERSION, false));
        }

        private void store(AgentDefinitionSpecDTO spec) {
            hash = json.definitionHash(spec);
            specification = json.write(spec, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        }

        private Object jdbcCall(InvocationOnMock call) throws SQLException {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var args = call.getRawArguments();
            String sql = (String) args[0];
            return switch (call.getMethod().getName()) {
                case "execute" -> {
                    if ("SET TRANSACTION READ ONLY".equals(sql)) calls.add("read-only");
                    else if ("SET LOCAL statement_timeout = '2s'".equals(sql)) calls.add("timeout");
                    else throw new AssertionError("Unexpected statement");
                    yield null;
                }
                case "queryForObject" -> {
                    calls.add("primary-clock");
                    assertTrue(sql.contains("NOT pg_catalog.pg_is_in_recovery()"));
                    assertTrue(sql.contains("pg_catalog.current_setting('transaction_read_only') = 'on'"));
                    yield observed == null ? null : Timestamp.from(observed);
                }
                case "query" -> {
                    calls.add("definition");
                    assertTrue(sql.contains("SELECT content_sha256, specification::text AS specification"));
                    assertTrue(sql.contains("FROM tianji_agent.agent_definition_version"));
                    assertTrue(sql.contains("WHERE tenant_id = ? AND definition_key = ? AND version = ?"));
                    assertFalse(sql.contains("FOR UPDATE"));
                    if (sourceFailed) throw new DataAccessResourceFailureException(BODY);
                    var bindings = (Object[]) args[2];
                    if (!List.of(42L, KEY, VERSION).equals(List.of(bindings))) yield List.of();
                    var row = mock(ResultSet.class);
                    when(row.getString("content_sha256")).thenReturn(hash);
                    when(row.getString("specification")).thenReturn(specification);
                    yield List.of(((RowMapper<?>) args[1]).mapRow(row, 0));
                }
                default -> throw new AssertionError("Unexpected JDBC method");
            };
        }

        @Override public void close() { jdbc.close(); }
    }
}
