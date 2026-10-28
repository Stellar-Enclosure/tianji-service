package top.egon.cola.platform.tianji.agent.server.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRunGrantSubmissionTest {
    @Test void enabled_submission_calls_the_issuer_after_the_scoped_ledger_start() {
        var ledger = mock(AgentLedgerService.class); var access = mock(TianjiAgentAccess.class);
        var issuer = mock(TianjiExecutionGrantIssuer.class);
        @SuppressWarnings("unchecked") ObjectProvider<TianjiExecutionGrantIssuer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(issuer);
        var environment = new MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true");
        var controller = new AgentRunController(ledger, access, provider, environment);
        var actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/tenants/21");
        var authorized = new TianjiAgentAccess.Authorized(actor, Instant.now().plusSeconds(120), URI.create("https://idp.example/tenants/21"));
        var run = new AgentRunDTO("00000000-0000-0000-0000-000000000123", "review", 1, null, AgentRunStatusEnum.QUEUED,
                null, false, 0, 0, 1, Instant.now().plusSeconds(60), Instant.now(), null);
        var command = new StartAgentRunCommand("review", 1, null, "request", null, null,
                new AgentInputDTO("input", List.of(), Map.of()), null);
        when(access.require(TianjiAgentPermissions.START_API)).thenReturn(authorized);
        when(ledger.start(actor, command)).thenReturn(run); when(issuer.ensure(authorized, run)).thenReturn(run);
        assertEquals(run, controller.start(command).getBody());
        var order = inOrder(access, ledger, issuer);
        order.verify(access).require(TianjiAgentPermissions.START_API); order.verify(ledger).start(actor, command);
        order.verify(issuer).ensure(authorized, run);
    }

    @Test void enabled_without_issuer_fails_startup_and_disabled_ignores_it() {
        var ledger = mock(AgentLedgerService.class); var access = mock(TianjiAgentAccess.class);
        @SuppressWarnings("unchecked") ObjectProvider<TianjiExecutionGrantIssuer> provider = mock(ObjectProvider.class);
        var enabled = new MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true");
        assertThrows(IllegalStateException.class, () -> new AgentRunController(ledger, access, provider, enabled));
        var disabled = new MockEnvironment();
        assertNotNull(new AgentRunController(ledger, access, provider, disabled));
        verify(provider, times(1)).getIfAvailable();
    }
}
