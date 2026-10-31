package top.egon.cola.platform.tianji.process.server.identity;

import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Internal current-user facts from the trusted task adapter. Constructing this value is not authentication or IAM evidence. */
public record HumanTaskContext(ProcessActor actor, String resourceKey, Set<String> roleIds, Instant deadlineAt, long authVersion, long policyVersion) {
    private static final Set<String> RESOURCES = Set.of("tianji.api.process.task.get", "tianji.api.process.task.query", "tianji.api.process.task.adminQuery", "tianji.api.process.task.claim",
            "tianji.api.process.task.unclaim", "tianji.api.process.task.transfer", "tianji.api.process.task.complete");
    public HumanTaskContext {
        actor = Objects.requireNonNull(actor, "actor"); roleIds = Set.copyOf(roleIds); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        if (actor.subjectType() != ProcessActor.SubjectType.USER || "LEGACY".equals(actor.issuerFingerprint()) || !RESOURCES.contains(resourceKey) || roleIds.size() > 256 || authVersion < 0 || policyVersion < 0) throw invalid();
        for (var id : roleIds) {
            try { if (!id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) < 1) throw invalid(); }
            catch (NumberFormatException malformed) { throw invalid(); }
        }
    }
    /** Native identity fields are bounded opaque keys; equal external subject strings in different realms cannot alias. */
    public String userKey() {
        return "user-" + ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_TASK_USER_V1", Long.toString(actor.tenantId()), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId()), Map.of());
    }
    public Set<String> candidateGroups() { return roleIds.stream().map(id -> "role:" + id).collect(Collectors.toUnmodifiableSet()); }
    public void requireResource(String expected) { if (!resourceKey.equals(expected)) throw new IllegalStateException("Human task context belongs to another operation"); }
    @Override public String toString() { return "HumanTaskContext[redacted]"; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Human task context is invalid"); }
}
