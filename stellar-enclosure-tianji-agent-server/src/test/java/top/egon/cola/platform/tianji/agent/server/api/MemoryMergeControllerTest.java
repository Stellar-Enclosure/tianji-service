package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemoryMergeControllerTest {
    @Test void comparison_is_a_private_strict_current_request_with_no_mutation_or_cache() throws Exception {
        var memory = mock(PostgresMemoryService.class); var access = mock(TianjiAgentAccess.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20"); var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var now = Instant.now(); var expiry = now.plusSeconds(20); when(access.require(TianjiAgentPermissions.MEMORY_MERGE_SUGGESTIONS_API)).thenReturn(new TianjiAgentAccess.Authorized(actor, expiry));
        var query = new MemoryMergeQuery(owner, "review", "candidate", 1, false, null, 5, null);
        var candidate = new MemoryEntryDTO(owner, "review", "candidate", MemoryKindEnum.FACT, 1, null, MemoryStateEnum.CANDIDATE, new MemoryContentDTO(null, Map.of("language", "zh-CN")), "a".repeat(64),
                new MemorySourceDTO("declared", "b".repeat(64), null, null), new BigDecimal("0.5"), false, MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "c".repeat(64), now, now.plusSeconds(60), now, now);
        when(memory.mergeSuggestions(any(), eq(query))).thenAnswer(call -> {
            var context = call.getArgument(0, MemoryOperationContext.class); assertEquals(actor, context.actor()); assertEquals(expiry, context.deadlineAt()); assertNull(context.runAuthorization());
            return new MemoryMergePageDTO(candidate, false, List.of(), "scanned");
        });
        var mvc = MockMvcBuilders.standaloneSetup(new MemoryMergeController(memory, access)).setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build();
        var encoded = JsonMapper.builder().build().writeValueAsString(query);
        mvc.perform(post("/api/tianji/v1/memories/merge-suggestions").contentType(MediaType.APPLICATION_JSON).content(encoded)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("candidate.state").value("CANDIDATE")).andExpect(jsonPath("nextAfterMemoryId").value("scanned"));
        for (var invalid : List.of(encoded.replace("\"expectedRevision\":1", "\"expectedRevision\":1.5"), encoded.replace("\"semantic\":false", "\"semantic\":\"false\""),
                encoded.replace("\"semantic\":false,", ""), encoded.replace("\"minimumScore\":null", "\"minimumScore\":\"0.5\""),
                encoded.substring(0, encoded.length() - 1) + ",\"confirm\":true}", encoded.substring(0, encoded.length() - 1) + ",\"tenantId\":21}"))
            mvc.perform(post("/api/tianji/v1/memories/merge-suggestions").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tianji/v1/memories/merge-suggestions").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(131_073))).andExpect(status().isPayloadTooLarge());
        verify(access, times(1)).require(TianjiAgentPermissions.MEMORY_MERGE_SUGGESTIONS_API); verify(memory, times(1)).mergeSuggestions(any(), any());
        verify(memory, never()).upsert(any(), any()); verify(memory, never()).correct(any(), any()); verify(memory, never()).forget(any(), any());
    }
}
