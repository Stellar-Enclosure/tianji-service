package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.server.integration.memory.MemoryPreferenceIntentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.CancelMemoryPreferenceIntentCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryPreferenceIntentQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentCancellationDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStatusDTO;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MemoryPreferenceIntentControllerTest {
    @Test void owned_status_and_cancellation_are_private_strict_bounded_and_preserve_the_current_deadline() throws Exception {
        var intents = mock(MemoryPreferenceIntentAccess.class); var access = mock(TianjiAgentAccess.class); var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        Instant now = Instant.now(); Instant expiry = now.plusSeconds(20); when(access.require(anyString())).thenReturn(new TianjiAgentAccess.Authorized(actor, expiry));
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var query = new GetMemoryPreferenceIntentQuery(owner, "research", "run");
        var command = new CancelMemoryPreferenceIntentCommand("cancel-1", owner, "research", "run", 3); var absent = new MemoryPreferenceIntentStatusDTO(owner, "research", "run", null);
        var cancelled = new MemoryPreferenceIntentStatusDTO(owner, "research", "run", new MemoryPreferenceIntentDTO("00000000-0000-0000-0000-000000000001", "source", "preferences@1#" + "a".repeat(64), MemoryPreferenceIntentStateEnum.CANCELLED, 4, 0, null, 1, "USER_CANCELLED", now, now, now));
        when(intents.status(any(), eq(query))).thenAnswer(call -> {
            var context = call.getArgument(0, top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext.class); assertEquals(actor, context.actor()); assertEquals(expiry, context.deadlineAt()); assertNull(context.preferenceWrite()); assertNull(context.runAuthorization()); return absent;
        });
        when(intents.cancel(any(), eq(command))).thenReturn(new MemoryPreferenceIntentCancellationDTO(command.commandId(), cancelled));
        var mvc = MockMvcBuilders.standaloneSetup(new MemoryPreferenceIntentController(intents, access)).setControllerAdvice(new AgentApiErrors(), new MemoryRequestBodyAdvice()).build(); var json = JsonMapper.builder().build();
        String encoded = json.writeValueAsString(query); String mutation = json.writeValueAsString(command);
        var statusResponse = mvc.perform(post("/api/tianji/v1/memories/preference-intents/status").contentType(MediaType.APPLICATION_JSON).content(encoded)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("runId").value("run")).andReturn();
        assertTrue(json.readTree(statusResponse.getResponse().getContentAsString()).path("intent").isNull());
        mvc.perform(post("/api/tianji/v1/memories/preference-intents/cancel").contentType(MediaType.APPLICATION_JSON).content(mutation)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("status.intent.state").value("CANCELLED"));
        for (var invalid : List.of(encoded.replace("\"run\"", "123"), encoded.substring(0, encoded.length() - 1) + ",\"tenantId\":21}", encoded.substring(0, encoded.length() - 1) + ",\"sourceEventId\":\"caller\"}"))
            mvc.perform(post("/api/tianji/v1/memories/preference-intents/status").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        for (var invalid : List.of(mutation.replace("\"expectedRevision\":3", "\"expectedRevision\":\"3\""), mutation.replace(",\"expectedRevision\":3", ""), mutation.replace("\"expectedRevision\":3", "\"expectedRevision\":null"),
                mutation.substring(0, mutation.length() - 1) + ",\"requestSha256\":\"caller\"}", mutation.substring(0, mutation.length() - 1) + ",\"intentId\":\"caller\"}", mutation.substring(0, mutation.length() - 1) + ",\"commandId\":\"other\"}"))
            mvc.perform(post("/api/tianji/v1/memories/preference-intents/cancel").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tianji/v1/memories/preference-intents/cancel").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(131_073))).andExpect(status().isPayloadTooLarge());
        verify(intents, times(1)).status(any(), any()); verify(intents, times(1)).cancel(any(), any()); verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API); verify(access).require(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API);
    }
}
