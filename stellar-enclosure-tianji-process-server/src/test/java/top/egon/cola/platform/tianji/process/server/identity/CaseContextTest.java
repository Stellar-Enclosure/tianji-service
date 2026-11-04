package top.egon.cola.platform.tianji.process.server.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CaseContextTest {
    private static final ProcessActor USER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://idp.example/20");
    private static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "case-worker", "https://idp.example/20");
    private static final Instant DEADLINE = Instant.parse("2026-11-04T08:00:30Z");

    @Test void current_user_roles_are_copied_and_summaries_do_not_expose_identity() {
        var roles = new HashSet<>(Set.of("7", "8"));
        var context = new CaseContext(USER, "tianji.api.process.case.get", roles, DEADLINE, 2, 3);
        roles.clear();
        assertEquals(Set.of("7", "8"), context.roleIds());
        assertThrows(UnsupportedOperationException.class, () -> context.roleIds().add("9"));
        assertEquals("CaseContext[redacted]", context.toString());
        assertDoesNotThrow(() -> context.requireResource("tianji.api.process.case.get"));
        assertThrows(IllegalStateException.class, () -> context.requireResource("tianji.api.process.case.start"));
    }

    @Test void service_context_is_owner_only_and_cannot_carry_user_roles_or_plan_action_authority() {
        assertDoesNotThrow(() -> new CaseContext(SERVICE, "tianji.api.process.case.start", Set.of(), DEADLINE, 0, 0));
        assertDoesNotThrow(() -> new CaseContext(SERVICE, "tianji.api.process.case.get", Set.of(), DEADLINE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(SERVICE, "tianji.api.process.case.get", Set.of("7"), DEADLINE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(SERVICE, "tianji.api.process.case.actPlanItem", Set.of(), DEADLINE, 0, 0));
    }

    @Test void malformed_roles_legacy_identity_foreign_resources_and_negative_versions_are_rejected() {
        for (String role : new String[]{"0", "-1", "01", "7 ", "9223372036854775808", "x"})
            assertThrows(IllegalArgumentException.class, () -> new CaseContext(USER, "tianji.api.process.case.get", Set.of(role), DEADLINE, 0, 0));
        var oversized = new HashSet<String>(); for (int i = 1; i <= 257; i++) oversized.add(Integer.toString(i));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(USER, "tianji.api.process.case.get", oversized, DEADLINE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(new ProcessActor(20, ProcessActor.SubjectType.USER, "old"), "tianji.api.process.case.get", Set.of(), DEADLINE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(USER, "tianji.api.process.task.get", Set.of(), DEADLINE, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(USER, "tianji.api.process.case.get", Set.of(), DEADLINE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CaseContext(USER, "tianji.api.process.case.get", Set.of(), DEADLINE, 0, -1));
        assertThrows(RuntimeException.class, () -> new CaseContext(null, "tianji.api.process.case.get", Set.of(), DEADLINE, 0, 0));
        assertThrows(RuntimeException.class, () -> new CaseContext(USER, "tianji.api.process.case.get", Set.of(), null, 0, 0));
    }
}
