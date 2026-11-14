package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.request;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.response;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.JSON;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.TOKEN;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.assertRequest;

class HttpAutomationWorkerClientTest {
    private static final String PATH = "/api/tianji/v2/worker-tasks/";
    private static final String SOURCE = "worker-main";
    private HttpProcessDraftClientTest.Fixture fixture;
    private HttpAutomationWorkerClient client;
    private HttpClient http;

    @BeforeEach void start() throws IOException {
        fixture = new HttpProcessDraftClientTest.Fixture();
        http = HttpClient.newHttpClient();
        client = new HttpAutomationWorkerClient(fixture.base, http, () -> TOKEN, () -> "report-secret", Duration.ofSeconds(5), 11L, SOURCE);
    }

    @AfterEach void stop() {
        client.close();
        http.close();
        fixture.close();
    }

    @Test void uses_fixed_acquire_and_report_routes_bearer_scopes_and_original_lease_fields() {
        var task = task(11, SOURCE);
        fixture.handler = request -> {
            String path = request.path();
            return path.endsWith("/acquire") ? json(acquired(task)) : response(path, "post", 200);
        };
        var acquire = new AutomationWorkerAcquireCommand("customer.lookup", 1);
        var complete = complete();
        var fail = failure();
        assertEquals(task, client.acquire(acquire).getTask());
        assertEquals(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, client.complete(complete).getOutcome());
        assertEquals(AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN, client.fail(fail).getOutcome());
        assertRequest(fixture.take(), "POST", PATH + "acquire", AutomationWorkerAcquireCommand.class, acquire);
        assertReport(fixture.take(), PATH + "complete", AutomationWorkerCompletionCommand.class, complete);
        assertReport(fixture.take(), PATH + "fail", AutomationWorkerFailureCommand.class, fail);
        assertNull(fixture.poll());
    }

    @Test void verifies_trusted_tenant_source_and_capability_independently_of_wire_values() {
        var command = new AutomationWorkerAcquireCommand("customer.lookup", 1);
        var correct = task(11, SOURCE);
        var wrongCapability = task(11, SOURCE).setCapabilityKey("other.lookup");
        wrongCapability.setTaskContractSha256(wrongCapability.contractSha256(11, SOURCE));
        var stringTaskId = task(11, SOURCE).setTaskId("true");
        stringTaskId.setTaskContractSha256(stringTaskId.contractSha256(11, SOURCE));
        for (String invalid : List.of(acquired(task(12, SOURCE)), acquired(task(11, "other-worker")), acquired(wrongCapability),
                acquired(stringTaskId).replace("\"taskId\":\"true\"", "\"taskId\":true"),
                acquired(correct).replace("\"capabilityVersion\":1", "\"capabilityVersion\":1e0"),
                acquired(correct).replace("\"effectRecovery\":null,", ""),
                acquired(correct).replace("\"ACQUIRED\"", "\"UNKNOWN\""))) {
            fixture.handler = request -> json(invalid);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.acquire(command));
            fixture.take();
        }
        fixture.handler = request -> json("{\"status\":\"EMPTY\",\"task\":null}");
        assertEquals(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, client.acquire(command).getStatus());
        fixture.take();
        assertNull(fixture.poll());
    }

    @Test void loses_response_after_real_request_without_reacquire_or_lease_replay() {
        fixture.handler = request -> null;
        assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.acquire(new AutomationWorkerAcquireCommand("customer.lookup", 1)));
        assertEquals(PATH + "acquire", fixture.take().path());
        assertNull(fixture.poll());
        var original = failure();
        assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.fail(original));
        assertReport(fixture.take(), PATH + "fail", AutomationWorkerFailureCommand.class, original);
        assertNull(fixture.poll());
    }

    @Test void rejects_report_drift_and_redacts_the_original_lease_and_bearer_from_errors() {
        String completed = response(PATH + "complete", "post", 200).body();
        for (String invalid : List.of(completed.replace("job-101", "job-other"), completed.replace("COMPLETED", "UNKNOWN"),
                completed.replace("\"outcome\":", "\"commandId\":\"1-report\",\"outcome\":"))) {
            fixture.handler = request -> json(invalid);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.complete(complete()));
            fixture.take();
        }
        for (String secret : List.of(complete().getLeaseToken(), "report-secret")) {
            fixture.handler = request -> new HttpProcessDraftClientTest.Reply(409, "application/json",
                    "{\"code\":\"TIANJI_PROCESS_VERSION_CONFLICT\",\"correlationId\":\"prefix-" + secret + "\"}");
            var failure = assertThrows(ProcessClientException.class, () -> client.complete(complete()));
            assertEquals(409, failure.httpStatus());
            assertEquals("TIANJI_PROCESS_VERSION_CONFLICT", failure.code());
            assertNull(failure.correlationId());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains(secret));
            fixture.take();
        }
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(409, "application/json",
                "{\"code\":\"TIANJI_TENANT_CONTEXT_CHANGED\",\"correlationId\":\"trace-worker\"}");
        assertCode("TIANJI_TENANT_CONTEXT_CHANGED", () -> client.complete(complete()));
        fixture.take();
        assertThrows(IllegalArgumentException.class, () -> client.complete(complete().setLeaseToken("bad")));
        assertThrows(IllegalArgumentException.class, () -> client.fail(failure().setKind(null)));
        assertNull(fixture.poll());
    }

    private static AutomationWorkerTaskDTO task(long tenant, String source) {
        try (var in = HttpAutomationWorkerClientTest.class.getResourceAsStream("/META-INF/tianji/openapi/automation-v2.json")) {
            var oas = JSON.readTree(in);
            var raw = oas.path("paths").path(PATH + "acquire").path("post").path("responses").path("200").path("content")
                    .path("application/json").path("examples").path("acquired").path("value").path("task");
            var task = JSON.treeToValue(raw, AutomationWorkerTaskDTO.class);
            return task.setTaskContractSha256(task.contractSha256(tenant, source));
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    private static String acquired(AutomationWorkerTaskDTO task) {
        return JSON.writeValueAsString(new AutomationWorkerAcquireDTO(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED, task));
    }

    private static AutomationWorkerCompletionCommand complete() {
        return request(PATH + "complete", "post", AutomationWorkerCompletionCommand.class).setCommandId("1-report");
    }

    private static AutomationWorkerFailureCommand failure() {
        return request(PATH + "fail", "post", AutomationWorkerFailureCommand.class).setCommandId("1-failure");
    }

    private static HttpProcessDraftClientTest.Reply json(String body) { return HttpProcessDraftClientTest.Reply.json(body); }

    private static void assertCode(String code, Runnable call) {
        assertEquals(code, assertThrows(ProcessClientException.class, call::run).code());
    }

    private static void assertReport(HttpProcessDraftClientTest.Seen request, String path, Class<?> type, Object body) {
        assertEquals("POST", request.method());
        assertEquals(path, request.path());
        assertEquals("Bearer report-secret", request.authorization());
        assertEquals("11", request.tenant());
        assertEquals("application/json", request.contentType());
        assertEquals(body, JSON.readValue(request.body(), type));
    }
}
