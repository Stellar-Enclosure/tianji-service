package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionJsonCodecTest {
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();
    private final ToolDescriptor descriptor = new ToolDescriptor("process.start", 1, "start_process", "Fixed operation",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "comment", ToolSchema.ValueType.STRING), Set.of("amount", "comment")),
            new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", "a".repeat(64), null));

    @Test void registered_descriptor_and_primitive_arguments_round_trip_without_numeric_or_schema_drift() {
        assertEquals(descriptor, codec.descriptor(codec.descriptor(descriptor), descriptor.reference()));
        var values = Map.<String, Object>of("amount", 20, "comment", "private arguments");
        assertEquals(Map.of("amount", 20L, "comment", "private arguments"), codec.arguments(codec.arguments(descriptor, values), descriptor));
    }
    @Test void changed_or_unknown_descriptor_metadata_never_matches_the_original_pinned_reference() {
        String json = codec.descriptor(descriptor);
        assertSanitized(() -> codec.descriptor(json.replace("Fixed operation", "Changed operation"), descriptor.reference()));
        assertSanitized(() -> codec.descriptor(json.substring(0, json.length() - 1) + ",\"hiddenCallback\":true}", descriptor.reference()));
        assertSanitized(() -> codec.descriptor(json + " {}", descriptor.reference()));
    }
    @Test void malformed_private_arguments_fail_closed_without_echoing_the_payload() {
        for (var json : java.util.List.of("{\"amount\":\"20\",\"comment\":\"private arguments\"}", "{\"amount\":20.0,\"comment\":\"private arguments\"}", "{\"amount\":20,\"amount\":21,\"comment\":\"private arguments\"}",
                "{\"amount\":20,\"comment\":{\"nested\":\"private arguments\"}}", "{\"amount\":20,\"comment\":\"private arguments\",\"tenantId\":21}",
                "{\"amount\":20,\"comment\":\"\uD800\"}", "{\"amount\":20,\"comment\":\"\\uD800\"}", "null", " ".repeat(ToolExecutionJsonCodec.MAX_BYTES + 1))) {
            assertSanitized(() -> codec.arguments(json, descriptor));
        }
    }
    @Test void successful_receipt_and_unknown_result_round_trip_with_distinct_effect_semantics() {
        var success = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "process-1"), "process-1", null, BigDecimal.ZERO);
        assertEquals(success, codec.outcome(codec.outcome(descriptor, success), descriptor));
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        assertEquals(unknown, codec.outcome(codec.outcome(descriptor, unknown), descriptor));
        String json = codec.outcome(descriptor, success);
        assertSanitized(() -> codec.outcome(json.replace("\"businessReceiptId\":\"process-1\"", "\"businessReceiptId\":null"), descriptor));
    }

    @Test void null_or_invalid_private_content_is_rejected_consistently_at_every_writer_boundary() {
        var missingReceipt = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private result"), null, null, null);
        var wrongOutput = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", 20L), "process-1", null, null);
        for (var operation : java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> codec.descriptor((ToolDescriptor) null),
                () -> codec.arguments(descriptor, null),
                () -> codec.arguments(null, Map.of()),
                () -> codec.arguments(descriptor, Map.of("amount", "private arguments", "comment", "private arguments")),
                () -> codec.arguments(descriptor, Map.of("amount", 20L, "comment", "\uD800")),
                () -> codec.outcome(descriptor, null),
                () -> codec.outcome(null, missingReceipt),
                () -> codec.outcome(descriptor, missingReceipt),
                () -> codec.outcome(descriptor, wrongOutput))) {
            assertSanitized(operation);
        }
    }

    private static void assertSanitized(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation);
        assertEquals("Stored tool execution content is invalid or changed", failure.getMessage());
        assertNull(failure.getCause());
    }
}
