package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessEventCommand {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String eventId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String definitionKey;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String routeKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long routeVersion;
    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.:-]{0,127}")
    private String type;
    @NotNull
    private Instant occurredAt;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String correlationKey;
    @JsonProperty(required = true) @JsonInclude(JsonInclude.Include.ALWAYS) @ToString.Exclude
    private String waitToken;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> payload;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation event envelope")
    public boolean isEventEnvelopeValid() {
        return ProcessTypedJson.isId(eventId) && ProcessTypedJson.isId(definitionKey) && ProcessTypedJson.isId(routeKey)
                && routeVersion >= 1 && routeVersion <= ProcessTypedJson.V2_MAX_WIRE_INTEGER
                && type != null && type.matches("[A-Za-z_][A-Za-z0-9_.:-]{0,127}")
                && ProcessLifecycleEventV2.utcMicros(occurredAt) && validCorrelationKey()
                && (waitToken == null || isWaitToken(waitToken)) && AutomationStartCommand.boundedData(payload);
    }

    /** The host provides the immutable route selected by the activation or SIGNAL reservation. */
    public void validateForRoute(EventRouteVO route) {
        if (route == null || !isEventEnvelopeValid() || !route.routeKey().equals(routeKey)
                || route.version() != routeVersion || !route.type().equals(type)) {
            throw new IllegalArgumentException("Automation event does not match its route");
        }
        if ("START".equals(route.mode())) {
            if (waitToken != null || !route.targetDefinitionKey().equals(definitionKey)) {
                throw new IllegalArgumentException("Invalid automation START event");
            }
        } else if (waitToken == null) {
            throw new IllegalArgumentException("Invalid automation SIGNAL event");
        }
        route.payloadSchema().validateValue(payload);
    }

    private boolean validCorrelationKey() {
        if (correlationKey == null || correlationKey.isBlank() || correlationKey.length() > 255
                || correlationKey.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            ProcessTypedJson.utf8Length(correlationKey, 4 * 255);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static boolean isWaitToken(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        return bytes.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token);
    }
}
