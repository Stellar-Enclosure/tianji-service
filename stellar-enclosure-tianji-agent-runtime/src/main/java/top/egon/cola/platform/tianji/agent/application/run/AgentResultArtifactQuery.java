package top.egon.cola.platform.tianji.agent.application.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Instant;
import java.util.Map;

/** The exact host-owned scope of a metadata check. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreType
public class AgentResultArtifactQuery {
    static final String REGISTERED_ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    static final String SHA256 = "[0-9a-f]{64}";
    private static final String NODE_REFERENCE = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";
    private static final String RUN_REFERENCE = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}";
    private static final Instant LAST_WIRE_INSTANT = Instant.parse("9999-12-31T23:59:59.999999Z");

    @NotNull
    private PurposeEnum purpose;
    @NotNull @Valid
    private TrustedAgentActor owner;
    @NotNull @Pattern(regexp = REGISTERED_ID)
    private String sourceId;
    @NotNull @Pattern(regexp = REGISTERED_ID)
    private String producerId;
    @NotNull @Pattern(regexp = REGISTERED_ID)
    private String destinationId;
    @NotNull @Pattern(regexp = RUN_REFERENCE)
    private String runId;
    @NotNull @Pattern(regexp = NODE_REFERENCE)
    private String nodeExecutionRef;
    @NotNull @Pattern(regexp = SHA256)
    private String outputSchemaSha256;
    @NotNull @Valid
    private AgentResultArtifactRefVO artifactRef;
    @NotNull
    private Instant deadlineAt;

    public enum PurposeEnum { PREPARE_TERMINAL, DELIVER_TERMINAL }

    @JsonIgnore
    @AssertTrue(message = "Artifact verification requires a verified SERVICE owner and a UTC microsecond deadline")
    public boolean isVerifiedServiceScope() {
        return owner != null && owner.tenantId() > 0 && owner.subjectType() == TrustedAgentActor.SubjectType.SERVICE
                && matches(owner.issuerFingerprint(), SHA256) && utcMicros(deadlineAt);
    }

    boolean hasValidShape() {
        return isVerifiedServiceScope() && purpose != null && matches(sourceId, REGISTERED_ID)
                && matches(producerId, REGISTERED_ID) && matches(destinationId, REGISTERED_ID)
                && matches(runId, RUN_REFERENCE) && matches(nodeExecutionRef, NODE_REFERENCE)
                && matches(outputSchemaSha256, SHA256) && artifactRef != null;
    }

    AgentResultArtifactQuery snapshot() {
        return new AgentResultArtifactQuery(purpose, owner, sourceId, producerId, destinationId, runId,
                nodeExecutionRef, outputSchemaSha256, artifactRef, deadlineAt);
    }

    /** SHA-256 of the closed canonical query, including its fixed destination and observation purpose. */
    public String querySha256() {
        if (!hasValidShape()) throw new IllegalArgumentException("Invalid Agent result artifact query");
        var value = Map.of(
                "purpose", purpose.name(),
                "owner", Map.of("tenantId", owner.tenantId(), "subjectType", owner.subjectType().name(),
                        "issuerFingerprint", owner.issuerFingerprint(), "subjectId", owner.subjectId()),
                "sourceId", sourceId, "producerId", producerId, "destinationId", destinationId,
                "runId", runId, "nodeExecutionRef", nodeExecutionRef, "outputSchemaSha256", outputSchemaSha256,
                "artifactRef", artifactRef.toWire(), "deadlineAt", deadlineAt.toString());
        return ProcessTypedJson.sha256("tianji-agent-artifact-query-v2", ProcessTypedJson.canonicalBytes(value));
    }

    static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    static boolean utcMicros(Instant value) {
        return value != null && value.getNano() % 1_000 == 0 && !value.isBefore(Instant.EPOCH) && !value.isAfter(LAST_WIRE_INSTANT);
    }
}
