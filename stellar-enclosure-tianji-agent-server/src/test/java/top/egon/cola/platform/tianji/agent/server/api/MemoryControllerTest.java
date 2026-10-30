package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.VerifiedAgentIssuerFilter;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidateQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidatePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemoryControllerTest {
    private static final Instant NOW = Instant.now();
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.APPLICATION, "worker");
    private static final MemoryContentDTO CONTENT = new MemoryContentDTO("private fact", Map.of("format", "short"));
    private static final MemorySourceDTO SOURCE = new MemorySourceDTO("explicit", "a".repeat(64), null, null);
    private final PostgresMemoryService memory = mock(PostgresMemoryService.class);
    private final PermissionExecutionGuard permissions = mock(PermissionExecutionGuard.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach void setup() {
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token", URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.MEMORY_READ_SCOPE, TianjiAgentPermissions.MEMORY_WRITE_SCOPE, TianjiAgentPermissions.MEMORY_FORGET_SCOPE),
                "xingyuan", "worker", "test", "credential", NOW.minusSeconds(30), NOW.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), URI.create("https://idp.example/20")));
        var controller = new MemoryController(memory, new TianjiAgentAccess(permissions, Clock.systemUTC()));
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new VerifiedAgentIssuerFilter()).setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void all_operations_preserve_commands_and_current_actor_with_bounded_non_cacheable_results() throws Exception {
        var get = new GetMemoryQuery(OWNER, "research", "memory"); var query = new MemoryRetrievalQuery(OWNER, "research", null, "preference", null, 5);
        var upsert = upsert(); var correct = new CorrectMemoryCommand("correct", OWNER, "research", "memory", 1, CONTENT, SOURCE, BigDecimal.ONE, "USER_CONFIRMED");
        var forget = new ForgetMemoryCommand("forget", OWNER, "research", "memory", 2, "USER_FORGET");
        var entry = new MemoryEntryDTO(OWNER, "research", "memory", MemoryKindEnum.PREFERENCE, 1, null, MemoryStateEnum.ACTIVE, CONTENT, "b".repeat(64), SOURCE,
                BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "c".repeat(64), NOW, NOW.plusSeconds(60), NOW, NOW);
        when(memory.get(any(), eq(get))).thenReturn(entry); when(memory.retrieve(any(), eq(query))).thenReturn(new MemoryRetrievalPageDTO(OWNER, "research", List.of(), null));
        when(memory.upsert(any(), eq(upsert))).thenReturn(receipt("create", 1, MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.ACTIVE));
        when(memory.correct(any(), eq(correct))).thenReturn(receipt("correct", 2, MemoryMutationOutcomeEnum.CORRECTED, MemoryStateEnum.ACTIVE));
        when(memory.forget(any(), eq(forget))).thenReturn(receipt("forget", 3, MemoryMutationOutcomeEnum.FORGOTTEN, MemoryStateEnum.FORGOTTEN));
        var requests = Map.of("get", get, "retrieve", query, "upsert", upsert, "correct", correct, "forget", forget);
        for (var request : requests.entrySet()) mvc.perform(post("/api/tianji/v1/memories/" + request.getKey()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request.getValue())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verify(memory).get(argThat(context -> context.actor().equals(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20"))
                && context.runAuthorization() == null && context.deadlineAt().isAfter(NOW) && !context.deadlineAt().isAfter(Instant.now().plusSeconds(30))), eq(get));
        for (var resource : List.of(TianjiAgentPermissions.MEMORY_GET_API, TianjiAgentPermissions.MEMORY_RETRIEVE_API, TianjiAgentPermissions.MEMORY_UPSERT_API,
                TianjiAgentPermissions.MEMORY_CORRECT_API, TianjiAgentPermissions.MEMORY_FORGET_API)) verify(permissions).requireExecution(resource);
    }

    @Test void candidate_review_requires_its_own_current_read_resource_and_strict_query_shape() throws Exception {
        var query = new MemoryCandidateQuery(OWNER, "research", null, null, 5); when(memory.candidates(any(), eq(query))).thenReturn(new MemoryCandidatePageDTO(OWNER, "research", List.of(), null));
        mvc.perform(post("/api/tianji/v1/memories/candidates").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(query))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(permissions).requireExecution(TianjiAgentPermissions.MEMORY_CANDIDATES_API);
        var encoded = json.writeValueAsString(query);
        for (var invalid : List.of(encoded.replace("\"limit\":5", "\"limit\":\"5\""), encoded.replace("\"limit\":5", "\"limit\":null"), encoded.substring(0, encoded.length() - 1) + ",\"text\":\"query\"}"))
            mvc.perform(post("/api/tianji/v1/memories/candidates").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        verify(memory, times(1)).candidates(any(), any());
    }

    @Test void malformed_or_ambiguous_intentions_never_reach_storage() throws Exception {
        String body = json.writeValueAsString(upsert());
        int caseNumber = 0;
        for (var invalid : List.of(body.replace("\"expectedRevision\":0,", ""), body.replace("\"confirmed\":true,", ""), body.replace("\"confirmed\":true", "\"confirmed\":\"true\""),
                body.replace("\"expectedRevision\":0", "\"expectedRevision\":0.5"), body.replace("\"kind\":\"PREFERENCE\"", "\"kind\":0"), body.replace("\"confidence\":1", "\"confidence\":\"1\""),
                body.replace("\"format\":\"short\"", "\"format\":123"), body.replace("\"effectiveAt\":null", "\"effectiveAt\":123"), body.substring(0, body.length() - 1) + ",\"tenantId\":21}",
                body.substring(0, body.length() - 1) + ",\"confirmed\":false}", body + " {}")) {
            var response = mvc.perform(post("/api/tianji/v1/memories/upsert").contentType(MediaType.APPLICATION_JSON).content(invalid)).andReturn().getResponse();
            assertEquals(400, response.getStatus(), "Invalid request case " + caseNumber++); assertEquals("no-store", response.getHeader("Cache-Control"));
        }
        mvc.perform(post("/api/tianji/v1/memories/get").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(MemoryRequestBodyAdvice.MAX_BYTES + 1)))
                .andExpect(status().isPayloadTooLarge()); verifyNoInteractions(memory);
    }

    @Test void denied_access_stops_storage_and_dependency_failures_disclose_only_the_error_code() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(permissions).requireExecution(TianjiAgentPermissions.MEMORY_GET_API);
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(post("/api/tianji/v1/memories/get").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new GetMemoryQuery(OWNER, "research", "memory"))))); verifyNoInteractions(memory);
        reset(permissions); when(memory.get(any(), any())).thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private fact/authority detail"));
        var result = mvc.perform(post("/api/tianji/v1/memories/get").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new GetMemoryQuery(OWNER, "research", "memory"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private fact"));
    }

    @Test void http_controller_is_absent_unless_all_three_features_are_enabled() {
        new ApplicationContextRunner().withUserConfiguration(MemoryController.class).withPropertyValues("stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.memory.enabled=true")
                .run(context -> assertTrue(context.getBeansOfType(MemoryController.class).isEmpty()));
    }
    private static UpsertMemoryCommand upsert() { return new UpsertMemoryCommand("create", OWNER, "research", "memory", MemoryKindEnum.PREFERENCE, 0, CONTENT, SOURCE, BigDecimal.ONE, true,
            MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "c".repeat(64), null, null); }
    private static MemoryMutationResultDTO receipt(String command, long revision, MemoryMutationOutcomeEnum outcome, MemoryStateEnum state) { return new MemoryMutationResultDTO(command, OWNER, "research", "memory", revision, outcome, state, NOW); }
}
