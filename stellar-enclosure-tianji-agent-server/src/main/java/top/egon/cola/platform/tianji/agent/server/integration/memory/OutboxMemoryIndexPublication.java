package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.transaction.OutboxTransactionGuard;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.LegacyMemoryStoreAdapter;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

/** Enlists reference-only build/deletion intentions in the exact transaction that changes Memory facts. */
public final class OutboxMemoryIndexPublication implements MemoryIndexPublication {
    public static final String CHANNEL = "tianji-memory-index";
    public static final String DESTINATION = "agent-memory";
    static final String FINGERPRINT = "tianji-memory-index-fingerprint";
    private static final String EVENT_ID_PREFIX = "memory-index:";
    private final DataSource source;
    private final Binding binding;
    private final TransactionalOutbox outbox;
    private final OutboxTransactionGuard guard;
    private final LegacyMemoryStoreAdapter authority;

    OutboxMemoryIndexPublication(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox, Binding binding) {
        this(source, infrastructure, outbox, binding, null);
    }

    OutboxMemoryIndexPublication(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox, Binding binding,
                                 LegacyMemoryStoreAdapter authority) {
        if (source == null || infrastructure == null || infrastructure.dataSource() != source
                || !(infrastructure.transactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() != source) {
            throw new IllegalArgumentException("Memory Outbox requires the selected fact DataSource and JDBC manager");
        }
        this.source = source; this.binding = Objects.requireNonNull(binding, "binding"); this.outbox = Objects.requireNonNull(outbox, "outbox");
        if (authority != null && !authority.usesDataSource(source)) throw new IllegalArgumentException("Memory Outbox scope authority uses different storage");
        this.authority = authority;
        guard = new OutboxTransactionGuard(source);
    }
    @Override public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    @Override public Binding binding() { return binding; }

    @Override public void publish(Event event) {
        guard.requireSelectedTransaction();
        Objects.requireNonNull(event, "event");
        if (authority != null) authority.requirePublication(event);
        if (event.action() == Action.BUILD && !binding.modelRef().equals(event.modelRef())) throw new IllegalArgumentException("Memory event model does not match its publication binding");
        String messageId = transportMessageId(event);
        String eventId = event.eventId();
        var receipt = outbox.enqueue(OutboxMessage.builder().messageId(messageId).idempotencyKey(eventId).channel(CHANNEL).destination(DESTINATION)
                .schemaVersion("1").contentType("application/json").payload(MemoryIndexEventJson.encode(event)).headers(Map.of(FINGERPRINT, event.fingerprint())).build());
        if (receipt == null || !messageId.equals(receipt.messageId()) || !eventId.equals(receipt.idempotencyKey())) throw new IllegalStateException("Memory index Outbox receipt does not match its intention");
    }

    static String transportMessageId(Event event) {
        return event.eventId().substring(EVENT_ID_PREFIX.length());
    }
}
