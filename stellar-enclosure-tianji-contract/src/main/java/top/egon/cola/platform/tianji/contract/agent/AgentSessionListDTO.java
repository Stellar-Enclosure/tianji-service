package top.egon.cola.platform.tianji.contract.agent;

import java.util.List;
import java.util.Objects;

public record AgentSessionListDTO(List<AgentSessionDTO> items) {
    public AgentSessionListDTO {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.size() > 500) throw new IllegalArgumentException("session list exceeds its limit");
    }
}
