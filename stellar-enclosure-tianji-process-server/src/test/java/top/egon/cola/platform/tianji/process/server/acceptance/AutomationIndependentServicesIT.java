package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import top.egon.cola.platform.tianji.client.HttpProcessActivationClient;
import top.egon.cola.platform.tianji.client.HttpProcessAutomationClient;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.client.HttpProcessDraftClient;
import top.egon.cola.platform.tianji.client.HttpProcessEventClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.BusinessFormVO;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.Cut;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.CutWitnessVO;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.GateMode;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.OperationWitnessVO;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceHarness.Scenario;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.Identity;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.JSON;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.Role;
import static top.egon.cola.platform.tianji.process.server.acceptance.AutomationAcceptanceManifestVO.TenantFixtureVO;

@EnabledIfSystemProperty(named = "tianji.automation.acceptance.required", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class AutomationIndependentServicesIT {
    private static final String V2 = "/api/tianji/v2/";
    private static final String V1 = "/api/tianji/v1/";
    private static final String AS_RESOLVE = V1 + "agent-process-dispatches/resolve";
    private static final String AS_SUBMIT = V2 + "agent-process-dispatches/submit";
    private static final String PS_RESULT = V1 + "agent-result-inbox";
    private static final String PS_WORKER_COMPLETE = V2 + "worker-tasks/complete";
    private static final String PS_EVENT = V2 + "process-events";
    private static final Duration REQUEST = Duration.ofSeconds(10);
    private static final ProcessSchemaVO EMPTY = schema(Map.of());
    private static final Map<String, Object> TEXT = Map.of("type", "string");
    private static final Map<String, Object> BOOLEAN = Map.of("type", "boolean");
    private static final Map<String, Object> INTEGER = Map.of("type", "integer");
    private static final ProcessSchemaVO HUMAN_RESULT_REFERENCE = schema(Map.of("resultRef", TEXT), "resultRef");
    private static final ProcessSchemaVO WORK_IN = schema(Map.of("value", TEXT, "waitToken", TEXT));
    private static final ProcessSchemaVO ACCEPTED = schema(Map.of("accepted", BOOLEAN), "accepted");
    private static final ProcessSchemaVO VALUE = schema(Map.of("value", TEXT), "value");
    private static final ProcessSchemaVO ROUTE = schema(Map.of("route", TEXT), "route");
    private static final ProcessSchemaVO APPROVED = schema(Map.of("approved", BOOLEAN), "approved");
    private static final ProcessSchemaVO STAGE = schema(Map.of("stage", INTEGER), "stage");
    private static final NodePolicyVO.PolicyActionVO END = new NodePolicyVO.PolicyActionVO("END", null);
    private static final Map<Long, Map<List<Object>, AgentRuntimeTemplateCommand>> TEMPLATES = new HashMap<>();
    private static AutomationAcceptanceHarness fixture;

    @BeforeAll
    static void openFixture() {
        String input = System.getProperty("tianji.automation.acceptance.manifest");
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Private automation acceptance manifest is required");
        fixture = AutomationAcceptanceHarness.open(AutomationAcceptanceManifestVO.read(Path.of(input)));
        fixture.launchAll();
    }

    @AfterAll
    static void closeFixture() {
        if (fixture == null) return;
        try {
            fixture.writeEvidence();
        } finally {
            fixture.close();
        }
    }

    @Test @Order(1)
    void human_only() {
        var witnesses = new ArrayList<OperationWitnessVO>();
        for (var tenant : fixture.tenants()) {
            publishActivate(tenant, humanModel(), "fx-human-r1", 0);
            var instance = submit(tenant, start("fx_human", "human-" + tenant.tenantId(), Map.of(), Map.of()));
            var nativeTaskId = task(tenant, instance, "review");
            var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE,
                    null, instance.getInstanceId(), null, null, 10);
            var candidateClient = legacy(tenant, Identity.CANDIDATE);
            var tasks = awaitAvailable("current candidate task query", () -> candidateClient.humanTasks(query)).items();
            assertEquals(1, tasks.size());
            var selected = tasks.getFirst();
            var taskId = selected.taskId();
            assertEquals(nativeTaskId, taskId);
            assertEquals(HumanTaskKindEnum.ACTION, selected.kind());
            assertFalse(selected.assigned());
            var foreign = fixture.tenants().stream().filter(candidate -> candidate.tenantId() != tenant.tenantId()).findFirst().orElseThrow();
            var foreignCandidate = legacy(foreign, Identity.CANDIDATE);
            assertTrue(awaitAvailable("foreign candidate task query", () -> foreignCandidate.humanTasks(query)).items().isEmpty());
            try (var wrong = process(foreign, Identity.CANDIDATE)) {
                assertClientDenied(() -> wrong.getTask(taskId));
            }
            String choice = "approved";
            String opinion = "Reviewed in the business application fixture";
            byte[] attachment = ("Synthetic review attachment " + tenant.tenantId()).getBytes(StandardCharsets.UTF_8);
            String attachmentRef = "review-" + AutomationAcceptanceHarness.sha256(attachment);
            String form = fixture.persistForm(tenant.tenantId(), taskId, choice, opinion, attachmentRef, attachment);
            assertEquals(new BusinessFormVO(choice, opinion, attachmentRef, HexFormat.of().formatHex(attachment)),
                    fixture.readForm(tenant.tenantId(), form).orElseThrow(), "The tenant can read its saved business form and attachment");
            assertTrue(fixture.readForm(foreign.tenantId(), form).isEmpty(), "A foreign tenant cannot resolve the saved form reference");
            var result = Map.<String, Object>of("resultRef", form);
            String receipt = complete(tenant, taskId, result);
            var finished = requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
            assertEquals(result, finished.getOutput(), "The completed human process exposes exactly the business result reference");
            assertEquals(1, count(Role.WORKER, """
                    SELECT count(*) FROM automation_fixture.business_form
                    WHERE run_id=? AND tenant_id=? AND task_id=? AND result_ref=?
                    """, fixture.runId(), tenant.tenantId(), taskId, form));
            assertEquals(1, count(Role.PROCESS, """
                    SELECT count(*) FROM tianji_process.act_hi_taskinst WHERE proc_inst_id_=? AND end_time_ IS NOT NULL
                    """, instance.getInstanceId()));
            assertEquals(1, count(Role.PROCESS, """
                    SELECT count(*) FROM tianji_process.tianji_process_node_execution
                    WHERE tenant_id=? AND instance_id=? AND node_id='review' AND state='COMPLETED'
                      AND output_json=jsonb_build_object('resultRef', CAST(? AS text))
                    """, tenant.tenantId(), instance.getInstanceId(), form),
                    "The completed human node stores exactly the business result reference");
            witnesses.add(witness(tenant, instance, receipt, 0, 0));
        }
        fixture.record(Scenario.human_only, witnesses);
    }

    @Test @Order(2)
    void business_event_automation() throws IOException {
        var tenant = firstTenant();
        publishActivate(tenant, businessModel(), "fx-business-r1", 0);
        var event = event("fx_business", "biz-start", false, null, Map.of("value", "fixture"));
        try (var client = events(tenant)) {
            var received = client.receive(event);
            assertEquals(received.getReceiptId(), client.receive(event).getReceiptId());
            var receipt = awaitEvent(client, received.getReceiptId());
            var instance = load(tenant, receipt.getInstanceId(), Identity.PRODUCER);
            assertEquals("fx-business-r1", instance.getReleaseVersion());
            var sent = fixture.await("Worker SIGNAL reservation", () -> fixture.targetCalls().stream()
                    .filter(call -> call.tenantId() == tenant.tenantId() && call.waitToken() != null
                            && call.effectKey().equals(nodeEffect(tenant, instance, "external").orElse(null))).findFirst());
            fixture.await("native event wait", () -> node(tenant, instance, "eventWait").filter(row -> "WAITING".equals(row.state())));
            var signal = event("fx_business", "biz-signal", true, sent.waitToken(), Map.of("approved", true));
            var signalReceipt = client.receive(signal);
            assertEquals(signalReceipt.getReceiptId(), client.receive(signal).getReceiptId());
            assertEquals(instance.getInstanceId(), awaitEvent(client, signalReceipt.getReceiptId()).getInstanceId());
            requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED, Identity.PRODUCER);
            assertEquals("automatic", output(tenant, instance, "rule", "route"));
            assertEquals("true", output(tenant, instance, "join", "accepted"));
            assertEquals("true", output(tenant, instance, "eventWait", "approved"));
            String local = nodeEffect(tenant, instance, "transaction").orElseThrow();
            assertEquals(2, count(Role.WORKER, """
                    SELECT count(*) FROM automation_fixture.business_effect
                    WHERE run_id=? AND tenant_id=? AND effect_key IN (?,?)
                    """, fixture.runId(), tenant.tenantId(), local, sent.effectKey()));
            assertEquals(1, externalRequests(tenant, sent.effectKey()));
            assertEquals(1, bindingCount(tenant, received.getReceiptId()));
            fixture.record(Scenario.business_event_automation, List.of(witness(tenant, instance, signalReceipt.getReceiptId(), 2, 2)));
        }
    }

    @Test @Order(3)
    void mixed_agent_routing() throws IOException {
        var tenant = firstTenant();
        var template = template(tenant, "fixtureClassifier", 1);
        publishActivate(tenant, mixedModel(template), "fx-mixed-r1", 0);
        var proof = new ArrayList<OperationWitnessVO>();
        for (String mode : List.of("automatic", "manual", "invalid-schema")) {
            var instance = submit(tenant, start("fx_mixed", "mixed-" + mode, Map.of("scenario", mode), Map.of()));
            complete(tenant, task(tenant, instance, "submit"), Map.of());
            if ("automatic".equals(mode)) {
                requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
                assertEquals("automatic", output(tenant, instance, "classify", "route"));
                assertEquals("automatic", output(tenant, instance, "rule", "route"));
                assertEquals("true", output(tenant, instance, "lookup", "accepted"));
                assertEquals(0, nodeCount(tenant, instance, "review"));
            } else if ("manual".equals(mode)) {
                complete(tenant, task(tenant, instance, "review"), Map.of());
                requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
                assertEquals("manual", output(tenant, instance, "rule", "route"));
                assertEquals(0, nodeCount(tenant, instance, "lookup"));
            } else {
                complete(tenant, task(tenant, instance, "technicalError"), Map.of());
                requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
                var failed = node(tenant, instance, "classify").orElseThrow();
                assertEquals("FAILED", failed.state());
                assertEquals("FAILED", agentRun(tenant, failed.runId()).status());
                assertEquals(0, nodeCount(tenant, instance, "lookup"));
                assertEquals(0, nodeCount(tenant, instance, "review"));
            }
            assertEquals(0, effectCount(tenant, instance));
            proof.add(witness(tenant, instance, inbox(tenant, instance).id(), 0, 0));
        }
        fixture.record(Scenario.mixed_agent_routing, proof);
    }

    @Test @Order(4)
    void pure_agent_control_flow() throws IOException {
        var tenant = firstTenant();
        var template = template(tenant, "fixturePipeline", 1);
        publishActivate(tenant, chainModel("fx_chain", template, List.of("agentA", "agentB", "agentC")), "fx-chain-r1", 0);
        var serial = submit(tenant, start("fx_chain", "chain", Map.of("scenario", "pipeline", "stage", 0), Map.of()));
        requireFinished(tenant, serial, AutomationInstanceDTO.StateEnum.COMPLETED);
        int value = 0;
        Instant last = null;
        for (String name : List.of("agentA", "agentB", "agentC")) {
            var node = node(tenant, serial, name).orElseThrow();
            assertEquals(Integer.toString(value++), node.inputStage());
            assertEquals(Integer.toString(value), node.outputStage());
            var run = assertPinnedRun(tenant, node, template);
            if (last != null) assertFalse(run.createdAt().isBefore(last));
            last = run.finishedAt();
        }
        publishActivate(tenant, controlModel(template), "fx-control-r1", 0);
        var overrides = Map.of("left", new AutomationStartCommand.AgentTaskOverrideVO(null, Map.of("increment", 2)));
        var variant = submit(tenant, start("fx_control", "control", Map.of("scenario", "pipeline", "stage", 0), overrides));
        requireFinished(tenant, variant, AutomationInstanceDTO.StateEnum.COMPLETED);
        assertEquals("2", output(tenant, variant, "left", "stage"));
        assertEquals("1", output(tenant, variant, "right", "stage"));
        assertEquals("3", output(tenant, variant, "join", "stage"));
        assertEquals(1, nodeCount(tenant, variant, "chosen"));
        assertEquals(0, nodeCount(tenant, variant, "unselected"));
        var loop = nodes(tenant, variant, "loop.step");
        assertEquals(3, loop.size());
        assertEquals(3L, loop.stream().map(NodeVO::occurrence).distinct().count());
        for (int index = 0; index < 3; index++) {
            var round = loop.get(index);
            assertEquals(index, round.iteration());
            assertEquals(Integer.toString(index), round.inputStage());
            assertEquals(Integer.toString(index + 1), round.outputStage());
            assertPinnedRun(tenant, round, template);
        }
        assertEquals(1, count(Role.PROCESS, "SELECT count(*) FROM tianji_process.act_hi_procinst WHERE proc_inst_id_=?", variant.getInstanceId()));
        fixture.record(Scenario.pure_agent_control_flow, List.of(witness(tenant, serial, inbox(tenant, serial).id(), 0, 0),
                witness(tenant, variant, inbox(tenant, variant).id(), 0, 0)));
    }

    @Test @Order(5)
    void published_and_active_versions_are_independent() throws IOException {
        var tenant = firstTenant();
        var oldTemplate = template(tenant, "fixturePipeline", 1);
        var newTemplate = template(tenant, "fixturePipeline", 2);
        publishActivate(tenant, versionModel(oldTemplate, 1), "fx-version-r1", 0);
        var original = start("fx_version", "original", Map.of("value", "fixture"), Map.of());
        var a = submit(tenant, original);
        String aTask = task(tenant, a, "review");
        var frozen = binding(tenant, a);
        var model = versionModel(newTemplate, 2);
        int releases = releases(tenant, "fx_version");
        try (var api = drafts(tenant, Identity.PUBLISHER);
             var anotherSession = drafts(tenant, Identity.PUBLISHER);
             var publishOnly = activation(tenant, Identity.PUBLISHER);
             var act = activation(tenant, Identity.ACTIVATOR);
             var eventApi = events(tenant)) {
            var draft = api.create("fx_version", new CreateProcessDraftCommand("Copy original", "fx-version-r1", "copy-version"));
            var raw = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", "fx_version", "<unfinished")),
                    "{", null, null, null);
            var saved = api.save(draft.getDraftId(), new SaveProcessDraftCommand("save-incomplete", 1, "V2", raw));
            var reloaded = anotherSession.get(draft.getDraftId(), "save-incomplete");
            assertEquals(saved.getRevision(), reloaded.getRevision());
            assertEquals("<unfinished", reloaded.getContent().models().getFirst().xml());
            assertEquals(releases, releases(tenant, "fx_version"));
            assertEquals(1L, act.list("fx_version", null, 5, null).getCurrent().revision());
            var finalSave = api.save(draft.getDraftId(), new SaveProcessDraftCommand("save-complete", saved.getRevision(),
                    "V2", AutomationReleaseStore.copyContent(model)));
            validateAndPublish(api, draft.getDraftId(), finalSave.getRevision(), finalSave.getContentSha256(), "fx-version-r2");
            assertClientDenied(() -> publishOnly.activate("fx_version", new ActivationCommand("fx-version-r2", 1L, "publish-only")));
            assertEquals("fx-version-r1", submit(tenant, start("fx_version", "pre-activation", Map.of("value", "fixture"), Map.of())).getReleaseVersion());

            var oldEvent = event("fx_version", "delayed-admission", false, null, Map.of("value", "fixture"));
            Path before = fixture.mark("process-event-" + oldEvent.getEventId());
            ProcessEventReceiptDTO originalEvent;
            Path after = null;
            try {
                try (var dropped = fixture.gate(Role.PROCESS, PS_EVENT, "/eventId", oldEvent.getEventId(), GateMode.DROP_AFTER_RESPONSE)) {
                    assertThrows(RuntimeException.class, () -> eventApi.receive(oldEvent));
                    assertEquals(202, dropped.upstreamStatus());
                }
                originalEvent = eventApi.receive(oldEvent);
                assertEquals("fx-version-r1", originalEvent.getReleaseVersion());
                assertEquals("RECEIVED", originalEvent.getState());
                assertEquals(0, bindingCount(tenant, originalEvent.getReceiptId()));
                after = fixture.mark("process-event-after-native-" + originalEvent.getReceiptId());
                var switched = new ActivationCommand("fx-version-r2", 1L, "cut-to-v2");
                assertEquals(act.activate("fx_version", switched), act.activate("fx_version", switched));
                assertEquals("fx-version-r2", eventApi.receive(event("fx_version", "new-event", false, null,
                        Map.of("value", "fixture"))).getReleaseVersion());
            } finally {
                fixture.unmark(before);
            }
            try {
                fixture.await("native event START committed before Inbox decision", () ->
                        bindingCount(tenant, originalEvent.getReceiptId()) == 1 ? Optional.of(true) : Optional.empty());
                assertEquals("RECEIVED", eventApi.getReceipt(originalEvent.getReceiptId()).getState());
                fixture.await("original admission expiry by Process UTC clock", () -> count(Role.PROCESS, """
                        SELECT count(*) FROM tianji_process.tianji_process_event_inbox
                        WHERE tenant_id=? AND receipt_id=? AND admission_not_after<=clock_timestamp()
                        """, tenant.tenantId(), originalEvent.getReceiptId()) == 1 ? Optional.of(true) : Optional.empty());
            } finally {
                if (after != null) fixture.unmark(after);
            }
            var delayed = awaitEvent(eventApi, originalEvent.getReceiptId());
            assertEquals("fx-version-r1", delayed.getReleaseVersion());
            assertEquals(originalEvent.getReceiptId(), eventApi.receive(oldEvent).getReceiptId());
            assertEquals(1, bindingCount(tenant, originalEvent.getReceiptId()));
            var b = submit(tenant, start("fx_version", "v2-instance", Map.of("value", "fixture"), Map.of()));
            assertEquals("fx-version-r2", b.getReleaseVersion());
            assertEquals(a.getInstanceId(), submit(tenant, original).getInstanceId());
            var legacyClient = legacy(tenant, Identity.REQUESTER);
            assertEquals(a.getInstanceId(), legacyClient.getStartReceipt(HttpProcessAutomationClient.startReceiptQuery(original)).instanceId());
            complete(tenant, aTask, Map.of());
            requireFinished(tenant, a, AutomationInstanceDTO.StateEnum.COMPLETED);
            assertEquals(frozen, binding(tenant, a));
            assertVersionDependencies(tenant, a, oldTemplate, 1);
            act.activate("fx_version", new ActivationCommand("fx-version-r1", 2L, "back-to-v1"));
            var c = submit(tenant, start("fx_version", "rollback", Map.of("value", "fixture"), Map.of()));
            assertEquals("fx-version-r1", c.getReleaseVersion());
            assertEquals(3L, c.getActivationRevision());
            complete(tenant, task(tenant, b, "review"), Map.of());
            requireFinished(tenant, b, AutomationInstanceDTO.StateEnum.COMPLETED);
            assertVersionDependencies(tenant, b, newTemplate, 2);
            fixture.record(Scenario.published_and_active_versions_are_independent, List.of(
                    witness(tenant, a, original.getCommandId(), 0, 0), witness(tenant, b, inbox(tenant, b).id(), 0, 0),
                    witness(tenant, c, delayed.getReceiptId(), 0, 0)));
        }
    }

    @Test @Order(6)
    void each_owner_recovers_at_persisted_cuts() throws Exception {
        var tenant = firstTenant();
        var template = template(tenant, "fixturePipeline", 1);
        publishActivate(tenant, recoveryModel(template), "fx-recover-r1", 0);
        publishActivate(tenant, externalModel(), "fx-external-r1", 0);
        var proof = new ArrayList<OperationWitnessVO>();
        for (Cut cut : List.of(Cut.PS_BEFORE_DISPATCH, Cut.PS_AFTER_LINKED_ACCEPT_BEFORE_ACK,
                Cut.AS_AFTER_RUN_PERSISTED_BEFORE_EXECUTION, Cut.AS_AFTER_TERMINAL_BEFORE_DELIVERY,
                Cut.PS_AFTER_INBOX_COMMIT_BEFORE_APPLY)) {
            var instance = submit(tenant, recoveryStart("cut-" + cut.name()));
            String initial = task(tenant, instance, "review");
            switch (cut) {
                case PS_BEFORE_DISPATCH, PS_AFTER_LINKED_ACCEPT_BEFORE_ACK -> cutDispatch(tenant, instance, initial, cut);
                case AS_AFTER_RUN_PERSISTED_BEFORE_EXECUTION -> cutUnclaimed(tenant, instance, initial);
                case AS_AFTER_TERMINAL_BEFORE_DELIVERY -> cutTerminal(tenant, instance, initial);
                case PS_AFTER_INBOX_COMMIT_BEFORE_APPLY -> cutInbox(tenant, instance, initial);
                default -> throw new AssertionError("Unrecognized Agent cut");
            }
            complete(tenant, task(tenant, instance, "finish"), Map.of());
            requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
            var event = inbox(tenant, instance);
            assertEquals("APPLIED", event.state());
            assertEquals(1, nodeCount(tenant, instance, "run"));
            var run = assertPinnedRun(tenant, node(tenant, instance, "run").orElseThrow(), template);
            assertEquals(event.runId(), run.id());
            assertEquals(1, count(Role.AGENT, "SELECT count(*) FROM tianji_agent.agent_run WHERE tenant_id=? AND idempotency_key=?",
                    tenant.tenantId(), dispatch(tenant, instance).orElseThrow().key()));
            assertEquals(2, count(Role.PROCESS, """
                    SELECT count(*) FROM tianji_process.act_hi_taskinst WHERE proc_inst_id_=? AND end_time_ IS NOT NULL
                    """, instance.getInstanceId()));
            proof.add(witness(tenant, instance, event.id(), 0, 0));
        }
        proof.add(cutWorker(tenant, false, true));
        proof.add(cutWorker(tenant, true, false));
        fixture.record(Scenario.each_owner_recovers_at_persisted_cuts, proof);
    }

    private static void cutDispatch(TenantFixtureVO tenant, AutomationInstanceDTO instance, String taskId, Cut cut) {
        boolean before = cut == Cut.PS_BEFORE_DISPATCH;
        try (var gate = fixture.gate(Role.AGENT, before ? AS_RESOLVE : AS_SUBMIT, "/link/scopeInstanceId", instance.getInstanceId(),
                before ? GateMode.BEFORE_FORWARD : GateMode.DROP_AFTER_RESPONSE)) {
            complete(tenant, taskId, Map.of());
            fixture.awaitDurableCut(cut, gate::observed, () -> dispatch(tenant, instance).filter(dispatch ->
                    "PENDING".equals(dispatch.state()) && dispatch.runId() == null && (before
                            ? findAgentRun(tenant, dispatch.key()).isEmpty()
                            : gate.upstreamStatus() == 202 && findAgentRun(tenant, dispatch.key()).isPresent()))
                    .map(dispatch -> cutWitness(cut, dispatch.key(), dispatch.state())));
            replace(Role.PROCESS);
        }
    }

    private static void cutUnclaimed(TenantFixtureVO tenant, AutomationInstanceDTO instance, String taskId) {
        var cut = Cut.AS_AFTER_RUN_PERSISTED_BEFORE_EXECUTION;
        Path gate = fixture.mark("agent-before-claim");
        int calls = fixture.providerCalls();
        try {
            complete(tenant, taskId, Map.of());
            fixture.awaitDurableCut(cut, () -> Files.exists(gate), () -> dispatch(tenant, instance)
                    .flatMap(dispatch -> findAgentRun(tenant, dispatch.key())).filter(run ->
                            "QUEUED".equals(run.status()) && run.attempt() == 0 && run.leaseOwner() == null
                            && calls == fixture.providerCalls() && count(Role.AGENT, """
                                SELECT count(*) FROM tianji_agent.agent_run_service_execution_grant
                                WHERE tenant_id=? AND run_id=? AND ciphertext IS NOT NULL
                                """, tenant.tenantId(), run.id()) == 1)
                    .map(run -> cutWitness(cut, run.id(), run.status())));
            replace(Role.AGENT);
        } finally {
            fixture.unmark(gate);
        }
    }

    private static void cutTerminal(TenantFixtureVO tenant, AutomationInstanceDTO instance, String taskId) {
        var cut = Cut.AS_AFTER_TERMINAL_BEFORE_DELIVERY;
        try (var gate = fixture.gate(Role.PROCESS, PS_RESULT, "/link/scopeInstanceId", instance.getInstanceId(), GateMode.BEFORE_FORWARD)) {
            complete(tenant, taskId, Map.of());
            fixture.awaitDurableCut(cut, gate::observed, () -> dispatch(tenant, instance)
                    .flatMap(dispatch -> findAgentRun(tenant, dispatch.key())).filter(run ->
                            "COMPLETED".equals(run.status()) && outboxCount(tenant, run.id()) == 1
                            && inboxRows(tenant, instance).isEmpty()).map(run -> cutWitness(cut, run.id(), run.status())));
            String originalHash = gate.bodySha256();
            replace(Role.AGENT);
            gate.allowFutureCalls();
            var applied = fixture.await("original terminal event accepted after AS restart", () -> inboxRows(tenant, instance).stream()
                    .filter(row -> "APPLIED".equals(row.state())).findFirst());
            var forwarded = fixture.await("replayed terminal event transport", () -> gate.forwardedHashes().isEmpty()
                    ? Optional.empty() : Optional.of(gate.forwardedHashes()));
            assertTrue(forwarded.stream().allMatch(originalHash::equals));
            assertEquals(originalHash, outboxHash(tenant, applied.runId()));
        }
    }

    private static void cutInbox(TenantFixtureVO tenant, AutomationInstanceDTO instance, String taskId) throws Exception {
        var cut = Cut.PS_AFTER_INBOX_COMMIT_BEFORE_APPLY;
        Path model = fixture.mark("agent-before-model-response");
        int calls = fixture.providerCalls();
        java.sql.Connection lock = null;
        try {
            complete(tenant, taskId, Map.of());
            fixture.await("acknowledged native Agent wait before locking root", () -> dispatch(tenant, instance)
                    .filter(row -> "ACKNOWLEDGED".equals(row.state()) && fixture.providerCalls() > calls));
            lock = fixture.lockRoot(tenant.tenantId(), instance.getInstanceId());
            fixture.unmark(model);
            var held = lock;
            fixture.awaitDurableCut(cut, () -> connectionOpen(held), () -> inboxRows(tenant, instance).stream()
                    .filter(row -> "PENDING".equals(row.state())).findFirst().map(row -> cutWitness(cut, row.id(), row.state())));
            replace(Role.PROCESS);
        } finally {
            if (lock != null) fixture.unlockRoot(lock);
            if (Files.exists(model)) fixture.unmark(model);
        }
    }

    private static OperationWitnessVO cutWorker(TenantFixtureVO tenant, boolean reject, boolean replaceWorker) {
        String mode = reject ? "rejected-effect" : "applied-effect";
        var variables = Map.<String, Object>of("value", mode);
        String hash = ProcessTypedJson.sha256("AUTOMATION_WORKER_BUSINESS_INPUT_V2", ProcessTypedJson.canonicalBytes(variables, 65536));
        AutomationInstanceDTO instance;
        AutomationAcceptanceHarness.TargetCallVO sent;
        try (var gate = fixture.gateExternal(tenant.tenantId(), hash, reject)) {
            instance = submit(tenant, start("fx_external", mode, variables, Map.of()));
            sent = fixture.await("committed exact external target arrival", gate::observed);
            assertEquals(!reject, sent.applied());
            assertEquals(1, externalRequests(tenant, sent.effectKey()));
            if (replaceWorker) {
                var cut = Cut.WORKER_AFTER_EXTERNAL_APPLIED_BEFORE_REPORT;
                fixture.awaitDurableCut(cut, () -> gate.observed().isPresent(), () -> node(tenant, instance, "external")
                        .filter(row -> sent.effectKey().equals(row.effectKey()) && workerState(tenant, sent.effectKey()).equals("STARTED")
                                && !row.hasOutput() && workerLeases(tenant, row) == 1)
                        .map(row -> cutWitness(cut, sent.effectKey(), "STARTED")));
                replace(Role.WORKER, () -> {
                    var parked = awaitWorkerRecovery(tenant, instance);
                    assertEquals(1, workerLeases(tenant, parked));
                    assertEquals(1, externalRequests(tenant, sent.effectKey()));
                });
            }
        }
        var parked = awaitWorkerRecovery(tenant, instance);
        assertEquals(1, nodeCount(tenant, instance, "external"));
        assertEquals(1, externalRequests(tenant, sent.effectKey()));
        try (var ack = reject ? fixture.gate(Role.PROCESS, PS_WORKER_COMPLETE, "/effectKey", sent.effectKey(), GateMode.DROP_FIRST_RESPONSE) : null;
             var owner = process(tenant, Identity.REQUESTER)) {
            var paused = owner.listNodes(instance.getInstanceId(), "external", null, 10).getItems().getFirst();
            var retry = new NodeRetryCommand("recover-" + mode, paused.getNodeExecutionRef(), paused.getVersion(), "Verify committed external test effect", null);
            var reply = owner.retryNode(instance.getInstanceId(), retry);
            assertEquals(reply, owner.retryNode(instance.getInstanceId(), retry));
            requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.COMPLETED);
            if (ack != null) {
                var attempts = fixture.await("original Worker ACK repeated after response loss", () -> ack.forwardedHashes().size() >= 2
                        ? Optional.of(ack.forwardedHashes()) : Optional.empty());
                assertEquals(200, ack.upstreamStatus());
                assertTrue(attempts.stream().allMatch(ack.bodySha256()::equals));
            }
        }
        var attempts = nodes(tenant, instance, "external");
        assertEquals(2, attempts.size());
        assertEquals(sent.effectKey(), attempts.getLast().effectKey());
        assertEquals("WAIT_RECOVERY", parked.state());
        assertEquals("COMPLETED", attempts.getLast().state());
        assertEquals(1, count(Role.WORKER, "SELECT count(*) FROM automation_fixture.business_effect WHERE run_id=? AND tenant_id=? AND effect_key=?",
                fixture.runId(), tenant.tenantId(), sent.effectKey()));
        int requests = externalRequests(tenant, sent.effectKey());
        assertEquals(reject ? 2 : 1, requests);
        return witness(tenant, instance, attempts.getLast().ref(), 1, requests);
    }

    private static NodeVO awaitWorkerRecovery(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        return fixture.await("Worker unknown parked with original effect key", () -> node(tenant, instance, "external")
                .filter(row -> "WAIT_RECOVERY".equals(row.state()) && "UNKNOWN".equals(row.effectEvidence())));
    }

    @Test @Order(7)
    void foreign_owner_and_late_results_cannot_apply() throws Exception {
        var tenant = firstTenant();
        var foreign = fixture.tenants().getLast();
        var instance = submit(tenant, recoveryStart("deny-late"));
        String submitTask = task(tenant, instance, "review");
        try (var wrong = process(foreign, Identity.REQUESTER); var wrongSubject = process(tenant, Identity.PUBLISHER)) {
            assertClientDenied(() -> wrong.getInstance(instance.getInstanceId()));
            assertClientDenied(() -> wrongSubject.getInstance(instance.getInstanceId()));
        }
        try (var wrongCandidate = process(foreign, Identity.CANDIDATE)) {
            assertClientDenied(() -> wrongCandidate.completeTask(submitTask, new CompleteEngineHumanTaskCommand(
                    "wrong-complete", "wrong-version", Map.of())));
        }
        assertEquals(0, nodeCount(tenant, instance, "run"));
        var owner = legacy(tenant, Identity.REQUESTER);
        try (var gate = fixture.gate(Role.PROCESS, PS_RESULT, "/link/scopeInstanceId", instance.getInstanceId(), GateMode.BEFORE_FORWARD)) {
            complete(tenant, submitTask, Map.of());
            fixture.await("late result held before Process transport", () -> gate.observed() ? Optional.of(true) : Optional.empty());
            var root = owner.control(new GetProcessInstanceQuery(instance.getInstanceId()));
            var cancel = new CancelProcessCommand(instance.getInstanceId(), "cancel-held-agent", root.version(), "Acceptance cancellation");
            assertEquals(owner.cancel(cancel), owner.cancel(cancel));
            requireFinished(tenant, instance, AutomationInstanceDTO.StateEnum.CANCELLED);
        }
        var late = fixture.await("durable late result decision", () -> inboxRows(tenant, instance).stream()
                .filter(row -> "IGNORED_LATE".equals(row.state())).findFirst());
        assertEquals(0, nodeCount(tenant, instance, "finish"));
        assertEquals(0, effectCount(tenant, instance));
        assertEquals(1, nodeCount(tenant, instance, "run"));
        fixture.record(Scenario.foreign_owner_and_late_results_cannot_apply,
                List.of(witness(tenant, instance, late.id(), 0, 0)));
    }

    private static TenantFixtureVO firstTenant() { return fixture.tenants().getFirst(); }

    private static HttpProcessAutomationClient process(TenantFixtureVO tenant, Identity identity) {
        return new HttpProcessAutomationClient(fixture.endpoint(Role.PROCESS), fixture.http(), () -> fixture.token(tenant, identity), REQUEST,
                tenant.tenantId());
    }

    private static HttpProcessClient legacy(TenantFixtureVO tenant, Identity identity) {
        return new HttpProcessClient(fixture.endpoint(Role.PROCESS), fixture.http(), () -> fixture.token(tenant, identity), REQUEST);
    }

    private static HttpProcessDraftClient drafts(TenantFixtureVO tenant, Identity identity) {
        return new HttpProcessDraftClient(fixture.endpoint(Role.PROCESS), fixture.http(), () -> fixture.token(tenant, identity), REQUEST, tenant.tenantId());
    }

    private static HttpProcessActivationClient activation(TenantFixtureVO tenant, Identity identity) {
        return new HttpProcessActivationClient(fixture.endpoint(Role.PROCESS), fixture.http(), () -> fixture.token(tenant, identity), REQUEST, tenant.tenantId());
    }

    private static HttpProcessEventClient events(TenantFixtureVO tenant) {
        return new HttpProcessEventClient(fixture.endpoint(Role.PROCESS), fixture.http(), () -> fixture.token(tenant, Identity.PRODUCER), REQUEST,
                tenant.tenantId());
    }

    private static void assertClientDenied(org.junit.jupiter.api.function.Executable call) {
        var denied = assertThrows(top.egon.cola.platform.tianji.client.ProcessClientException.class, call);
        assertTrue(denied.httpStatus() == 403 || denied.httpStatus() == 404, () -> "Unexpected denial " + denied.httpStatus() + " " + denied.code());
    }

    private static void publishActivate(TenantFixtureVO tenant, AutomationModelSourceVO model, String version, long expected) {
        String definition = model.models().getFirst().definitionKey();
        try (var author = drafts(tenant, Identity.PUBLISHER); var publisher = activation(tenant, Identity.ACTIVATOR)) {
            var draft = author.create(definition, new CreateProcessDraftCommand(definition, null, "create-" + version));
            var saved = author.save(draft.getDraftId(), new SaveProcessDraftCommand("save-" + version, draft.getRevision(), definition,
                    AutomationReleaseStore.copyContent(model)));
            validateAndPublish(author, draft.getDraftId(), saved.getRevision(), saved.getContentSha256(), version);
            var activated = publisher.activate(definition, new ActivationCommand(version, expected, "activate-" + version));
            assertEquals(version, activated.getReleaseVersion());
            assertEquals(expected + 1, activated.getRevision());
        }
    }

    private static void validateAndPublish(HttpProcessDraftClient client, String draft, long revision, String hash, String version) {
        var validation = client.validate(new ValidateProcessDraftQuery(draft, revision, hash));
        assertTrue(validation.isValid(), () -> "Fixture source rejected " + version + " " +
                validation.getIssues().stream().map(issue -> issue.path() + "=" + issue.code()).toList());
        var command = new PublishProcessDraftCommand(draft, revision, hash, version, "publish-" + version);
        var original = awaitAvailable("original PUBLISH command response", () -> client.publish(command));
        assertEquals(version, original.releaseVersion());
        assertEquals(original, awaitAvailable("replayed PUBLISH command response", () -> client.publish(command)));
    }

    private static AgentRuntimeTemplateCommand template(TenantFixtureVO tenant, String key, long version) throws IOException {
        var registered = TEMPLATES.computeIfAbsent(tenant.tenantId(), ignored -> new HashMap<>());
        List<Object> ref = List.of(key, version);
        var cached = registered.get(ref);
        if (cached != null) return cached;
        var manifest = fixture.manifest();
        Path settings = manifest.candidate(Role.AGENT).roleProperties();
        var properties = new Properties();
        try (var reader = Files.newInputStream(settings)) {
            properties.load(reader);
        }
        Path catalog = AutomationAcceptanceManifestVO.requirePrivateChild(manifest.privateRoot(),
                Path.of(properties.getProperty("tianji.automation.fixture.template-catalog")));
        JsonNode selected = null;
        for (var row : JSON.readTree(AutomationAcceptanceManifestVO.readBytes(catalog, 128 * 1024)).path("templates")) {
            var command = row.path("command");
            if (row.path("tenantId").asLong() == tenant.tenantId()
                    && key.equals(command.path("templateKey").asText()) && version == command.path("templateVersion").asLong()) {
                assertEquals(tenant.issuer().toASCIIString(), row.path("issuer").asText());
                if (selected != null) throw new IllegalArgumentException("Duplicate fixture template");
                selected = command;
            }
        }
        assertNotNull(selected, "Expected a fixed fixture template");
        var local = AgentRuntimeTemplateCommand.fromExactWire(JSON.convertValue(selected,
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() { }));
        var published = raw(Role.AGENT, tenant, Identity.AGENT_PUBLISHER, "POST", V2 + "agent-definitions", selected, 200);
        assertEquals(local.contentSha256(), published.path("contentSha256").asText());
        assertEquals(key, published.path("templateKey").asText());
        assertEquals(version, published.path("templateVersion").asLong());
        registered.put(ref, local);
        return local;
    }

    private static JsonNode raw(Role role, TenantFixtureVO tenant, Identity identity, String method, String path, Object value, int status) {
        byte[] bytes = value == null ? null : JSON.writeValueAsBytes(value);
        var request = HttpRequest.newBuilder(fixture.endpoint(role).resolve(path)).timeout(REQUEST)
                .header("Authorization", "Bearer " + fixture.token(tenant, identity))
                .header("X-Egon-Tianji-Tenant", Long.toString(tenant.tenantId()))
                .header("Accept", "application/json")
                .method(method, bytes == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (bytes != null) {
            request.header("Content-Type", "application/json");
        }
        try {
            var reply = fixture.http().send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = reply.body()) {
                byte[] response = body.readNBytes(65537);
                assertTrue(response.length <= 65536);
                var wire = JSON.readTree(response);
                String code = wire == null ? "" : wire.path("code").asText();
                assertEquals(status, reply.statusCode(), () -> "Fixture API status " + path + " " + (code.matches("[A-Z0-9_]{1,96}") ? code : ""));
                return wire;
            }
        } catch (IOException unavailable) {
            throw new AssertionError("Fixture API transport unavailable at " + path);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Fixture API interrupted");
        }
    }

    private static AutomationStartCommand start(String definition, String operation, Map<String, Object> values,
            Map<String, AutomationStartCommand.AgentTaskOverrideVO> overrides) {
        return new AutomationStartCommand(definition, operation, "start-" + operation, "correlation-" + operation, values, overrides);
    }

    private static AutomationStartCommand recoveryStart(String operation) {
        return start("fx_recover", operation, Map.of("scenario", "pipeline", "stage", 0), Map.of());
    }

    private static AutomationInstanceDTO submit(TenantFixtureVO tenant, AutomationStartCommand command) {
        try (var owner = process(tenant, Identity.REQUESTER)) {
            return awaitAvailable("original START command response", () -> owner.start(command));
        }
    }

    private static AutomationInstanceDTO load(TenantFixtureVO tenant, String instance, Identity identity) {
        try (var owner = process(tenant, identity)) { return owner.getInstance(instance); }
    }

    private static AutomationInstanceDTO requireFinished(TenantFixtureVO tenant, AutomationInstanceDTO instance, AutomationInstanceDTO.StateEnum expected) {
        return requireFinished(tenant, instance, expected, Identity.REQUESTER);
    }

    private static AutomationInstanceDTO requireFinished(TenantFixtureVO tenant, AutomationInstanceDTO instance, AutomationInstanceDTO.StateEnum expected,
            Identity identity) {
        fixture.await("persisted terminal Process state for " + instance.getDefinitionKey(), () ->
                count(Role.PROCESS, "SELECT count(*) FROM tianji_process.act_hi_procinst WHERE proc_inst_id_=? AND end_time_ IS NOT NULL",
                        instance.getInstanceId()) == 1 ? Optional.of(true) : Optional.empty());
        var observed = awaitAvailable("terminal Process API state", () -> load(tenant, instance.getInstanceId(), identity));
        assertNotNull(observed.getFinishedAt());
        assertEquals(expected, observed.getState());
        assertEquals(instance.getReleaseVersion(), observed.getReleaseVersion());
        assertEquals(instance.getActivationRevision(), observed.getActivationRevision());
        assertEquals(1, count(Role.PROCESS, "SELECT count(*) FROM tianji_process.act_hi_procinst WHERE proc_inst_id_=? AND end_time_ IS NOT NULL",
                instance.getInstanceId()));
        assertEquals(0, count(Role.PROCESS, "SELECT count(*) FROM tianji_process.act_ru_execution WHERE proc_inst_id_=?", instance.getInstanceId()));
        return observed;
    }

    private static <T> T awaitAvailable(String description, Supplier<T> call) {
        return awaitAvailable(description, call, ignored -> true);
    }

    private static <T> T awaitAvailable(String description, Supplier<T> call, Predicate<T> ready) {
        return fixture.await(description, () -> {
            try {
                return Optional.of(call.get()).filter(ready);
            } catch (ProcessClientException failure) {
                if (failure.httpStatus() != 503 || !"TIANJI_DEPENDENCY_UNAVAILABLE".equals(failure.code())) throw failure;
                return Optional.empty();
            }
        });
    }

    private static String task(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node) {
        return fixture.await("persisted human wait " + node, () -> fixture.observe(Role.PROCESS).queryForList("""
                 SELECT t.id_ FROM tianji_process.act_ru_task t JOIN tianji_process.act_ru_execution e ON e.id_=t.execution_id_
                 JOIN tianji_process.tianji_process_node_execution n ON n.attempt_scope_id=e.parent_id_
                 WHERE n.tenant_id=? AND n.instance_id=? AND n.node_id=? AND n.state='WAITING'
                 """, String.class, tenant.tenantId(), instance.getInstanceId(), node).stream().findFirst());
    }

    private static String complete(TenantFixtureVO tenant, String task, Map<String, Object> result) {
        var claims = legacy(tenant, Identity.CANDIDATE);
        try (var candidate = process(tenant, Identity.CANDIDATE)) {
            var before = awaitAvailable("unclaimed HUMAN task API state", () -> candidate.getTask(task));
            assertEquals(List.of("CLAIM"), before.getAvailableActions());
            var claim = new ClaimHumanTaskCommand("claim-" + task, task, before.getVersion());
            var claimedReceipt = awaitAvailable("original HUMAN claim response", () -> claims.claimHumanTask(claim));
            assertEquals(claimedReceipt, awaitAvailable("replayed HUMAN claim response", () -> claims.claimHumanTask(claim)));
            var claimed = awaitAvailable("claimed HUMAN task API state", () -> candidate.getTask(task));
            assertTrue(claimed.isAssignedToMe());
            var command = new CompleteEngineHumanTaskCommand("complete-" + task, claimed.getVersion(), result);
            var original = awaitAvailable("original HUMAN complete response", () -> candidate.completeTask(task, command));
            assertEquals(original, awaitAvailable("replayed HUMAN complete response", () -> candidate.completeTask(task, command)));
            return command.getCommandId();
        }
    }

    private static ProcessEventCommand event(String definition, String id, boolean signal, String token, Map<String, Object> payload) {
        return new ProcessEventCommand().setEventId(id).setDefinitionKey(definition).setRouteKey(signal ? "fxSignal" : "fxStart")
                .setRouteVersion(1L).setType(signal ? "FixtureSignaled" : "FixtureCreated").setOccurredAt(fixture.now(Role.PROCESS))
                .setCorrelationKey("business-event-" + id).setWaitToken(token).setPayload(payload);
    }

    private static ProcessEventReceiptDTO awaitEvent(HttpProcessEventClient client, String receipt) {
        var settled = awaitAvailable("terminal event receipt", () -> client.getReceipt(receipt),
                observed -> !"RECEIVED".equals(observed.getState()));
        assertEquals("APPLIED", settled.getState(), settled::getDecisionCode);
        return settled;
    }

    private static CutWitnessVO cutWitness(Cut cut, String id, String state) {
        return new CutWitnessVO(cut, id, state, fixture.now(cut.owner()));
    }

    private static void replace(Role role) { replace(role, () -> { }); }

    private static void replace(Role role, Runnable whileStopped) {
        var before = fixture.currentChildren();
        fixture.replaceChildAtCut(role, whileStopped);
        var after = fixture.currentChildren();
        assertEquals(3, after.size());
        for (var child : before) {
            var replacement = after.stream().filter(entry -> entry.role() == child.role()).findFirst().orElseThrow();
            if (role == child.role()) {
                assertNotEquals(child.pid(), replacement.pid());
                assertEquals(child.generation() + 1, replacement.generation());
                assertEquals(child.candidateSha256(), replacement.candidateSha256());
            } else {
                assertEquals(child, replacement);
            }
        }
    }

    private static boolean connectionOpen(java.sql.Connection connection) {
        try { return !connection.isClosed(); }
        catch (java.sql.SQLException unavailable) { return false; }
    }

    private static OperationWitnessVO witness(TenantFixtureVO tenant, AutomationInstanceDTO instance, String receipt, int effects, int requests) {
        return new OperationWitnessVO(tenant.tenantId(), instance.getInstanceId(), instance.getReleaseVersion(),
                instance.getActivationRevision(), receipt, effects, requests);
    }

    private static int count(Role role, String sql, Object... args) {
        return fixture.observe(role).queryForObject(sql, Integer.class, args);
    }

    private static int nodeCount(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node) {
        return count(Role.PROCESS, "SELECT count(*) FROM tianji_process.tianji_process_node_execution WHERE tenant_id=? AND instance_id=? AND node_id=?",
                tenant.tenantId(), instance.getInstanceId(), node);
    }

    private static int bindingCount(TenantFixtureVO tenant, String receipt) {
        return count(Role.PROCESS, "SELECT count(*) FROM tianji_process.tianji_process_instance_binding WHERE tenant_id=? AND source_event_receipt_id=?",
                tenant.tenantId(), receipt);
    }

    private static int releases(TenantFixtureVO tenant, String key) {
        return count(Role.PROCESS, "SELECT count(*) FROM tianji_process.tianji_process_automation_release WHERE tenant_id=? AND root_definition_key=?",
                tenant.tenantId(), key);
    }

    private static int externalRequests(TenantFixtureVO tenant, String key) {
        return count(Role.WORKER, "SELECT count(*) FROM automation_fixture.target_dispatch WHERE run_id=? AND tenant_id=? AND effect_key=?",
                fixture.runId(), tenant.tenantId(), key);
    }

    private static int effectCount(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        var keys = fixture.observe(Role.PROCESS).queryForList("""
                SELECT DISTINCT effect_key FROM tianji_process.tianji_process_node_execution
                WHERE tenant_id=? AND instance_id=? AND effect_key IS NOT NULL
                """, String.class, tenant.tenantId(), instance.getInstanceId());
        return keys.stream().mapToInt(key -> count(Role.WORKER, """
                SELECT count(*) FROM automation_fixture.business_effect WHERE run_id=? AND tenant_id=? AND effect_key=?
                """, fixture.runId(), tenant.tenantId(), key)).sum();
    }

    private static String output(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node, String field) {
        return fixture.observe(Role.PROCESS).queryForObject("""
                SELECT output_json->>? FROM tianji_process.tianji_process_node_execution
                WHERE tenant_id=? AND instance_id=? AND node_id=? AND state='COMPLETED'
                """, String.class, field, tenant.tenantId(), instance.getInstanceId(), node);
    }

    private static Optional<String> nodeEffect(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node) {
        return node(tenant, instance, node).flatMap(row -> Optional.ofNullable(row.effectKey()));
    }

    private static Optional<NodeVO> node(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node) {
        return nodes(tenant, instance, node).stream().findFirst();
    }

    private static List<NodeVO> nodes(TenantFixtureVO tenant, AutomationInstanceDTO instance, String node) {
        return fixture.observe(Role.PROCESS).query("""
                SELECT node_execution_ref,occurrence_id,attempt,iteration_index,state,effect_key,agent_run_id,
                       input_json->>'stage' AS input_stage,output_json->>'stage' AS output_stage,
                       side_effect_outcome,capability_version,native_job_id,output_json IS NOT NULL
                FROM tianji_process.tianji_process_node_execution WHERE tenant_id=? AND instance_id=? AND node_id=?
                ORDER BY iteration_index NULLS FIRST,attempt
                """, (rows, row) -> new NodeVO(rows.getString(1), rows.getString(2), rows.getInt(3), rows.getInt(4),
                rows.getString(5), rows.getString(6), rows.getString(7), rows.getString(8), rows.getString(9), rows.getString(10),
                rows.getInt(11), rows.getString(12), rows.getBoolean(13)), tenant.tenantId(), instance.getInstanceId(), node);
    }

    private record NodeVO(String ref, String occurrence, int attempt, int iteration, String state, String effectKey, String runId,
                          String inputStage, String outputStage, String effectEvidence, int capabilityVersion, String nativeJobId, boolean hasOutput) { }

    private static Optional<DispatchVO> dispatch(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        return fixture.observe(Role.PROCESS).query("""
                SELECT idempotency_key,state,run_id FROM tianji_process.tianji_process_agent_dispatch WHERE tenant_id=? AND scope_id=?
                """, (rs, row) -> new DispatchVO(rs.getString(1), rs.getString(2), rs.getString(3)),
                tenant.tenantId(), instance.getInstanceId()).stream().findFirst();
    }

    private record DispatchVO(String key, String state, String runId) { }

    private static InboxVO inbox(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        return fixture.await("Agent inbox receipt applied", () -> inboxRows(tenant, instance).stream()
                .filter(row -> "APPLIED".equals(row.state())).findFirst());
    }

    private static List<InboxVO> inboxRows(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        return fixture.observe(Role.PROCESS).query("""
                SELECT event_id,state,run_id,event_sha256 FROM tianji_process.tianji_process_agent_result_inbox
                WHERE tenant_id=? AND scope_id=? ORDER BY received_at,event_id
                """, (rs, row) -> new InboxVO(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                tenant.tenantId(), instance.getInstanceId());
    }

    private record InboxVO(String id, String state, String runId, String digest) { }

    private static Optional<RunVO> findAgentRun(TenantFixtureVO tenant, String key) {
        return runRows(tenant, "r.idempotency_key", key).stream().findFirst();
    }

    private static RunVO agentRun(TenantFixtureVO tenant, String id) {
        return runRows(tenant, "r.run_id", id).stream().findFirst().orElseThrow();
    }

    private static List<RunVO> runRows(TenantFixtureVO tenant, String lookup, String value) {
        if (!List.of("r.idempotency_key", "r.run_id").contains(lookup)) throw new IllegalArgumentException("Invalid run lookup");
        return fixture.observe(Role.AGENT).query("""
                SELECT r.run_id,r.status,r.definition_key,r.definition_version,b.template_sha256,r.attempt,r.lease_owner,
                       r.created_at,r.finished_at,b.node_execution_ref
                FROM tianji_agent.agent_run r JOIN tianji_agent.agent_runtime_binding b USING (tenant_id,run_id)
                WHERE r.tenant_id=? AND %s=?
                """.formatted(lookup), (rs, row) -> {
            var end = rs.getObject(9, OffsetDateTime.class);
            return new RunVO(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getInt(6),
                    rs.getString(7), rs.getObject(8, OffsetDateTime.class).toInstant(), end == null ? null : end.toInstant(), rs.getString(10));
        }, tenant.tenantId(), value);
    }

    private record RunVO(String id, String status, String template, long version, String templateHash, int attempt,
                         String leaseOwner, Instant createdAt, Instant finishedAt, String nodeRef) { }

    private static RunVO assertPinnedRun(TenantFixtureVO tenant, NodeVO node, AgentRuntimeTemplateCommand template) {
        assertNotNull(node.runId());
        var run = agentRun(tenant, node.runId());
        assertEquals("COMPLETED", run.status());
        assertEquals(template.getTemplateKey(), run.template());
        assertEquals(template.getTemplateVersion(), run.version());
        assertEquals(template.contentSha256(), run.templateHash());
        assertEquals(node.ref(), run.nodeRef());
        assertEquals(1, node.attempt());
        return run;
    }

    private static int outboxCount(TenantFixtureVO tenant, String run) {
        return count(Role.AGENT, """
                SELECT count(*) FROM tianji_agent.egon_cola_outbox_message
                WHERE channel='tianji-agent-result' AND schema_version='2'
                  AND payload::jsonb->>'tenantId'=? AND payload::jsonb->>'runId'=?
                """, Long.toString(tenant.tenantId()), run);
    }

    private static String outboxHash(TenantFixtureVO tenant, String run) {
        String body = fixture.observe(Role.AGENT).queryForObject("""
                SELECT payload FROM tianji_agent.egon_cola_outbox_message
                WHERE channel='tianji-agent-result' AND schema_version='2'
                  AND payload::jsonb->>'tenantId'=? AND payload::jsonb->>'runId'=?
                """, String.class, Long.toString(tenant.tenantId()), run);
        return AutomationAcceptanceHarness.sha256(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String workerState(TenantFixtureVO tenant, String key) {
        return fixture.observe(Role.WORKER).queryForObject("""
                SELECT state FROM tianji_worker.tianji_worker_effect_receipt WHERE source_id='fixture-worker' AND tenant_id=? AND effect_key=?
                """, String.class, tenant.tenantId(), key);
    }

    private static int workerLeases(TenantFixtureVO tenant, NodeVO row) {
        return count(Role.PROCESS, """
                SELECT count(*) FROM tianji_process.tianji_worker_lease_receipt WHERE tenant_id=? AND job_id=?
                """, tenant.tenantId(), row.nativeJobId());
    }

    private static Map<String, Object> binding(TenantFixtureVO tenant, AutomationInstanceDTO instance) {
        return fixture.observe(Role.PROCESS).queryForMap("""
                SELECT b.release_key,b.activation_revision,b.configuration_sha256,b.start_request_sha256,h.proc_def_id_
                FROM tianji_process.tianji_process_instance_binding b JOIN tianji_process.act_hi_procinst h ON h.proc_inst_id_=b.instance_id
                WHERE b.tenant_id=? AND b.instance_id=?
                """, tenant.tenantId(), instance.getInstanceId());
    }

    private static void assertVersionDependencies(TenantFixtureVO tenant, AutomationInstanceDTO instance,
                                                    AgentRuntimeTemplateCommand template, int version) {
        assertEquals("version-" + version, output(tenant, instance, "rule", "route"));
        assertEquals(version, node(tenant, instance, "lookup").orElseThrow().capabilityVersion());
        assertPinnedRun(tenant, node(tenant, instance, "run").orElseThrow(), template);
    }

    private static AutomationModelSourceVO humanModel() {
        var mappings = List.of(new MappingVO("resultRef", "nodes.review.output.resultRef"));
        return model("fx_human", "", linear(List.of("review"), List.of("<userTask id=\"review\"/>")), EMPTY, HUMAN_RESULT_REFERENCE,
                List.of(human("review", HUMAN_RESULT_REFERENCE, mappings)), List.of(), List.of());
    }

    private static AutomationModelSourceVO businessModel() throws IOException {
        var decision = decision("fxBusinessRule", "value", "fixture", "automatic", "manual", null);
        var rootOutput = schema(Map.of("route", TEXT, "accepted", BOOLEAN, "approved", BOOLEAN), "accepted", "approved");
        var nodes = List.of(worker("lookup", "fixtureLookup", 1, WorkerEffectSafetyEnum.READ_ONLY, "input.value", normalPolicy()),
                rule("rule", decision, VALUE, ROUTE, List.of(new MappingVO("value", "input.value")),
                        List.of(new MappingVO("route", "nodes.rule.output.route"))),
                worker("transaction", "fixtureTransaction", 1, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, "input.value", normalPolicy()),
                worker("external", "fixtureExternal", 1, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "input.value", normalPolicy()),
                new AutomationNodeBindingVO("join", "AND_JOIN", EMPTY, ACCEPTED, List.of(),
                        List.of(new MappingVO("accepted", "nodes.join.output.accepted")), normalPolicy(), null, null, null, null, null,
                        List.of(new AutomationNodeBindingVO.MergeVO("accepted", List.of("transaction", "external"), "ALL"))),
                new AutomationNodeBindingVO("eventWait", "EVENT_WAIT", schema(Map.of("approved", BOOLEAN)), APPROVED, List.of(),
                        List.of(new MappingVO("approved", "nodes.eventWait.output.approved")), normalPolicy(), null, null, null, null, null, null));
        String graph = """
                <startEvent id="start"/><serviceTask id="lookup"/><businessRuleTask id="rule"/>
                <exclusiveGateway id="choice" default="fallback"/><parallelGateway id="split"/>
                <serviceTask id="transaction"/><serviceTask id="external"/><parallelGateway id="join"/>
                <intermediateCatchEvent id="eventWait"><messageEventDefinition messageRef="fixtureMessage"/></intermediateCatchEvent>
                <endEvent id="end"/>
                """
                + flow("start", "lookup") + flow("lookup", "rule") + flow("rule", "choice")
                + conditional("automatic", "choice", "split", "route", "\"automatic\"")
                + edge("fallback", "choice", "end") + flow("split", "transaction") + flow("split", "external")
                + flow("transaction", "join") + flow("external", "join") + flow("join", "eventWait") + flow("eventWait", "end");
        return model("fx_business", "<message id=\"fixtureMessage\" name=\"fixtureMessage\"/>", graph, VALUE, rootOutput,
                nodes, List.of(startRoute("fx_business"), signalRoute()), List.of(decision));
    }

    private static AutomationModelSourceVO mixedModel(AgentRuntimeTemplateCommand template) throws IOException {
        var decision = decision("fxMixedRule", "route", "automatic", "automatic", "manual", null);
        var onFailure = new NodePolicyVO(Duration.ofMinutes(3), Duration.ofSeconds(95), Duration.ofSeconds(30),
                new NodePolicyVO.PolicyActionVO("BRANCH", "technicalError"), END, END,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var nodes = List.of(human("submit", EMPTY, List.of()), human("review", EMPTY, List.of()), human("technicalError", EMPTY, List.of()),
                agent("classify", template, List.of(new MappingVO("scenario", "input.scenario")), onFailure, false),
                rule("rule", decision, template.getOutputSchema(), ROUTE, List.of(new MappingVO("route", "nodes.classify.output.route")),
                        List.of(new MappingVO("route", "nodes.rule.output.route"))),
                worker("lookup", "fixtureLookup", 1, WorkerEffectSafetyEnum.READ_ONLY, null, normalPolicy()));
        String graph = """
                <startEvent id="start"/><userTask id="submit"/><serviceTask id="classify"/><businessRuleTask id="rule"/>
                <exclusiveGateway id="choice" default="manual"/><serviceTask id="lookup"/><userTask id="review"/>
                <userTask id="technicalError"/><endEvent id="end"/>
                """
                + flow("start", "submit") + flow("submit", "classify") + flow("classify", "rule") + flow("rule", "choice")
                + conditional("automatic", "choice", "lookup", "route", "\"automatic\"")
                + edge("manual", "choice", "review") + flow("lookup", "end") + flow("review", "end") + flow("technicalError", "end");
        return model("fx_mixed", "", graph, template.getInputSchema(), schema(Map.of("route", TEXT)), nodes, List.of(), List.of(decision));
    }

    private static AutomationModelSourceVO chainModel(String definition, AgentRuntimeTemplateCommand template, List<String> names) {
        var nodes = new ArrayList<AutomationNodeBindingVO>();
        String source = "input.stage";
        for (String name : names) {
            nodes.add(agent(name, template, pipelineInputs(source), normalPolicy(), false));
            source = "nodes." + name + ".output.stage";
        }
        return model(definition, "", linear(names, names.stream().map(name -> "<serviceTask id=\"" + name + "\"/>").toList()),
                template.getInputSchema(), EMPTY, nodes, List.of(), List.of());
    }

    private static AutomationModelSourceVO controlModel(AgentRuntimeTemplateCommand template) {
        var carry = schema(Map.of("stage", template.getInputSchema().properties().get("stage").toWire()), "stage");
        var loopCondition = new AutomationNodeBindingVO.ConditionOperationVO("LT", List.of(
                new AutomationNodeBindingVO.ConditionPathVO("stage"), new AutomationNodeBindingVO.ConditionLiteralVO(3)));
        var loop = new AutomationNodeBindingVO("loop", "LOOP", EMPTY, carry, List.of(), List.of(), normalPolicy(),
                null, null, null, null, new AutomationNodeBindingVO.LoopControlVO(3, loopCondition, carry, Map.of("stage", 0),
                List.of(new MappingVO("stage", "nodes.loop.step.output.stage"))), null);
        var join = new AutomationNodeBindingVO("join", "AND_JOIN", EMPTY, STAGE, List.of(), List.of(), normalPolicy(),
                null, null, null, null, null, List.of(new AutomationNodeBindingVO.MergeVO("stage", List.of("left", "right"), "SUM")));
        var nodes = List.of(agent("left", template, pipelineInputs("input.stage"), normalPolicy(), true),
                agent("right", template, pipelineInputs("input.stage"), normalPolicy(), false), join,
                agent("chosen", template, pipelineInputs("input.stage"), normalPolicy(), false),
                agent("unselected", template, pipelineInputs("input.stage"), normalPolicy(), false), loop,
                agent("loop.step", template, pipelineInputs("scope.stage"), normalPolicy(), false));
        String graph = """
                <startEvent id="start"/><parallelGateway id="split"/><serviceTask id="left"/><serviceTask id="right"/>
                <parallelGateway id="join"/><exclusiveGateway id="choice" default="fallback"/>
                <serviceTask id="chosen"/><serviceTask id="unselected"/><exclusiveGateway id="rejoin"/>
                <subProcess id="loop"><startEvent id="loopStart"/><serviceTask id="step"/><endEvent id="stepEnd"/>
                """
                + flow("loopStart", "step") + flow("step", "stepEnd") + "</subProcess><endEvent id=\"end\"/>"
                + flow("start", "split") + flow("split", "left") + flow("split", "right") + flow("left", "join") + flow("right", "join")
                + flow("join", "choice") + conditional("normal", "choice", "chosen", "stage", "0") + edge("fallback", "choice", "unselected")
                + flow("chosen", "rejoin") + flow("unselected", "rejoin") + flow("rejoin", "loop") + flow("loop", "end");
        return model("fx_control", "", graph, template.getInputSchema(), EMPTY, nodes, List.of(), List.of());
    }

    private static AutomationModelSourceVO versionModel(AgentRuntimeTemplateCommand template, int version) throws IOException {
        var decision = decision("fxVersionRule" + version, "value", "fixture", "version-" + version, "version-" + version, version - 1);
        var ruleOutput = schema(Map.of("route", TEXT, "scenario", template.getInputSchema().properties().get("scenario").toWire(),
                "stage", template.getInputSchema().properties().get("stage").toWire()), "route", "scenario", "stage");
        var nodes = List.of(human("review", EMPTY, List.of()),
                worker("lookup", "fixtureLookup", version, WorkerEffectSafetyEnum.READ_ONLY, "input.value", normalPolicy()),
                rule("rule", decision, VALUE, ruleOutput, List.of(new MappingVO("value", "input.value")), List.of()),
                agent("run", template, List.of(new MappingVO("scenario", "nodes.rule.output.scenario"),
                        new MappingVO("stage", "nodes.rule.output.stage")), normalPolicy(), false));
        return model("fx_version", "", linear(List.of("review", "lookup", "rule", "run"), List.of(
                "<userTask id=\"review\"/>", "<serviceTask id=\"lookup\"/>", "<businessRuleTask id=\"rule\"/>", "<serviceTask id=\"run\"/>")),
                VALUE, EMPTY, nodes, List.of(startRoute("fx_version")), List.of(decision));
    }

    private static AutomationModelSourceVO recoveryModel(AgentRuntimeTemplateCommand template) {
        var nodes = List.of(human("review", EMPTY, List.of()), agent("run", template, pipelineInputs("input.stage"), normalPolicy(), false),
                human("finish", EMPTY, List.of()));
        return model("fx_recover", "", linear(List.of("review", "run", "finish"), List.of(
                "<userTask id=\"review\"/>", "<serviceTask id=\"run\"/>", "<userTask id=\"finish\"/>")),
                template.getInputSchema(), EMPTY, nodes, List.of(), List.of());
    }

    private static AutomationModelSourceVO externalModel() {
        var shortAttempt = new NodePolicyVO(Duration.ofSeconds(120), Duration.ofSeconds(16), Duration.ofSeconds(10), END, END, END,
                new NodePolicyVO.RetryPolicyVO(2, Duration.ZERO, List.of("TRANSIENT_WORK")), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        return model("fx_external", "", linear(List.of("external"), List.of("<serviceTask id=\"external\"/>")), VALUE, EMPTY,
                List.of(worker("external", "fixtureExternal", 1, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "input.value", shortAttempt)),
                List.of(), List.of());
    }

    private static AutomationModelSourceVO model(String definition, String declarations, String flow, ProcessSchemaVO input,
            ProcessSchemaVO output, List<AutomationNodeBindingVO> nodes, List<EventRouteVO> routes,
            List<AutomationModelSourceVO.ModelSourceVO> additional) {
        String source = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:tianji:automation-fixture">
                  %s<process id="%s" isExecutable="true">%s</process>
                </definitions>
                """.formatted(declarations, definition, flow);
        var models = new ArrayList<AutomationModelSourceVO.ModelSourceVO>();
        models.add(new AutomationModelSourceVO.ModelSourceVO("BPMN", definition, source));
        models.addAll(additional);
        return new AutomationModelSourceVO("AUTOMATION_V2", models, input, output, nodes, routes);
    }

    private static String linear(List<String> names, List<String> content) {
        var xml = new StringBuilder("<startEvent id=\"start\"/>").append(String.join("", content)).append("<endEvent id=\"end\"/>");
        String last = "start";
        for (String name : names) {
            xml.append(flow(last, name));
            last = name;
        }
        return xml.append(flow(last, "end")).toString();
    }

    private static String flow(String from, String to) { return edge("path-" + from + "-" + to, from, to); }
    private static String edge(String id, String from, String to) {
        return "<sequenceFlow id=\"%s\" sourceRef=\"%s\" targetRef=\"%s\"/>".formatted(id, from, to);
    }
    private static String conditional(String id, String from, String to, String path, String literal) {
        return """
                <sequenceFlow id="%s" sourceRef="%s" targetRef="%s">
                  <conditionExpression>{"op":"EQ","args":[{"path":"%s"},{"literal":%s}]}</conditionExpression>
                </sequenceFlow>
                """.formatted(id, from, to, path, literal);
    }

    private static ProcessSchemaVO schema(Map<String, Object> properties, String... required) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false));
    }

    private static NodePolicyVO normalPolicy() {
        return new NodePolicyVO(Duration.ofMinutes(4), Duration.ofSeconds(105), Duration.ofSeconds(30), END, END, END,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
    }

    private static AutomationNodeBindingVO human(String node, ProcessSchemaVO result, List<MappingVO> output) {
        return new AutomationNodeBindingVO(node, "HUMAN", EMPTY, result, List.of(), output, normalPolicy(),
                null, null, null, new AutomationNodeBindingVO.HumanBindingVO("fixture-review", result, Duration.ofMinutes(3)), null, null);
    }

    private static AutomationNodeBindingVO worker(String node, String capability, int version, WorkerEffectSafetyEnum mode,
                                                   String valueSource, NodePolicyVO policy) {
        var input = valueSource == null ? List.<MappingVO>of() : List.of(new MappingVO("value", valueSource));
        return new AutomationNodeBindingVO(node, "SERVICE", WORK_IN, ACCEPTED, input, List.of(), policy,
                new AutomationNodeBindingVO.CapabilityRefVO(capability, version, schemaHash(WORK_IN), schemaHash(ACCEPTED), mode),
                null, null, null, null, null);
    }

    private static String schemaHash(ProcessSchemaVO schema) {
        return AutomationAcceptanceHarness.sha256(ProcessTypedJson.canonicalBytes(schema.toWire()));
    }

    private static AutomationNodeBindingVO agent(String node, AgentRuntimeTemplateCommand template, List<MappingVO> input,
                                                  NodePolicyVO policy, boolean overridable) {
        var reference = new TemplateRefVO(template.getTemplateKey(), template.getTemplateVersion(), template.contentSha256());
        var binding = new AgentTaskBindingVO(reference, List.of(reference), template.getParameterSchema(), Map.of(),
                overridable ? List.of("increment") : List.of(), null);
        return new AutomationNodeBindingVO(node, "AGENT", template.getInputSchema(), template.getOutputSchema(), input, List.of(),
                policy, null, null, binding, null, null, null);
    }

    private static List<MappingVO> pipelineInputs(String stage) {
        return List.of(new MappingVO("scenario", "input.scenario"), new MappingVO("stage", stage));
    }

    private static AutomationNodeBindingVO rule(String node, AutomationModelSourceVO.ModelSourceVO artifact, ProcessSchemaVO input,
                                                 ProcessSchemaVO output, List<MappingVO> inputMapping, List<MappingVO> outputMapping) {
        var inputs = input.properties().keySet().stream().map(key -> new MappingVO(key, "input." + key)).toList();
        var outputs = output.properties().keySet().stream().map(key -> new MappingVO(key, "scope." + key)).toList();
        return new AutomationNodeBindingVO(node, "RULE", input, output, inputMapping, outputMapping, normalPolicy(), null,
                new AutomationNodeBindingVO.DecisionRefVO(artifact.definitionKey(),
                        AutomationAcceptanceHarness.sha256(artifact.xml().getBytes(StandardCharsets.UTF_8)), inputs, outputs),
                null, null, null, null);
    }

    private static AutomationModelSourceVO.ModelSourceVO decision(String key, String input, String test,
            String matched, String fallback, Integer stage) throws IOException {
        String xml = new ClassPathResource("fixtures/baseline.dmn").getContentAsString(StandardCharsets.UTF_8)
                .replace("baselineDecision", key)
                .replace("typeRef=\"number\"><text>amount", "typeRef=\"string\"><text>" + input)
                .replace("&lt;= 10", "'" + test + "'")
                .replace("&gt; 10", "!= '" + test + "'")
                .replace("'standard'", "'" + matched + "'")
                .replace("'review'", "'" + fallback + "'");
        if (stage != null) {
            String output = "<output id=\"routeOutput\" name=\"route\" typeRef=\"string\"/>";
            xml = xml.replace(output, output + "<output id=\"scenarioOutput\" name=\"scenario\" typeRef=\"string\"/>"
                    + "<output id=\"stageOutput\" name=\"stage\" typeRef=\"number\"/>");
            xml = xml.replace("</rule>", "<outputEntry><text>'pipeline'</text></outputEntry><outputEntry><text>" + stage + "</text></outputEntry></rule>");
        }
        return new AutomationModelSourceVO.ModelSourceVO("DMN", key, xml);
    }

    private static EventRouteVO startRoute(String definition) {
        return new EventRouteVO("fxStart", 1, "FixtureCreated", "START", "fixture-events", VALUE,
                List.of(new MappingVO("value", "event.value")), Duration.ofSeconds(60), definition, null, null);
    }

    private static EventRouteVO signalRoute() {
        return new EventRouteVO("fxSignal", 1, "FixtureSignaled", "SIGNAL", "fixture-events", APPROVED,
                List.of(new MappingVO("approved", "event.approved")), Duration.ofSeconds(60), null, "eventWait", null);
    }
}
