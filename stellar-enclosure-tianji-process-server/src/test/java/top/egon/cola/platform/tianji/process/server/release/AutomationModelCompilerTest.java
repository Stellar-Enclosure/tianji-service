package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.ExternalWorkerServiceTask;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.MessageEventDefinition;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO.ModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledModelEvidenceVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.DependencySnapshotVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.VersionedRefVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.WorkerRegistrationVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.TemplateRegistrationVO;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutomationModelCompilerTest {
    private static final int PACK_BYTES = 4 * 1024 * 1024;
    private static final ProcessSchemaVO EMPTY = ProcessSchemaVO.fromWire(Map.of(
            "type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));
    private static final AutomationModelValidator.DependencySnapshotVO NO_DEPENDENCIES =
            AutomationModelValidator.DependencySnapshotVO.empty(11);
    private static final String LINEAR = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:test">
              <process id="bounded" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="finish" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String PARALLEL = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:test">
              <process id="bounded" isExecutable="true">
                <startEvent id="start"/>
                <parallelGateway id="split"/>
                <parallelGateway id="join"/>
                <endEvent id="end"/>
                <sequenceFlow id="enter" sourceRef="start" targetRef="split"/>
                <sequenceFlow id="left" sourceRef="split" targetRef="join"/>
                <sequenceFlow id="right" sourceRef="split" targetRef="join"/>
                <sequenceFlow id="exit" sourceRef="join" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String MIXED = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:test">
              <process id="bounded" isExecutable="true">
                <startEvent id="start"/>
                <businessRuleTask id="ruleOne"/>
                <exclusiveGateway id="choiceOne" default="skipRuleTwo"/>
                <businessRuleTask id="ruleTwo"/>
                <exclusiveGateway id="ruleJoin"/>
                <subProcess id="loop">
                  <startEvent id="startLoop"/>
                  <subProcess id="multi">
                    <startEvent id="startMulti"/>
                    <callActivity id="call" calledElement="child"/>
                    <endEvent id="endMulti"/>
                    <sequenceFlow id="callIn" sourceRef="startMulti" targetRef="call"/>
                    <sequenceFlow id="callOut" sourceRef="call" targetRef="endMulti"/>
                  </subProcess>
                  <endEvent id="endLoop"/>
                  <sequenceFlow id="multiIn" sourceRef="startLoop" targetRef="multi"/>
                  <sequenceFlow id="multiOut" sourceRef="multi" targetRef="endLoop"/>
                </subProcess>
                <exclusiveGateway id="choiceTwo" default="defaultEnd"/>
                <endEvent id="end"/>
                <sequenceFlow id="enter" sourceRef="start" targetRef="ruleOne"/>
                <sequenceFlow id="decide" sourceRef="ruleOne" targetRef="choiceOne"/>
                <sequenceFlow id="toRuleTwo" sourceRef="choiceOne" targetRef="ruleTwo">
                  <conditionExpression>{"op":"GT","args":[{"path":"amount"},{"literal":10}]}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="skipRuleTwo" sourceRef="choiceOne" targetRef="ruleJoin"/>
                <sequenceFlow id="fromRuleTwo" sourceRef="ruleTwo" targetRef="ruleJoin"/>
                <sequenceFlow id="loopIn" sourceRef="ruleJoin" targetRef="loop"/>
                <sequenceFlow id="loopOut" sourceRef="loop" targetRef="choiceTwo"/>
                <sequenceFlow id="defaultEnd" sourceRef="choiceTwo" targetRef="end"/>
                <sequenceFlow id="anotherEnd" sourceRef="choiceTwo" targetRef="end">
                  <conditionExpression>{"op":"LE","args":[{"path":"amount"},{"literal":10}]}</conditionExpression>
                </sequenceFlow>
              </process>
            </definitions>
            """;
    private static final String POLICY_BRANCH = LINEAR
            .replace("<endEvent id=\"end\"/>", """
                    <serviceTask id="work"/>
                    <intermediateCatchEvent id="recovery">
                      <timerEventDefinition><timeDuration>PT1M</timeDuration></timerEventDefinition>
                    </intermediateCatchEvent>
                    <endEvent id="end"/>
                    <sequenceFlow id="success" sourceRef="work" targetRef="end"/>
                    <sequenceFlow id="recovered" sourceRef="recovery" targetRef="end"/>
                    """)
            .replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"work\"");
    private final AutomationModelValidator validator = new AutomationModelValidator();
    private final AutomationModelCompiler compiler = new AutomationModelCompiler();

    @Test
    void compiles_the_same_native_bootstrap_and_independent_dmn_artifacts_twice() throws IOException {
        var model = source(List.of(root(LINEAR), decision("baselineDecision"),
                decision("baselineDecisionTwo")), List.of());
        var first = compile(model);
        var second = compile(model);

        assertEquals("bounded", first.rootDefinitionKey());
        assertEquals(first.sourceSha256(), second.sourceSha256());
        assertEquals(first.compiledSha256(), second.compiledSha256());
        assertEquals(first.dependencySha256(), second.dependencySha256());
        assertNotEquals(first.sourceSha256(), first.compiledSha256());
        assertEquals(first.nativeIds(), second.nativeIds());
        assertEquals(first.artifacts().keySet(), second.artifacts().keySet());
        for (var artifact : first.artifacts().entrySet()) {
            assertArrayEquals(artifact.getValue().xml(), second.artifacts().get(artifact.getKey()).xml());
        }
        byte[] xml = first.artifacts().get("bounded.bpmn20.xml").xml();
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var definition = nativeModel.getMainProcess();
        var bootstrap = assertInstanceOf(ServiceTask.class, definition.getFlowElement(first.nativeFirstActivity()));
        assertTrue(bootstrap.isAsynchronous());
        assertEquals(bootstrap.getId(), definition.findFlowElementsOfType(StartEvent.class, false)
                .getFirst().getOutgoingFlows().getFirst().getTargetRef());
        assertNotEquals(bootstrap.getId(), first.nativeIds().get("end"));
        for (String key : List.of("baselineDecision", "baselineDecisionTwo")) {
            byte[] artifact = first.artifacts().get(key + ".dmn").xml();
            var decision = new DmnXMLConverter().convertToDmnModel(() -> new ByteArrayInputStream(artifact), true, true);
            assertEquals(key, decision.getDecisions().getFirst().getId());
        }
    }

    @Test
    void accepts_models_above_the_legacy_limit_only_within_the_utf8_package_budget() {
        String padding = "<!--" + "界".repeat(SafeModelXml.MAX_BYTES / 3 + 20) + "-->";
        String large = LINEAR + padding;
        byte[] bytes = utf8(large);
        assertTrue(bytes.length > SafeModelXml.MAX_BYTES);
        assertThrows(IllegalArgumentException.class, () -> SafeModelXml.parse(bytes, true));
        assertEquals("definitions", SafeModelXml.parseAutomation(bytes, true, bytes.length)
                .getDocumentElement().getLocalName());
        assertThrows(IllegalArgumentException.class, () -> SafeModelXml.parseAutomation(bytes, true, bytes.length - 1));
        assertThrows(IllegalArgumentException.class, () -> SafeModelXml.parseAutomation(bytes, true, PACK_BYTES + 1));

        assertEquals("bounded", compile(source(List.of(root(large)), List.of())).rootDefinitionKey());
        String oversize = LINEAR + "<!--" + "界".repeat(PACK_BYTES / 6 + 10) + "-->";
        var first = root(oversize);
        var second = new ModelSourceVO("BPMN", "second", oversize.replace("id=\"bounded\"", "id=\"second\""));
        assertThrows(IllegalArgumentException.class, () -> source(List.of(first, second), List.of()));
    }

    @Test
    void rejects_dtd_processor_instructions_user_delegates_and_unbounded_cycles_before_emission() {
        var invalid = List.of(
                "<!DOCTYPE definitions>" + LINEAR,
                "<?external processing?>" + LINEAR,
                LINEAR.replace("<endEvent id=\"end\"/>", """
                        <serviceTask xmlns:flowable="http://flowable.org/bpmn" id="end" flowable:delegateExpression="${arbitraryBean}"/>
                        """),
                LINEAR.replace("targetRef=\"end\"", "targetRef=\"start\""));
        for (String xml : invalid) {
            assertThrows(IllegalArgumentException.class, () -> compile(source(List.of(root(xml)), List.of())));
        }
    }

    @Test
    void and_join_requires_a_binding_only_for_the_converging_gateway() {
        var join = binding("join", "AND_JOIN", List.of());
        var result = compile(source(List.of(root(PARALLEL)), List.of(join)));
        assertTrue(result.nativeIds().containsKey("join"));
        assertFalse(result.nativeIds().get("join").isBlank());
        assertThrows(IllegalArgumentException.class, () -> compile(source(List.of(root(PARALLEL)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> compile(source(List.of(root(PARALLEL)),
                List.of(join, binding("split", "AND_JOIN", List.of())))));
    }

    @Test
    void emits_a_stable_native_fixture_for_two_xor_two_rules_nested_bounded_controls_and_pinned_worker() throws IOException {
        var fixture = mixed(MIXED);
        var first = compiler.compile(validator.validate(fixture, workerRegistry(11)));
        var second = compiler.compile(validator.validate(fixture, workerRegistry(11)));
        byte[] xml = first.artifacts().get("bounded.bpmn20.xml").xml();
        assertArrayEquals(xml, second.artifacts().get("bounded.bpmn20.xml").xml());
        assertEquals(first.evidence(), second.evidence());
        assertEquals(first.occurrenceSlots(), second.occurrenceSlots());
        assertEquals(first.conditions(), second.conditions());
        assertEquals(3, first.conditions().size());
        assertEquals(3, first.artifacts().size());
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        assertEquals(1, nativeModel.getProcesses().size());
        assertInstanceOf(SubProcess.class, nativeModel.getFlowElement(first.nativeIds().get("loop.multi.call")));
        var multi = first.occurrenceSlots().get("loop.multi");
        assertTrue(assertInstanceOf(SubProcess.class, nativeModel.getFlowElement(multi.attemptOrBodyId()))
                .getLoopCharacteristics().isSequential());
        var worker = assertInstanceOf(ExternalWorkerServiceTask.class,
                nativeModel.getFlowElement(first.occurrenceSlots().get("loop.multi.call.work").activityId()));
        assertEquals("tianji-v2:worker:ffe2bfe745984f1226698a83cdf3fb65523de40f5ec62dbe505ceed3fd0080ca", worker.getTopic());
        assertTrue(worker.isDoNotIncludeVariables());
        assertEquals(List.of("nodeExecutionRef", "inputSha256", "configurationSha256"),
                worker.getInParameters().stream().map(parameter -> parameter.getTarget()).toList());
        assertFalse(new String(xml, StandardCharsets.UTF_8).contains("\"path\":\"amount\""));
        assertThrows(IllegalArgumentException.class, () -> new ProcessModelValidator().validateBpmn(xml, "bounded"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(fixture, NO_DEPENDENCIES));
        assertNotEquals(first.compiledSha256(), compiler.compile(validator.validate(fixture, workerRegistry(12))).compiledSha256());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(mixed(MIXED.replace(
                "{\"path\":\"amount\"}", "{\"path\":\"amount\",\"path\":\"unknown\"}")), workerRegistry(11)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(mixed(MIXED.replace(
                "{\"op\":\"GT\",\"args\":[{\"path\":\"amount\"},{\"literal\":10}]}", "${arbitraryBean.eval()}")), workerRegistry(11)));
    }

    @Test
    void compiles_distinct_policy_boundaries_and_rejects_cross_scope_or_cyclic_failure_targets() {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var branch = new NodePolicyVO.PolicyActionVO("BRANCH", "recovery");
        var compiled = compiler.compile(validator.validate(source(List.of(root(POLICY_BRANCH)), List.of(
                worker("work", policy(branch, branch, end)), binding("recovery", "TIMER", null))), workerRegistry(11)));
        byte[] xml = compiled.artifacts().get("bounded.bpmn20.xml").xml();
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true);
        var slots = compiled.policySlots().get("work");
        assertEquals(4, Set.of(slots.failureBoundaryId(), slots.attemptTimeoutBoundaryId(),
                slots.attemptEscalationBoundaryId(), slots.nodeTimeoutBoundaryId()).size());
        var failure = assertInstanceOf(BoundaryEvent.class, nativeModel.getFlowElement(slots.failureBoundaryId()));
        var attemptTimeout = assertInstanceOf(BoundaryEvent.class, nativeModel.getFlowElement(slots.attemptTimeoutBoundaryId()));
        assertEquals(compiled.nativeIds().get("work"), failure.getAttachedToRefId());
        assertEquals(compiled.occurrenceSlots().get("work").attemptOrBodyId(), attemptTimeout.getAttachedToRefId());
        var targetFlow = nativeModel.getFlowElement(slots.failureBoundaryId() + "_resolved");
        assertEquals(compiled.nativeIds().get("recovery"),
                assertInstanceOf(SequenceFlow.class, targetFlow).getTargetRef());

        for (String target : List.of("child.other", "work")) {
            var invalid = new NodePolicyVO.PolicyActionVO("BRANCH", target);
            var fixture = source(List.of(root(POLICY_BRANCH)), List.of(worker("work", policy(invalid, end, end)),
                    binding("recovery", "TIMER", null)));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(fixture, workerRegistry(11)));
        }
    }

    @Test
    void compiled_retry_and_recovery_wait_stay_parked_and_hard_timeout_terminates() {
        var recover = new NodePolicyVO.PolicyActionVO("WAIT_RECOVERY", null);
        var retry = new NodePolicyVO.PolicyActionVO("RETRY", null);
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        String xml = LINEAR.replace("<endEvent id=\"end\"/>", """
                <serviceTask id="work"/><endEvent id="end"/>
                <sequenceFlow id="worked" sourceRef="work" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"work\"");
        var compiled = compiler.compile(validator.validate(source(List.of(root(xml)),
                List.of(worker("work", policy(recover, retry, end)))), workerRegistry(11)));
        byte[] nativeXml = compiled.artifacts().get("bounded.bpmn20.xml").xml();
        var nativeModel = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(nativeXml), true, true);
        var slots = compiled.policySlots().get("work");
        for (String boundary : List.of(slots.failureBoundaryId(), slots.attemptEscalationBoundaryId())) {
            var wait = assertInstanceOf(IntermediateCatchEvent.class, nativeModel.getFlowElement(boundary + "_wait"));
            assertInstanceOf(MessageEventDefinition.class, wait.getEventDefinitions().getFirst());
            assertTrue(wait.getOutgoingFlows().isEmpty());
        }
        var timeout = assertInstanceOf(EndEvent.class, nativeModel.getFlowElement(slots.nodeTimeoutBoundaryId() + "_end"));
        assertTrue(assertInstanceOf(TerminateEventDefinition.class, timeout.getEventDefinitions().getFirst()).isTerminateAll());
        assertEquals(Duration.ofSeconds(30), slots.dispatchTimeout());
    }

    @Test
    void signed_artifact_shape_rejects_rehashed_foreign_delegate_and_alternate_native_start() {
        var compiled = compiler.compile(validator.validate(source(List.of(root(POLICY_BRANCH)), List.of(
                worker("work", policy(new NodePolicyVO.PolicyActionVO("BRANCH", "recovery"),
                        new NodePolicyVO.PolicyActionVO("END", null), new NodePolicyVO.PolicyActionVO("END", null))),
                binding("recovery", "TIMER", null))), workerRegistry(11)));
        String xml = new String(compiled.artifacts().get("bounded.bpmn20.xml").xml(), StandardCharsets.UTF_8);
        String first = compiled.nativeFirstActivity();
        String alternate = AutomationModelCompiler.id("test-second-start", "bounded");
        String tested = compiled.policySlots().get("work").failureBoundaryId() + "_recover";
        String allowedDelegate = "id=\"" + tested + "\" flowable:delegateExpression=\"${tianjiAutomationPolicy}\"";
        assertTrue(xml.contains(allowedDelegate));
        assertTrue(xml.contains("PT1M"));
        for (String variant : List.of(
                xml.replace(allowedDelegate, "id=\"" + tested + "\" flowable:class=\"user.NonBootstrapClass\""),
                xml.replace(allowedDelegate, allowedDelegate.replace("tianjiAutomationPolicy", "userBean")),
                xml.replace("PT1M", "${userBean.delay()}"),
                xml.replace("PT1M", "PT60S"),
                xml.replace("PT1M", "PT721H"),
                xml.replace("</process>", "<startEvent id=\"" + alternate + "\"/><sequenceFlow id=\""
                        + AutomationModelCompiler.id("test-second-flow", "bounded") + "\" targetRef=\"" + first
                        + "\" sourceRef=\"" + alternate + "\"/></process>"))) {
            byte[] bytes = utf8(variant);
            var forged = rehash(compiled.evidence(), bytes, compiled.nativeIds());
            assertThrows(IllegalArgumentException.class, () -> ProcessModelValidator.validateCompiledAutomationBpmn(bytes, forged));
        }
    }

    @Test
    void typed_scope_mappings_reject_uninitialized_outputs_and_business_failure_bypass() {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var branch = new NodePolicyVO.PolicyActionVO("BRANCH", "recovery");
        var sourceSchema = schema(Map.of("status", Map.of("type", "string")));
        var stagePolicy = policy(branch, end, end);
        var upstream = new AutomationNodeBindingVO("work", "TIMER", EMPTY, sourceSchema, List.of(),
                List.of(new AutomationNodeBindingVO.MappingVO("status", "nodes.work.output.status")), stagePolicy,
                null, null, null, null, null, null);
        var downstream = new AutomationNodeBindingVO("recovery", "TIMER", sourceSchema, EMPTY,
                List.of(new AutomationNodeBindingVO.MappingVO("status", "scope.status")), List.of(), defaultPolicy(),
                null, null, null, null, null, null);
        var sourceXml = POLICY_BRANCH.replace("<serviceTask id=\"work\"/>", """
                <intermediateCatchEvent id="work"><timerEventDefinition><timeDuration>PT1M</timeDuration>
                </timerEventDefinition></intermediateCatchEvent>
                """);
        var source = new AutomationModelSourceVO("AUTOMATION_V2", List.of(root(sourceXml)), EMPTY, sourceSchema,
                List.of(upstream, downstream), List.of());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(source, NO_DEPENDENCIES));
    }

    @Test
    void freezes_only_referenced_agent_configuration_in_tenant_dependency_evidence() {
        String xml = LINEAR.replace("<endEvent id=\"end\"/>", """
                <serviceTask id="ask"/><endEvent id="end"/>
                <sequenceFlow id="asked" sourceRef="ask" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"ask\"");
        var template = new TemplateRefVO("approvedAgent", 1, "a".repeat(64));
        var firstConfig = new TemplateRefVO("config", 1, "b".repeat(64));
        var nextConfig = new TemplateRefVO("config", 2, "c".repeat(64));
        String schemaHash = AutomationModelValidator.schemaSha256(EMPTY);
        var authority = new TemplateRegistrationVO(template, schemaHash, schemaHash, schemaHash);
        var registered = Map.of(new VersionedRefVO(template.key(), template.version()), authority);
        var trusted = new DependencySnapshotVO(11, Map.of(), registered, Set.of(firstConfig, nextConfig), Map.of(), Map.of());
        var shown = compiler.compile(validator.validate(source(List.of(root(xml)), List.of(agent(template, firstConfig))), trusted));
        var moved = compiler.compile(validator.validate(source(List.of(root(xml)), List.of(agent(template, nextConfig))), trusted));
        assertNotEquals(shown.dependencySha256(), moved.dependencySha256());
        assertEquals(Map.of("ask", Map.of("key", "config", "version", 1L, "contentSha256", "b".repeat(64))),
                shown.dependencyManifest().get("agentConfigurations"));
        var unavailable = new DependencySnapshotVO(11, Map.of(), registered, Set.of(nextConfig), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(source(List.of(root(xml)), List.of(agent(template, firstConfig))), unavailable));
    }

    @Test
    void rejects_required_input_that_has_no_compiled_mapping() {
        String xml = LINEAR.replace("<endEvent id=\"end\"/>", """
                <intermediateCatchEvent id="wait"><timerEventDefinition><timeDuration>PT1M</timeDuration>
                </timerEventDefinition></intermediateCatchEvent><endEvent id="end"/>
                <sequenceFlow id="waited" sourceRef="wait" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"wait\"");
        var required = schema(Map.of("amount", Map.of("type", "integer")));
        var missing = new AutomationNodeBindingVO("wait", "TIMER", required, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, null, null, null, null);
        var fixture = new AutomationModelSourceVO("AUTOMATION_V2", List.of(root(xml)), required, EMPTY, List.of(missing), List.of());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(fixture, NO_DEPENDENCIES));
    }

    private static AutomationNodeBindingVO agent(TemplateRefVO template, TemplateRefVO configuration) {
        var binding = new AgentTaskBindingVO(template, List.of(template), EMPTY, Map.of(), List.of(), configuration);
        return new AutomationNodeBindingVO("ask", "AGENT", EMPTY, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, binding, null, null, null);
    }

    @Test
    void reopens_only_matching_tenant_profile_definition_resource_and_compiler_evidence() {
        var source = source(List.of(root(LINEAR)), List.of());
        var compiled = compile(source);
        var evidence = compiled.evidence();
        var release = mock(SpringProcessEngineConfiguration.class);
        var repository = mock(RepositoryService.class);
        var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        var process = mock(ProcessDefinition.class);
        String filename = "bounded.bpmn20.xml";
        var definition = new ProcessReleaseService.Definition(Type.BPMN, "bounded", "def", "dep", 4,
                evidence.resourceDigests().get(filename), Map.of());
        var definitions = new ProcessReleaseService.Release("automationRelease", evidence.compiledSha256(),
                java.time.Instant.EPOCH, List.of(definition));
        when(release.getRepositoryService()).thenReturn(repository);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(process);
        when(process.getId()).thenReturn("def");
        when(process.getDeploymentId()).thenReturn("dep");
        when(process.getKey()).thenReturn("bounded");
        when(process.getTenantId()).thenReturn("11");
        when(process.getResourceName()).thenReturn(filename);
        when(process.getVersion()).thenReturn(4);
        when(repository.getResourceAsStream("dep", filename))
                .thenAnswer(ignored -> new ByteArrayInputStream(compiled.artifacts().get(filename).xml()));
        var verifier = new VerifiedProcessModels(release, mock(SpringDmnEngineConfiguration.class), new ProcessModelValidator());
        assertEquals(compiled.nativeIds(), verifier.automation(11, evidence, source, compiled.dependencyManifest(), definitions).nativeIds());
        var reopened = verifier.automation(11, evidence.compilerVersion(), evidence.sourceSha256(), evidence.compiledSha256(),
                evidence.dependencySha256(), source, compiled.dependencyManifest(), definitions);
        assertEquals(compiled.nativeIds(), reopened.nativeIds());
        assertEquals(compiled.controlBindings(), reopened.controlBindings());
        assertThrows(ProcessResourceNotFoundException.class, () -> verifier.automation(11, evidence.compilerVersion(),
                evidence.sourceSha256(), "f".repeat(64), evidence.dependencySha256(), source, compiled.dependencyManifest(), definitions));
        assertThrows(ProcessResourceNotFoundException.class,
                () -> verifier.automation(12, evidence, source, compiled.dependencyManifest(), definitions));
        assertThrows(ProcessResourceNotFoundException.class, () -> verifier.automation(11, evidence,
                source(List.of(root(LINEAR + "<!-- drift -->")), List.of()), compiled.dependencyManifest(), definitions));
        var changedIds = new java.util.LinkedHashMap<>(evidence.nativeIds());
        changedIds.put("end", changedIds.get("start"));
        var drift = rehash(evidence, compiled.artifacts().get(filename).xml(), changedIds);
        assertThrows(IllegalArgumentException.class,
                () -> ProcessModelValidator.validateCompiledAutomationBpmn(compiled.artifacts().get(filename).xml(), drift));
        assertThrows(ProcessResourceNotFoundException.class,
                () -> verifier.automation(11, drift, source, compiled.dependencyManifest(), definitions));
        var legacy = new CompiledModelEvidenceVO(11, "LEGACY_V1", evidence.compilerVersion(), evidence.rootDefinitionKey(),
                evidence.sourceSha256(), evidence.dependencySha256(), evidence.compiledSha256(), evidence.nativeFirstActivity(),
                evidence.resourceDigests(), evidence.nativeIds());
        assertThrows(ProcessResourceNotFoundException.class,
                () -> verifier.automation(11, legacy, source, compiled.dependencyManifest(), definitions));
        when(process.getResourceName()).thenReturn("foreign.bpmn20.xml");
        assertThrows(ProcessResourceNotFoundException.class,
                () -> verifier.automation(11, evidence, source, compiled.dependencyManifest(), definitions));
        assertThrows(ProcessResourceNotFoundException.class, () -> verifier.automation(11, evidence.compilerVersion(),
                evidence.sourceSha256(), evidence.compiledSha256(), evidence.dependencySha256(), source, compiled.dependencyManifest(), definitions));
        when(process.getResourceName()).thenReturn(filename);
        when(repository.getResourceAsStream("dep", filename)).thenReturn(new ByteArrayInputStream(utf8(LINEAR)));
        assertThrows(ProcessResourceNotFoundException.class,
                () -> verifier.automation(11, evidence, source, compiled.dependencyManifest(), definitions));
    }

    private static CompiledModelEvidenceVO rehash(CompiledModelEvidenceVO evidence, byte[] xml, Map<String, String> nativeIds) {
        var resourceDigests = new java.util.LinkedHashMap<>(evidence.resourceDigests());
        resourceDigests.put(evidence.rootDefinitionKey() + ".bpmn20.xml", AutomationModelValidator.rawSha256(xml));
        String digest = AutomationModelCompiler.compiledSha256(evidence.tenantId(), evidence.profile(), evidence.compilerVersion(),
                evidence.rootDefinitionKey(), evidence.nativeFirstActivity(), resourceDigests, nativeIds);
        return new CompiledModelEvidenceVO(evidence.tenantId(), evidence.profile(), evidence.compilerVersion(), evidence.rootDefinitionKey(),
                evidence.sourceSha256(), evidence.dependencySha256(), digest, evidence.nativeFirstActivity(), resourceDigests, nativeIds);
    }

    private AutomationModelCompiler.CompiledAutomationModelVO compile(AutomationModelSourceVO source) {
        return compiler.compile(validator.validate(source, NO_DEPENDENCIES));
    }

    private static AutomationNodeBindingVO binding(String id, String kind, List<AutomationNodeBindingVO.MergeVO> merge) {
        return new AutomationNodeBindingVO(id, kind, EMPTY, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, null, null, null, merge);
    }

    private static NodePolicyVO defaultPolicy() {
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        return policy(end, end, end);
    }

    private static NodePolicyVO policy(NodePolicyVO.PolicyActionVO failure, NodePolicyVO.PolicyActionVO attempt,
                                       NodePolicyVO.PolicyActionVO node) {
        return new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30),
                failure, attempt, node, new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()),
                "BEST_EFFORT_STOP", "WAIT_RECOVERY");
    }

    private static AutomationNodeBindingVO worker(String id, NodePolicyVO policy) {
        return new AutomationNodeBindingVO(id, "SERVICE", EMPTY, EMPTY, List.of(), List.of(), policy,
                capability(), null, null, null, null, null);
    }

    private static AutomationNodeBindingVO.CapabilityRefVO capability() {
        String schemaSha = AutomationModelValidator.schemaSha256(EMPTY);
        return new AutomationNodeBindingVO.CapabilityRefVO("_customer.check", 7, schemaSha, schemaSha, WorkerEffectSafetyEnum.READ_ONLY);
    }

    private static DependencySnapshotVO workerRegistry(long tenant) {
        return new DependencySnapshotVO(tenant, Map.of(new VersionedRefVO("_customer.check", 7),
                new WorkerRegistrationVO(capability(), "a".repeat(64))), Map.of(), Set.of(), Map.of(), Map.of());
    }

    private static AutomationModelSourceVO mixed(String xml) throws IOException {
        String childXml = LINEAR.replace("id=\"bounded\"", "id=\"child\"")
                .replace("<endEvent id=\"end\"/>", "<serviceTask id=\"work\"/><endEvent id=\"end\"/>")
                .replace("targetRef=\"end\"/>", "targetRef=\"work\"/>"
                        + "<sequenceFlow id=\"worked\" sourceRef=\"work\" targetRef=\"end\"/>");
        var child = new ModelSourceVO("BPMN", "child", childXml);
        var firstDecision = decision("baselineDecision");
        var secondDecision = decision("baselineDecisionTwo");
        var amount = schema(Map.of("amount", Map.of("type", "integer")));
        var rootInput = schema(Map.of("amount", Map.of("type", "integer"),
                "items", Map.of("type", "array", "items", Map.of("type", "integer"), "maxItems", 4)));
        var loop = new AutomationNodeBindingVO("loop", "LOOP", EMPTY, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, null, null, new AutomationNodeBindingVO.LoopControlVO(3,
                new AutomationNodeBindingVO.ConditionLiteralVO(false), EMPTY, Map.of(), List.of()), null);
        var multi = new AutomationNodeBindingVO("loop.multi", "MULTI_INSTANCE", EMPTY, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, null, null, new AutomationNodeBindingVO.MultiInstanceControlVO("input.items", true, 4, null), List.of());
        var call = new AutomationNodeBindingVO("loop.multi.call", "SUBPROCESS", EMPTY, EMPTY, List.of(), List.of(), defaultPolicy(),
                null, null, null, null, new AutomationNodeBindingVO.SubprocessControlVO("child",
                AutomationModelValidator.rawSha256(utf8(childXml))), null);
        return new AutomationModelSourceVO("AUTOMATION_V2", List.of(root(xml), child, firstDecision, secondDecision), rootInput,
                EMPTY, List.of(rule("ruleOne", amount, firstDecision), rule("ruleTwo", amount, secondDecision), loop, multi, call,
                worker("loop.multi.call.work", defaultPolicy())), List.of());
    }

    private static AutomationNodeBindingVO rule(String id, ProcessSchemaVO amount, ModelSourceVO decision) {
        var input = List.of(new AutomationNodeBindingVO.MappingVO("amount", "input.amount"));
        var ref = new AutomationNodeBindingVO.DecisionRefVO(decision.definitionKey(),
                AutomationModelValidator.rawSha256(utf8(decision.xml())), input,
                List.of(new AutomationNodeBindingVO.MappingVO("route", "scope.route")));
        return new AutomationNodeBindingVO(id, "RULE", amount, schema(Map.of("route", Map.of("type", "string"))),
                input, List.of(), defaultPolicy(), null, ref, null, null, null, null);
    }

    private static ProcessSchemaVO schema(Map<String, Object> fields) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", fields,
                "required", List.copyOf(fields.keySet()), "additionalProperties", false));
    }

    private static AutomationModelSourceVO source(List<ModelSourceVO> models, List<AutomationNodeBindingVO> bindings) {
        return new AutomationModelSourceVO("AUTOMATION_V2", models, EMPTY, EMPTY, bindings, List.of());
    }

    private static ModelSourceVO root(String xml) {
        return new ModelSourceVO("BPMN", "bounded", xml);
    }

    private static ModelSourceVO decision(String key) throws IOException {
        try (var stream = AutomationModelCompilerTest.class.getResourceAsStream("/fixtures/baseline.dmn")) {
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new ModelSourceVO("DMN", key, xml.replace("baselineDecision", key));
        }
    }

    private static byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
