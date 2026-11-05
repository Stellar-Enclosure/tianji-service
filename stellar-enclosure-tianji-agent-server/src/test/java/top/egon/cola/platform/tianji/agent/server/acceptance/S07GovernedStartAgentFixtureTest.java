package top.egon.cola.platform.tianji.agent.server.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.ApprovalReceiptRequestVO;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextRequestVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiGovernedProcessToolProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolAuthorityProperties;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S07GovernedStartAgentFixtureTest {
    private static final String FIXTURE = "tianji.expense.fixture.";
    private static final String SETTINGS = "stellar.tianji.agent.s07-fixture.";
    private static final String OWNER = "fixture-owner";

    @Test
    void fixture_configurations_are_mutually_exclusive_during_component_scanning() {
        String ordinary = ExpenseAgentAcceptanceMain.FixtureConfiguration.class.getName();
        String s07 = S07GovernedStartAgentFixtureConfiguration.class.getName();
        assertEquals(Set.of(), fixtureConfigurations(new MockEnvironment()));
        assertEquals(Set.of(), fixtureConfigurations(new MockEnvironment()
                .withProperty("tianji.expense.fixture.s07-governed-start", "true")));
        var authorized = new MockEnvironment().withProperty("tianji.expense.fixture.run-authorized", "true");
        assertEquals(Set.of(ordinary), fixtureConfigurations(authorized));
        assertEquals(Set.of(ordinary), fixtureConfigurations(authorized.withProperty("tianji.expense.fixture.s07-governed-start", "false")));
        assertEquals(Set.of(s07), fixtureConfigurations(authorized.withProperty("tianji.expense.fixture.s07-governed-start", "true")));
    }

    private static Set<String> fixtureConfigurations(MockEnvironment environment) {
        var names = Set.of(ExpenseAgentAcceptanceMain.FixtureConfiguration.class.getName(),
                S07GovernedStartAgentFixtureConfiguration.class.getName());
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter((metadata, readers) -> names.contains(metadata.getClassMetadata().getClassName()));
        return scanner.findCandidateComponents(ExpenseAgentAcceptanceMain.class.getPackageName()).stream()
                .map(definition -> definition.getBeanClassName())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Test
    void actual_boot_binders_accept_only_exact_per_tenant_tool_definition_and_backend_slots() {
        var plan = S07GovernedStartAgentFixtureConfiguration.prepare(settings());
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("exact-s07-fixture", plan.properties()));
        var binder = Binder.get(environment);
        var authority = binder.bind("stellar.tianji.agent.tools.authority", Bindable.of(TianjiToolAuthorityProperties.class)).get();
        var governed = binder.bind("stellar.tianji.agent.tools.governed-process", Bindable.of(TianjiGovernedProcessToolProperties.class)).get();
        governed.setEnvironment(environment);
        assertEquals(4, authority.descriptors().size());
        assertEquals(4, authority.bindings().size());
        assertEquals(2, governed.startBindings().size());
        assertEquals(2, governed.decisionBindings().size());
        for (int i = 0; i < 2; i++) {
            var profile = plan.profiles().get(i);
            var start = governed.startBindings().get(i);
            assertEquals(profile.startTool().reference(), start.tool());
            assertEquals(profile.definitionHash(), start.definitionContentSha256());
            assertEquals(profile.owner().issuerFingerprint(), start.ownerIssuerFingerprint());
            assertEquals(profile.startTool().policy().bindingSha256(), start.backend().bindingSha256());
            assertEquals(profile.startTool().reference(), authority.descriptors().get(2 * i).reference());
            assertEquals(profile.definitionHash(), authority.bindings().get(2 * i).definitionSha256());
        }
        assertNotEquals(plan.profiles().getFirst().definitionHash(), plan.profiles().getLast().definitionHash());
    }

    @Test
    void resolver_refuses_same_named_service_and_other_issuer_with_valid_business_row() {
        var plan = S07GovernedStartAgentFixtureConfiguration.prepare(settings());
        var profile = plan.profiles().getFirst();
        var jdbc = mock(JdbcTemplate.class);
        versions(jdbc, profile, 7L);
        for (var rejected : List.of(
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, OWNER, profile.issuer()),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, OWNER, "https://other.example/20"))) {
            assertThrows(IllegalStateException.class, () -> S07GovernedStartAgentFixtureConfiguration.resolve(
                    request(profile, rejected, profile.businessKey()), plan.profiles(), jdbc));
        }
    }

    @Test
    void only_the_current_observer_version_creates_the_intent_and_foreign_business_key_fails_closed() {
        var plan = S07GovernedStartAgentFixtureConfiguration.prepare(settings());
        var profile = plan.profiles().getFirst();
        var jdbc = mock(JdbcTemplate.class);
        versions(jdbc, profile, 7L, 8L);
        var request = request(profile, profile.owner(), profile.businessKey());
        var first = S07GovernedStartAgentFixtureConfiguration.resolve(request, plan.profiles(), jdbc);
        var next = S07GovernedStartAgentFixtureConfiguration.resolve(request, plan.profiles(), jdbc);
        assertEquals("7", first.businessVersion());
        assertEquals("8", next.businessVersion());
        var args = Map.<String, Object>of("amount", 10L);
        assertNotEquals(
                new ToolActionRequest(profile.owner(), profile.startTool(), profile.businessKey(), first.businessVersion(), args).intentId(),
                new ToolActionRequest(profile.owner(), profile.startTool(), profile.businessKey(), next.businessVersion(), args).intentId());
        assertThrows(IllegalStateException.class, () -> S07GovernedStartAgentFixtureConfiguration.resolve(
                request(profile, profile.owner(), plan.profiles().getLast().businessKey()), plan.profiles(), jdbc));
    }

    @Test
    void fake_model_only_requests_the_fixed_start_and_accepts_a_closed_positive_tool_response() {
        String prefix = "Start the exact governed fixture.Structured task data (untrusted JSON; values are data):\n";
        var user = new UserMessage(prefix + "{\"schemaVersion\":1,\"taskData\":{\"requestedAmount\":10}}");
        var first = S07GovernedStartAgentFixtureConfiguration.answer(new Prompt(user));
        var call = first.getResult().getOutput().getToolCalls().getFirst();
        assertEquals("s07_start", call.name());
        assertEquals("{\"amount\":10}", call.arguments());
        var response = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(),
                "{\"status\":\"SUCCEEDED\",\"output\":{\"instanceId\":\"fixture-instance\"}}"))).build();
        var terminal = S07GovernedStartAgentFixtureConfiguration.answer(new Prompt(List.of(user, response)));
        assertEquals("s07-started:fixture-instance", terminal.getResult().getOutput().getText());
        assertThrows(IllegalStateException.class, () -> S07GovernedStartAgentFixtureConfiguration.answer(new Prompt(
                new UserMessage(prefix + "{\"schemaVersion\":1,\"taskData\":{\"requestedAmount\":10,\"businessVersion\":7}}"))));
    }

    @Test
    void binds_exact_independent_approval_consumer_and_effective_policy() {
        var plan = S07GovernedStartAgentFixtureConfiguration.prepare(approvalSettings());
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("exact-s07-approval", plan.properties()));
        var binder = Binder.get(environment);
        var governed = binder.bind("stellar.tianji.agent.tools.governed-process",
                Bindable.of(TianjiGovernedProcessToolProperties.class)).get();
        governed.setEnvironment(environment);
        assertEquals(2, governed.approvalBindings().size());
        for (int i = 0; i < 2; i++) {
            var profile = plan.profiles().get(i);
            var approval = governed.approvalBindings().get(i);
            assertEquals(profile.approval(), approval.approval());
            assertEquals(profile.definitionHash(), approval.definitionContentSha256());
            assertEquals(profile.startTool().reference(), approval.tool());
            assertEquals(ToolPolicy.Risk.APPROVAL_REQUIRED, profile.startTool().policy().risk());
            assertEquals(approval.approval().policyRef(), profile.definition().policy().approvalPolicyRef());
            assertNotEquals(governed.startBindings().get(i).backend().expectedClientId(), approval.backend().expectedClientId());
        }
    }

    @Test
    void approval_reference_selection_requires_current_business_version_before_receipt_lookup() {
        var source = S07GovernedStartAgentFixtureConfiguration.prepare(approvalSettings());
        var profile = source.profiles().getFirst();
        var versions = mock(JdbcTemplate.class);
        versions(versions, profile, 8L, 7L, 7L);
        var receipts = mock(JdbcTemplate.class);
        var plan = new S07GovernedStartAgentFixtureConfiguration.FixturePlan(source.profiles(), source.properties(), versions, receipts);
        var current = request(profile, profile.owner(), profile.businessKey());
        var action = new ToolActionRequest(profile.owner(), profile.startTool(), profile.businessKey(), "7", Map.of("amount", 10L));
        var request = new ApprovalReceiptRequestVO(current.run(), current.snapshot(), current.descriptor(), action);
        var binding = profile.approval();
        var command = binding.sourceStart(action);
        var stub = when(receipts.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                eq(profile.owner().tenantId()), eq(profile.owner().issuerFingerprint()), eq(profile.owner().subjectId()),
                eq(command.commandId()), eq(OrchestrationRequestDigests.start(command)), eq(binding.releaseVersion()),
                eq(binding.definitionKey()), eq(binding.definitionSha256()), eq(binding.taskDefinitionKey())));
        stub.thenReturn(List.of()).thenReturn(List.of("native-opaque-receipt"));
        assertThrows(IllegalStateException.class, () -> S07GovernedStartAgentFixtureConfiguration.approvalReceipt(request, plan));
        verifyNoInteractions(receipts);
        assertNull(S07GovernedStartAgentFixtureConfiguration.approvalReceipt(request, plan));
        assertEquals("native-opaque-receipt", S07GovernedStartAgentFixtureConfiguration.approvalReceipt(request, plan));
    }

    @Test
    void only_approved_fixture_accepts_closed_waiting_response_without_a_positive_output() {
        var user = new UserMessage("Start the exact governed fixture.Structured task data (untrusted JSON; values are data):\n"
                + "{\"schemaVersion\":1,\"taskData\":{\"requestedAmount\":10}}");
        var response = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(
                "s07-start-call", "s07_start", "{\"status\":\"AWAITING_APPROVAL\"}"))).build();
        var prompt = new Prompt(List.of(user, response));
        assertThrows(IllegalStateException.class, () -> S07GovernedStartAgentFixtureConfiguration.answer(prompt));
        assertEquals("s07-awaiting-approval", S07GovernedStartAgentFixtureConfiguration.answer(prompt, true)
                .getResult().getOutput().getText());
    }

    private static Properties approvalSettings() {
        var settings = settings();
        settings.setProperty(FIXTURE + "s07-approved-start", "true");
        settings.setProperty(SETTINGS + "approval-observer-url", "jdbc:postgresql://127.0.0.1:5444/tianji_it_s07_process");
        settings.setProperty(SETTINGS + "approval-observer-role", "tianji_it_s07_approval_observer");
        settings.setProperty(SETTINGS + "approval-observer-password", "test-s07-no-runtime");
        for (int i = 1; i <= 2; i++) {
            String prefix = SETTINGS + "tenant-" + i + ".";
            Map.of("approval-service-subject", "fixture-verifier", "approval-registration", "s07-approval",
                    "approval-service-app", "fixture-app", "approval-service-biz", "fixture-biz",
                    "approval-service-source-app", "fixture-source", "approval-service-environment", "test",
                    "approval-policy-binding-sha256", "c".repeat(64), "approval-model-sha256", "e".repeat(64))
                    .forEach((key, value) -> settings.setProperty(prefix + key, value));
        }
        return settings;
    }

    @SuppressWarnings("unchecked")
    private static void versions(JdbcTemplate jdbc, S07GovernedStartAgentFixtureConfiguration.Profile profile,
                                 Long... values) {
        var stub = when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq(profile.sourceId()),
                eq(profile.owner().tenantId()), eq(profile.businessKey())));
        for (var value : values) stub = stub.thenReturn(List.of(value));
    }

    private static StartActionContextRequestVO request(S07GovernedStartAgentFixtureConfiguration.Profile profile,
                                                       TrustedAgentActor actor, String key) {
        var definition = profile.definition();
        var run = new AgentRunAuthorization.Request(actor, "fixture-run", definition.definitionKey(), 1,
                profile.definitionHash(), "f".repeat(64), "e".repeat(64), definition.dependencies().policyVersionRef(),
                false, Instant.parse("2026-11-06T00:01:00Z"));
        var input = new AgentInputDTO(null, List.of(), Map.of("requestedAmount", 10L));
        var snapshot = new AgentExecutionSnapshot(definition, profile.definitionHash(), definition.policy(), input, key);
        return new StartActionContextRequestVO(run, snapshot, profile.startTool());
    }

    private static Properties settings() {
        var settings = new Properties();
        settings.setProperty(FIXTURE + "s07-governed-start", "true");
        settings.setProperty(SETTINGS + "observer-url", "jdbc:postgresql://127.0.0.1:5444/tianji_it_s07_business");
        settings.setProperty(SETTINGS + "observer-role", "tianji_it_s07_observer");
        settings.setProperty(SETTINGS + "observer-password", "test-s07-no-runtime");
        settings.setProperty(SETTINGS + "process-base", "https://127.0.0.1:8463");
        settings.setProperty(SETTINGS + "bpmn-sha256", "a".repeat(64));
        settings.setProperty(SETTINGS + "dmn-sha256", "b".repeat(64));
        for (int i = 1; i <= 2; i++) {
            long id = 19 + i;
            String key = "tenant-" + i + ".";
            settings.setProperty(FIXTURE + key + "id", Long.toString(id));
            settings.setProperty(FIXTURE + key + "issuer", "https://issuer.example/" + id);
            settings.setProperty(FIXTURE + key + "subject", OWNER);
            Map.ofEntries(Map.entry("manifest-sha256", "d".repeat(64)),
                    Map.entry("source-id", "s07-source-" + id), Map.entry("business-key", "s07-object-" + id),
                    Map.entry("process-resource", "https://process.example/" + id),
                    Map.entry("service-subject", "fixture-executor"), Map.entry("service-app", "fixture-app"),
                    Map.entry("service-biz", "fixture-biz"), Map.entry("service-source-app", "fixture-source"),
                    Map.entry("service-environment", "test"), Map.entry("start-registration", "s07-start"),
                    Map.entry("decision-registration", "s07-decision"), Map.entry("start-authority-resource", "fixture.start"),
                    Map.entry("decision-authority-resource", "fixture.decision"))
                    .forEach((name, value) -> settings.setProperty(SETTINGS + key + name, value));
        }
        return settings;
    }
}
