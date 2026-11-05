package top.egon.cola.platform.tianji.process.server.acceptance;

import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.task.ApprovalConsumerRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class S07ApprovalFixture {
    static final String RELEASE = "s07-approval-source";
    static final String DEFINITION = "s07ApprovalSource";
    static final String TASK = "approveStart";
    private static final String POLICY = "s07-start-approval";
    private static final Duration AGE = Duration.ofMinutes(2);
    private static final Map<String, ProcessValueTypeEnum> INPUTS = Map.of(
            "actionIntentId", ProcessValueTypeEnum.STRING, "inputSha256", ProcessValueTypeEnum.STRING,
            "businessVersion", ProcessValueTypeEnum.STRING, "toolSha256", ProcessValueTypeEnum.STRING);
    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:task="urn:stellar-enclosure:tianji:task:1" targetNamespace="https://stellar-enclosure.example/test">
              <process id="s07ApprovalSource" name="S07 approval source" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="approveStart" name="Review governed START" task:kind="APPROVAL"
                          task:initialLane="reviewer" task:approvalVariable="startApproved">
                  <extensionElements>
                    <task:taskLane key="reviewer" roleIds="%s"/>
                    <task:taskField name="approved" target="startApproved" type="BOOLEAN"/>
                  </extensionElements>
                </userTask>
                <endEvent id="end"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="approveStart"/>
                <sequenceFlow id="toEnd" sourceRef="approveStart" targetRef="end"/>
              </process>
            </definitions>
            """;

    private S07ApprovalFixture() { }

    static PublishProcessReleaseCommand release(TenantBindingDTO tenant) {
        return new PublishProcessReleaseCommand(RELEASE, List.of(new ProcessModelResource(
                ProcessModelTypeEnum.BPMN, DEFINITION, model(tenant), INPUTS)));
    }

    static ApprovalConsumerRegistry.Registration registration(TenantBindingDTO tenant, String subject) {
        return new ApprovalConsumerRegistry.Registration(
                ProcessActor.verified(tenant.tenantId(), ProcessActor.SubjectType.SERVICE, subject, tenant.issuer()), POLICY,
                RELEASE, DEFINITION, sha(model(tenant)), TASK, Set.of(tenant.roleIds().getFirst()), AGE,
                Set.of(ApprovalRequesterTypeEnum.USER));
    }

    static void processProperties(Map<String, String> settings, int index, TenantBindingDTO tenant, String subject) {
        var binding = registration(tenant, subject);
        String prefix = "stellar.tianji.process.approval-verification.consumers[" + index + "].";
        var values = Map.ofEntries(Map.entry("tenant-id", Long.toString(tenant.tenantId())), Map.entry("issuer", tenant.issuer()),
                Map.entry("subject-id", subject), Map.entry("policy-ref", binding.policyRef()),
                Map.entry("release-version", binding.releaseVersion()), Map.entry("definition-key", binding.definitionKey()),
                Map.entry("definition-sha256", binding.definitionSha256()), Map.entry("task-definition-key", binding.taskDefinitionKey()),
                Map.entry("eligible-roles[0]", tenant.roleIds().getFirst()), Map.entry("maximum-age", AGE.toString()),
                Map.entry("requester-types[0]", "USER"));
        values.forEach((key, value) -> {
            if (settings.putIfAbsent(prefix + key, value) != null) throw invalid();
        });
    }

    static StartProcessCommand sourceStart(String businessKey, String intent, String inputSha, String version, String toolSha) {
        return new StartProcessCommand(DEFINITION, RELEASE, businessKey, intent, intent,
                Map.of("actionIntentId", intent, "inputSha256", inputSha, "businessVersion", version, "toolSha256", toolSha));
    }

    static void verify(ProcessReleaseDTO release, String modelSha) {
        if (release == null || !RELEASE.equals(release.releaseVersion()) || !release.models().equals(List.of(
                new ProcessModelDTO(ProcessModelTypeEnum.BPMN, DEFINITION, modelSha, INPUTS)))) throw invalid();
    }

    private static String model(TenantBindingDTO tenant) {
        String role = tenant.roleIds().getFirst();
        if (!role.matches("[1-9][0-9]{0,18}") || Long.parseLong(role) < 1) throw invalid();
        return BPMN.formatted(role);
    }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) { throw invalid(); }
    }

    private static IllegalStateException invalid() { return new IllegalStateException("S07 approval source is invalid"); }
}
