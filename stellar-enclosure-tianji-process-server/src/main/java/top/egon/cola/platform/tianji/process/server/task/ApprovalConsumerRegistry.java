package top.egon.cola.platform.tianji.process.server.task;

import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Operator-owned consumer/approval-policy affinity; no registration is accepted from a tool or verification request. */
public final class ApprovalConsumerRegistry {
    private final Map<Key, Registration> registrations;
    public ApprovalConsumerRegistry(List<Registration> values) {
        if (values == null || values.size() > 128) throw invalid(); var copy = new HashMap<Key, Registration>();
        for (var value : values) if (value == null || copy.putIfAbsent(new Key(value.consumer(), value.policyRef()), value) != null) throw invalid(); registrations = Map.copyOf(copy);
    }
    public Registration require(ProcessActor consumer, VerifyApprovalQuery query) {
        var binding = registrations.get(new Key(consumer, query.policyRef()));
        if (binding == null || !binding.fingerprint().equals(query.policyBindingSha256()) || !binding.requesterTypes().contains(query.requesterType())
                || !binding.releaseVersion().equals(query.sourceStart().releaseVersion()) || !binding.definitionKey().equals(query.sourceStart().definitionKey())
                || query.requesterType() == ApprovalRequesterTypeEnum.SERVICE && !consumer.subjectId().equals(query.requesterSubjectId())) throw new ProcessResourceNotFoundException();
        return binding;
    }
    public record Registration(ProcessActor consumer, String policyRef, String releaseVersion, String definitionKey, String definitionSha256,
                               String taskDefinitionKey, Set<String> eligibleRoles, Duration maximumAge, Set<ApprovalRequesterTypeEnum> requesterTypes) {
        public Registration {
            eligibleRoles = Set.copyOf(eligibleRoles); requesterTypes = Set.copyOf(requesterTypes);
            if (consumer == null || consumer.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(consumer.issuerFingerprint())
                    || policyRef == null || !policyRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || releaseVersion == null || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || taskDefinitionKey == null || !taskDefinitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || definitionSha256 == null || !definitionSha256.matches("[0-9a-f]{64}") || eligibleRoles.isEmpty() || eligibleRoles.size() > 128 || requesterTypes.isEmpty()
                    || maximumAge == null || maximumAge.getNano() != 0 || maximumAge.compareTo(Duration.ofSeconds(1)) < 0 || maximumAge.compareTo(Duration.ofDays(7)) > 0) throw invalid();
            for (var role : eligibleRoles) {
                try { if (!role.matches("[1-9][0-9]{0,18}") || Long.parseLong(role) < 1) throw invalid(); }
                catch (NumberFormatException malformed) { throw invalid(); }
            }
        }
        public String fingerprint() {
            var values = new ArrayList<>(List.of("TIANJI_APPROVAL_POLICY_V1", Long.toString(consumer.tenantId()), consumer.subjectType().name(), consumer.issuerFingerprint(), consumer.subjectId(),
                    policyRef, releaseVersion, definitionKey, definitionSha256, taskDefinitionKey, maximumAge.toString()));
            eligibleRoles.stream().sorted().map(value -> "ROLE:" + value).forEach(values::add);
            requesterTypes.stream().map(value -> "REQUESTER:" + value.name()).sorted().forEach(values::add);
            return OrchestrationRequestDigests.hash(values, Map.of());
        }
        @Override public String toString() { return "ApprovalConsumerRegistration[redacted]"; }
    }
    private record Key(ProcessActor actor, String policyRef) { }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Approval consumer binding is invalid"); }
}
