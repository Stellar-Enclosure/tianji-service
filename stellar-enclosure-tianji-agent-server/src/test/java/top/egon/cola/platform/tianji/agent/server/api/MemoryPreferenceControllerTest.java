package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemoryPreferenceControllerTest {
    @Test void private_request_is_strict_bounded_and_never_accepts_user_supplied_text_approval_or_identity() throws Exception {
        var admission = mock(MemoryPreferenceAdmission.class); var access = mock(TianjiAgentAccess.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        Instant expiry = Instant.now().plusSeconds(20); when(access.require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API)).thenReturn(new TianjiAgentAccess.Authorized(actor, expiry));
        var request = new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "run", "terminal", "source", "preferences@1#" + "a".repeat(64));
        when(admission.extract(any(), eq(request))).thenAnswer(call -> {
            var context = call.getArgument(0, top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext.class);
            assertEquals(actor, context.actor()); assertEquals(expiry, context.deadlineAt()); assertNull(context.runAuthorization());
            return new MemoryPreferenceExtractionResultDTO(request.owner(), request.scopeId(), request.runId(), request.sourceEventId(), request.bindingId(), request.policyRef(), MemoryPreferenceExtractionOutcomeEnum.NO_DECLARATION, List.of());
        });
        var mvc = MockMvcBuilders.standaloneSetup(new MemoryPreferenceController(admission, access)).setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build();
        String encoded = JsonMapper.builder().build().writeValueAsString(request);
        mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(encoded)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("outcome").value("NO_DECLARATION"));
        for (var invalid : List.of(encoded.replace("\"run\"", "123"), encoded.replace("\"sourceEventId\":\"terminal\",", ""),
                encoded.substring(0, encoded.length() - 1) + ",\"approved\":true}", encoded.substring(0, encoded.length() - 1) + ",\"text\":\"injected\"}",
                encoded.substring(0, encoded.length() - 1) + ",\"tenantId\":21}", encoded.substring(0, encoded.length() - 1) + ",\"commandId\":\"other\"}"))
            mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tianji/v1/memories/extract-preferences").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(131_073))).andExpect(status().isPayloadTooLarge());
        verify(admission, times(1)).extract(any(), any()); verify(access, times(1)).require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API);
    }
}
