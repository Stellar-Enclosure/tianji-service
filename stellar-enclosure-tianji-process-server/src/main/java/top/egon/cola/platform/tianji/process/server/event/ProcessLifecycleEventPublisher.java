package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.transaction.OutboxTransactionGuard;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessLifecycleEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.regex.Pattern;

/** Publishes reference-only facts under the native root lock and the owning Process JDBC transaction. */
@Slf4j
@Validated
public class ProcessLifecycleEventPublisher {
    public static final String CHANNEL = "tianji-process-lifecycle";
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_EVENT_BYTES = 16 * 1024;
    private static final int MAX_OUTBOX_MESSAGE_ID = 64;
    private static final String EVENT_ID_PREFIX = "pl-";
    private static final Pattern SOURCE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern DESTINATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");

    private final TransactionalOutbox outbox;
    private final OutboxTransactionGuard guard;
    private final String sourceId;
    private final String destination;

    public ProcessLifecycleEventPublisher(OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                          String sourceId, String destination) {
        if (infrastructure == null || infrastructure.dataSource() == null
                || !(infrastructure.transactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() != infrastructure.dataSource()
                || sourceId == null || !SOURCE_ID.matcher(sourceId).matches()
                || destination == null || !DESTINATION.matcher(destination).matches()) {
            throw new IllegalArgumentException("Process lifecycle Outbox binding is invalid");
        }
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.guard = new OutboxTransactionGuard(infrastructure.dataSource());
        this.sourceId = sourceId;
        this.destination = destination;
    }

    public void publish(@NotNull AutomationInstanceBindingBO binding, @NotNull String type, @NotNull Instant occurredAt,
                        String occurrenceId, Integer attempt, String taskId, String resultRef) {
        Objects.requireNonNull(binding, "binding");
        AutomationInstanceGuardCommandInterceptor.requireHeld(binding.getTenantId(), binding.getInstanceId());
        guard.requireSelectedTransaction();
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Process lifecycle Outbox requires the owning write transaction");
        }
        try {
            String eventId = eventId(binding, type, occurrenceId, attempt, taskId);
            var event = new ProcessLifecycleEventV2(SCHEMA_VERSION, eventId, binding.getTenantId(), sourceId,
                    binding.getInstanceId(), binding.getReleaseKey(), type, occurredAt, occurrenceId, attempt, taskId, resultRef);
            var receipt = outbox.enqueue(OutboxMessage.builder().messageId(eventId).idempotencyKey(eventId)
                    .channel(CHANNEL).destination(destination).schemaVersion(Integer.toString(SCHEMA_VERSION))
                    .payload(encode(event)).build());
            if (receipt == null || !eventId.equals(receipt.messageId()) || !eventId.equals(receipt.idempotencyKey())) {
                throw new IllegalStateException("Process lifecycle Outbox receipt does not match the event");
            }
        } catch (RuntimeException | Error failure) {
            guard.markRollbackOnly();
            throw failure;
        }
    }

    private String eventId(AutomationInstanceBindingBO binding, String type, String occurrenceId, Integer attempt, String taskId) {
        var transition = Arrays.asList(binding.getTenantId(), sourceId, binding.getInstanceId(), binding.getReleaseKey(),
                type, occurrenceId, attempt, taskId);
        var hash = ProcessTypedJson.sha256("AUTOMATION_PROCESS_LIFECYCLE_EVENT_V2",
                ProcessTypedJson.canonicalBytes(transition, MAX_EVENT_BYTES));
        return EVENT_ID_PREFIX + hash.substring(0, MAX_OUTBOX_MESSAGE_ID - EVENT_ID_PREFIX.length());
    }

    private static String encode(ProcessLifecycleEventV2 event) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("schemaVersion", event.schemaVersion());
        wire.put("eventId", event.eventId());
        wire.put("tenantId", event.tenantId());
        wire.put("sourceId", event.sourceId());
        wire.put("instanceId", event.instanceId());
        wire.put("releaseVersion", event.releaseVersion());
        wire.put("type", event.type());
        wire.put("occurredAt", event.occurredAt().toString());
        if (event.occurrenceId() != null) {
            wire.put("occurrenceId", event.occurrenceId());
            wire.put("attempt", event.attempt());
        }
        if (event.taskId() != null) wire.put("taskId", event.taskId());
        if (event.resultRef() != null) wire.put("resultRef", event.resultRef());
        return new String(ProcessTypedJson.canonicalBytes(wire, MAX_EVENT_BYTES), StandardCharsets.UTF_8);
    }
}
