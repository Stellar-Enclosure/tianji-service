package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Revision-checked logical forgetting. The result does not claim synchronous physical deletion of remote vectors or backups. */
public record ForgetMemoryCommand(String commandId, MemoryOwnerDTO owner, String scopeId, String memoryId, long expectedRevision, String reasonCode) {
    public ForgetMemoryCommand {
        commandId = MemoryChecks.identifier(commandId, 128); owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128); reasonCode = MemoryChecks.reason(reasonCode);
        if (expectedRevision < 1) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "ForgetMemoryCommand[redacted]"; }
}
