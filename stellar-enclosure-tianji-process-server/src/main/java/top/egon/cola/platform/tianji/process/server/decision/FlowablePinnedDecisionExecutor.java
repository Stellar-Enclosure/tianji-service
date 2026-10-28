package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.ExecuteDecisionContext;
import org.flowable.dmn.engine.impl.cmd.ExecuteDecisionWithAuditTrailCmd;
import org.flowable.dmn.engine.impl.cmd.PersistHistoricDecisionExecutionCmd;
import org.flowable.dmn.engine.impl.persistence.entity.DecisionEntity;
import org.flowable.dmn.engine.impl.util.CommandContextUtil;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal adapter; its binding must be loaded from an already authorized tenant-scoped release. */
public final class FlowablePinnedDecisionExecutor {
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
        return evaluate(binding, inputs, null);
    }

    /** A supplied execution reference is platform-owned and is not a BPMN process instance. */
    public Result evaluate(Binding binding, Map<String, Object> inputs, String executionReference) {
        Objects.requireNonNull(binding, "binding");
        if (executionReference != null && !executionReference.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Decision execution reference is invalid");
        }
        Map<String, Object> checked = ProcessValues.inputs(binding.model().inputs(), inputs);
        return Objects.requireNonNull(transaction.execute(status -> evaluateInTransaction(binding, checked, executionReference)));
    }

    private Result evaluateInTransaction(Binding binding, Map<String, Object> inputs, String executionReference) {
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
        if (executionReference != null) {
            context.setInstanceId(executionReference);
            context.setScopeType("tianji-decision");
        }
        engine.getCommandExecutor().execute(new PinnedCommand(context, binding, engine));
        DecisionExecutionAuditContainer audit = context.getDecisionExecution();
        if (audit == null || !Boolean.FALSE.equals(audit.isFailed())
                || !binding.definitionId().equals(context.getDecisionId())
                || !binding.deploymentId().equals(context.getDeploymentId())
                || binding.definitionVersion() != context.getDecisionVersion()
                || binding.definitionVersion() != audit.getDecisionVersion()
                || !binding.model().definitionKey().equals(audit.getDecisionKey())) {
            throw new ProcessDecisionFailedException();
        }
        // Flowable's public decision service persists history in a second command after execution.
        if (engine.isHistoryEnabled()) engine.getCommandExecutor().execute(new PersistHistoricDecisionExecutionCmd(context));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (audit.getDecisionResult() != null) {
            if (audit.getDecisionResult().size() > 256) throw new IllegalStateException("Decision evaluation failed");
            for (Map<String, Object> result : audit.getDecisionResult()) rows.add(ProcessValues.outputs(binding.model().outputs(), result));
        }
        List<Integer> matchedRules = audit.getRuleExecutions().values().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.isValid())).map(rule -> rule.getRuleNumber()).sorted().toList();
        return new Result(binding.definitionId(), binding.definitionVersion(), rows, matchedRules);
    }

    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }

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
