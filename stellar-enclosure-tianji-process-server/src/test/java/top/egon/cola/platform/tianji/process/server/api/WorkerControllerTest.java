package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerAcquireStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkerControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final FlowableWorkerService workers = mock(FlowableWorkerService.class);
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessActor actor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", "https://idp.example/20");

    @Test
    void authorizes_each_service_operation_and_returns_noncacheable_receipts() throws Exception {
        when(access.require(TianjiProcessPermissions.WORKER_ACQUIRE_API)).thenReturn(actor);
        when(access.require(TianjiProcessPermissions.WORKER_COMPLETE_API)).thenReturn(actor);
        when(access.require(TianjiProcessPermissions.WORKER_FAIL_API)).thenReturn(actor);
        var acquire = new AcquireWorkerTaskCommand("riskCheck", "1");
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var complete = new CompleteWorkerTaskCommand("task", "occurrence", lease, "receipt", Map.of("approved", true));
        var fail = new FailWorkerTaskCommand("task", "occurrence", lease, WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN");
        var accepted = new WorkerReceiptDTO("task", "occurrence", WorkerReceiptOutcomeEnum.COMPLETED, Instant.parse("2026-01-01T00:00:00Z"));
        var manual = new WorkerReceiptDTO("task", "occurrence", WorkerReceiptOutcomeEnum.NEEDS_MANUAL, Instant.parse("2026-01-01T00:00:00Z"));
        when(workers.acquire(actor, acquire)).thenReturn(new AcquireWorkerTaskResult(WorkerAcquireStatusEnum.EMPTY, null));
        when(workers.complete(actor, complete)).thenReturn(accepted);
        when(workers.fail(actor, fail)).thenReturn(manual);
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(workers, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(acquire)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.status").value("EMPTY"));
        mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(complete)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("COMPLETED"));
        mvc.perform(post("/api/tianji/v1/worker-tasks/fail").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(fail)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("NEEDS_MANUAL"));
        verify(workers).acquire(actor, acquire);
        verify(workers).complete(actor, complete);
        verify(workers).fail(actor, fail);

        when(access.require(TianjiProcessPermissions.WORKER_ACQUIRE_API)).thenReturn(
                ProcessActor.verified(20, ProcessActor.SubjectType.USER, "user", "https://idp.example/20"));
        mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(acquire)))
                .andExpect(status().isForbidden());
        when(access.require(TianjiProcessPermissions.WORKER_ACQUIRE_API)).thenThrow(new AccessDeniedException("denied"));
        mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(acquire)))
                .andExpect(status().isForbidden());
        verify(workers, times(1)).acquire(actor, acquire);
        when(workers.complete(actor, complete)).thenThrow(new WorkerLeaseLostException());
        mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(complete)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_WORKER_LEASE_LOST"));
        doThrow(new IllegalStateException(lease)).when(workers).complete(actor, complete);
        String error = mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(complete)))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertFalse(error.contains(lease));
    }

    @Test
    void declared_business_rejection_uses_the_existing_noncacheable_receipt_contract() throws Exception {
        when(access.require(TianjiProcessPermissions.WORKER_FAIL_API)).thenReturn(actor);
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var command = new FailWorkerTaskCommand("task", "occurrence", lease,
                WorkerFailureKindEnum.BUSINESS_REJECTED, "CUSTOMER_INELIGIBLE");
        var decided = Instant.parse("2026-11-05T00:00:00.123456Z");
        var receipt = new WorkerReceiptDTO("task", "occurrence", WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, decided);
        when(workers.fail(actor, command)).thenReturn(receipt);
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(workers, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        String body = mvc.perform(post("/api/tianji/v1/worker-tasks/fail").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(command)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.taskId").value("task")).andExpect(jsonPath("$.activityOccurrenceId").value("occurrence"))
                .andExpect(jsonPath("$.outcome").value("BUSINESS_REJECTED"))
                .andExpect(jsonPath("$.decidedAt").value(decided.toString())).andReturn().getResponse().getContentAsString();
        assertEquals(4, mapper.readTree(body).size());
        assertFalse(body.contains(lease)); assertFalse(body.contains(command.errorCode()));
        var ordered = inOrder(access, workers);
        ordered.verify(access).require(TianjiProcessPermissions.WORKER_FAIL_API);
        ordered.verify(workers).fail(actor, command);
    }

    @Test
    void bounds_fixed_and_streamed_worker_bodies_before_deserialization() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(workers, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        byte[] oversized = new byte[ProcessRequestBodyAdvice.MAX_WORKER_BYTES + 1];
        mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(oversized); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(ProcessRequestBodyAdvice.TooLarge.class, () -> new ProcessRequestBodyAdvice().beforeBodyRead(
                streamed, null, AcquireWorkerTaskCommand.class, null));
        verifyNoInteractions(workers, access);
    }

    @Test
    void rejects_forged_context_unknown_fields_duplicates_and_trailing_json() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WorkerController(workers, access))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
        for (String body : new String[] {
                "{\"topic\":\"riskCheck\",\"schemaVersion\":\"1\",\"tenantId\":21}",
                "{\"topic\":\"riskCheck\",\"topic\":\"other\",\"schemaVersion\":\"1\"}",
                "{\"topic\":\"riskCheck\"}",
                "{\"topic\":\"riskCheck\",\"schemaVersion\":\"1\"} {}"
        }) {
            mvc.perform(post("/api/tianji/v1/worker-tasks/acquire").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        String lease = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String duplicateOutput = "{\"taskId\":\"task\",\"activityOccurrenceId\":\"occurrence\",\"leaseToken\":\"" + lease
                + "\",\"businessReceiptId\":\"receipt\",\"output\":{\"approved\":true,\"approved\":false}}";
        mvc.perform(post("/api/tianji/v1/worker-tasks/complete").contentType(MediaType.APPLICATION_JSON).content(duplicateOutput))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workers, access);
    }
}
