package top.egon.cola.platform.tianji.process.server.task;

import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;

import java.util.Objects;
import java.util.Set;

/** Pure task ownership/membership rules over already verified current-user and released native facts. It performs no IAM lookup or mutation. */
public final class HumanTaskAccessRules {
    private static final String PREFIX = "tianji.api.process.task.";
    private static final Set<String> READ = Set.of(PREFIX + "get", PREFIX + "query", PREFIX + "adminQuery");
    private HumanTaskAccessRules() { }

    public static boolean readable(HumanTaskContext context, Facts facts, HumanTask policy) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(facts, "facts"); Objects.requireNonNull(policy, "policy");
        if (!READ.contains(context.resourceKey())) throw new IllegalStateException("Task read context is required");
        if (!sameOrigin(context.actor(), facts.requester())) return false;
        if (context.resourceKey().equals(PREFIX + "adminQuery")) return true;
        if (context.userKey().equals(facts.assignee())) return true;
        return facts.assignee() == null && candidate(context, facts, policy) && !applicant(context, facts, policy);
    }

    public static void claim(HumanTaskContext context, Facts facts, HumanTask policy) {
        context.requireResource(PREFIX + "claim"); origin(context, facts); active(facts);
        if (!candidate(context, facts, policy) || applicant(context, facts, policy)) throw notFound();
        if (facts.assignee() != null) throw conflict();
    }

    public static void unclaim(HumanTaskContext context, Facts facts) {
        context.requireResource(PREFIX + "unclaim"); origin(context, facts); active(facts);
        if (!context.userKey().equals(facts.assignee())) throw notFound();
    }

    public static void transfer(HumanTaskContext context, Facts facts, HumanTask policy, String targetLane) {
        context.requireResource(PREFIX + "transfer"); origin(context, facts); active(facts);
        if (targetLane == null || !policy.lanes().containsKey(targetLane)) throw new IllegalArgumentException("Task transfer queue is not approved by the release");
    }

    public static void complete(HumanTaskContext context, Facts facts, HumanTask policy) {
        context.requireResource(PREFIX + "complete"); origin(context, facts); active(facts);
        if (!context.userKey().equals(facts.assignee()) || !candidate(context, facts, policy) || applicant(context, facts, policy)) throw notFound();
    }

    private static boolean candidate(HumanTaskContext context, Facts facts, HumanTask policy) {
        var roles = policy.lanes().get(facts.lane()); if (roles == null) throw new IllegalStateException("Task queue does not match the released policy");
        return roles.stream().anyMatch(context.roleIds()::contains);
    }
    private static boolean applicant(HumanTaskContext context, Facts facts, HumanTask policy) {
        return policy.kind() == HumanTaskKind.APPROVAL && facts.requester().subjectType() == ProcessActor.SubjectType.USER && context.actor().equals(facts.requester());
    }
    private static boolean sameOrigin(ProcessActor actor, ProcessActor requester) { return actor.tenantId() == requester.tenantId() && actor.issuerFingerprint().equals(requester.issuerFingerprint()) && !"LEGACY".equals(requester.issuerFingerprint()); }
    private static void origin(HumanTaskContext context, Facts facts) { if (!sameOrigin(context.actor(), facts.requester())) throw notFound(); }
    private static void active(Facts facts) { if (facts.suspended() || facts.delegated()) throw conflict(); }
    private static ProcessResourceNotFoundException notFound() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    /** Requester identity must come from the scoped START receipt, not a process variable or caller parameter. */
    public record Facts(ProcessActor requester, String lane, String assignee, boolean suspended, boolean delegated) {
        public Facts { Objects.requireNonNull(requester, "requester"); if (lane == null || !lane.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Task facts are invalid"); }
    }
}
