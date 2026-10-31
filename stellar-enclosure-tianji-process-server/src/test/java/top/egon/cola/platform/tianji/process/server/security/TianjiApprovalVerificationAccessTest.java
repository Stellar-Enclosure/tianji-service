package top.egon.cola.platform.tianji.process.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiApprovalVerificationAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "consumer", "https://idp.example/20");
    private final TianjiProcessAccess base = mock(TianjiProcessAccess.class);
    private final TianjiApprovalVerificationAccess access = new TianjiApprovalVerificationAccess(base, Clock.fixed(NOW, ZoneOffset.UTC));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void the_exact_current_service_guard_and_token_expiry_bound_the_read_context() {
        when(base.require(TianjiProcessPermissions.APPROVAL_VERIFY_API)).thenReturn(ACTOR); token(NOW.plusSeconds(20));
        var context = access.require(); assertEquals(ACTOR, context.actor()); assertEquals(NOW.plusSeconds(20), context.deadlineAt()); assertFalse(context.toString().contains("consumer"));
        token(NOW.plusSeconds(60)); assertEquals(NOW.plusSeconds(30), access.require().deadlineAt());
        verify(base, times(2)).require(TianjiProcessPermissions.APPROVAL_VERIFY_API);
    }

    @Test void user_missing_changed_or_expired_service_facts_cannot_supply_an_approval_consumer_context() {
        when(base.require(TianjiProcessPermissions.APPROVAL_VERIFY_API)).thenReturn(ACTOR); assertThrows(AccessDeniedException.class, access::require);
        token(NOW); assertThrows(AccessDeniedException.class, access::require);
        token(NOW.plusSeconds(20)); when(base.require(TianjiProcessPermissions.APPROVAL_VERIFY_API)).thenReturn(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "other", "https://idp.example/20")); assertThrows(AccessDeniedException.class, access::require);
        when(base.require(TianjiProcessPermissions.APPROVAL_VERIFY_API)).thenReturn(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "consumer", "https://idp.example/20")); assertThrows(AccessDeniedException.class, access::require);
    }

    @Test void current_authorization_never_runs_inside_an_ambient_transaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, access::require); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(base);
    }
    private static void token(Instant expires) {
        var principal = new ServiceIdentityPrincipal("consumer", "20", "consumer", "token", URI.create("https://process.example"), 1, Set.of(ApprovalClient.VERIFY_SCOPE), "xingyuan", "tianji", "test", "credential", NOW.minusSeconds(1), expires);
        var token = mock(IdpAuthenticationToken.class); when(token.isAuthenticated()).thenReturn(true); when(token.getPrincipal()).thenReturn(principal); when(token.verifiedIssuer()).thenReturn(URI.create("https://idp.example/20"));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
