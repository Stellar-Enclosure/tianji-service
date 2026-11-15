package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.agentflow.api.capability.CallPermitVO;
import top.egon.cola.component.agentflow.api.memory.MemoryDeleteCommand;
import top.egon.cola.component.agentflow.api.memory.MemoryQuery;
import top.egon.cola.component.agentflow.api.memory.MemoryRetrievalResult;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteResult;
import top.egon.cola.component.agentflow.api.memory.MemoryScopeVO;
import top.egon.cola.component.agentflow.api.memory.MemoryStore;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteCommand;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@Slf4j
public final class LegacyMemoryStoreAdapter {
    private static final String NAMESPACE = "tianji-memory-v10";
    private static final String VERSION = "TIANJI_MEMORY_SCOPE_V10";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DataSource source;
    private final String schema;
    private final JdbcMemoryStore store;
    private final Clock clock;

    public LegacyMemoryStoreAdapter(DataSource source, String schema, JdbcMemoryStore store, Clock clock) {
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || store == null || !store.usesStorage(source, schema)) {
            throw new IllegalArgumentException("Memory requires the exact configured JDBC scope store");
        }
        this.source = Objects.requireNonNull(source, "source");
        this.schema = schema;
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A separate host-only SPI. Generic Agent Flow JDBC Memory continues to reject HOST commands. */
    public MemoryStore hostStore(PostgresMemoryService memory, LegacyMemoryFactConverter converter) {
        if (!memory.usesHostAuthority(this)) throw unavailable();
        return new HostStore(memory, Objects.requireNonNull(converter, "Memory converter"));
    }

    /**
     * Only trusted in-process host code may issue a permit carrying the verified actor and source mapping.
     * The inherited policy and recheck callbacks must use local state. The verified context and source
     * mapping are resolved before entering the database transaction.
     */
    public interface HostAuthority extends MemoryStore.MemoryAuthority {
        MemoryOperationContext context(MemoryScopeVO scope);
        VerifiedWriteSourceVO verifyWriteSource(MemoryScopeVO scope, MemoryWriteCommand command);
    }

    public record VerifiedWriteSourceVO(MemorySourceDTO source, BigDecimal confidence) {
        public VerifiedWriteSourceVO {
            Objects.requireNonNull(source, "Memory source");
            Objects.requireNonNull(confidence, "Memory confidence");
        }
    }

    private record HostStore(PostgresMemoryService memory, LegacyMemoryFactConverter converter) implements MemoryStore {
        @Override public MemoryRetrievalResult retrieve(MemoryQuery query, MemoryScopeVO scope, CallPermitVO permit) {
            return execute(() -> memory.hostRetrieve(query, scope, permit, hostAuthority(permit), converter));
        }
        @Override public MemoryWriteResult upsert(MemoryWriteCommand command, MemoryScopeVO scope, CallPermitVO permit) {
            return execute(() -> memory.hostUpsert(command, scope, permit, hostAuthority(permit)));
        }
        @Override public MemoryWriteResult delete(MemoryDeleteCommand command, MemoryScopeVO scope, CallPermitVO permit) {
            return execute(() -> memory.hostDelete(command, scope, permit, hostAuthority(permit)));
        }

        private static HostAuthority hostAuthority(CallPermitVO permit) {
            if (MemoryStore.Access.requireAuthority(permit) instanceof HostAuthority host) return host;
            throw MemoryStore.Access.denied("MEMORY_AUTHORITY_REQUIRED");
        }

        private static <T> T execute(java.util.function.Supplier<T> call) {
            try {
                return call.get();
            } catch (AgentLedgerException failure) {
                throw MemoryStore.Access.denied(switch (failure.code()) {
                    case TIANJI_PERMISSION_DENIED -> "MEMORY_PERMISSION_DENIED";
                    case TIANJI_IDEMPOTENCY_CONFLICT -> "MEMORY_COMMAND_CONFLICT";
                    case TIANJI_REVISION_CONFLICT -> "MEMORY_REVISION_CONFLICT";
                    case TIANJI_RESOURCE_NOT_FOUND -> "MEMORY_FACT_NOT_FOUND";
                    case TIANJI_INVALID_COMMAND -> "MEMORY_INVALID_COMMAND";
                    default -> "MEMORY_UNAVAILABLE";
                });
            } catch (IllegalArgumentException malformed) {
                throw MemoryStore.Access.denied("MEMORY_INVALID_COMMAND");
            }
        }
    }

    public boolean usesDataSource(DataSource selected) {
        return selected == source;
    }

