package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal process entry point; the caller must authenticate and authorize the supplied actor. */
public final class ProcessExecutionService {
    private final SpringProcessEngineConfiguration engine;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.models = new VerifiedProcessModels(engine, dmn, validator);
        this.releases = new ProcessReleaseService(engine, dmn, validator);
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || engine.getRuntimeService() == null || engine.getHistoryService() == null
                || !engine.isDbHistoryUsed() || engine.getHistoryLevel() == null
                || !engine.getHistoryLevel().isAtLeast(HistoryLevel.INSTANCE) || engine.isAsyncHistoryEnabled()) {
            throw new IllegalArgumentException("Process execution requires synchronous instance history and a shared JDBC transaction manager");
        }
        this.receipts = new ProcessCommandReceiptStore(new JdbcTemplate(manager.getDataSource()), engine.getDatabaseSchema());
        this.write = new TransactionTemplate(manager);
        this.read = new TransactionTemplate(manager);
        this.read.setReadOnly(true);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Snapshot start(ProcessActor actor, StartCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        return Objects.requireNonNull(write.execute(status -> startInTransaction(actor, command)));
    }

    public Snapshot get(ProcessActor actor, String instanceId) {
        Objects.requireNonNull(actor, "actor");
        if (!validId(instanceId, 255)) throw invalid();
        return Objects.requireNonNull(read.execute(status -> snapshot(actor, receipts.startByInstance(actor, instanceId))));
    }

    private Snapshot startInTransaction(ProcessActor actor, StartCommand command) {
        Release release = releases.get(actor, command.releaseKey());
        Definition binding = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.definitionKey().equals(command.definitionKey())).findFirst().orElseThrow(ProcessExecutionService::unavailable);
        Map<String, Object> inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String requestHash = ProcessRequestDigest.hash(List.of(command.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId()), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), requestHash,
                command.releaseKey(), command.correlationId())) {
            var existing = receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
            if (!existing.requestHash().equals(requestHash)) throw conflict();
            if (!"APPLIED".equals(existing.state()) || existing.resourceId() == null
                    || !command.releaseKey().equals(existing.releaseKey())
                    || !command.correlationId().equals(existing.correlationId())) throw unavailable();
            return snapshot(actor, existing);
        }
        models.process(actor.tenantId(), binding);
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(binding.engineDefinitionId()).tenantId(Long.toString(actor.tenantId()))
                .businessKey(command.businessKey()).variables(new HashMap<>(inputs)).start();
        if (instance == null || !binding.engineDefinitionId().equals(instance.getProcessDefinitionId())
                || !Long.toString(actor.tenantId()).equals(instance.getTenantId()) || !validId(instance.getId(), 255)) {
            throw new IllegalStateException("Process instance does not match its release");
        }
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), instance.getId(), null);
        return snapshot(actor, receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId()));
    }

    private Snapshot snapshot(ProcessActor actor, ProcessCommandReceiptStore.Receipt receipt) {
        if (!"APPLIED".equals(receipt.state()) || receipt.resourceId() == null) throw unavailable();
        Release release = releases.get(actor, receipt.releaseKey());
        String tenant = Long.toString(actor.tenantId());
        ProcessInstance current = engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (current != null) {
            Definition definition = releaseDefinition(release, current.getProcessDefinitionId());
            return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), current.getBusinessKey(),
                    receipt.correlationId(), current.isSuspended() ? State.SUSPENDED : State.ACTIVE, receipt.createdAt(), null);
        }
        HistoricProcessInstance history = engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (history == null || history.getEndTime() == null) throw unavailable();
        Definition definition = releaseDefinition(release, history.getProcessDefinitionId());
        State state = history.getDeleteReason() == null ? State.COMPLETED : State.CANCELLED;
        return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), history.getBusinessKey(),
                receipt.correlationId(), state, receipt.createdAt(), history.getEndTime().toInstant());
    }

    private static Definition releaseDefinition(Release release, String definitionId) {
        return release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.engineDefinitionId().equals(definitionId)).findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private static boolean validId(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process request is invalid"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    public enum State { ACTIVE, SUSPENDED, COMPLETED, CANCELLED }

    public record StartCommand(String releaseKey, String definitionKey, String businessKey, String commandId,
                               String correlationId, Map<String, Object> variables) {
        public StartCommand {
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || !validId(businessKey, 255) || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || variables == null) throw invalid();
            try { variables = Map.copyOf(variables); }
            catch (RuntimeException failure) { throw invalid(); }
        }
    }

    public record Snapshot(String instanceId, String releaseKey, String definitionKey, String businessKey,
                           String correlationId, State state, Instant submittedAt, Instant finishedAt) { }
}
