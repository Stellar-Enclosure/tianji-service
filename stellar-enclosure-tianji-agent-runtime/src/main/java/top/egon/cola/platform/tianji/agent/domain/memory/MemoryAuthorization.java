package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;

import java.util.Objects;

/** Current Memory permission and owner/scope authority. TEAM operations require current verified IAM access scope, including reads. */
@FunctionalInterface
public interface MemoryAuthorization {
    /** Bounded by the context deadline and outside a database transaction; unavailable authority must never imply ALLOW. */
    Decision authorize(Request request);

    enum Decision { ALLOW, DENY, UNAVAILABLE }
    enum Operation { GET, RETRIEVE, SEARCH, UPSERT, CORRECT, FORGET }

    /** Source run/event IDs additionally require current source-read permission; caller-declared hashes are not a proof. */
    record Request(MemoryOperationContext context, MemoryOwnerDTO owner, String scopeId, Operation operation,
                   String memoryId, long expectedRevision, String sourceRunId, String sourceEventId) {
        public Request {
            context = Objects.requireNonNull(context, "context"); owner = Objects.requireNonNull(owner, "owner"); operation = Objects.requireNonNull(operation, "operation");
            if (scopeId == null || scopeId.isBlank() || scopeId.length() > 128 || !scopeId.equals(scopeId.trim())
                    || (operation == Operation.RETRIEVE || operation == Operation.SEARCH) != (memoryId == null) || memoryId != null && (memoryId.isBlank() || memoryId.length() > 128)
                    || expectedRevision < 0 || (operation == Operation.CORRECT || operation == Operation.FORGET) && expectedRevision < 1
                    || (sourceRunId == null) != (sourceEventId == null)) throw new IllegalArgumentException("Memory authorization request is invalid");
        }
        @Override public String toString() { return "MemoryAuthorization.Request[redacted]"; }
    }
}
