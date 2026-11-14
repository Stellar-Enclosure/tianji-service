package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EventInboxBO {
    static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    static final int MAX_CONFIGURATION_BYTES = 256 * 1024;
    static final int MAX_ADMISSION_BYTES = 512 * 1024;
    private static final Set<String> MAPPED_START_INPUT_FIELDS = Set.of("variables", "agentTaskOverrides");
    private static final Pattern FIELD_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\[[0-9]+\\]");
    private static final Object MISSING = new Object();

    @Min(1) private long tenantId;
    @NotNull private String producerIssuer;
    @NotNull private String producerSubject;
    @NotNull private String eventId;
    @NotNull private String receiptId;
    @NotNull private String fingerprint;
    @NotNull private String routeKey;
    @Min(1) private long routeVersion;
    @NotNull private String routeSha256;
    @NotNull private String releaseKey;
    @NotNull private String mode;
    private String commandId;
    @ToString.Exclude private String waitTokenSha256;
    @NotNull @ToString.Exclude private Map<String, Object> payload;
    @NotNull private String correlationKey;
    @NotNull private String state;
    private String instanceId;
    private String nodeExecutionRef;
    @NotNull private Instant occurredAt;
    @NotNull private Instant receivedAt;
    @NotNull private Instant expiresAt;
    private Instant decidedAt;
    private Instant nextRetryAt;
    @Min(0) private int attempts;
    private String decisionCode;
    @NotNull private String definitionKey;
    @Min(1) private long activationRevision;
    @NotNull private Instant admittedAt;
    private Instant admissionNotAfter;
    @ToString.Exclude private EventAdmissionVO admission;

    public ProcessActor producer() {
        return new ProcessActor(tenantId, ProcessActor.SubjectType.SERVICE, producerSubject, producerIssuer);
    }

    /** Maps only the schema-checked, frozen payload paths declared on the published START route. */
    public static Map<String, Object> mapStartInput(EventRouteVO route, Map<String, Object> payload) {
        if (!"START".equals(route.mode())) throw invalid();
        Object normalizedPayload = route.payloadSchema().normalizeValue(payload);
        var result = new LinkedHashMap<String, Object>();
        for (var mapping : route.mapping()) {
            Object value = normalizedPayload;
            for (String segment : segments(mapping.source().substring("event.".length()))) {
                value = readSegment(value, segment);
                if (value == MISSING) throw invalid();
            }
            var target = segments(mapping.target());
            Object container = result;
            for (int index = 0; index < target.size() - 1; index++) {
                String part = target.get(index);
                Object child = readSegment(container, part);
                if (child == MISSING) {
                    child = target.get(index + 1).startsWith("[") ? new ArrayList<>() : new LinkedHashMap<>();
                    putSegment(container, part, child);
                }
                container = child;
            }
            putSegment(container, target.getLast(), value);
        }
        return immutableObject(result, MAX_PAYLOAD_BYTES);
    }

    private static List<String> segments(String path) {
        return FIELD_SEGMENT.matcher(path).results().map(MatchResult::group).toList();
    }

    private static Object readSegment(Object container, String segment) {
        if (segment.startsWith("[") && container instanceof List<?> values) {
            int index = arrayIndex(segment);
            return index < values.size() ? values.get(index) : MISSING;
        }
        if (!segment.startsWith("[") && container instanceof Map<?, ?> values) {
            return values.containsKey(segment) ? values.get(segment) : MISSING;
        }
        throw invalid();
    }

    @SuppressWarnings("unchecked")
    private static void putSegment(Object container, String segment, Object value) {
        if (segment.startsWith("[") && container instanceof List<?> values) {
            int index = arrayIndex(segment);
            var array = (List<Object>) values;
            while (array.size() <= index) array.add(MISSING);
            if (array.get(index) != MISSING) throw invalid();
            array.set(index, value);
        } else if (!segment.startsWith("[") && container instanceof Map<?, ?> values) {
            if (values.containsKey(segment)) throw invalid();
            ((Map<String, Object>) values).put(segment, value);
        } else {
            throw invalid();
        }
    }

    private static int arrayIndex(String segment) {
        return Integer.parseInt(segment.substring(1, segment.length() - 1));
    }

    public record EventAdmissionVO(String receiptId, String definitionKey, long activationRevision, String releaseVersion,
            String routeSha256, String originalRequestSha256, Map<String, Object> mappedStartInput,
            Map<String, Object> effectiveConfiguration, String configurationSha256, String authorizationSha256,
            Instant admittedAt, Instant admissionNotAfter) {
        public EventAdmissionVO {
            if (!eventId(receiptId) || !eventId(definitionKey) || releaseVersion == null
                    || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || activationRevision < 1 || activationRevision > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                    || !sha256(routeSha256) || !sha256(originalRequestSha256) || !sha256(configurationSha256)
                    || !sha256(authorizationSha256) || !utcMicros(admittedAt) || !utcMicros(admissionNotAfter)
                    || !admissionNotAfter.isAfter(admittedAt)) throw invalid();
            mappedStartInput = immutableObject(mappedStartInput, MAX_CONFIGURATION_BYTES);
            if (!mappedStartInput.keySet().equals(MAPPED_START_INPUT_FIELDS)
                    || !(mappedStartInput.get("variables") instanceof Map<?, ?> variables)
                    || !(mappedStartInput.get("agentTaskOverrides") instanceof Map<?, ?>)) throw invalid();
            ProcessTypedJson.canonicalBytes(variables, MAX_PAYLOAD_BYTES);
            effectiveConfiguration = immutableObject(effectiveConfiguration, MAX_CONFIGURATION_BYTES);
        }
    }

    static Map<String, Object> immutableObject(Map<?, ?> source, int maxBytes) {
        if (source == null) throw invalid();
        ProcessTypedJson.canonicalBytes(source, maxBytes);
        return copyObject(source);
    }

    private static Map<String, Object> copyObject(Map<?, ?> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> copy.put((String) key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> object) return copyObject(object);
        if (value instanceof List<?> array) return array.stream().map(EventInboxBO::copyValue).toList();
        return value;
    }

    static boolean eventId(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    }

    static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean utcMicros(Instant time) {
        return time != null && time.getNano() % 1_000 == 0;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process event admission evidence");
    }
}
