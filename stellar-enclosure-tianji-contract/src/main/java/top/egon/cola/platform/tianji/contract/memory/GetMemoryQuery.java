package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

public record GetMemoryQuery(MemoryOwnerDTO owner, String scopeId, String memoryId) {
    public GetMemoryQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128);
    }
    @Override public String toString() { return "GetMemoryQuery[redacted]"; }
}
