package top.egon.cola.platform.tianji.contract.agent;

import java.util.List;
import java.util.Objects;

public record AgentRunPageDTO(List<AgentRunDTO> items, String nextCursor) {
    public AgentRunPageDTO {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.size() > 100) throw new IllegalArgumentException("run page exceeds its limit");
        nextCursor = ContractChecks.optional(nextCursor, "nextCursor", 256);
    }
}
