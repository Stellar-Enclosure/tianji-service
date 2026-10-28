package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.ExecuteDecisionContext;
import org.flowable.dmn.engine.impl.cmd.ExecuteDecisionWithAuditTrailCmd;
import org.flowable.dmn.engine.impl.persistence.entity.DecisionEntity;
import org.flowable.dmn.engine.impl.util.CommandContextUtil;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal adapter; its binding must be loaded from an already authorized tenant-scoped release. */
public final class FlowablePinnedDecisionExecutor {
    private static final long MAX_INTEGER = 999_999_999_999_999L;
    private static final int MAX_XML_BYTES = 512 * 1024;
    private final SpringDmnEngineConfiguration engine;
    private final TransactionTemplate transaction;

    public FlowablePinnedDecisionExecutor(SpringDmnEngineConfiguration engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)) throw invalidConfiguration();
        DataSource source = engine.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        if (source == null || source != manager.getDataSource() || engine.getDmnRepositoryService() == null
                || engine.getCommandExecutor() == null || engine.getBeans() == null || !engine.getBeans().isEmpty()) {
            throw invalidConfiguration();
        }
        this.transaction = new TransactionTemplate(manager);
    }

    public Result evaluate(Binding binding, Map<String, Object> inputs) {
        Objects.requireNonNull(binding, "binding");
        Map<String, Object> checked = values(binding.model().inputs(), inputs, false);
        return Objects.requireNonNull(transaction.execute(status -> evaluateInTransaction(binding, checked)));
    }

    private Result evaluateInTransaction(Binding binding, Map<String, Object> inputs) {
        var repository = engine.getDmnRepositoryService();
        DmnDecision definition = repository.createDecisionQuery().decisionId(binding.definitionId())
                .deploymentId(binding.deploymentId()).decisionVersion(binding.definitionVersion())
                .decisionKey(binding.model().definitionKey()).decisionTenantId(Long.toString(binding.tenantId())).singleResult();
        if (definition == null) throw unavailable();
        try (InputStream source = repository.getDmnResource(binding.definitionId())) {
            if (source == null || !MessageDigest.isEqual(source.readNBytes(MAX_XML_BYTES + 1), binding.model().xml())) throw unavailable();
        } catch (FlowableObjectNotFoundException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Decision model could not be read safely");
        }

        ExecuteDecisionContext context = new ExecuteDecisionContext();
        context.setDecisionKey(binding.model().definitionKey());
        context.setTenantId(Long.toString(binding.tenantId()));
        context.setVariables(new HashMap<>(inputs));
        engine.getCommandExecutor().execute(new PinnedCommand(context, binding, engine));
        DecisionExecutionAuditContainer audit = context.getDecisionExecution();
        if (audit == null || !Boolean.FALSE.equals(audit.isFailed())
                || !binding.definitionId().equals(context.getDecisionId())
                || !binding.deploymentId().equals(context.getDeploymentId())
                || binding.definitionVersion() != context.getDecisionVersion()
                || binding.definitionVersion() != audit.getDecisionVersion()
                || !binding.model().definitionKey().equals(audit.getDecisionKey())) {
            throw new IllegalStateException("Decision evaluation failed");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (audit.getDecisionResult() != null) {
            if (audit.getDecisionResult().size() > 256) throw new IllegalStateException("Decision evaluation failed");
            for (Map<String, Object> result : audit.getDecisionResult()) rows.add(values(binding.model().outputs(), result, true));
        }
        List<Integer> matchedRules = audit.getRuleExecutions().values().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.isValid())).map(rule -> rule.getRuleNumber()).sorted().toList();
        return new Result(binding.definitionId(), binding.definitionVersion(), rows, matchedRules);
    }

    private static Map<String, Object> values(Map<String, ValueType> schema, Map<String, Object> values, boolean output) {
        if (values == null || !schema.keySet().equals(values.keySet())) throw invalidValues(output);
        Map<String, Object> checked = new LinkedHashMap<>();
        schema.forEach((key, type) -> {
            Object value = values.get(key);
            if (type == ValueType.STRING && value instanceof String text && safeText(text)) checked.put(key, text);
            else if (type == ValueType.BOOLEAN && value instanceof Boolean flag) checked.put(key, flag);
            else if (type == ValueType.INTEGER) checked.put(key, integer(value, output));
            else throw invalidValues(output);
        });
        return Map.copyOf(checked);
    }

    private static Long integer(Object value, boolean output) {
        try {
            long number;
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                number = ((Number) value).longValue();
            } else if (value instanceof BigInteger integer) number = integer.longValueExact();
            else if (output && value instanceof BigDecimal decimal) number = decimal.longValueExact();
            else if (output && (value instanceof Float || value instanceof Double)) {
                double decimal = ((Number) value).doubleValue();
                if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                        || decimal < -MAX_INTEGER || decimal > MAX_INTEGER) throw invalidValues(true);
                number = (long) decimal;
            } else throw invalidValues(output);
            if (number < -MAX_INTEGER || number > MAX_INTEGER) throw invalidValues(output);
            return number;
        } catch (ArithmeticException failure) {
            throw invalidValues(output);
        }
    }

    private static boolean safeText(String value) {
        if (value.length() > 1_024 || value.getBytes(StandardCharsets.UTF_8).length > 4_096) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\0' || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }

    private static RuntimeException invalidValues(boolean output) {
        return output ? new IllegalStateException("Decision evaluation failed")
                : new IllegalArgumentException("Decision inputs do not match the release schema");
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("Decision is unavailable for this release");
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Decision engine requires isolated beans and a shared JDBC transaction manager");
    }

    /** This value is internal and is not an authorization decision or an external request DTO. */
    public record Binding(long tenantId, String definitionId, String deploymentId, int definitionVersion,
                          ValidatedProcessModel model) {
        public Binding {
            if (tenantId <= 0 || !validId(definitionId) || !validId(deploymentId) || definitionVersion <= 0
                    || model == null || model.type() != Type.DMN) throw unavailable();
        }

        private static boolean validId(String id) { return id != null && !id.isBlank() && id.length() <= 255; }
    }

    public record Result(String definitionId, int definitionVersion, List<Map<String, Object>> outputs,
                         List<Integer> matchedRuleNumbers) {
        public Result {
            outputs = outputs.stream().map(Map::copyOf).toList();
            matchedRuleNumbers = List.copyOf(matchedRuleNumbers);
        }
    }

    /** Reuses Flowable's command template while replacing its latest-version fallback with exact-ID lookup. */
    private static final class PinnedCommand extends ExecuteDecisionWithAuditTrailCmd {
        private final Binding binding;
        private final SpringDmnEngineConfiguration expectedEngine;

        private PinnedCommand(ExecuteDecisionContext context, Binding binding, SpringDmnEngineConfiguration engine) {
            super(context);
            this.binding = binding;
            this.expectedEngine = engine;
        }

        @Override
        protected DmnDefinition resolveDefinition() {
            var configuration = CommandContextUtil.getDmnEngineConfiguration();
            if (configuration != expectedEngine) throw unavailable();
            DecisionEntity definition = configuration.getDecisionEntityManager().findById(binding.definitionId());
            if (definition == null || !Long.toString(binding.tenantId()).equals(definition.getTenantId())
                    || !binding.model().definitionKey().equals(definition.getKey())
                    || !binding.deploymentId().equals(definition.getDeploymentId())
                    || binding.definitionVersion() != definition.getVersion()) throw unavailable();
            executeDecisionContext.setDecisionId(definition.getId());
            executeDecisionContext.setDecisionVersion(definition.getVersion());
            executeDecisionContext.setDeploymentId(definition.getDeploymentId());
            return configuration.getDeploymentManager().resolveDecision(definition).getDmnDefinition();
        }
    }
}
