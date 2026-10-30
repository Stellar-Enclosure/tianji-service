package top.egon.cola.platform.tianji.contract.agent;

import java.util.List;
import java.util.Objects;

public record AgentContextSnapshotPageDTO(List<AgentContextSnapshotDTO> items, Integer nextAfterModelCall) {
    public AgentContextSnapshotPageDTO {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.size() > 5 || nextAfterModelCall != null && (items.isEmpty() || nextAfterModelCall < 1
                || nextAfterModelCall != items.getLast().modelCallNumber())) throw new IllegalArgumentException("context snapshot page is invalid");
        for (int i = 1; i < items.size(); i++) {
            if (!items.get(i).runId().equals(items.getFirst().runId()) || items.get(i).modelCallNumber() <= items.get(i - 1).modelCallNumber()) {
                throw new IllegalArgumentException("context snapshot page is not scoped and ordered");
            }
        }
    }
}
