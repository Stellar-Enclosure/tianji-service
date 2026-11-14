package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationAdmissionQuery {
    static final String SHA256 = "[0-9a-f]{64}";
    static final String DEFINITION_KEY = "[A-Za-z_][A-Za-z0-9_.-]{0,127}";
    static final String RELEASE_VERSION = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    static final String COMMAND_ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";

    public enum Purpose { ACTIVATE, REPLAY_ACTIVATION, START, REPLAY_START, EVENT_ADMIT, EVENT_START }

    @NotNull private Purpose purpose;
    @NotNull private ProcessActor actor;
    @NotNull @Pattern(regexp = DEFINITION_KEY) private String definitionKey;
    @NotNull @Pattern(regexp = RELEASE_VERSION) private String releaseVersion;
    @NotNull @Pattern(regexp = SHA256) private String sourceManifestSha256;
    @NotNull @Pattern(regexp = SHA256) private String compiledManifestSha256;
    @NotNull @Pattern(regexp = SHA256) private String dependencyManifestSha256;
    @NotNull @Pattern(regexp = COMMAND_ID) private String commandId;
    @NotNull @Pattern(regexp = SHA256) private String requestSha256;
    @Min(0) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) private long activationRevision;
    @Pattern(regexp = SHA256) private String effectiveConfigurationSha256;
    @Valid private EventIdentityVO eventIdentity;
    @Pattern(regexp = SHA256) private String routeSha256;
    @Pattern(regexp = COMMAND_ID) private String admissionReceiptId;

    @AssertTrue(message = "Invalid automation admission scope")
    public boolean isScopeConsistent() {
        if (purpose == null || actor == null || !matches(actor.issuerFingerprint(), SHA256)) return false;
        boolean activation = purpose == Purpose.ACTIVATE || purpose == Purpose.REPLAY_ACTIVATION;
        boolean event = purpose == Purpose.EVENT_ADMIT || purpose == Purpose.EVENT_START;
        return (!activation || actor.subjectType() == ProcessActor.SubjectType.USER)
                && (purpose == Purpose.ACTIVATE || activationRevision > 0)
                && ((purpose == Purpose.START || event) == (effectiveConfigurationSha256 != null))
                && (event == (eventIdentity != null)) && (event == (routeSha256 != null))
                && ((purpose == Purpose.EVENT_START) == (admissionReceiptId != null));
    }

    /** Domain-separated canonical locator used both by the caller and the host. */
    public String sha256() {
        if (!isScopeConsistent() || !matches(definitionKey, DEFINITION_KEY) || !matches(releaseVersion, RELEASE_VERSION)
                || !matches(commandId, COMMAND_ID) || !matches(sourceManifestSha256, SHA256)
                || !matches(compiledManifestSha256, SHA256) || !matches(dependencyManifestSha256, SHA256)
                || !matches(requestSha256, SHA256) || activationRevision < 0
                || activationRevision > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !optional(effectiveConfigurationSha256, SHA256) || !optional(routeSha256, SHA256)
                || !optional(admissionReceiptId, COMMAND_ID)) {
            throw new IllegalArgumentException("Invalid automation admission query");
        }
        var wire = new LinkedHashMap<String, Object>();
        wire.put("purpose", purpose.name());
        wire.put("actor", actorWire(actor));
        wire.put("definitionKey", definitionKey);
        wire.put("releaseVersion", releaseVersion);
        wire.put("sourceManifestSha256", sourceManifestSha256);
        wire.put("compiledManifestSha256", compiledManifestSha256);
        wire.put("dependencyManifestSha256", dependencyManifestSha256);
        wire.put("commandId", commandId);
        wire.put("requestSha256", requestSha256);
        wire.put("activationRevision", activationRevision);
        wire.put("effectiveConfigurationSha256", effectiveConfigurationSha256);
        wire.put("eventIdentity", eventIdentity == null ? null : eventIdentity.wire());
        wire.put("routeSha256", routeSha256);
        wire.put("admissionReceiptId", admissionReceiptId);
        return ProcessTypedJson.sha256("AUTOMATION_ADMISSION_QUERY_V2", ProcessTypedJson.canonicalBytes(wire));
    }

    static AutomationAdmissionQuery activation(Purpose purpose, ProcessActor actor, String definitionKey, String releaseVersion,
            String commandId, String requestSha256, long revision, String sourceSha256, String compiledSha256, String dependencySha256) {
        return new AutomationAdmissionQuery().setPurpose(purpose).setActor(actor).setDefinitionKey(definitionKey)
                .setReleaseVersion(releaseVersion).setCommandId(commandId).setRequestSha256(requestSha256).setActivationRevision(revision)
                .setSourceManifestSha256(sourceSha256).setCompiledManifestSha256(compiledSha256).setDependencyManifestSha256(dependencySha256);
    }

    static String activationRequestSha256(ProcessActor actor, String definitionKey, String releaseVersion, long expected, String commandId) {
        return ProcessTypedJson.sha256("AUTOMATION_ACTIVATION_COMMAND_V2", ProcessTypedJson.canonicalBytes(
                Map.of("actor", actorWire(actor), "definitionKey", definitionKey, "releaseVersion", releaseVersion,
                        "expectedActivationRevision", expected, "commandId", commandId)));
    }

    static Map<String, Object> actorWire(ProcessActor actor) {
        return Map.of("tenantId", actor.tenantId(), "subjectType", actor.subjectType().name(),
                "subjectId", actor.subjectId(), "issuerFingerprint", actor.issuerFingerprint());
    }

    static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static boolean optional(String value, String pattern) {
        return value == null || matches(value, pattern);
    }

    public record EventIdentityVO(String producerIssuer, String producerSubject, String eventId, String eventFingerprint) {
        public EventIdentityVO {
            if (!matches(producerIssuer, "[^\\p{Cc}]{1,2048}") || !matches(producerSubject, "[^\\p{Cc}]{1,256}")
                    || !matches(eventId, "[^\\p{Cc}]{1,256}") || !matches(eventFingerprint, SHA256)) {
                throw new IllegalArgumentException("Invalid automation event identity");
            }
        }

        private Map<String, Object> wire() {
            return Map.of("producerIssuer", producerIssuer, "producerSubject", producerSubject, "eventId", eventId,
                    "eventFingerprint", eventFingerprint);
        }
    }
}
