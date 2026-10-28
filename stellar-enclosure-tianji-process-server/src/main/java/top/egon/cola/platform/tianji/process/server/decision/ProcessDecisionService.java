package top.egon.cola.platform.tianji.process.server.decision;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Standalone decision application service; the caller must authorize the trusted actor and release. */
public final class ProcessDecisionService {
    private static final int MAX_XML_BYTES = 512 * 1024;
    private static final int MAX_RESULT_BYTES = 1_048_576;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private final SpringDmnEngineConfiguration engine;
    private final ProcessModelValidator validator;
    private final ProcessReleaseService releases;
    private final FlowablePinnedDecisionExecutor executor;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate transaction;

    public ProcessDecisionService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration engine,
                                  ProcessModelValidator validator) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.validator = Objects.requireNonNull(validator, "validator");
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

    private Result evaluateInTransaction(ProcessActor actor, EvaluateCommand command) {
        var release = releases.get(actor, command.releaseKey());
        Definition binding = release.definitions().stream().filter(item -> item.type() == Type.DMN
                && item.definitionKey().equals(command.decisionKey())).findFirst().orElseThrow(ProcessDecisionService::unavailable);
        ValidatedProcessModel model = loadModel(actor.tenantId(), binding);
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

    private ValidatedProcessModel loadModel(long tenantId, Definition binding) {
        var repository = engine.getDmnRepositoryService();
        DmnDecision definition = repository.createDecisionQuery().decisionId(binding.engineDefinitionId())
                .decisionTenantId(Long.toString(tenantId)).deploymentId(binding.engineDeploymentId())
                .decisionKey(binding.definitionKey()).decisionVersion(binding.engineVersion()).singleResult();
        if (definition == null) throw unavailable();
        try (InputStream source = repository.getDmnResource(binding.engineDefinitionId())) {
            if (source == null) throw unavailable();
            byte[] xml = source.readNBytes(MAX_XML_BYTES + 1);
            if (!hash(xml).equals(binding.modelSha256())) throw unavailable();
            ValidatedProcessModel model = validator.validateDmn(xml, binding.definitionKey());
            if (!model.inputs().equals(binding.inputs())) throw unavailable();
            return model;
        } catch (FlowableObjectNotFoundException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Decision model could not be read safely");
        }
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
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
    private static IllegalArgumentException unavailable() { return new IllegalArgumentException("Decision or release is unavailable"); }
    private static IllegalStateException conflict() { return new IllegalStateException("Decision request ID already binds different content"); }
    private static IllegalStateException evaluationFailed() { return new IllegalStateException("Decision evaluation failed"); }
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
