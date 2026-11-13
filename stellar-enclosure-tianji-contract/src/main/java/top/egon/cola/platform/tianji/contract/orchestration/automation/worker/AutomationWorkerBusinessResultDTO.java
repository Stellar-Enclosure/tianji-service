package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationWorkerBusinessResultDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") @ToString.Exclude
    private String businessReceiptId;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> output;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation worker business result")
    public boolean isBusinessResultValid() {
        try {
            requireBusinessReceiptId(businessReceiptId);
            canonicalData(output);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    /** Hashes an already schema-validated business output and its independent receipt. */
    public static String resultSha256(String effectFingerprint, String businessReceiptId, Map<String, Object> output) {
        requireHash(effectFingerprint);
        requireBusinessReceiptId(businessReceiptId);
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_RESULT_V2", effectFingerprint.getBytes(StandardCharsets.UTF_8),
                businessReceiptId.getBytes(StandardCharsets.UTF_8), canonicalData(output));
    }

    /** Hashes an already schema-validated node output under its fixed output schema. */
    public static String nodeOutputSha256(String outputSchemaSha256, Map<String, Object> output) {
        requireHash(outputSchemaSha256);
        return ProcessTypedJson.sha256("AUTOMATION_NODE_OUTPUT_V2", outputSchemaSha256.getBytes(StandardCharsets.UTF_8),
                canonicalData(output));
    }

    static byte[] canonicalData(Map<String, Object> value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing worker typed data");
        }
        byte[] canonical = ProcessTypedJson.canonicalBytes(value, 64 * 1024);
        ProcessTypedJson.validateTypedIntegerLeaves(value);
        return canonical;
    }

    static void requireBusinessReceiptId(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid worker business receipt ID");
        }
        ProcessTypedJson.canonicalBytes(value, 2 + 4 * 255);
    }

    static boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void requireHash(String value) {
        if (!isHash(value)) {
            throw new IllegalArgumentException("Invalid worker digest");
        }
    }
}
