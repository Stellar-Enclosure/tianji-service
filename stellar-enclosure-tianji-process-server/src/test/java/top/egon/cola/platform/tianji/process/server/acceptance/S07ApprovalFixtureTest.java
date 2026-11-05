package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S07ApprovalFixtureTest {
    private static final TenantBindingDTO TENANT = new TenantBindingDTO(20, "https://issuer.example/20", "requester",
            List.of("reviewer-1", "reviewer-2"), List.of("501", "502", "503"), "fixture-app",
            "https://process.example/20", "https://agent.example/20", "/tmp/test-fixture");

    @Test
    void native_approval_source_is_pinned_to_role_and_action_provenance() {
        var model = S07ApprovalFixture.release(TENANT).models().getFirst();
        var validator = new ProcessModelValidator();
        var parsed = assertDoesNotThrow(() -> validator.validateBpmn(model.xml().getBytes(StandardCharsets.UTF_8), model.definitionKey()));
        assertDoesNotThrow(() -> validator.validateHumanTaskBindings(parsed, Map.of(
                "actionIntentId", ValueType.STRING, "inputSha256", ValueType.STRING, "businessVersion", ValueType.STRING,
                "toolSha256", ValueType.STRING), null));
        var registration = S07ApprovalFixture.registration(TENANT, "verifier");
        assertEquals(Set.of("501"), registration.eligibleRoles());
        var properties = new LinkedHashMap<String, String>();
        S07ApprovalFixture.processProperties(properties, 0, TENANT, "verifier");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("approved-process-fixture", new LinkedHashMap<>(properties)));
        var configured = Binder.get(environment).bind("stellar.tianji.process.approval-verification",
                Bindable.of(ApprovalVerificationProperties.class)).get().registrations();
        assertEquals(List.of(registration), configured);
        assertNotEquals(registration.fingerprint(), S07ApprovalFixture.registration(TENANT, "wrong-verifier").fingerprint());
        var command = S07ApprovalFixture.sourceStart("business", "intent", "a".repeat(64), "7", "b".repeat(64));
        assertEquals("intent", command.commandId());
        assertEquals("intent", command.correlationId());
        assertEquals(Map.of("actionIntentId", "intent", "inputSha256", "a".repeat(64),
                "businessVersion", "7", "toolSha256", "b".repeat(64)), command.variables());
    }

    @Test
    void approval_source_publication_must_report_the_exact_model_digest_and_inputs() {
        var model = S07ApprovalFixture.release(TENANT).models().getFirst();
        String hash = S07ApprovalFixture.registration(TENANT, "verifier").definitionSha256();
        var pinned = new ProcessModelDTO(model.type(), model.definitionKey(), hash, model.inputs());
        assertDoesNotThrow(() -> S07ApprovalFixture.verify(release(pinned), hash));
        var wrong = new ProcessModelDTO(model.type(), model.definitionKey(), "f".repeat(64), model.inputs());
        assertThrows(IllegalStateException.class, () -> S07ApprovalFixture.verify(release(wrong), hash));
    }

    private static ProcessReleaseDTO release(ProcessModelDTO model) {
        return new ProcessReleaseDTO(S07ApprovalFixture.RELEASE, "a".repeat(64), Instant.parse("2026-11-06T00:00:00Z"), List.of(model));
    }
}
