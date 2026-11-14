package top.egon.cola.platform.tianji.process.server.decision;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels.VerifiedAutomationModelsVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Standalone decision application service; the caller must authorize the trusted actor and release. */
public final class ProcessDecisionService {
    private static final int MAX_RESULT_BYTES = 1_048_576;
    private static final int MAX_AUTOMATION_NODE_BYTES = 64 * 1024;
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\[(?:0|[1-9][0-9]{0,2})\\]");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private final SpringDmnEngineConfiguration engine;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final FlowablePinnedDecisionExecutor executor;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate transaction;

    public ProcessDecisionService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration engine,
                                  ProcessModelValidator validator) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.models = new VerifiedProcessModels(process, engine, validator);
        this.releases = new ProcessReleaseService(process, engine, validator);
        this.executor = new FlowablePinnedDecisionExecutor(engine);
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || !engine.isHistoryEnabled() || engine.getDmnHistoryService() == null) {
            throw new IllegalArgumentException("Decision execution requires synchronous history and a shared JDBC transaction manager");
        }
        this.receipts = new ProcessCommandReceiptStore(new JdbcTemplate(manager.getDataSource()), engine.getDatabaseSchema());
        this.transaction = new TransactionTemplate(manager);
    }

    public Result evaluate(ProcessActor actor, EvaluateCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        return Objects.requireNonNull(transaction.execute(status -> evaluateInTransaction(actor, command)));
    }

    /** Executes a fixed rule in the caller's native instance command and returns its typed node-local output. */
    public static Map<String, Object> evaluateAutomation(SpringDmnEngineConfiguration engine, VerifiedAutomationModelsVO verified,
            AutomationNodeBindingVO binding, ProcessNodeExecutionBO node, FlowablePinnedDecisionExecutor.ProcessReference reference) {
        AutomationInstanceGuardCommandInterceptor.requireHeld(node.getTenantId(), node.getInstanceId());
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || !TransactionSynchronizationManager.hasResource(manager.getDataSource())
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !engine.isHistoryEnabled() || engine.getDmnHistoryService() == null) {
            throw new IllegalStateException("Automation decision requires the owning process transaction and synchronous history");
        }
        if (binding.rule() == null) throw unavailable();
        String key = binding.rule().definitionKey();
        return evaluateAutomation(new FlowablePinnedDecisionExecutor(engine), binding, node,
                verified.decisionDefinitions().get(key), verified.decisions().get(key), reference);
    }

    static Map<String, Object> evaluateAutomation(FlowablePinnedDecisionExecutor executor, AutomationNodeBindingVO binding,
            ProcessNodeExecutionBO node, Definition definition, ValidatedProcessModel model,
            FlowablePinnedDecisionExecutor.ProcessReference reference) {
        var rule = binding.rule();
        if (rule == null || node.getKind() != KindEnum.RULE || node.getState() != StateEnum.RUNNING || node.isExcluded()
                || !binding.nodeId().equals(node.getNodeId()) || !reference.instanceId().equals(node.getInstanceId())
                || !ProcessNodeLifecycleListener.schemaSha256(binding.inputSchema()).equals(node.getInputSchemaSha256())
                || !ProcessNodeLifecycleListener.schemaSha256(binding.outputSchema()).equals(node.getOutputSchemaSha256())
                || definition == null || model == null || definition.type() != Type.DMN || model.type() != Type.DMN
                || !rule.definitionKey().equals(definition.definitionKey()) || !rule.definitionKey().equals(model.definitionKey())
                || !rule.modelSha256().equals(definition.modelSha256()) || !rule.modelSha256().equals(modelSha256(model))
                || !definition.inputs().equals(model.inputs())) throw unavailable();

        var typedInput = binding.inputSchema().normalizeValue(node.getInput());
        ProcessTypedJson.canonicalBytes(typedInput, MAX_AUTOMATION_NODE_BYTES);
        var dmnInput = new LinkedHashMap<String, Object>();
        for (var mapping : rule.inputMapping()) {
            dmnInput.put(mapping.target(), readLocalPath(typedInput, stripScope(mapping.source(), "input.")));
        }
        var input = ProcessValues.inputs(model.inputs(), dmnInput);
        var selected = new FlowablePinnedDecisionExecutor.Binding(node.getTenantId(), definition.engineDefinitionId(),
                definition.engineDeploymentId(), definition.engineVersion(), model);
        var result = executor.evaluateAutomationForProcess(selected, input, reference);
        if (result == null || !selected.definitionId().equals(result.definitionId())
                || selected.definitionVersion() != result.definitionVersion()) throw evaluationFailed();
        return automationOutput(binding, model, result.outputs());
    }

    private static Map<String, Object> automationOutput(AutomationNodeBindingVO binding, ValidatedProcessModel model,
                                                         List<Map<String, Object>> outputs) {
        try {
            var output = new LinkedHashMap<String, Object>();
            if (!outputs.isEmpty()) {
                var row = ProcessValues.outputs(model.outputs(), outputs.getFirst());
                if (outputs.stream().anyMatch(candidate -> !row.equals(ProcessValues.outputs(model.outputs(), candidate)))) {
                    throw evaluationFailed();
                }
                for (var mapping : binding.rule().outputMapping()) {
                    String key = stripScope(mapping.source(), "scope.");
                    if (!row.containsKey(key)) throw evaluationFailed();
                    writeLocalPath(output, mapping.target(), row.get(key));
                }
            }
            var normalized = binding.outputSchema().normalizeValue(output);
            ProcessTypedJson.canonicalBytes(normalized, MAX_AUTOMATION_NODE_BYTES);
            return typedObject(normalized);
        } catch (IllegalArgumentException failure) {
            throw evaluationFailed();
        }
    }

    private static Object readLocalPath(Object object, String path) {
        Object value = object;
        for (String segment : segments(path)) {
            if (segment.startsWith("[")) {
                int index = arrayIndex(segment);
                if (!(value instanceof List<?> array) || index >= array.size()) throw invalid();
                value = array.get(index);
            } else {
                if (!(value instanceof Map<?, ?> fields) || !fields.containsKey(segment)) throw invalid();
                value = fields.get(segment);
            }
        }
        return value;
    }

    private static void writeLocalPath(Map<String, Object> object, String path, Object value) {
        var segments = segments(path);
        Object parent = object;
        for (int index = 0; index < segments.size(); index++) {
            String segment = segments.get(index);
            Object child = index + 1 == segments.size() ? value
                    : segments.get(index + 1).startsWith("[") ? new ArrayList<>() : new LinkedHashMap<>();
            if (segment.startsWith("[")) {
                var array = typedArray(parent);
                int item = arrayIndex(segment);
                while (array.size() <= item) array.add(null);
                if (array.get(item) == null) array.set(item, child);
                parent = array.get(item);
            } else {
                parent = typedObject(parent).computeIfAbsent(segment, ignored -> child);
            }
        }
    }

    private static List<String> segments(String path) {
        return PATH_SEGMENT.matcher(path).results().map(match -> match.group()).toList();
    }

    private static String stripScope(String source, String scope) {
        if (!source.startsWith(scope)) throw invalid();
        return source.substring(scope.length());
    }

    private static int arrayIndex(String segment) {
        return Integer.parseInt(segment, 1, segment.length() - 1, 10);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> typedObject(Object value) {
        if (!(value instanceof Map<?, ?> object)) throw invalid();
        return (Map<String, Object>) object;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> typedArray(Object value) {
        if (!(value instanceof List<?> array)) throw invalid();
        return (List<Object>) array;
    }

    private static String modelSha256(ValidatedProcessModel model) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(model.xml()));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private Result evaluateInTransaction(ProcessActor actor, EvaluateCommand command) {
        var release = releases.get(actor, command.releaseKey());
        Definition binding = release.definitions().stream().filter(item -> item.type() == Type.DMN
                && item.definitionKey().equals(command.decisionKey())).findFirst().orElseThrow(ProcessDecisionService::unavailable);
        ValidatedProcessModel model = models.decision(actor.tenantId(), binding);
        Map<String, Object> inputs = ProcessValues.inputs(model.inputs(), command.inputs());
        String hash = ProcessRequestDigest.hash(List.of(command.releaseKey(), command.decisionKey(), command.correlationId()), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.DECISION, command.requestId(), hash,
                command.releaseKey(), command.correlationId())) {
            var existing = receipts.lock(actor, ProcessCommandReceiptStore.Kind.DECISION, command.requestId());
            if (!existing.requestHash().equals(hash)) throw conflict();
            if (!"APPLIED".equals(existing.state()) || existing.resourceId() == null || existing.decisionResult() == null
                    || !command.releaseKey().equals(existing.releaseKey())
                    || !command.correlationId().equals(existing.correlationId())) throw unavailable();
            return decode(existing, binding, model);
        }
        String executionId = UUID.randomUUID().toString();
        var selected = new FlowablePinnedDecisionExecutor.Binding(actor.tenantId(), binding.engineDefinitionId(),
                binding.engineDeploymentId(), binding.engineVersion(), model);
        var execution = executor.evaluate(selected, inputs, executionId);
        var history = engine.getDmnHistoryService().createHistoricDecisionExecutionQuery().instanceId(executionId)
                .scopeType("tianji-decision").tenantId(Long.toString(actor.tenantId()))
                .decisionDefinitionId(binding.engineDefinitionId()).singleResult();
        if (history == null || history.isFailed() || !binding.engineDeploymentId().equals(history.getDeploymentId())) {
            throw new IllegalStateException("Decision execution history is inconsistent");
        }
        List<String> matched = new ArrayList<>();
        for (Integer number : execution.matchedRuleNumbers()) {
            if (number == null || number < 1 || number > model.ruleIds().size()) throw evaluationFailed();
            matched.add(model.ruleIds().get(number - 1));
        }
        String result = encode(execution.outputs(), matched);
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.DECISION, command.requestId(), executionId, result);
        return decode(receipts.lock(actor, ProcessCommandReceiptStore.Kind.DECISION, command.requestId()), binding, model);
    }

    private static String encode(List<Map<String, Object>> rows, List<String> rules) {
        try {
            String value = MAPPER.writeValueAsString(Map.of("codecVersion", 1, "outputs", rows, "matchedRuleIds", rules));
            requireSize(value);
            return value;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw evaluationFailed();
        }
    }

    private static Result decode(ProcessCommandReceiptStore.Receipt receipt, Definition binding, ValidatedProcessModel model) {
        try {
            String json = receipt.decisionResult();
            requireSize(json);
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject() || root.size() != 3 || !root.path("codecVersion").isInt()
                    || root.path("codecVersion").intValue() != 1 || !root.path("outputs").isArray()
                    || !root.path("matchedRuleIds").isArray() || root.path("outputs").size() > 256
                    || root.path("matchedRuleIds").size() > model.ruleIds().size()) throw evaluationFailed();
            List<Map<String, Object>> outputs = new ArrayList<>();
            for (JsonNode row : root.path("outputs")) {
                if (!row.isObject()) throw evaluationFailed();
                outputs.add(ProcessValues.outputs(model.outputs(), MAPPER.convertValue(row, OBJECT)));
            }
            List<String> matched = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int previous = -1;
            for (JsonNode rule : root.path("matchedRuleIds")) {
                if (!rule.isTextual() || !model.ruleIds().contains(rule.textValue()) || !seen.add(rule.textValue())) throw evaluationFailed();
                int position = model.ruleIds().indexOf(rule.textValue());
                if (position <= previous) throw evaluationFailed();
                previous = position;
                matched.add(rule.textValue());
            }
            if (outputs.isEmpty() != matched.isEmpty()
                    || receipt.resourceId() == null
                    || !receipt.resourceId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw evaluationFailed();
            return new Result(receipt.resourceId(), receipt.releaseKey(), binding.definitionKey(), receipt.correlationId(),
                    receipt.createdAt(), outputs, matched);
        } catch (JsonProcessingException | RuntimeException failure) {
            throw evaluationFailed();
        }
    }

    private static void requireSize(String value) {
        if (value == null || value.length() > MAX_RESULT_BYTES || value.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
            throw evaluationFailed();
        }
    }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    private static ProcessDecisionFailedException evaluationFailed() { return new ProcessDecisionFailedException(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Decision request is invalid"); }

    public record EvaluateCommand(String releaseKey, String decisionKey, String requestId, String correlationId,
                                  Map<String, Object> inputs) {
        public EvaluateCommand {
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || decisionKey == null || !decisionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || inputs == null) throw invalid();
            try { inputs = Map.copyOf(inputs); }
            catch (RuntimeException failure) { throw invalid(); }
        }
    }

    public record Result(String executionId, String releaseKey, String decisionKey, String correlationId,
                         Instant submittedAt, List<Map<String, Object>> outputs, List<String> matchedRuleIds) {
        public Result {
            outputs = outputs.stream().map(Map::copyOf).toList();
            matchedRuleIds = List.copyOf(matchedRuleIds);
        }
    }
}
