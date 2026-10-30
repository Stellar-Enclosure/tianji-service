package top.egon.cola.platform.tianji.contract.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextContractTest {
    private static final String SHA = "a".repeat(64);

    @Test
    void context_window_arithmetic_is_overflow_safe_and_required_instruction_sources_cannot_be_trimmed() {
        var window = new AgentContextWindowDTO("model", "route", "policy", Long.MAX_VALUE, 1);
        var valid = metadata("run-one", "call-one", window, Long.MAX_VALUE - 2, 1, List.of(source()));
        assertEquals(Long.MAX_VALUE - 2, valid.estimatedInputTokens());
        assertThrows(IllegalArgumentException.class, () -> metadata("run-one", "call-one", window, Long.MAX_VALUE - 1, 1, List.of(source())));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSourceDTO(AgentContextSourceKindEnum.PROMPT,
                AgentContextRoleEnum.USER, "prompt", "1", "published", SHA, SHA, 1, java.time.Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextTrimDecisionDTO(AgentContextSourceKindEnum.DEFINITION,
                "definition", "1", "OLD_COMPLETE_TURN", SHA, SHA, 1));
    }

    @Test
    void provenance_is_immutable_exact_and_unique_without_source_bodies() {
        var metadata = metadata("run-one", "call-one", new AgentContextWindowDTO("model", "route", "policy", 100, 5), 20, 10, List.of(source()));
        assertThrows(UnsupportedOperationException.class, () -> metadata.sources().add(source()));
        assertThrows(IllegalArgumentException.class, () -> metadata("run-one", "call-one", metadata.window(), 20, 10, List.of(source(), source())));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextWindowDTO(" model", "route", "policy", 100, 5));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSourceDTO(AgentContextSourceKindEnum.USER_INPUT,
                AgentContextRoleEnum.USER, "input" + (char) 0xd800, "1", "snapshot", SHA, SHA, 1, null));
    }

    @Test
    void snapshots_and_pages_bind_run_call_epoch_and_monotonic_model_call_numbers() {
        var window = new AgentContextWindowDTO("model", "route", "policy", 100, 5);
        var first = new AgentContextSnapshotDTO("run-one", "call-one", 1, 1, 1,
                metadata("run-one", "call-one", window, 20, 10, List.of(source())), java.time.Instant.EPOCH);
        var second = new AgentContextSnapshotDTO("run-one", "call-two", 3, 1, 1,
                metadata("run-one", "call-two", window, 20, 10, List.of(source())), java.time.Instant.EPOCH);
        assertEquals(3, new AgentContextSnapshotPageDTO(List.of(first, second), 3).nextAfterModelCall());
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSnapshotPageDTO(List.of(second, first), null));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSnapshotPageDTO(List.of(first), 2));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSnapshotDTO("other-run", "call-one", 1, 1, 1, first.metadata(), java.time.Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSnapshotDTO("run-one", "call-one", 1, 1, 2, first.metadata(), java.time.Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new AgentContextSnapshotsQuery("run-one", 0, 6));
    }

    private static AgentContextMetadataDTO metadata(String run, String call, AgentContextWindowDTO window, long input, int output, List<AgentContextSourceDTO> sources) {
        return new AgentContextMetadataDTO(1, AgentContextMetadataDTO.PROJECTION_V1, SHA, run, call, 1, "writer", "invocation", "session", 0, SHA, null,
                SHA, SHA, SHA, SHA, SHA, SHA, SHA, window, input, output, sources, List.of());
    }
    private static AgentContextSourceDTO source() {
        return new AgentContextSourceDTO(AgentContextSourceKindEnum.DEFINITION, AgentContextRoleEnum.SYSTEM,
                "definition", "1", "published-version", SHA, SHA, 1, null);
    }
}
