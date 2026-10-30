package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiMemoryPreferencePermitBoundaryTest {
    @Test void deferred_candidate_permits_cannot_borrow_the_current_http_identity_or_permission_guard() {
        var now = Instant.parse("2026-10-31T00:00:00Z"); var access = mock(TianjiAgentAccess.class);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20"); var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var command = new UpsertMemoryCommand("write", owner, "research", "candidate", MemoryKindEnum.PREFERENCE, 0, new MemoryContentDTO(null, Map.of("language", "zh-CN")),
                new MemorySourceDTO("source", "a".repeat(64), "run", "event"), new BigDecimal("0.5"), false, MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "b".repeat(64), null, null);
        var permit = mock(MemoryPreferenceWritePermit.class); when(permit.actor()).thenReturn(actor); when(permit.command()).thenReturn(command); when(permit.expiresAt()).thenReturn(now.plusSeconds(10));
        var authority = new TianjiMemoryHttpAuthorization(access, new TianjiMemoryAccessProperties(List.of()), Clock.fixed(now, ZoneOffset.UTC));
        var context = new MemoryOperationContext(actor, now.plusSeconds(5), null, permit);
        assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, authority.authorize(new MemoryAuthorization.Request(context, owner, "research", MemoryAuthorization.Operation.UPSERT, "candidate", 0, "run", "event")));
        verifyNoInteractions(access); verify(permit, never()).verifyTransaction();
    }
}
