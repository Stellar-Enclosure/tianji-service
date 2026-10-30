package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** An intended owner, not a tenant selector, membership assertion or authorization grant. */
public record MemoryOwnerDTO(MemoryOwnerTypeEnum type, String id) {
    public MemoryOwnerDTO { type = Objects.requireNonNull(type, "type"); id = MemoryChecks.identifier(id, 256); }
    @Override public String toString() { return "MemoryOwner[redacted]"; }
}
