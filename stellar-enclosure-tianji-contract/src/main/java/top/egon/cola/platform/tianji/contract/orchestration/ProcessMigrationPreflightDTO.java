package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** One owned instance's observed structural validation. It grants no migration authority. */
public record ProcessMigrationPreflightDTO(String instanceId, String sourceReleaseVersion, String targetReleaseVersion,
                                           Status status, String activityId, Instant observedAt) {
    private static final Instant MAX_OBSERVATION = Instant.parse("9999-12-31T23:59:59.999999Z");

    public ProcessMigrationPreflightDTO {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        sourceReleaseVersion = new GetProcessReleaseQuery(sourceReleaseVersion).releaseVersion();
        targetReleaseVersion = new GetProcessReleaseQuery(targetReleaseVersion).releaseVersion();
        if (status == null || observedAt == null || observedAt.isBefore(Instant.EPOCH)
                || observedAt.isAfter(MAX_OBSERVATION) || !observedAt.equals(observedAt.truncatedTo(ChronoUnit.MICROS))
                || (activityId != null) != (status == Status.NATIVE_STRUCTURE_VALIDATED_ONLY || status == Status.NATIVE_REJECTED)) {
            throw OrchestrationChecks.invalid();
        }
        if (activityId != null) activityId = OrchestrationChecks.definition(activityId);
    }

    public enum Status {
        NATIVE_STRUCTURE_VALIDATED_ONLY, INSTANCE_NOT_LIVE, TARGET_UNAVAILABLE, TARGET_PIN_MISMATCH,
        RELEASE_PAIR_UNSUPPORTED, SHAPE_UNSUPPORTED, ACTIVE_EVIDENCE_UNSAFE, NATIVE_REJECTED
    }
}