    public static MemoryScopeVO scopeOf(long tenantId, String issuer, MemoryOwnerDTO owner, String scopeId) {
        if (tenantId <= 0 || issuer == null || !issuer.matches("[0-9a-f]{64}") || !identifier(scopeId, 128)) {
            throw new IllegalArgumentException("Invalid Memory host scope");
        }
        String tenant = Long.toString(tenantId);
        return new MemoryScopeVO(NAMESPACE, tenant, MemoryScopeVO.RetentionScopeEnum.HOST, null, null, null,
                encode(VERSION, tenant, issuer, owner.type().name(), owner.id(), scopeId));
    }

    static HostIdentityVO identity(MemoryScopeVO scope) {
        if (scope == null || !NAMESPACE.equals(scope.namespace()) || scope.retentionScope() != MemoryScopeVO.RetentionScopeEnum.HOST) {
            throw new IllegalArgumentException("Invalid Memory host scope");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(scope.hostScopeKey())))) {
            String version = readField(input);
            long tenantId = Long.parseLong(readField(input));
            String issuer = readField(input);
            var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.valueOf(readField(input)), readField(input));
            String scopeId = readField(input);
            if (input.read() != -1 || !VERSION.equals(version) || !scope.equals(scopeOf(tenantId, issuer, owner, scopeId))) {
                throw new IllegalArgumentException("Invalid Memory host scope");
            }
            return new HostIdentityVO(tenantId, issuer, owner, scopeId);
        } catch (IOException | IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Invalid Memory host scope");
        }
    }

    static byte[] originPrefix(long tenantId, String issuer) {
        return Base64.getUrlDecoder().decode(encode(VERSION, Long.toString(tenantId), issuer));
    }

    public static String namespaceOf(TrustedAgentActor actor, String operation) {
        if (!("UPSERT".equals(operation) || "CORRECT".equals(operation) || "FORGET".equals(operation))
                || actor == null || !identifier(actor.subjectId(), 256)) {
            throw new IllegalArgumentException("Invalid Memory receipt namespace");
        }
        return "HOST_V1:" + encode(actor.subjectType().name(), actor.subjectId(), operation);
    }

    <T> T inScope(JdbcMemoryRepository repository, Instant deadline, boolean write, boolean create,
                  Long expectedEpoch, BooleanSupplier current, Function<JdbcMemoryRepository, T> execute) {
        return inScope(repository, deadline, write, create, expectedEpoch, current, execute, true);
    }

    /** A pre-guard legacy receipt remains readable without creating an authority row. */
    <T> T inReceiptScope(JdbcMemoryRepository repository, Instant deadline, BooleanSupplier current,
                         Function<JdbcMemoryRepository, T> lookup) {
        return inScope(repository, deadline, false, false, null, current, lookup, false);
    }

    private <T> T inScope(JdbcMemoryRepository repository, Instant deadline, boolean write, boolean create,
                          Long expectedEpoch, BooleanSupplier current, Function<JdbcMemoryRepository, T> execute,
                          boolean materializeAuthority) {
        requireTransaction();
        var scope = scopeOf(repository.tenantId(), repository.issuer(), repository.owner(), repository.scopeId());
        try {
            var jdbc = repository.jdbc();
            var selected = jdbc.query("SELECT authority_epoch FROM " + schema + ".scope WHERE scope_hash=?",
                    (rs, row) -> rs.getLong("authority_epoch"), scope.hash());
            if (selected.isEmpty()) {
                if (!materializeAuthority) return invokeHost(repository, execute);
                if (expectedEpoch != null && expectedEpoch != 0L) throw unavailable();
                if (!create && !repository.scopeExists()) return execute.apply(repository.absent());
                if (expectedEpoch != null) throw unavailable();
                jdbc.update("""
                        INSERT INTO %s.scope(scope_hash,canonical_scope,authority_epoch,storage_ref,state,high_water_revision,created_at,updated_at)
                        VALUES (?,?::jsonb,1,'LEGACY','ACTIVE',?,clock_timestamp(),clock_timestamp())
                        ON CONFLICT (scope_hash) DO NOTHING
                        """.formatted(schema), scope.hash(), json(scope.canonicalScope()), repository.legacyRevisionCount());
                selected = jdbc.query("SELECT authority_epoch FROM " + schema + ".scope WHERE scope_hash=?",
                        (rs, row) -> rs.getLong("authority_epoch"), scope.hash());
            }
            if (selected.size() != 1 || expectedEpoch != null && !expectedEpoch.equals(selected.getFirst())) throw unavailable();
            long epoch = selected.getFirst();
            String purpose = write ? MemoryStore.UPSERT_PURPOSE : MemoryStore.READ_PURPOSE;
            var permit = new CallPermitVO("tianji-memory-host", MemoryStore.CAPABILITY_REF, scope.hash(), 1, epoch, deadline,
                    () -> Thread.currentThread().isInterrupted(), new LocalAuthority(purpose, current));
            return store.inHostScope(source, scope, permit, purpose, write, fence -> {
                var route = repository.fenced(schema, scope.hash(), fence);
                TransactionSynchronizationManager.bindResource(this, new PublicationFenceVO(scope, write, route));
                try {
                    return invokeHost(route, execute);
                } finally {
                    TransactionSynchronizationManager.unbindResource(this);
                }
            });
        } catch (HostCallbackException deniedByHost) {
            throw (CommonException) deniedByHost.getCause();
        } catch (CommonException | DataAccessException failure) {
            log.debug("Memory scope authority unavailable");
            throw unavailable();
        }
    }

    private static <T> T invokeHost(JdbcMemoryRepository repository, Function<JdbcMemoryRepository, T> execute) {
        try {
            return execute.apply(repository);
        } catch (CommonException deniedByHost) {
            throw new HostCallbackException(deniedByHost);
        }
    }

    public void requirePublication(MemoryIndexPublication.Event event) {
        requireTransaction();
        Object resource = TransactionSynchronizationManager.getResource(this);
        if (!(resource instanceof PublicationFenceVO held) || !held.write()
                || !held.scope().equals(scopeOf(event.tenantId(), event.ownerIssuer(), event.owner(), event.scopeId()))) throw unavailable();
        var repository = held.repository();
        var version = repository.reference(event.memoryId(), event.revision()).orElseThrow(LegacyMemoryStoreAdapter::unavailable);
        if (!event.contentSha256().equals(version.contentSha256()) || !event.occurredAt().equals(version.writtenAt())) throw unavailable();
        switch (event.action()) {
            case BUILD -> {
                if (repository.component() || version.state() != MemoryStateEnum.ACTIVE
                        || !event.modelRef().equals(version.modelRef())) throw unavailable();
            }
            case DELETE_REVISION -> {
                if (version.state() != MemoryStateEnum.SUPERSEDED) throw unavailable();
            }
            case DELETE_MEMORY -> {
                if (version.state() != MemoryStateEnum.FORGOTTEN
                        && version.state() != MemoryStateEnum.EXPIRED) throw unavailable();
            }
        }
    }

    String schema() {
        return schema;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
    }

    static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw unavailable();
        }
    }

    private static String encode(String... fields) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            for (String field : fields) {
                if (!StandardCharsets.UTF_8.newEncoder().canEncode(field)) throw new IllegalArgumentException("Invalid Memory identifier encoding");
                byte[] utf8 = field.getBytes(StandardCharsets.UTF_8);
                output.writeInt(utf8.length);
                output.write(utf8);
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode Memory identity", impossible);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static String readField(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > input.available()) throw new IOException("Invalid Memory identity field");
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readNBytes(length))).toString();
    }

    private static boolean identifier(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }

    static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory authority or storage is unavailable");
    }

    record HostIdentityVO(long tenantId, String issuer, MemoryOwnerDTO owner, String scopeId) { }

    private static final class HostCallbackException extends RuntimeException {
        private HostCallbackException(CommonException cause) { super(cause); }
    }

    private record PublicationFenceVO(MemoryScopeVO scope, boolean write, JdbcMemoryRepository repository) { }

    private record LocalAuthority(String purpose, BooleanSupplier current) implements MemoryStore.MemoryAuthority {
        @Override public boolean isCurrent(CallPermitVO permit, String selectedPurpose) {
            return purpose.equals(selectedPurpose) && current.getAsBoolean();
        }

        // This private permit only holds a scope for already-authorized local host SQL.
        @Override public MemoryStore.ReadPolicyVO authorizeRead(MemoryScopeVO scope, String selectedPurpose) { throw unavailable(); }
        @Override public MemoryStore.WriteMetadataVO authorizeWrite(MemoryScopeVO scope, MemoryWriteCommand command) { throw unavailable(); }
        @Override public void authorizeDelete(MemoryScopeVO scope, MemoryDeleteCommand command) { throw unavailable(); }
        @Override public MemoryStore.ExtractionPolicyVO authorizeExtraction(MemoryScopeVO scope, String strategyRef, long strategyVersion) { throw unavailable(); }
    }
}
