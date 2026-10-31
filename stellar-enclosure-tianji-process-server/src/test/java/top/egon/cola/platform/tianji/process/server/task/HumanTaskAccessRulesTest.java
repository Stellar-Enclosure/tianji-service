package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HumanTaskAccessRulesTest {
    private static final String ISSUER = "https://idp.example/20";
    private static final ProcessActor REQUESTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", ISSUER);
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", ISSUER);

    @Test void candidate_read_and_claim_are_bound_to_current_roles_origin_and_operation() {
        var facts = facts(REQUESTER, null, false, false); var policy = policy(HumanTaskKind.APPROVAL);
        assertTrue(HumanTaskAccessRules.readable(context(REVIEWER, "get", Set.of("50")), facts, policy));
        assertFalse(HumanTaskAccessRules.readable(context(REVIEWER, "query", Set.of("60")), facts, policy));
        assertDoesNotThrow(() -> HumanTaskAccessRules.claim(context(REVIEWER, "claim", Set.of("50")), facts, policy));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.claim(context(REVIEWER, "claim", Set.of("60")), facts, policy));
        assertThrows(IllegalStateException.class, () -> HumanTaskAccessRules.claim(context(REVIEWER, "get", Set.of("50")), facts, policy));
        var foreign = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://other-idp.example/20");
        assertFalse(HumanTaskAccessRules.readable(context(foreign, "get", Set.of("50")), facts, policy));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.claim(context(foreign, "claim", Set.of("50")), facts, policy));
    }

    @Test void an_applicant_cannot_claim_or_complete_an_approval_even_with_candidate_roles_and_a_forged_native_assignment() {
        var policy = policy(HumanTaskKind.APPROVAL); var claim = context(REQUESTER, "claim", Set.of("50"));
        assertFalse(HumanTaskAccessRules.readable(context(REQUESTER, "get", Set.of("50")), facts(REQUESTER, null, false, false), policy));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.claim(claim, facts(REQUESTER, null, false, false), policy));
        var complete = context(REQUESTER, "complete", Set.of("50"));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.complete(complete, facts(REQUESTER, complete.userKey(), false, false), policy));
        assertDoesNotThrow(() -> HumanTaskAccessRules.complete(complete, facts(REQUESTER, complete.userKey(), false, false), policy(HumanTaskKind.ACTION)));
    }

    @Test void completion_needs_current_membership_and_self_assignment_but_a_user_can_release_its_own_orphaned_assignment() {
        var complete = context(REVIEWER, "complete", Set.of("50")); var own = facts(REQUESTER, complete.userKey(), false, false); var policy = policy(HumanTaskKind.APPROVAL);
        assertDoesNotThrow(() -> HumanTaskAccessRules.complete(complete, own, policy));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.complete(context(REVIEWER, "complete", Set.of()), own, policy));
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskAccessRules.complete(complete, facts(REQUESTER, "other", false, false), policy));
        assertTrue(HumanTaskAccessRules.readable(context(REVIEWER, "get", Set.of()), own, policy));
        assertDoesNotThrow(() -> HumanTaskAccessRules.unclaim(context(REVIEWER, "unclaim", Set.of()), own));
        assertThrows(ProcessConflictException.class, () -> HumanTaskAccessRules.claim(context(REVIEWER, "claim", Set.of("50")), own, policy));
    }

    @Test void administrator_recovery_uses_a_separate_resource_and_only_release_approved_lanes() {
        var facts = facts(REQUESTER, "other", false, false); var policy = policy(HumanTaskKind.APPROVAL);
        assertFalse(HumanTaskAccessRules.readable(context(REVIEWER, "query", Set.of()), facts, policy));
        assertTrue(HumanTaskAccessRules.readable(context(REVIEWER, "adminQuery", Set.of()), facts, policy));
        assertDoesNotThrow(() -> HumanTaskAccessRules.transfer(context(REVIEWER, "transfer", Set.of()), facts, policy, "manager"));
        assertThrows(IllegalArgumentException.class, () -> HumanTaskAccessRules.transfer(context(REVIEWER, "transfer", Set.of()), facts, policy, "arbitrary-user"));
        assertThrows(IllegalStateException.class, () -> HumanTaskAccessRules.transfer(context(REVIEWER, "complete", Set.of("50")), facts, policy, "manager"));
    }

    @Test void suspended_or_delegated_native_state_cannot_be_silently_resumed_or_resolved_by_task_operations() {
        var context = context(REVIEWER, "complete", Set.of("50")); var policy = policy(HumanTaskKind.APPROVAL);
        assertThrows(ProcessConflictException.class, () -> HumanTaskAccessRules.complete(context, facts(REQUESTER, context.userKey(), true, false), policy));
        assertThrows(ProcessConflictException.class, () -> HumanTaskAccessRules.complete(context, facts(REQUESTER, context.userKey(), false, true), policy));
        assertThrows(ProcessConflictException.class, () -> HumanTaskAccessRules.transfer(context(REVIEWER, "transfer", Set.of()), facts(REQUESTER, null, true, false), policy, "manager"));
    }

    private static HumanTaskContext context(ProcessActor actor, String operation, Set<String> roles) { return new HumanTaskContext(actor, "tianji.api.process.task." + operation, roles, Instant.parse("2026-10-31T00:00:30Z"), 2, 3); }
    private static HumanTaskAccessRules.Facts facts(ProcessActor actor, String assignee, boolean suspended, boolean delegated) { return new HumanTaskAccessRules.Facts(actor, "finance", assignee, suspended, delegated); }
    private static HumanTask policy(HumanTaskKind kind) { return new HumanTask(kind, Map.of("finance", Set.of("50"), "manager", Set.of("60")), "finance", null, kind == HumanTaskKind.APPROVAL ? "approved" : null,
            List.of(new ValidatedProcessModel.HumanTaskField("approved", "approved", ValidatedProcessModel.ValueType.BOOLEAN))); }
}
