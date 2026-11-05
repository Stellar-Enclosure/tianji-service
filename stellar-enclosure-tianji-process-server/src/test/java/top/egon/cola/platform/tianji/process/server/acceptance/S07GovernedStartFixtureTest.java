package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S07GovernedStartFixtureTest {
    @Test
    void exact_fixed_bpmn_and_companion_dmn_pass_the_existing_model_validator() {
        var models = S07GovernedStartFixture.release().models();
        var validator = new ProcessModelValidator();
        var process = models.getFirst();
        var decision = models.getLast();
        assertDoesNotThrow(() -> validator.validateBpmn(process.xml().getBytes(StandardCharsets.UTF_8), process.definitionKey()));
        assertDoesNotThrow(() -> validator.validateDmn(decision.xml().getBytes(StandardCharsets.UTF_8), decision.definitionKey()));
    }

    @Test
    void process_publication_must_report_the_exact_model_digest_and_inputs() {
        var requested = S07GovernedStartFixture.release().models();
        var first = requested.getFirst();
        var second = requested.getLast();
        var process = new ProcessModelDTO(first.type(), first.definitionKey(), S07GovernedStartFixture.BPMN_SHA, first.inputs());
        var decision = new ProcessModelDTO(second.type(), second.definitionKey(), S07GovernedStartFixture.DMN_SHA, second.inputs());
        assertDoesNotThrow(() -> S07GovernedStartFixture.verifyRelease(release(process, decision)));
        var wrong = new ProcessModelDTO(process.type(), process.definitionKey(), "f".repeat(64), process.inputs());
        assertThrows(IllegalStateException.class, () -> S07GovernedStartFixture.verifyRelease(release(wrong, decision)));
    }

    private static ProcessReleaseDTO release(ProcessModelDTO process, ProcessModelDTO decision) {
        return new ProcessReleaseDTO(S07GovernedStartFixture.RELEASE, "a".repeat(64),
                Instant.parse("2026-11-06T00:00:00Z"), List.of(process, decision));
    }
}
