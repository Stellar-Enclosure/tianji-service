package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiCaseAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MVC adapters and packaged consumer contract, without a listener, IAM connection or native engine. */
class CaseControllerTest {
    private static final String PATH = "/api/tianji/v1/cases", HASH = "a".repeat(64), AFTER = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00.123456789Z");
    private final JsonMapper json = JsonMapper.builder().build();
    private final FlowableCaseService cases = mock(FlowableCaseService.class);
    private final TianjiCaseAccess access = mock(TianjiCaseAccess.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new CaseController(cases, access))
            .setControllerAdvice(new CaseRequestBodyAdvice(), new ProcessApiErrors()).build();

    @Test void three_operations_dispatch_the_exact_current_context_and_return_the_frozen_noncacheable_shapes() throws Exception {
        when(access.require(anyString())).thenAnswer(call -> context(call.getArgument(0)));
        when(cases.start(any(), any())).thenReturn(instance()); when(cases.get(any(), any())).thenReturn(instance()); when(cases.actOnPlanItem(any(), any())).thenReturn(result());
        var start = start(); var get = new CaseInstanceQuery("case-one"); var act = act();
        String response = mvc.perform(post(PATH + "/start").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(start)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.caseInstanceId").value("case-one")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.submittedAt").value(NOW.toString())).andReturn().getResponse().getContentAsString();
        var body = json.readTree(response); assertEquals(9, body.size()); assertTrue(body.has("finishedAt")); assertTrue(body.get("finishedAt").isNull()); assertEquals(6, body.get("planItems").get(0).size());
        mvc.perform(post(PATH + "/get").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(get)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var operation = mvc.perform(post(PATH + "/act-plan-item").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(act)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.previousVersion").value(HASH)).andExpect(jsonPath("$.resultingVersion").value(AFTER)).andReturn().getResponse().getContentAsString();
        assertEquals(12, json.readTree(operation).size());
        verify(cases).start(context("tianji.api.process.case.start"), start); verify(cases).get(context("tianji.api.process.case.get"), get);
        verify(cases).actOnPlanItem(context("tianji.api.process.case.actPlanItem"), act);
    }

    @Test void unknown_identity_fields_duplicates_trailing_tokens_and_scalar_coercion_fail_before_authorization() throws Exception {
        String valid = json.writeValueAsString(start());
        for (var invalid : List.of(valid.substring(0, valid.length()-1) + ",\"tenantId\":20}", valid.substring(0, valid.length()-1) + ",\"actor\":\"other\"}",
                valid.substring(0, valid.length()-1) + ",\"roles\":[\"7\"]}", valid.substring(0, valid.length()-1) + ",\"businessKey\":\"business\"}",
                valid.replace("\"businessKey\":\"business\"", "\"businessKey\":42"), valid.replace("\"definitionKey\":\"governedCase\",", ""), valid + " {}", "[]")) {
            mvc.perform(post(PATH + "/start").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH + "/act-plan-item").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(act()).replace("\"action\":\"START\"", "\"action\":0"))).andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/get").contentType(MediaType.APPLICATION_JSON).content("{\"caseInstanceId\":null}")).andExpect(status().isBadRequest());
        verifyNoInteractions(access, cases);
    }

    @Test void fixed_and_streamed_requests_have_the_same_64kib_limit() throws Exception {
        byte[] oversized = new byte[64 * 1024 + 1];
        mvc.perform(post(PATH + "/start").contentType(MediaType.APPLICATION_JSON).content(oversized)).andExpect(status().isPayloadTooLarge());
        HttpInputMessage streamed = new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(oversized); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        assertThrows(CaseRequestBodyAdvice.TooLarge.class, () -> new CaseRequestBodyAdvice().beforeBodyRead(streamed, null, StartCaseCommand.class, null));
        verifyNoInteractions(access, cases);
    }

    @Test void all_nine_application_error_pairs_are_closed_noncacheable_and_private_cause_free() throws Exception {
        when(access.require(anyString())).thenAnswer(call -> context(call.getArgument(0)));
        var errors = List.of(new Error(new IllegalArgumentException("private"),400,"TIANJI_INVALID_COMMAND"),
                new Error(new AuthenticationCredentialsNotFoundException("private"),401,"TIANJI_UNAUTHENTICATED"),
                new Error(new AccessDeniedException("private"),403,"TIANJI_PERMISSION_DENIED"),
                new Error(new ProcessResourceNotFoundException(),404,"TIANJI_RESOURCE_NOT_FOUND"),
                new Error(new ProcessConflictException(),409,"TIANJI_IDEMPOTENCY_CONFLICT"),
                new Error(new ProcessTenantChangedException(),409,"TIANJI_TENANT_CONTEXT_CHANGED"),
                new Error(new CaseRequestBodyAdvice.TooLarge(),413,"TIANJI_REQUEST_TOO_LARGE"),
                new Error(new RejectedExecutionException("private"),429,"TIANJI_CAPACITY_EXCEEDED"),
                new Error(new RuntimeException("private",new RuntimeException("credential")),503,"TIANJI_DEPENDENCY_UNAVAILABLE"));
        for (var error : errors) {
            doThrow(error.failure()).when(cases).start(any(),any());
            var response = mvc.perform(post(PATH + "/start").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(start())))
                    .andExpect(status().is(error.status())).andExpect(jsonPath("$.code").value(error.code())).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            var body = json.readTree(response.getContentAsString()); assertTrue(body.get("correlationId").isNull()); assertEquals(2,body.size());
            assertFalse(response.getContentAsString().contains("private")); assertFalse(response.getContentAsString().contains("credential")); assertNull(response.getHeader("Retry-After"));
        }
    }

    @Test void access_denial_prevents_service_dispatch_and_contract_declares_only_the_expected_principals() throws Exception {
        when(access.require(anyString())).thenThrow(new AccessDeniedException("private"));
        mvc.perform(post(PATH + "/get").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new CaseInstanceQuery("case-one"))))
                .andExpect(status().isForbidden()); verifyNoInteractions(cases);
        for (var spec : List.of(Map.entry("start", StartCaseCommand.class), Map.entry("get", CaseInstanceQuery.class), Map.entry("actOnPlanItem", ActOnPlanItemCommand.class))) {
            var method=CaseController.class.getMethod(spec.getKey(),spec.getValue()); var resource=method.getAnnotation(PermissionResource.class); assertNotNull(resource);
            assertArrayEquals(spec.getKey().equals("actOnPlanItem") ? new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER}
                    : new PermissionManifest.PrincipalType[]{PermissionManifest.PrincipalType.USER,PermissionManifest.PrincipalType.SERVICE},resource.allowedPrincipals());
            var suffix=spec.getKey().equals("actOnPlanItem")?"actPlanItem":spec.getKey();
            assertEquals("tianji.api.process.case."+suffix,resource.key());
            assertArrayEquals(new String[]{"tianji:case:"+(suffix.equals("get")?"read":suffix.equals("actPlanItem")?"act":"start")},resource.oauthScopes());
            assertArrayEquals(new String[]{suffix.equals("get")?"TIANJI_CASE_READ":suffix.equals("actPlanItem")?"TIANJI_CASE_PLAN_ITEM_ACT":"TIANJI_CASE_START"},method.getAnnotation(RequiresPermissions.class).codes());
        }
    }

    @Test void provider_operations_and_exact_shapes_are_the_packaged_contract_authority() throws Exception {
        try(var input=CaseControllerTest.class.getResourceAsStream("/META-INF/tianji/openapi/case-v1.json")) {
            assertNotNull(input); var oas=json.readTree(input); assertEquals("3.1.0",oas.path("openapi").asString()); assertEquals(3,oas.path("paths").size());
            for(var route:List.of("start","get","act-plan-item")) assertTrue(oas.path("paths").path(PATH+'/'+route).has("post"));
            assertEquals(11,oas.path("components").path("schemas").size());
            assertEquals(Set.of("commandId","caseInstanceId","planItemInstanceId","expectedVersion","action"),
                    java.util.Arrays.stream(ActOnPlanItemCommand.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
        }
    }
    private record Error(RuntimeException failure,int status,String code) {}
    private static CaseContext context(String resource) { return new CaseContext(ProcessActor.verified(20,ProcessActor.SubjectType.USER,"operator","https://idp.example/20"),resource,Set.of("7"),NOW.plusSeconds(30),2,3); }
    private static StartCaseCommand start() { return new StartCaseCommand("governedCase","release","business","start","correlation"); }
    private static ActOnPlanItemCommand act() { return new ActOnPlanItemCommand("act","case-one","item-one",HASH,CasePlanItemActionEnum.START); }
    private static CaseInstanceDTO instance() { return new CaseInstanceDTO("case-one","governedCase","release","business","correlation",CaseInstanceStatusEnum.ACTIVE,NOW,null,
            List.of(new CasePlanItemDTO("item-one","primaryTask",CasePlanItemKindEnum.HUMAN_TASK,CasePlanItemStateEnum.ENABLED,HASH,List.of(CasePlanItemActionEnum.START)))); }
    private static CaseOperationResult result() { return new CaseOperationResult("act","case-one","item-one","primaryTask",CasePlanItemActionEnum.START,"release","governedCase",CasePlanItemStateEnum.ENABLED,HASH,CasePlanItemStateEnum.ACTIVE,AFTER,NOW); }
}
