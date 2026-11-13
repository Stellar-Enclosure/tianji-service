package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Set;

/** A committed lifecycle fact. The host supplies the trusted producer and tenant scope. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessLifecycleEventV2(
        @JsonProperty(required = true) int schemaVersion,
        @NotNull String eventId,
        @Positive long tenantId,
        @NotNull String sourceId,
        @NotNull String instanceId,
        @NotNull String releaseVersion,
        @NotNull String type,
        @NotNull Instant occurredAt,
        String occurrenceId,
        @Positive Integer attempt,
        String taskId,
        @Pattern(regexp = "[^\\p{Cc}]{1,255}") String resultRef) {

    private static final Set<String> INSTANCE_EVENTS = Set.of("INSTANCE_STARTED", "INSTANCE_COMPLETED", "INSTANCE_CANCELLED");
    private static final Set<String> NODE_EVENTS = Set.of("NODE_WAITING", "NODE_COMPLETED");
    private static final Set<String> TASK_EVENTS = Set.of("TASK_CREATED", "TASK_COMPLETED");
    private static final Set<String> COMPLETION_EVENTS = Set.of("INSTANCE_COMPLETED", "NODE_COMPLETED", "TASK_COMPLETED");

    public ProcessLifecycleEventV2 {
        if (schemaVersion != 2 || tenantId < 1 || !ProcessTypedJson.isId(eventId) || !ProcessTypedJson.isId(sourceId)
                || !safeReference(instanceId, 255) || releaseVersion == null
                || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || !utcMicros(occurredAt) || type == null) {
            throw invalid();
        }
        boolean instanceEvent = INSTANCE_EVENTS.contains(type);
        boolean nodeEvent = NODE_EVENTS.contains(type);
        boolean taskEvent = TASK_EVENTS.contains(type);
        if ((!instanceEvent && !nodeEvent && !taskEvent)
                || (occurrenceId == null) != (attempt == null)
                || (occurrenceId != null && (!ProcessTypedJson.isId(occurrenceId) || attempt < 1))
                || (nodeEvent && occurrenceId == null) || (instanceEvent && occurrenceId != null)
                || (taskEvent ? !safeReference(taskId, 255) : taskId != null)
                || (resultRef != null && (!safeReference(resultRef, 255)
                        || !COMPLETION_EVENTS.contains(type)))) {
            throw invalid();
        }
    }

    @Override
    public String toString() {
        return "ProcessLifecycleEventV2[eventId=" + eventId + ", type=" + type + "]";
    }

    private static boolean safeReference(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        ProcessTypedJson.utf8Length(value, 4 * maximum);
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation lifecycle event");
    }
    static boolean utcMicros(Instant time) {
        return time != null && time.getNano() % 1_000 == 0;
    }
}
