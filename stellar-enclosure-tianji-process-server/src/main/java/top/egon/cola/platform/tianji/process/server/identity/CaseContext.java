package top.egon.cola.platform.tianji.process.server.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Current verified operation facts. Constructing this value is neither authentication nor a permission grant. */
public record CaseContext(ProcessActor actor, String resourceKey, Set<String> roleIds, Instant deadlineAt, long authVersion, long policyVersion) {
    private static final Set<String> RESOURCES = Set.of("tianji.api.process.case.start", "tianji.api.process.case.get", "tianji.api.process.case.actPlanItem");
    public CaseContext {
        actor = Objects.requireNonNull(actor, "actor"); roleIds = Set.copyOf(roleIds); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        if ("LEGACY".equals(actor.issuerFingerprint()) || !RESOURCES.contains(resourceKey) || roleIds.size() > 256 || authVersion < 0 || policyVersion < 0
                || actor.subjectType() == ProcessActor.SubjectType.SERVICE && (!roleIds.isEmpty() || "tianji.api.process.case.actPlanItem".equals(resourceKey))) throw invalid();
        for (var id : roleIds) {
            try { if (!id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) < 1) throw invalid(); }
            catch (NumberFormatException malformed) { throw invalid(); }
        }
    }
    public void requireResource(String expected) { if (!resourceKey.equals(expected)) throw new IllegalStateException("Case context belongs to another operation"); }
    @Override public String toString() { return "CaseContext[redacted]"; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case context is invalid"); }
}
