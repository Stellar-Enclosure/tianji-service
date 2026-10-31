package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiApprovalVerificationAccess;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApprovalVerificationControllerTest {
    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private final ApprovalVerificationService approvals = mock(ApprovalVerificationService.class);
    private final TianjiApprovalVerificationAccess access = mock(TianjiApprovalVerificationAccess.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final VerifyApprovalQuery query = new VerifyApprovalQuery("approval.v1", SHA, "approval-ref", ApprovalRequesterTypeEnum.USER, "requester",
            new StartProcessCommand("approval", "release", "business", "start", "correlation", Map.of("inputSha256", SHA, "objectVersion", "7")));
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApprovalVerificationController(approvals, access))
            .setControllerAdvice(new ProcessApiErrors(), new HumanTaskRequestBodyAdvice()).build();

    @Test void a_registered_service_operation_projects_an_explicit_false_decision_without_caching() throws Exception {
        var context = new ApprovalVerificationContext(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "consumer", "https://idp.example/20"), NOW.plusSeconds(20));
        var proof = new VerifiedApprovalDTO(query.policyRef(), SHA, query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), "start", "instance", "task", "release", "approval", SHA,
                "review", OrchestrationRequestDigests.start(query.sourceStart()), false, NOW.minusSeconds(1), NOW, NOW.plusSeconds(20));
        when(access.require()).thenReturn(context); when(approvals.verify(context, query)).thenReturn(proof);
        mvc.perform(post(ApprovalClient.PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(query)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.approved").value(false)).andExpect(jsonPath("$.requestSha256").value(query.fingerprint()));
        verify(approvals).verify(context, query);
        var resource = ApprovalVerificationController.class.getMethod("verify", VerifyApprovalQuery.class).getAnnotation(PermissionResource.class);
        assertArrayEquals(new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.SERVICE}, resource.allowedPrincipals()); assertArrayEquals(new String[]{ApprovalClient.VERIFY_SCOPE}, resource.oauthScopes());
    }

    @Test void provenance_filters_and_nested_start_inputs_have_closed_explicit_types_before_dispatch() throws Exception {
        String valid = json.writeValueAsString(query);
        for (var invalid : List.of(valid.replace("\"requesterSubjectId\":\"requester\"", "\"requesterSubjectId\":42"), valid.replace("\"requesterType\":\"USER\"", "\"requesterType\":0"),
                valid.replace("\"businessKey\":\"business\"", "\"businessKey\":42"), valid.substring(0, valid.length() - 1) + ",\"tenantId\":21}",
                valid.substring(0, valid.length() - 1) + ",\"approved\":true}", valid + " {}", valid.replace("\"sourceStart\":", "\"sourceStart\":null,\"duplicateSource\":"))) {
            mvc.perform(post(ApprovalClient.PATH).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(ApprovalClient.PATH).contentType(MediaType.APPLICATION_JSON).content(new byte[HumanTaskRequestBodyAdvice.MAX_BYTES + 1])).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(access, approvals);
    }

    @Test void current_guard_refusal_never_reaches_the_store_and_hides_dependency_details() throws Exception {
        when(access.require()).thenThrow(new AccessDeniedException("private upstream detail"));
        var response = mvc.perform(post(ApprovalClient.PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(query))).andExpect(status().isForbidden()).andReturn().getResponse();
        assertEquals("no-store", response.getHeader("Cache-Control")); assertFalse(response.getContentAsString().contains("private upstream detail")); verifyNoInteractions(approvals);
    }
}
