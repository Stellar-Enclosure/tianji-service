package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AgentEventDTO(String eventId, String runId, long sequence, String type, String author,
                            String branch, String toolCallId, Instant occurredAt, String correlationId,
                            String payloadSchemaVersion, Map<String, Object> payload) {
    public AgentEventDTO {
        eventId = ContractChecks.required(eventId, "eventId", 128);
        runId = ContractChecks.required(runId, "runId", 128);
        type = ContractChecks.required(type, "type", 128);
        author = ContractChecks.optional(author, "author", 128);
        branch = ContractChecks.optional(branch, "branch", 128);
        toolCallId = ContractChecks.optional(toolCallId, "toolCallId", 128);
        correlationId = ContractChecks.optional(correlationId, "correlationId", 128);
        payloadSchemaVersion = ContractChecks.required(payloadSchemaVersion, "payloadSchemaVersion", 64);
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        payload = ContractChecks.jsonObject(payload);
    }
}
