package top.egon.cola.platform.tianji.process.server.task;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Fixed administrative consumer bindings; a verification request cannot register its own policy or identity. */
@ConfigurationProperties(prefix = "stellar.tianji.process.approval-verification")
public record ApprovalVerificationProperties(List<Consumer> consumers) {
    public ApprovalVerificationProperties { consumers = consumers == null ? List.of() : List.copyOf(consumers); if (consumers.size() > 128) throw new IllegalArgumentException("Approval verification settings are invalid"); }
    public List<ApprovalConsumerRegistry.Registration> registrations() { return consumers.stream().map(Consumer::registration).toList(); }
    public record Consumer(long tenantId, String issuer, String subjectId, String policyRef, String releaseVersion, String definitionKey,
                           String definitionSha256, String taskDefinitionKey, Set<String> eligibleRoles, Duration maximumAge, Set<ApprovalRequesterTypeEnum> requesterTypes) {
        public Consumer {
            eligibleRoles = eligibleRoles == null ? Set.of() : Set.copyOf(eligibleRoles); requesterTypes = requesterTypes == null ? Set.of(ApprovalRequesterTypeEnum.USER) : Set.copyOf(requesterTypes);
            maximumAge = maximumAge == null ? Duration.ofHours(1) : maximumAge;
            new ApprovalConsumerRegistry.Registration(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer), policyRef, releaseVersion, definitionKey, definitionSha256, taskDefinitionKey, eligibleRoles, maximumAge, requesterTypes);
        }
        ApprovalConsumerRegistry.Registration registration() { return new ApprovalConsumerRegistry.Registration(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer), policyRef, releaseVersion, definitionKey, definitionSha256, taskDefinitionKey, eligibleRoles, maximumAge, requesterTypes); }
        @Override public String toString() { return "ApprovalVerificationConsumer[redacted]"; }
    }
}
