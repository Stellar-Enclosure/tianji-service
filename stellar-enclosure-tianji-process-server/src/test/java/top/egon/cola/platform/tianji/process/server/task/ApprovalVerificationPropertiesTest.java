package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalVerificationPropertiesTest {
    @Test void administrative_bindings_are_copied_and_default_to_user_requesters_with_a_bounded_age() {
        var roles = new HashSet<>(Set.of("50")); var consumer = consumer("https://idp.example/20", roles, null);
        roles.clear(); var values = new ArrayList<>(List.of(consumer)); var properties = new ApprovalVerificationProperties(values); values.clear();
        var binding = properties.registrations().getFirst(); assertEquals(Set.of("50"), binding.eligibleRoles()); assertEquals(Set.of(ApprovalRequesterTypeEnum.USER), binding.requesterTypes()); assertEquals(Duration.ofHours(1), binding.maximumAge());
        assertThrows(UnsupportedOperationException.class, () -> properties.consumers().clear()); assertEquals(List.of(), new ApprovalVerificationProperties(null).registrations());
    }
    @Test void unsafe_issuer_invalid_roles_or_age_cannot_become_registered_policy() {
        assertThrows(IllegalArgumentException.class, () -> consumer("http://idp.example/20", Set.of("50"), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> consumer("https://idp.example/20", Set.of("0"), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> consumer("https://idp.example/20", Set.of("50"), Duration.ofDays(8)));
    }
    private static ApprovalVerificationProperties.Consumer consumer(String issuer, Set<String> roles, Duration age) { return new ApprovalVerificationProperties.Consumer(20, issuer, "consumer", "approval.v1", "release", "approval", "a".repeat(64), "review", roles, age, null); }
}
