package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionItemDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemoryPreferenceControllerTest {
    @Test void retired_prepare_preserves_current_permission_and_cannot_prepare_work() throws Exception {
        var admission = mock(MemoryPreferenceAdmission.class);
        var access = mock(TianjiAgentAccess.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        var command = new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research",
                "run", "terminal", "source", "preferences@1#" + "a".repeat(64));
        var mvc = MockMvcBuilders.standaloneSetup(new MemoryPreferenceController(admission, access))
                .setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build();
        var request = post("/api/tianji/v1/memories/prepare-preferences").contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsBytes(command.sourceQuery()));
        when(access.require(TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API)).thenThrow(new AccessDeniedException("denied"));
        assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(request));
        verifyNoInteractions(admission);

        reset(access);
        when(access.require(TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API))
                .thenReturn(new TianjiAgentAccess.Authorized(actor, Instant.now().plusSeconds(20)));
        mvc.perform(request).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        verify(access).require(TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API);
        verifyNoInteractions(admission);
    }

    @Test void private_request_is_strict_bounded_and_never_accepts_user_supplied_text_approval_or_identity() throws Exception {
        var admission = mock(MemoryPreferenceAdmission.class); var access = mock(TianjiAgentAccess.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        Instant expiry = Instant.now().plusSeconds(20); when(access.require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API)).thenReturn(new TianjiAgentAccess.Authorized(actor, expiry));
        var request = new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "run", "terminal", "source", "preferences@1#" + "a".repeat(64));
        var receipt = new MemoryMutationResultDTO(request.itemCommandId("language"), request.owner(), request.scopeId(), "original", 1,
                MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE, expiry.minusSeconds(120));
        when(admission.replayCommittedExtraction(any(), eq(request))).thenAnswer(call -> {
            var context = call.getArgument(0, top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext.class);
            assertEquals(actor, context.actor()); assertEquals(expiry, context.deadlineAt()); assertNull(context.runAuthorization());
            return new MemoryPreferenceExtractionResultDTO(request.owner(), request.scopeId(), request.runId(), request.sourceEventId(),
                    request.bindingId(), request.policyRef(), MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE,
                    List.of(new MemoryPreferenceExtractionItemDTO("language", receipt)));
        });
        var mvc = MockMvcBuilders.standaloneSetup(new MemoryPreferenceController(admission, access)).setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build();
        String encoded = JsonMapper.builder().build().writeValueAsString(request);
        mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(encoded))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("outcome").value("ELIGIBLE"))
                .andExpect(jsonPath("items[0].receipt.memoryId").value("original"));
        for (var invalid : List.of(encoded.replace("\"run\"", "123"), encoded.replace("\"sourceEventId\":\"terminal\",", ""),
                encoded.substring(0, encoded.length() - 1) + ",\"approved\":true}", encoded.substring(0, encoded.length() - 1) + ",\"text\":\"injected\"}",
                encoded.substring(0, encoded.length() - 1) + ",\"tenantId\":21}", encoded.substring(0, encoded.length() - 1) + ",\"commandId\":\"other\"}"))
            mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(131_073))).andExpect(status().isPayloadTooLarge());
        verify(admission, times(1)).replayCommittedExtraction(any(), any()); verify(access, times(1)).require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "No committed extraction"))
                .when(admission).replayCommittedExtraction(any(), eq(request));
        mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(encoded))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        when(access.require(TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API)).thenReturn(new TianjiAgentAccess.Authorized(actor, expiry));
        String query = JsonMapper.builder().build().writeValueAsString(request.sourceQuery());
        for (var invalid : List.of(query.substring(0, query.length() - 1) + ",\"policyRef\":\"caller-policy\"}", query.replace("\"terminal\"", "123"), query.substring(0, query.length() - 1) + ",\"approved\":true}"))
            mvc.perform(post("/api/tianji/v1/memories/prepare-preferences").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        verify(admission, never()).extract(any(), any());
        verify(admission, never()).prepare(any(), any());
    }
}
