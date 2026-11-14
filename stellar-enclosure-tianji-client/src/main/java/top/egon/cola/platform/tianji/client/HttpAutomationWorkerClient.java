package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.JsonNode;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.decode;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exact;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exactInteger;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.nullable;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.require;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.string;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.utc;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.encodeRequest;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.matching;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.opaque;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.requestTree;

/** Bounded typed Worker transport for one trusted tenant and source registration. */
public final class HttpAutomationWorkerClient implements AutomationWorkerClient, AutoCloseable {
    private static final String PATH = AutomationHttpTransport.V2 + "worker-tasks/";
    private static final int ACQUIRE_REQUEST_LIMIT = 4096;
    private static final int TASK_RESPONSE_LIMIT = 72 * 1024;
    private static final int REPORT_REQUEST_LIMIT = 68 * 1024;
    private static final int REPORT_RESPONSE_LIMIT = 4096;
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern REGISTERED_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Set<String> TENANT_CONFLICT = Set.of("TIANJI_TENANT_CONTEXT_CHANGED");
    private static final Map<Integer, String> EXTENDED_ERRORS = Map.of(415, "TIANJI_UNSUPPORTED_MEDIA_TYPE",
            429, "TIANJI_CAPACITY_EXCEEDED", 500, "TIANJI_INTERNAL_ERROR");
    private final AutomationHttpTransport acquireTransport;
    private final AutomationHttpTransport reportTransport;
    private final long tenantId;
    private final String sourceId;

    public HttpAutomationWorkerClient(URI baseUri, HttpClient http, Supplier<String> acquireToken, Supplier<String> reportToken,
                                      Duration timeout, long trustedTenantId, String trustedSourceId) {
        AutomationHttpTransport.require(trustedTenantId > 0 && trustedSourceId != null && REGISTERED_ID.matcher(trustedSourceId).matches()
                && acquireToken != null && reportToken != null);
        tenantId = trustedTenantId;
        sourceId = trustedSourceId;
        acquireTransport = new AutomationHttpTransport(baseUri, http, acquireToken, timeout, tenantId);
        reportTransport = new AutomationHttpTransport(baseUri, http, reportToken, timeout, tenantId);
    }

    @Override public AutomationWorkerAcquireDTO acquire(AutomationWorkerAcquireCommand command) {
        AutomationHttpTransport.require(command != null);
        var request = new AutomationWorkerAcquireCommand(command.getCapabilityKey(), command.getCapabilityVersion());
        AutomationHttpTransport.require(AutomationNodeBindingVO.isCapabilityKey(request.getCapabilityKey()));
        AutomationHttpTransport.revision(request.getCapabilityVersion(), 1);
        var response = acquireTransport.send("POST", PATH + "acquire", Map.of(), encodeRequest(request), ACQUIRE_REQUEST_LIMIT,
                200, TASK_RESPONSE_LIMIT, TENANT_CONFLICT, EXTENDED_ERRORS);
        return decode(response, AutomationWorkerAcquireDTO.class, HttpAutomationWorkerClient::acquisitionShape, acquired -> {
            if (!acquired.isAcquisitionConsistent()) return false;
            var task = acquired.getTask();
            if (task == null) return true;
            task.verifyContractHash(tenantId, sourceId);
            return request.getCapabilityKey().equals(task.getCapabilityKey()) && request.getCapabilityVersion() == task.getCapabilityVersion();
        });
    }

    @Override public AutomationWorkerReportDTO complete(AutomationWorkerCompletionCommand command) {
        AutomationHttpTransport.require(command != null && command.isCompletionValid());
        byte[] body = encodeRequest(command);
        JsonNode wire = requestTree(body, REPORT_REQUEST_LIMIT);
        exact(wire, "taskId", "activityOccurrenceId", "nodeExecutionRef", "leaseToken", "commandId", "effectKey", "businessReceiptId", "output");
        ReportIdentityVO identity = reportIdentity(wire);
        matching(wire.path("effectKey"), REGISTERED_ID);
        opaque(wire.path("businessReceiptId"), 255);
        validateReportCommand(body, AutomationWorkerCompletionCommand.class, AutomationWorkerCompletionCommand::isCompletionValid);
        return report("complete", body, identity, Set.of(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED));
    }

