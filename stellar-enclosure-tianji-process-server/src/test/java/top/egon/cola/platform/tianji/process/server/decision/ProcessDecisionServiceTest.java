package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionQuery;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.DecisionRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessDecisionServiceTest {
    @Test
    void requires_decision_history_and_shared_storage() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        var process = mock(SpringProcessEngineConfiguration.class);
        when(process.getDataSource()).thenReturn(source);
        when(process.getTransactionManager()).thenReturn(manager);
        when(process.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(process.getDatabaseSchema()).thenReturn("tianji_process");
        when(process.getBeans()).thenReturn(Map.of());
        var dmn = mock(SpringDmnEngineConfiguration.class);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(dmn.getDmnHistoryService()).thenReturn(mock(DmnHistoryService.class));
        when(dmn.getCommandExecutor()).thenReturn(mock(CommandExecutor.class));
        when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
        when(dmn.getBeans()).thenReturn(Map.of());
        when(dmn.isHistoryEnabled()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService(process, dmn, new ProcessModelValidator()));
        verifyNoInteractions(source);
    }

    @Test
    void validates_request_identifiers_and_copies_inputs() {
        Map<String, Object> source = new java.util.HashMap<>(Map.of("amount", 10));
        var command = new ProcessDecisionService.EvaluateCommand("v1", "decision", "request-1", "trace-1", source);
        source.put("amount", 20);
        assertEquals(10, command.inputs().get("amount"));
        assertThrows(UnsupportedOperationException.class, () -> command.inputs().put("amount", 30));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("../v1", "decision", "r", "c", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("v1", "decision", "r\n", "c", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("v1", "decision", "r", "c", null));
    }

    @Test
    void executes_each_automation_rule_against_its_pinned_definition_with_closed_local_results() throws Exception {
        var first = automationRule("checkAmount", "baselineDecision", "decision-a-1", "deployment-a-1", 1);
        var second = automationRule("confirmRoute", "secondDecision", "decision-b-4", "deployment-b-4", 4);
        var executor = mock(FlowablePinnedDecisionExecutor.class);
        when(executor.evaluateAutomationForProcess(first.expected(), Map.of("amount", 10L), first.reference()))
                .thenReturn(new FlowablePinnedDecisionExecutor.Result("decision-a-1", 1, List.of(Map.of("route", "standard")), List.of(1)));
        when(executor.evaluateAutomationForProcess(second.expected(), Map.of("amount", 10L), second.reference()))
                .thenReturn(new FlowablePinnedDecisionExecutor.Result("decision-b-4", 4, List.of(Map.of("route", "review")), List.of(2)));

        var firstResult = ProcessDecisionService.evaluateAutomation(executor, first.binding(), first.row(), first.definition(),
                first.model(), first.reference());
        var secondResult = ProcessDecisionService.evaluateAutomation(executor, second.binding(), second.row(), second.definition(),
                second.model(), second.reference());

        assertEquals(Map.of("selected", List.of(Map.of("name", "standard"))), firstResult);
        assertEquals(Map.of("selected", List.of(Map.of("name", "review"))), secondResult);
        assertThrows(UnsupportedOperationException.class, () -> firstResult.put("extra", true));
        verify(executor).evaluateAutomationForProcess(first.expected(), Map.of("amount", 10L), first.reference());
        verify(executor).evaluateAutomationForProcess(second.expected(), Map.of("amount", 10L), second.reference());
        verifyNoMoreInteractions(executor);
    }

    @Test
    void rejects_wrong_automation_archive_or_typed_input_before_the_native_decision() throws Exception {
        var rule = automationRule("checkAmount", "baselineDecision", "decision-a-1", "deployment-a-1", 1);
        var executor = mock(FlowablePinnedDecisionExecutor.class);
        var wrongArchive = new Definition(Type.DMN, rule.definition().definitionKey(), "other-decision", "other-deployment", 2,
                "b".repeat(64), rule.definition().inputs());
        assertThrows(ProcessResourceNotFoundException.class, () -> ProcessDecisionService.evaluateAutomation(executor,
                rule.binding(), rule.row(), wrongArchive, rule.model(), rule.reference()));

        rule.row().setInput(Map.of("form", Map.of("amount", "10")));
        assertThrows(IllegalArgumentException.class, () -> ProcessDecisionService.evaluateAutomation(executor,
                rule.binding(), rule.row(), rule.definition(), rule.model(), rule.reference()));
        verifyNoInteractions(executor);
    }

    @Test
    void rejects_an_unmapped_or_inconsistent_native_decision_result() throws Exception {
        var rule = automationRule("checkAmount", "baselineDecision", "decision-a-1", "deployment-a-1", 1);
        var executor = mock(FlowablePinnedDecisionExecutor.class);
        when(executor.evaluateAutomationForProcess(rule.expected(), Map.of("amount", 10L), rule.reference()))
                .thenReturn(new FlowablePinnedDecisionExecutor.Result("decision-a-1", 1,
                        List.of(Map.of("route", "outside-published-enum")), List.of(1)))
                .thenReturn(new FlowablePinnedDecisionExecutor.Result("decision-a-2", 2,
                        List.of(Map.of("route", "standard")), List.of(1)))
                .thenReturn(new FlowablePinnedDecisionExecutor.Result("decision-a-1", 1,
                        List.of(Map.of("route", "standard"), Map.of("route", "review")), List.of(1, 2)));
        for (int index = 0; index < 3; index++) {
            assertThrows(ProcessDecisionFailedException.class, () -> ProcessDecisionService.evaluateAutomation(executor,
                    rule.binding(), rule.row(), rule.definition(), rule.model(), rule.reference()));
        }
    }

    @Test
    void legacy_decisions_reject_a_resource_above_the_legacy_size_limit() throws Exception {
        byte[] raw = baselineDecisionBytes("baselineDecision");
        byte[] padded = (new String(raw, StandardCharsets.UTF_8) + "<!--" + "x".repeat(512 * 1024) + "-->")
                .getBytes(StandardCharsets.UTF_8);
        var published = new ProcessModelValidator().validateDmn(raw, "baselineDecision");
        var oversized = mock(ValidatedProcessModel.class);
        when(oversized.type()).thenReturn(Type.DMN);
        when(oversized.definitionKey()).thenReturn(published.definitionKey());
        when(oversized.inputs()).thenReturn(published.inputs());
        when(oversized.xml()).thenReturn(padded);
        var binding = new FlowablePinnedDecisionExecutor.Binding(20, "decision-a-1", "deployment-a-1", 1, oversized);
        var fixture = nativeDecision();

        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.executor().evaluate(binding, Map.of("amount", 10)));
        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.executor().evaluateForProcess(binding, Map.of("amount", 10),
                new FlowablePinnedDecisionExecutor.ProcessReference("process-1", "execution-1", "activity-1")));
        verify(fixture.repository(), never()).getDmnResource(anyString());
        verifyNoInteractions(fixture.commands());
    }

    @Test
    void automation_decisions_reject_appended_and_truncated_native_bytes_before_execution() throws Exception {
        var rule = automationRule("checkAmount", "baselineDecision", "decision-a-1", "deployment-a-1", 1);
        var fixture = nativeDecision();
        byte[] expected = rule.model().xml();
        byte[] appended = Arrays.copyOf(expected, expected.length + 1);
        appended[expected.length] = ' ';
        byte[] truncated = Arrays.copyOf(expected, expected.length - 1);
        when(fixture.repository().getDmnResource(rule.expected().definitionId()))
                .thenReturn(new ByteArrayInputStream(appended), new ByteArrayInputStream(truncated));

        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.executor().evaluateAutomationForProcess(
                rule.expected(), Map.of("amount", 10), rule.reference()));
        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.executor().evaluateAutomationForProcess(
                rule.expected(), Map.of("amount", 10), rule.reference()));
        verify(fixture.repository(), times(2)).getDmnResource(rule.expected().definitionId());
        verifyNoInteractions(fixture.commands());
    }

    private static NativeDecisionVO nativeDecision() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(source.getConnection()).thenReturn(connection);
        var engine = mock(SpringDmnEngineConfiguration.class);
        var repository = mock(DmnRepositoryService.class);
        var commands = mock(CommandExecutor.class);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(new DataSourceTransactionManager(source));
        when(engine.getDmnRepositoryService()).thenReturn(repository);
        when(engine.getCommandExecutor()).thenReturn(commands);
        when(engine.getBeans()).thenReturn(Map.of());
        var query = mock(DmnDecisionQuery.class, RETURNS_SELF);
        when(repository.createDecisionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mock(DmnDecision.class));
        return new NativeDecisionVO(new FlowablePinnedDecisionExecutor(engine), repository, commands);
    }

    private static AutomationRuleVO automationRule(String nodeId, String decisionKey, String nativeId, String deploymentId,
                                                    int nativeVersion) throws Exception {
        byte[] xml = baselineDecisionBytes(decisionKey);
        var model = new ProcessModelValidator().validateDmn(xml, decisionKey);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml));
        var definition = new Definition(Type.DMN, decisionKey, nativeId, deploymentId, nativeVersion, sha256, model.inputs());
        var input = objectSchema("form", objectField("amount", Map.of("type", "integer")));
        var output = objectSchema("selected", Map.of("type", "array", "maxItems", 2, "items",
                objectField("name", Map.of("type", "string", "enum", List.of("standard", "review")))));
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofSeconds(30),
                end, end, end, new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var reference = new DecisionRefVO(decisionKey, sha256, List.of(new MappingVO("amount", "input.form.amount")),
                List.of(new MappingVO("selected[0].name", "scope.route")));
        var binding = new AutomationNodeBindingVO(nodeId, "RULE", input, output, List.of(), List.of(), policy,
                null, reference, null, null, null, null);
        var row = new ProcessNodeExecutionBO().setTenantId(20).setInstanceId("process-1").setNodeId(nodeId)
                .setKind(KindEnum.RULE).setState(StateEnum.RUNNING).setInput(Map.of("form", Map.of("amount", 10)))
                .setInputSchemaSha256(ProcessNodeLifecycleListener.schemaSha256(input))
                .setOutputSchemaSha256(ProcessNodeLifecycleListener.schemaSha256(output));
        return new AutomationRuleVO(model, definition, binding, row,
                new FlowablePinnedDecisionExecutor.ProcessReference("process-1", "exec-" + nodeId, "activity-" + nodeId),
                new FlowablePinnedDecisionExecutor.Binding(20, nativeId, deploymentId, nativeVersion, model));
    }

    private static byte[] baselineDecisionBytes(String decisionKey) throws Exception {
        try (var fixture = ProcessDecisionServiceTest.class.getResourceAsStream("/fixtures/baseline.dmn")) {
            assertNotNull(fixture);
            return new String(fixture.readAllBytes(), StandardCharsets.UTF_8).replace("baselineDecision", decisionKey)
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private static ProcessSchemaVO objectSchema(String name, Map<String, Object> field) {
        return ProcessSchemaVO.fromWire(objectField(name, field));
    }

    private static Map<String, Object> objectField(String name, Map<String, Object> field) {
        return Map.of("type", "object", "properties", Map.of(name, field), "required", List.of(name), "additionalProperties", false);
    }

    private record AutomationRuleVO(ValidatedProcessModel model, Definition definition, AutomationNodeBindingVO binding,
            ProcessNodeExecutionBO row, FlowablePinnedDecisionExecutor.ProcessReference reference,
            FlowablePinnedDecisionExecutor.Binding expected) { }

    private record NativeDecisionVO(FlowablePinnedDecisionExecutor executor, DmnRepositoryService repository, CommandExecutor commands) { }
}