    @Override public AutomationWorkerReportDTO fail(AutomationWorkerFailureCommand command) {
        AutomationHttpTransport.require(command != null && command.getKind() != null && command.isFailureValid());
        byte[] body = encodeRequest(command);
        JsonNode wire = requestTree(body, REPORT_REQUEST_LIMIT);
        exact(wire, "taskId", "activityOccurrenceId", "nodeExecutionRef", "leaseToken", "commandId", "kind", "errorCode", "businessReceiptId");
        ReportIdentityVO identity = reportIdentity(wire);
        string(wire.path("kind"));
        matching(wire.path("errorCode"), ERROR_CODE);
        nullable(wire, "businessReceiptId", value -> opaque(value, 255));
        var accepted = validateReportCommand(body, AutomationWorkerFailureCommand.class, AutomationWorkerFailureCommand::isFailureValid);
        Set<AutomationWorkerReportDTO.OutcomeEnum> outcomes = switch (accepted.getKind()) {
            case UNKNOWN -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN);
            case PERMANENT -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.PERMANENT);
            case RETRYABLE -> Set.of(AutomationWorkerReportDTO.OutcomeEnum.RETRY_SCHEDULED, AutomationWorkerReportDTO.OutcomeEnum.NEEDS_MANUAL);
        };
        return report("fail", body, identity, outcomes);
    }

    private static <T> T validateReportCommand(byte[] body, Class<T> type, java.util.function.Predicate<T> validator) {
        try {
            return decode(new AutomationHttpTransport.ResponseVO(200, body), type, ignored -> { }, validator);
        } catch (ProcessClientException invalid) {
            throw new IllegalArgumentException("Automation Worker report is invalid");
        }
    }

    private AutomationWorkerReportDTO report(String operation, byte[] body, ReportIdentityVO identity,
                                            Set<AutomationWorkerReportDTO.OutcomeEnum> outcomes) {
        try {
            var response = reportTransport.send("POST", PATH + operation, Map.of(), body, REPORT_REQUEST_LIMIT,
                    200, REPORT_RESPONSE_LIMIT, TENANT_CONFLICT, EXTENDED_ERRORS);
            return decode(response, AutomationWorkerReportDTO.class, HttpAutomationWorkerClient::reportShape,
                    result -> identity.taskId().equals(result.getTaskId())
                            && identity.occurrenceId().equals(result.getActivityOccurrenceId()) && outcomes.contains(result.getOutcome()));
        } catch (ProcessClientException failure) {
            if (failure.correlationId() != null && failure.correlationId().contains(identity.leaseToken())) {
                throw new ProcessClientException(failure.httpStatus(), failure.code(), null, failure.retryAfter());
            }
            throw failure;
        }
    }

    private static void acquisitionShape(JsonNode root) {
        exact(root, "status", "task");
        string(root.path("status"));
        nullable(root, "task", task -> {
            exact(task, "taskId", "activityOccurrenceId", "nodeExecutionRef", "leaseToken", "capabilityKey", "capabilityVersion", "input",
                    "inputSha256", "effectKey", "attemptDeadlineAt", "leaseExpiresAt", "handlerVersion", "effectMode", "targetId",
                    "inputSchemaSha256", "outputSchemaSha256", "effectFingerprint", "effectRecovery", "taskContractSha256");
            for (String field : Set.of("taskId", "activityOccurrenceId", "nodeExecutionRef", "leaseToken", "capabilityKey",
                    "inputSha256", "effectKey", "handlerVersion", "effectMode", "inputSchemaSha256", "outputSchemaSha256",
                    "effectFingerprint", "taskContractSha256")) {
                string(task.path(field));
            }
            require(task.path("input").isObject());
            exactInteger(task.path("capabilityVersion"), 1);
            utc(task.path("attemptDeadlineAt"));
            utc(task.path("leaseExpiresAt"));
            nullable(task, "targetId", AutomationResponseCodec::string);
            nullable(task, "effectRecovery", HttpAutomationWorkerClient::effectRecoveryShape);
        });
    }

    private static void effectRecoveryShape(JsonNode evidence) {
        exact(evidence, "mode", "observedGeneration", "observedAt", "validUntil", "authorityRef", "evidenceSha256", "businessReceiptId", "resultSha256");
        string(evidence.path("mode"));
        string(evidence.path("authorityRef"));
        string(evidence.path("evidenceSha256"));
        nullable(evidence, "businessReceiptId", AutomationResponseCodec::string);
        nullable(evidence, "resultSha256", AutomationResponseCodec::string);
        exactInteger(evidence.path("observedGeneration"), 1);
        utc(evidence.path("observedAt"));
        utc(evidence.path("validUntil"));
    }

    private static void reportShape(JsonNode node) {
        exact(node, "taskId", "activityOccurrenceId", "outcome", "decidedAt");
        opaque(node.path("taskId"), 255);
        opaque(node.path("activityOccurrenceId"), 255);
        string(node.path("outcome"));
        utc(node.path("decidedAt"));
    }

    private static ReportIdentityVO reportIdentity(JsonNode node) {
        matching(node.path("nodeExecutionRef"), ID);
        matching(node.path("commandId"), REGISTERED_ID);
        return new ReportIdentityVO(opaque(node.path("taskId"), 255), opaque(node.path("activityOccurrenceId"), 255),
                string(node.path("leaseToken")));
    }

    @Override public void close() {
        acquireTransport.close();
        reportTransport.close();
    }

    private record ReportIdentityVO(String taskId, String occurrenceId, String leaseToken) {
        @Override public String toString() { return "ReportIdentityVO"; }
    }
}
